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

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PbhSettingsTest {

    @Test
    public void defaults_areOutOfTheBoxEnabled() {
        PbhSettings s = PbhSettings.builder().build();
        assertTrue(s.enabled);
        assertTrue(s.antiVampireEnabled);
        assertTrue(s.pcbEnabled);
        assertTrue(s.clientNameBlacklistEnabled);
        assertEquals(0, s.banDurationMs); // permanent
    }

    @Test
    public void immutableBlacklists() {
        HashSet<String> src = new HashSet<>(Arrays.asList("a", "b"));
        PbhSettings s = PbhSettings.builder().clientNameBlacklist(src).build();
        src.clear(); // mutating the source must not affect the settings
        assertEquals(2, s.clientNameBlacklist.size());
        assertTrue(s.clientNameBlacklist.contains("a"));
    }

    @Test
    public void builderOverrides() {
        PbhSettings s = PbhSettings.builder()
                .enabled(false)
                .pcbMaximumDifference(0.5)
                .antiVampireUploadThreshold(999)
                .build();
        assertFalse(s.enabled);
        assertEquals(0.5, s.pcbMaximumDifference, 1e-9);
        assertEquals(999, s.antiVampireUploadThreshold);
    }
}