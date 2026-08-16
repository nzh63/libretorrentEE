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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClientNameBlacklistModuleTest {
    private final ClientNameBlacklistModule module = new ClientNameBlacklistModule();
    private final TorrentSnapshot torrent = new TorrentSnapshot("t1", "Torrent", 1000, 0,
            java.util.Collections.emptyList());

    @Test
    public void matchingClientName_isBanned() {
        PbhSettings settings = PbhSettings.builder()
                .clientNameBlacklist(java.util.Collections.singleton("Xunlei"))
                .build();
        PeerSnapshot peer = new PeerSnapshot("1.2.3.4", 6881, "Xunlei Thunder 5.0", 0, 0, 0, 0);

        BanResult result = module.check(torrent, peer, settings);

        assertTrue(result.shouldBan());
        assertEquals(BanResult.Action.BAN, result.action);
    }

    @Test
    public void caseInsensitiveMatch() {
        PbhSettings settings = PbhSettings.builder()
                .clientNameBlacklist(java.util.Collections.singleton("xunlei"))
                .build();
        PeerSnapshot peer = new PeerSnapshot("1.2.3.4", 6881, "XUNLEI Thunder", 0, 0, 0, 0);

        assertTrue(module.check(torrent, peer, settings).shouldBan());
    }

    @Test
    public void nonMatchingClientName_passes() {
        PbhSettings settings = PbhSettings.builder()
                .clientNameBlacklist(java.util.Collections.singleton("Xunlei"))
                .build();
        PeerSnapshot peer = new PeerSnapshot("1.2.3.4", 6881, "qBittorrent 4.5", 0, 0, 0, 0);

        assertFalse(module.check(torrent, peer, settings).shouldBan());
    }

    @Test
    public void emptyBlacklist_passes() {
        PbhSettings settings = PbhSettings.builder()
                .clientNameBlacklist(java.util.Collections.emptySet())
                .build();
        PeerSnapshot peer = new PeerSnapshot("1.2.3.4", 6881, "Xunlei", 0, 0, 0, 0);

        assertFalse(module.check(torrent, peer, settings).shouldBan());
    }

    @Test
    public void disabledModule_passes() {
        PbhSettings settings = PbhSettings.builder()
                .clientNameBlacklistEnabled(false)
                .clientNameBlacklist(java.util.Collections.singleton("Xunlei"))
                .build();
        PeerSnapshot peer = new PeerSnapshot("1.2.3.4", 6881, "Xunlei", 0, 0, 0, 0);

        assertFalse(module.check(torrent, peer, settings).shouldBan());
    }
}