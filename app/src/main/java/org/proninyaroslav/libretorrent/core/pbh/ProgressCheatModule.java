/*
 * Copyright (C) 2026
 *
 * This file is part of LibreTorrent.
 *
 * LibreTorrent is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LibreTorrent is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LibreTorrent.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.proninyaroslav.libretorrent.core.pbh;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.ConcurrentHashMap;

/*
 * PBH "ProgressCheatBlocker" equivalent. Detects peers that fake their
 * download progress by comparing the progress the client reports against the
 * progress implied by how much data we have uploaded to them.
 *
 * Sub-checks (mirroring PeerBanHelper):
 *  1. excessiveClient  - peer received more upload than the torrent allows.
 *  2. differenceTest   - reported progress differs from computed progress by
 *                        more than `maximumDifference` (with a ban-delay window).
 *  3. progressRewind   - peer reports lower progress than it reported before.
 *  4. fastPcbTest      - proactive disconnect probe on suspected cheaters.
 *
 * State is keyed by (torrentId, ip) and (torrentId, prefix) so that a peer
 * that changes IP cannot reset its tracked upload across the same prefix.
 */
public final class ProgressCheatModule implements BanModule {
    /* Keyed by "torrentId|ip" and "torrentId|prefix" */
    private final ConcurrentHashMap<String, PcbTrackState> addrStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PcbTrackState> prefixStates = new ConcurrentHashMap<>();

    @NonNull
    @Override
    public String name() {
        return "ProgressCheatBlocker";
    }

    @NonNull
    @Override
    public BanResult check(@NonNull TorrentSnapshot torrent,
                           @NonNull PeerSnapshot peer,
                           @NonNull PbhSettings settings) {
        if (!settings.pcbEnabled)
            return BanResult.pass(name(), peer.ip);
        if (torrent.totalSize <= 0)
            return BanResult.pass(name(), peer.ip);

        byte[] ipBytes = IpUtils.parseIp(peer.ip);
        if (ipBytes == null || !IpUtils.isTrackableAddress(ipBytes))
            return BanResult.pass(name(), peer.ip);

        int prefixLength = IpUtils.isIpv4(ipBytes)
                ? settings.pcbIpv4PrefixLength
                : settings.pcbIpv6PrefixLength;
        byte[] prefixBytes = IpUtils.toPrefixBlock(ipBytes, prefixLength);
        String prefixStr = IpUtils.formatIp(prefixBytes) + "/" + prefixLength;

        String addrKey = key(torrent.id, peer.ip);
        String prefixKey = key(torrent.id, prefixStr);
        PcbTrackState addrState = addrStates.computeIfAbsent(addrKey, k -> new PcbTrackState());
        PcbTrackState prefixState = prefixStates.computeIfAbsent(prefixKey, k -> new PcbTrackState());

        long nowMs = System.currentTimeMillis();
        long torrentSize = torrent.totalSize;
        long completedSize = torrent.completedSize;
        long computedCompletedSize = Math.max(completedSize,
                Math.max(prefixState.lastTorrentCompletedSize, addrState.lastTorrentCompletedSize));

        // A peer we are not uploading to (and never have) cannot be a progress
        // cheat from our perspective.
        if (!peer.isUploadingToPeer())
            return BanResult.pass(name(), peer.ip);

        // Computed upload: take the max of the client-reported total upload and
        // the accumulated deltas we tracked (peer + prefix).
        long computedUploaded = Math.max(peer.totalUpload,
                Math.max(addrState.trackingUploadedIncreaseTotal,
                        prefixState.trackingUploadedIncreaseTotal));

        // 1. Excessive client: client claims to have received more data than
        //    the torrent size (+ threshold) allows.
        if (settings.pcbBlockExcessiveClients && computedUploaded != -1) {
            long maxAllowed = (long) (Math.max(torrentSize, settings.pcbTorrentMinimumSize)
                    * settings.pcbExcessiveThreshold);
            if (computedUploaded > maxAllowed) {
                addrState.resetBanDelayWindow();
                prefixState.resetBanDelayWindow();
                record(addrState, prefixState, peer, computedCompletedSize, nowMs);
                return BanResult.ban(name(), peer.ip,
                        "excessive client: uploaded " + computedUploaded
                                + " > max allowed " + maxAllowed);
            }

            // 1b. Excessive client on an incomplete task: while we are still
            //     downloading, a peer cannot have received more from us than
            //     we have completed ourselves (+ threshold).
            if (computedCompletedSize > 0 && computedUploaded > computedCompletedSize) {
                long maxAllowedIncomplete = (long) (Math.max(computedCompletedSize,
                        settings.pcbTorrentMinimumSize)
                        * settings.pcbExcessiveThreshold);
                if (computedUploaded > maxAllowedIncomplete) {
                    addrState.resetBanDelayWindow();
                    prefixState.resetBanDelayWindow();
                    record(addrState, prefixState, peer, computedCompletedSize, nowMs);
                    return BanResult.ban(name(), peer.ip,
                            "excessive client (incomplete task): uploaded " + computedUploaded
                                    + " > completed " + computedCompletedSize
                                    + " * threshold " + maxAllowedIncomplete);
                }
            }
        }

        // 2. Fast PCB test: proactively disconnect a peer that has already
        //    received a large fraction of the torrent in upload, to probe
        //    whether it is a real client.
        if (settings.pcbFastPcbTestPercentage > 0
                && !fileTooSmall(torrentSize, settings.pcbTorrentMinimumSize)
                && !addrState.fastPcbTestExecuted.get()
                && !prefixState.fastPcbTestExecuted.get()) {
            if (computedUploaded >= settings.pcbFastPcbTestPercentage * torrentSize) {
                addrState.fastPcbTestExecuted.set(true);
                prefixState.fastPcbTestExecuted.set(true);
                addrState.fastPcbTestExecutedAtMs = nowMs;
                prefixState.fastPcbTestExecutedAtMs = nowMs;
                record(addrState, prefixState, peer, computedCompletedSize, nowMs);
                return BanResult.banForDisconnect(name(), peer.ip,
                        "fast-PCB probe: uploaded " + computedUploaded
                                + " >= " + (settings.pcbFastPcbTestPercentage * torrentSize));
            }
        }

        final double computedProgress = (double) computedUploaded / torrentSize;
        final double clientReportedProgress = peer.progress();

        // If the client reports at least as much progress as we computed, it
        // is not under-reporting; skip the difference/rewind checks.
        if (computedProgress <= clientReportedProgress) {
            record(addrState, prefixState, peer, computedCompletedSize, nowMs);
            return BanResult.pass(name(), peer.ip);
        }

        // 3. Difference test: reported progress lags computed progress by too much.
        double difference = computedProgress - clientReportedProgress;
        if (difference > settings.pcbMaximumDifference
                && !fileTooSmall(torrentSize, settings.pcbTorrentMinimumSize)
                && peer.isUploadingToPeer()) {
            if (!addrState.hasBanDelayWindowScheduled() && !prefixState.hasBanDelayWindowScheduled()) {
                addrState.scheduleBanDelayWindow(settings.pcbBanDelayDurationMs, nowMs);
                prefixState.scheduleBanDelayWindow(settings.pcbBanDelayDurationMs, nowMs);
                record(addrState, prefixState, peer, computedCompletedSize, nowMs);
                return BanResult.pass(name(), peer.ip);
            }
            if (addrState.isBanDelayWindowExpired(nowMs) || prefixState.isBanDelayWindowExpired(nowMs)) {
                addrState.progressDifferenceCounter++;
                prefixState.progressDifferenceCounter++;
                addrState.resetBanDelayWindow();
                prefixState.resetBanDelayWindow();
                record(addrState, prefixState, peer, computedCompletedSize, nowMs);
                return BanResult.ban(name(), peer.ip,
                        "progress difference " + percent(difference)
                                + " > max " + percent(settings.pcbMaximumDifference));
            }
        }

        // 4. Progress rewind: peer reported a higher progress before.
        if (settings.pcbRewindMaximumDifference > 0
                && !fileTooSmall(torrentSize, settings.pcbTorrentMinimumSize)) {
            double lastReportProgress = Math.max(addrState.lastReportProgress, prefixState.lastReportProgress);
            if (lastReportProgress > 0.0d) {
                double rewind = lastReportProgress - clientReportedProgress;
                if (rewind > settings.pcbRewindMaximumDifference) {
                    if (peer.isUploadingToPeer()) {
                        addrState.rewindCounter++;
                        prefixState.rewindCounter++;
                        addrState.resetBanDelayWindow();
                        prefixState.resetBanDelayWindow();
                        record(addrState, prefixState, peer, computedCompletedSize, nowMs);
                        return BanResult.ban(name(), peer.ip,
                                "progress rewind " + percent(rewind)
                                        + " > max " + percent(settings.pcbRewindMaximumDifference));
                    } else {
                        if (!addrState.hasBanDelayWindowScheduled() && !prefixState.hasBanDelayWindowScheduled()) {
                            addrState.scheduleBanDelayWindow(settings.pcbBanDelayDurationMs, nowMs);
                            prefixState.scheduleBanDelayWindow(settings.pcbBanDelayDurationMs, nowMs);
                        }
                    }
                }
            }
        }

        record(addrState, prefixState, peer, computedCompletedSize, nowMs);
        return BanResult.pass(name(), peer.ip);
    }

    private static void record(PcbTrackState addrState,
                               PcbTrackState prefixState,
                               PeerSnapshot peer,
                               long computedCompletedSize,
                               long nowMs) {
        addrState.record(peer.progress(), peer.totalUpload, computedCompletedSize, nowMs);
        prefixState.record(peer.progress(), peer.totalUpload, computedCompletedSize, nowMs);
    }

    private static boolean fileTooSmall(long torrentSize, long minimumSize) {
        return torrentSize < minimumSize;
    }

    private static String percent(double d) {
        return String.format(java.util.Locale.ROOT, "%.2f%%", d * 100.0d);
    }

    private static String key(String torrentId, String ip) {
        return torrentId + "|" + ip;
    }

    /* For tests / diagnostics */
    @Nullable
    public PcbTrackState getAddrState(String torrentId, String ip) {
        return addrStates.get(key(torrentId, ip));
    }

    @Nullable
    public PcbTrackState getPrefixState(String torrentId, String prefix) {
        return prefixStates.get(key(torrentId, prefix));
    }

    public int addrStateCount() {
        return addrStates.size();
    }

    public int prefixStateCount() {
        return prefixStates.size();
    }

    public void clear() {
        addrStates.clear();
        prefixStates.clear();
    }

    /*
     * Removes tracking states that have not been updated for longer than
     * maxAgeMs (based on the last time the peer was seen, falling back to the
     * state creation time when the peer never uploaded anything). Without
     * this, the state maps grow without bound on long-running sessions.
     */
    public void evictStale(long maxAgeMs, long nowMs) {
        addrStates.entrySet().removeIf(e -> {
            PcbTrackState s = e.getValue();
            long last = Math.max(s.lastTimeSeenMs, s.createdAtMs);
            return nowMs - last > maxAgeMs;
        });
        prefixStates.entrySet().removeIf(e -> {
            PcbTrackState s = e.getValue();
            long last = Math.max(s.lastTimeSeenMs, s.createdAtMs);
            return nowMs - last > maxAgeMs;
        });
    }

    /* Removes all tracking state of the given torrent (e.g. after deletion). */
    public void evictTorrent(String torrentId) {
        String prefix = torrentId + "|";
        addrStates.keySet().removeIf(k -> k.startsWith(prefix));
        prefixStates.keySet().removeIf(k -> k.startsWith(prefix));
    }
}