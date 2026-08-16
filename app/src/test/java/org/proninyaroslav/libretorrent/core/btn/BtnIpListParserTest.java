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

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BtnIpListParserTest {

    private final String list =
            """
                    # IPV4
                    180.113.146.249
                    # IPV4 CIDR
                    127.0.0.1/24
                    # IPV6
                    2001:da8:1026:2f00::1
                    # IPV6 CIDR
                    2001:da8:1026:2f00::/56
                    // Java-style comment
                    192.168.0.1
                    """;

    @Test
    public void parsesIpsAndCidrs_skipsComments() {
        Set<String> ips = BtnIpListParser.parse(list);
        assertTrue(ips.contains("180.113.146.249"));
        assertTrue(ips.contains("127.0.0.1/24"));
        assertTrue(ips.contains("2001:da8:1026:2f00::1"));
        assertTrue(ips.contains("2001:da8:1026:2f00::/56"));
        assertTrue(ips.contains("192.168.0.1"));
        assertEquals(5, ips.size());
    }

    @Test
    public void emptyBody_returnsEmpty() {
        assertTrue(BtnIpListParser.parse("").isEmpty());
        assertTrue(BtnIpListParser.parse("   \n # only comments\n").isEmpty());
    }

    @Test
    public void invalidEntries_areSkipped() {
        String body = "not-an-ip\n999.999.999.999\n10.0.0.0/99\n10.0.0.0/abc\n";
        Set<String> ips = BtnIpListParser.parse(body);
        assertTrue(ips.isEmpty());
    }

    @Test
    public void inlineComments_stripped() {
        Set<String> ips = BtnIpListParser.parse("1.2.3.4 # trailing\n5.6.7.8 // trailing\n");
        assertTrue(ips.contains("1.2.3.4"));
        assertTrue(ips.contains("5.6.7.8"));
    }
}