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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProgressCheatModuleTest {
    private final ProgressCheatModule module = new ProgressCheatModule();

    private final long size = 100L * 1024 * 1024; /* 100 MiB */

    private PeerSnapshot peer(String ip, long uploaded, int progressPpm, int upSpeed) {
        return new PeerSnapshot(ip, 6881, "client", uploaded, 0, progressPpm, upSpeed, 0);
    }

    private TorrentSnapshot torrent() {
        return new TorrentSnapshot("t1", "Torrent", size, 0, false, Collections.emptyList());
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
        TorrentSnapshot small = new TorrentSnapshot("t1", "small", 1024, 0, false, Collections.emptyList());
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

    @Test
    public void incompleteTorrent_excessiveUpload_banned() {
        /*
         * We have completed 50 MiB of the 100 MiB torrent but uploaded
         * 65 MiB to the peer: below the torrent-size threshold (120 MiB)
         * yet above the completed-size threshold (60 MiB) -> the
         * incomplete-task branch must catch it.
         */
        TorrentSnapshot halfDone = new TorrentSnapshot("t1", "Torrent",
                size, size / 2, false, Collections.emptyList());
        PeerSnapshot p = peer("8.8.8.8", size * 65 / 100, 0, 100);
        BanResult r = module.check(halfDone, p, defaultSettings());
        assertTrue(r.shouldBan());
        assertTrue(r.reason.contains("incomplete task"));
    }

    @Test
    public void incompleteTorrent_normalUpload_passes() {
        TorrentSnapshot halfDone = new TorrentSnapshot("t1", "Torrent",
                size, size / 2, false, Collections.emptyList());
        PeerSnapshot p = peer("8.8.8.8", size * 55 / 100, 600_000, 100);
        assertFalse(module.check(halfDone, p, defaultSettings()).shouldBan());
    }

    @Test
    public void evictStale_dropsOnlyOldEntries() {
        module.check(torrent(), peer("8.8.8.8", 1000, 500_000, 100), defaultSettings());
        assertTrue(module.addrStateCount() > 0);

        // Fresh entries survive
        module.evictStale(60_000L, System.currentTimeMillis());
        assertEquals(1, module.addrStateCount());

        // Entries older than the TTL are dropped
        module.evictStale(60_000L, System.currentTimeMillis() + 120_000L);
        assertEquals(0, module.addrStateCount());
        assertEquals(0, module.prefixStateCount());
    }

    @Test
    public void evictTorrent_dropsThatTorrentOnly() {
        module.check(torrent(), peer("8.8.8.8", 1000, 500_000, 100), defaultSettings());
        TorrentSnapshot other = new TorrentSnapshot("t2", "T", size, 0, false,
                Collections.emptyList());
        module.check(other, peer("9.9.9.9", 1000, 500_000, 100), defaultSettings());
        assertEquals(2, module.addrStateCount());

        module.evictTorrent("t1");
        assertEquals(1, module.addrStateCount());
        assertNull(module.getAddrState("t1", "8.8.8.8"));
        assertNotNull(module.getAddrState("t2", "9.9.9.9"));
    }
}