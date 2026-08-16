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

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProgressCheatModuleTest {
    private final ProgressCheatModule module = new ProgressCheatModule();

    private final long size = 100L * 1024 * 1024; /* 100 MiB */

    private PeerSnapshot peer(String ip, long uploaded, int progressPpm, int upSpeed) {
        return new PeerSnapshot(ip, 6881, "client", uploaded, 0, progressPpm, upSpeed);
    }

    private TorrentSnapshot torrent() {
        return new TorrentSnapshot("t1", "Torrent", size, 0, Collections.emptyList());
    }

    private PbhSettings defaultSettings() {
        return PbhSettings.builder()
                .pcbTorrentMinimumSize(1L * 1024 * 1024)
                .pcbBlockExcessiveClients(true)
                .pcbExcessiveThreshold(1.2d)
                .pcbMaximumDifference(0.1d)
                .pcbRewindMaximumDifference(0.05d)
                .pcbBanDelayDurationMs(0) // ban immediately
                .pcbFastPcbTestPercentage(-1) // disable probe
                .pcbIpv4PrefixLength(24)
                .pcbIpv6PrefixLength(64)
                .build();
    }

    @Test
    public void notUploading_passes() {
        // Nothing uploaded, no upload speed -> cannot be a cheat from our side.
        PeerSnapshot p = peer("8.8.8.8", 0, 0, 0);
        BanResult r = module.check(torrent(), p, defaultSettings());
        assertFalse(r.shouldBan());
    }

    @Test
    public void excessiveClient_banned() {
        // Uploaded more than 1.2 * torrent size while reporting 0 progress.
        PeerSnapshot p = peer("8.8.8.8", (long) (size * 1.5), 0, 100);
        BanResult r = module.check(torrent(), p, defaultSettings());
        assertTrue(r.shouldBan());
        assertEquals(BanResult.Action.BAN, r.action);
    }

    @Test
    public void consistentProgress_passes() {
        // Uploaded 50% of torrent, reports 50% progress.
        PeerSnapshot p = peer("8.8.8.8", size / 2, 500_000, 100);
        assertFalse(module.check(torrent(), p, defaultSettings()).shouldBan());
    }

    @Test
    public void progressDifference_banAfterDelayWindow() {
        // Uploaded 50% of torrent but reports only 5% progress.
        PeerSnapshot p1 = peer("8.8.8.8", size / 2, 50_000, 100);
        // First scan: schedules the ban-delay window, does not ban yet.
        BanResult first = module.check(torrent(), p1, defaultSettings());
        assertFalse(first.shouldBan());

        // Second scan with the same lagging report: ban-delay window is
        // considered expired (duration 0), so it bans.
        BanResult second = module.check(torrent(), p1, defaultSettings());
        assertTrue(second.shouldBan());
    }

    @Test
    public void progressRewind_banned() {
        // Use a high maximumDifference so the difference check does not
        // preempt the rewind check.
        PbhSettings settings = PbhSettings.builder()
                .pcbTorrentMinimumSize(1L * 1024 * 1024)
                .pcbBlockExcessiveClients(true)
                .pcbExcessiveThreshold(1.2d)
                .pcbMaximumDifference(0.5d)
                .pcbRewindMaximumDifference(0.05d)
                .pcbBanDelayDurationMs(0)
                .pcbFastPcbTestPercentage(-1)
                .pcbIpv4PrefixLength(24)
                .pcbIpv6PrefixLength(64)
                .build();

        // Peer first reports 40% progress.
        module.check(torrent(), peer("8.8.8.8", size / 2, 400_000, 100), settings);
        // Then drops to 10% while we keep uploading -> rewind detected.
        PeerSnapshot p = peer("8.8.8.8", size / 2, 100_000, 100);
        BanResult r = module.check(torrent(), p, settings);
        assertTrue(r.shouldBan());
    }

    @Test
    public void fastPcbProbe_disconnectAction() {
        PbhSettings settings = defaultSettings();
        PbhSettings withProbe = PbhSettings.builder()
                .pcbTorrentMinimumSize(1L * 1024 * 1024)
                .pcbBlockExcessiveClients(true)
                .pcbExcessiveThreshold(1.2d)
                .pcbMaximumDifference(0.1d)
                .pcbRewindMaximumDifference(0.05d)
                .pcbBanDelayDurationMs(0)
                .pcbFastPcbTestPercentage(0.1d) // enable probe
                .pcbFastPcbTestBlockingDurationMs(1000)
                .pcbIpv4PrefixLength(24)
                .pcbIpv6PrefixLength(64)
                .build();

        // Uploaded 20% of torrent (>= 10% probe threshold), reports 0%.
        PeerSnapshot p = peer("8.8.8.8", (long) (size * 0.2), 0, 100);
        BanResult r = module.check(torrent(), p, withProbe);
        assertTrue(r.shouldBan());
        assertEquals(BanResult.Action.BAN_FOR_DISCONNECT, r.action);
    }

    @Test
    public void smallTorrent_passes() {
        // Torrent smaller than the minimum size: skip difference checks.
        TorrentSnapshot small = new TorrentSnapshot("t1", "small", 1024, 0, Collections.emptyList());
        PeerSnapshot p = peer("8.8.8.8", 512, 0, 100);
        assertFalse(module.check(small, p, defaultSettings()).shouldBan());
    }

    @Test
    public void disabledModule_passes() {
        PbhSettings settings = defaultSettings();
        PbhSettings disabled = PbhSettings.builder()
                .pcbEnabled(false)
                .pcbTorrentMinimumSize(1L * 1024 * 1024)
                .pcbBlockExcessiveClients(true)
                .build();
        PeerSnapshot p = peer("8.8.8.8", (long) (size * 1.5), 0, 100);
        assertFalse(module.check(torrent(), p, disabled).shouldBan());
    }

    @Test
    public void loopbackOrInvalidIp_passes() {
        PeerSnapshot p = peer("127.0.0.1", (long) (size * 1.5), 0, 100);
        assertFalse(module.check(torrent(), p, defaultSettings()).shouldBan());
    }
}