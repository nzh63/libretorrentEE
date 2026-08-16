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
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/*
 * Regression tests for the user-agent / client-name blacklist matching
 * (single implementation shared by the manual blacklist enforcement).
 */
public class ClientNameMatcherTest {

    @Test
    public void substringMatch_caseInsensitive() {
        assertTrue(ClientNameMatcher.matches(
                "Xunlei 0.1.9", Collections.singleton("xunlei")));
        assertTrue(ClientNameMatcher.matches(
                "some XL0019 build", new HashSet<>(Arrays.asList("xl0019"))));
        assertFalse(ClientNameMatcher.matches(
                "qBittorrent 4.6", Collections.singleton("xunlei")));
    }

    @Test
    public void emptyOrNullInputs_neverMatch() {
        assertFalse(ClientNameMatcher.matches(null, Collections.singleton("a")));
        assertFalse(ClientNameMatcher.matches("client", null));
        assertFalse(ClientNameMatcher.matches("client", Collections.emptySet()));
        assertFalse(ClientNameMatcher.matches("client",
                new HashSet<>(Arrays.asList("", null))));
    }

    @Test
    public void multiplePatterns_anyMatch() {
        assertTrue(ClientNameMatcher.matches(
                "gopeed dev", new HashSet<>(Arrays.asList("xunlei", "gopeed"))));
    }
}
