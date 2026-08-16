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

package org.proninyaroslav.libretorrent.core.btn;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TorrentIdentifierTest {

    @Test
    public void knownExample_matchesSpec() {
        // From the BTN-Spec: known input/output pair.
        String input = "a5b24a285c3533d80ce62181813640ac4a0e6ed7";
        String expected = "52fa13494a4571a951b46b1a04be19ab9d8089c3d3761956c99f5435e6f2c8ad";
        assertEquals(expected, TorrentIdentifier.getHashedIdentifier(input));
    }

    @Test
    public void id_isLowercase() {
        String id = TorrentIdentifier.getHashedIdentifier("A5B24A285C3533D80CE62181813640AC4A0E6ED7");
        assertEquals(id.toLowerCase(), id);
    }

    @Test
    public void id_isStableAndLength() {
        String a = TorrentIdentifier.getHashedIdentifier("a5b24a285c3533d80ce62181813640ac4a0e6ed7");
        String b = TorrentIdentifier.getHashedIdentifier("a5b24a285c3533d80ce62181813640ac4a0e6ed7");
        assertEquals(a, b);
        assertEquals(64, a.length()); // sha256 hex
    }

    @Test
    public void differentHashes_differentIdentifiers() {
        String a = TorrentIdentifier.getHashedIdentifier("a5b24a285c3533d80ce62181813640ac4a0e6ed7");
        String b = TorrentIdentifier.getHashedIdentifier("ffffffffffffffffffffffffffffffffffffffff");
        assertEquals(a.equals(b), false);
    }
}