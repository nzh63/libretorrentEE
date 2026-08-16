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

import java.util.concurrent.atomic.AtomicBoolean;

/*
 * In-memory tracking state for the Progress Cheat Blocker. In PeerBanHelper
 * this is persisted in a database; here we keep it in memory keyed by
 * (torrent id, ip) and (torrent id, prefix). Mirrors the PCB fields that the
 * anti-cheat logic depends on.
 */
public class PcbTrackState {
    /* Last progress reported by the peer (fraction 0..1); -1 = never reported */
    public volatile double lastReportProgress = -1.0d;
    /* Last upload value reported by the client for this peer */
    public volatile long lastReportUploaded = 0L;
    /* Accumulated upload delta observed across scans */
    public volatile long trackingUploadedIncreaseTotal = 0L;
    /* Largest torrent completed size observed */
    public volatile long lastTorrentCompletedSize = 0L;
    /* Time (epoch millis) the peer was last seen */
    public volatile long lastTimeSeenMs = 0L;
    /* Ban-delay window end (epoch millis); 0 = not scheduled */
    public volatile long banDelayWindowEndMs = 0L;
    /* Whether the fast-PCB probe has been executed for this key */
    public final AtomicBoolean fastPcbTestExecuted = new AtomicBoolean(false);
    public volatile long fastPcbTestExecutedAtMs = 0L;

    /* Weak-path progress counters (kept for parity with PBH diagnostics) */
    public volatile int progressDifferenceCounter = 0;
    public volatile int rewindCounter = 0;

    public boolean hasBanDelayWindowScheduled() {
        return banDelayWindowEndMs > 0;
    }

    public boolean isBanDelayWindowExpired(long nowMs) {
        return banDelayWindowEndMs > 0 && banDelayWindowEndMs <= nowMs;
    }

    public void scheduleBanDelayWindow(long durationMs, long nowMs) {
        if (banDelayWindowEndMs <= 0)
            banDelayWindowEndMs = nowMs + Math.max(0, durationMs);
    }

    public void resetBanDelayWindow() {
        banDelayWindowEndMs = 0;
    }

    /* Record the peer's report and serialise the tracking state. */
    public void record(double progress, long uploaded, long torrentCompletedSize, long nowMs) {
        if (progress > 0.0d)
            lastReportProgress = progress;
        if (uploaded < lastReportUploaded) {
            trackingUploadedIncreaseTotal += uploaded;
        } else {
            trackingUploadedIncreaseTotal += (uploaded - lastReportUploaded);
        }
        lastReportUploaded = uploaded;
        lastTorrentCompletedSize = Math.max(torrentCompletedSize, lastTorrentCompletedSize);
        lastTimeSeenMs = nowMs;
    }
}