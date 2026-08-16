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

public class IpAddressBlacklistModuleTest {
    private final IpAddressBlacklistModule module = new IpAddressBlacklistModule();
    private final TorrentSnapshot torrent = new TorrentSnapshot("t1", "Torrent", 1000, 0, false,
            Collections.emptyList());

    @Test
    public void exactIpMatch_banned() {
        PbhSettings settings = PbhSettings.builder()
                .ipCidrBlacklist(Collections.singleton("1.2.3.4"))
                .build();
        PeerSnapshot peer = new PeerSnapshot("1.2.3.4", 6881, "client", "", 0, 0, 0, 0, 0);

        assertTrue(module.check(torrent, peer, settings).shouldBan());
    }

    @Test
    public void subnetMatch_banned() {
        PbhSettings settings = PbhSettings.builder()
                .ipCidrBlacklist(Collections.singleton("10.0.0.0/8"))
                .build();
        PeerSnapshot peer = new PeerSnapshot("10.5.5.5", 6881, "client", "", 0, 0, 0, 0, 0);

        assertTrue(module.check(torrent, peer, settings).shouldBan());
    }

    @Test
    public void nonMatchingIp_passes() {
        PbhSettings settings = PbhSettings.builder()
                .ipCidrBlacklist(Collections.singleton("10.0.0.0/8"))
                .build();
        PeerSnapshot peer = new PeerSnapshot("8.8.8.8", 6881, "client", "", 0, 0, 0, 0, 0);

        assertFalse(module.check(torrent, peer, settings).shouldBan());
    }

    @Test
    public void emptyBlacklist_passes() {
        PbhSettings settings = PbhSettings.builder()
                .ipCidrBlacklist(Collections.emptySet())
                .build();
        PeerSnapshot peer = new PeerSnapshot("1.2.3.4", 6881, "client", "", 0, 0, 0, 0, 0);

        assertFalse(module.check(torrent, peer, settings).shouldBan());
    }
}