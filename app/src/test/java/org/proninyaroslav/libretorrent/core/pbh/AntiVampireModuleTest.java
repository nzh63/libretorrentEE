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

public class AntiVampireModuleTest {
    private final AntiVampireModule module = new AntiVampireModule();
    private final TorrentSnapshot torrent = new TorrentSnapshot("t1", "Torrent", 1000, 0,
            Collections.emptyList());

    @Test
    public void highUploadZeroProgress_banned() {
        PbhSettings settings = PbhSettings.builder()
                .antiVampireUploadThreshold(100)
                .antiVampireMinProgressPpm(1000)
                .build();
        PeerSnapshot peer = new PeerSnapshot("1.2.3.4", 6881, "client", 10_000, 0, 0, 0);

        BanResult result = module.check(torrent, peer, settings);

        assertTrue(result.shouldBan());
        assertEquals(BanResult.Action.BAN, result.action);
    }

    @Test
    public void lowUpload_passes() {
        PbhSettings settings = PbhSettings.builder()
                .antiVampireUploadThreshold(100)
                .antiVampireMinProgressPpm(1000)
                .build();
        PeerSnapshot peer = new PeerSnapshot("1.2.3.4", 6881, "client", 50, 0, 0, 0);

        assertFalse(module.check(torrent, peer, settings).shouldBan());
    }

    @Test
    public void highUploadButRealProgress_passes() {
        PbhSettings settings = PbhSettings.builder()
                .antiVampireUploadThreshold(100)
                .antiVampireMinProgressPpm(1000)
                .build();
        // 50% progress reported -> not a vampire
        PeerSnapshot peer = new PeerSnapshot("1.2.3.4", 6881, "client", 10_000, 0, 500_000, 0);

        assertFalse(module.check(torrent, peer, settings).shouldBan());
    }

    @Test
    public void boundaryJustBelowThreshold_passes() {
        PbhSettings settings = PbhSettings.builder()
                .antiVampireUploadThreshold(100)
                .antiVampireMinProgressPpm(1000)
                .build();
        PeerSnapshot peer = new PeerSnapshot("1.2.3.4", 6881, "client", 100, 0, 0, 0);

        // upload == threshold, not strictly greater -> pass
        assertFalse(module.check(torrent, peer, settings).shouldBan());
    }

    @Test
    public void disabledModule_passes() {
        PbhSettings settings = PbhSettings.builder()
                .antiVampireEnabled(false)
                .antiVampireUploadThreshold(100)
                .antiVampireMinProgressPpm(1000)
                .build();
        PeerSnapshot peer = new PeerSnapshot("1.2.3.4", 6881, "client", 10_000, 0, 0, 0);

        assertFalse(module.check(torrent, peer, settings).shouldBan());
    }
}