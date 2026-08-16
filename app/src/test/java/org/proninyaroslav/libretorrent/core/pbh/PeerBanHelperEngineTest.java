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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PeerBanHelperEngineTest {

    private PbhSettings settings() {
        return PbhSettings.builder()
                .pcbTorrentMinimumSize(1L * 1024 * 1024)
                .pcbBlockExcessiveClients(true)
                .pcbExcessiveThreshold(1.2d)
                .pcbMaximumDifference(0.1d)
                .pcbRewindMaximumDifference(0.05d)
                .pcbBanDelayDurationMs(0)
                .pcbFastPcbTestPercentage(-1)
                .pcbIpv4PrefixLength(24)
                .pcbIpv6PrefixLength(64)
                .antiVampireUploadThreshold(100)
                .antiVampireMinProgressPpm(1000)
                .clientNameBlacklist(Collections.singleton("Xunlei"))
                .build();
    }

    @Test
    public void disabledEngine_returnsNothing() {
        PeerBanHelperEngine engine = new PeerBanHelperEngine();
        PbhSettings off = PbhSettings.builder().enabled(false).build();
        List<TorrentSnapshot> torrents = Collections.singletonList(
                snapshot(banPeers()));
        List<BanResult> results = engine.evaluate(torrents, off);
        assertTrue(results.isEmpty());
    }

    @Test
    public void evaluate_returnsBannedIpsAcrossModules() {
        PeerBanHelperEngine engine = new PeerBanHelperEngine();
        List<TorrentSnapshot> torrents = Collections.singletonList(
                snapshot(banPeers()));
        Set<String> banned = engine.evaluateBanSet(torrents, settings());

        // Vampire IP, client-name IP, excessive-client IP.
        assertTrue(banned.contains("1.1.1.1"));
        assertTrue(banned.contains("2.2.2.2"));
        assertTrue(banned.contains("3.3.3.3"));
    }

    @Test
    public void evaluate_ignoresGoodPeers() {
        PeerBanHelperEngine engine = new PeerBanHelperEngine();
        PeerSnapshot good = new PeerSnapshot("8.8.8.8", 6881, "qBittorrent", 50L * 1024 * 1024, 0, 900_000, 100);
        List<TorrentSnapshot> torrents = Collections.singletonList(
                new TorrentSnapshot("t1", "T", 100L * 1024 * 1024, 0, Collections.singletonList(good)));
        Set<String> banned = engine.evaluateBanSet(torrents, settings());
        assertTrue(banned.isEmpty());
    }

    @Test
    public void evaluateGrouped_groupsByModule() {
        PeerBanHelperEngine engine = new PeerBanHelperEngine();
        List<TorrentSnapshot> torrents = Collections.singletonList(
                snapshot(banPeers()));
        var grouped = engine.evaluateGrouped(torrents, settings());
        assertTrue(grouped.containsKey("AntiVampire"));
        assertTrue(grouped.containsKey("ClientNameBlacklist"));
        assertTrue(grouped.containsKey("ProgressCheatBlocker"));
    }

    private TorrentSnapshot snapshot(List<PeerSnapshot> peers) {
        return new TorrentSnapshot("t1", "T", 100L * 1024 * 1024, 0, peers);
    }

    private List<PeerSnapshot> banPeers() {
        List<PeerSnapshot> peers = new ArrayList<>();
        // Vampire: high upload, zero progress.
        peers.add(new PeerSnapshot("1.1.1.1", 6881, "client", 10_000, 0, 0, 100));
        // Client name blacklist match.
        peers.add(new PeerSnapshot("2.2.2.2", 6881, "Xunlei Thunder", 10, 0, 0, 0));
        // Excessive client: uploaded more than torrent size.
        peers.add(new PeerSnapshot("3.3.3.3", 6881, "client", 150L * 1024 * 1024, 0, 0, 100));
        return peers;
    }
}