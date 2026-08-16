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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class IpUtilsTest {

    @Test
    public void parseIp_validIpv4() {
        byte[] bytes = IpUtils.parseIp("192.168.1.1");
        assertNotNull(bytes);
        assertEquals(4, bytes.length);
    }

    @Test
    public void parseIp_validIpv6() {
        byte[] bytes = IpUtils.parseIp("2001:db8::1");
        assertNotNull(bytes);
        assertEquals(16, bytes.length);
    }

    @Test
    public void parseIp_invalid_returnsNull() {
        assertNull(IpUtils.parseIp("not-an-ip"));
        assertNull(IpUtils.parseIp(""));
        assertNull(IpUtils.parseIp("999.999.999.999"));
    }

    @Test
    public void stripPort_ipv4WithPort() {
        assertEquals("1.2.3.4", IpUtils.stripPort("1.2.3.4:6881"));
        assertEquals("1.2.3.4", IpUtils.stripPort("1.2.3.4"));
    }

    @Test
    public void stripPort_bareIpv6NotMangled() {
        // Bare IPv6 with no port should be returned untouched.
        String ipv6 = "2001:db8::1";
        assertEquals(ipv6, IpUtils.stripPort(ipv6));
    }

    @Test
    public void stripPort_stripsLibtorrentEndpoint() {
        // libtorrent exposes peer endpoints with a transport prefix and port.
        assertEquals("60.188.85.163", IpUtils.stripPort("tcp://60.188.85.163:34729"));
        assertEquals("60.188.85.163", IpUtils.stripPort("utp://60.188.85.163:34729"));
        assertEquals("2001:db8::1", IpUtils.stripPort("tcp://[2001:db8::1]:6881"));
        assertEquals("2001:db8::1", IpUtils.stripPort("udp://[2001:db8::1]:6881"));
    }

    @Test
    public void matchesCidr_exactIp() {
        assertTrue(IpUtils.matchesAnyCidr("192.168.1.1", Collections.singletonList("192.168.1.1")));
        assertFalse(IpUtils.matchesAnyCidr("192.168.1.2", Collections.singletonList("192.168.1.1")));
    }

    @Test
    public void matchesCidr_subnetIpv4() {
        assertTrue(IpUtils.matchesAnyCidr("10.0.0.5", Collections.singletonList("10.0.0.0/24")));
        assertTrue(IpUtils.matchesAnyCidr("10.0.0.255", Collections.singletonList("10.0.0.0/24")));
        assertFalse(IpUtils.matchesAnyCidr("10.0.1.0", Collections.singletonList("10.0.0.0/24")));
    }

    @Test
    public void matchesCidr_subnetIpv6() {
        assertTrue(IpUtils.matchesAnyCidr("2001:db8::5", Collections.singletonList("2001:db8::/32")));
        assertFalse(IpUtils.matchesAnyCidr("2001:db9::5", Collections.singletonList("2001:db8::/32")));
    }

    @Test
    public void matchesCidr_emptyList() {
        assertFalse(IpUtils.matchesAnyCidr("1.2.3.4", Collections.emptyList()));
    }

    @Test
    public void matchesCidr_invalidEntryIgnored() {
        assertFalse(IpUtils.matchesAnyCidr("1.2.3.4", Collections.singletonList("bad-entry")));
    }

    @Test
    public void toPrefixBlock_ipv4() {
        byte[] ip = IpUtils.parseIp("192.168.1.200");
        byte[] block = IpUtils.toPrefixBlock(ip, 24);
        assertArrayEquals(IpUtils.parseIp("192.168.1.0"), block);
    }

    @Test
    public void toPrefixBlock_fullLengthNoChange() {
        byte[] ip = IpUtils.parseIp("192.168.1.200");
        assertArrayEquals(ip, IpUtils.toPrefixBlock(ip, 32));
    }

    @Test
    public void formatIp_roundTrip() {
        byte[] bytes = IpUtils.parseIp("10.1.2.3");
        assertEquals("10.1.2.3", IpUtils.formatIp(bytes));
    }

    @Test
    public void isIpv4_isIpv6() {
        assertTrue(IpUtils.isIpv4(IpUtils.parseIp("1.2.3.4")));
        assertFalse(IpUtils.isIpv6(IpUtils.parseIp("1.2.3.4")));
        assertTrue(IpUtils.isIpv6(IpUtils.parseIp("::1")));
    }

    @Test
    public void isTrackableAddress_loopbackAndLinkLocalExcluded() {
        assertFalse(IpUtils.isTrackableAddress(IpUtils.parseIp("127.0.0.1")));
        assertFalse(IpUtils.isTrackableAddress(IpUtils.parseIp("169.254.1.1")));
        assertFalse(IpUtils.isTrackableAddress(IpUtils.parseIp("::1")));
        assertTrue(IpUtils.isTrackableAddress(IpUtils.parseIp("8.8.8.8")));
    }

    @Test
    public void toBitSet_consistentAcrossCalls() {
        assertArrayEquals(
                IpUtils.toBitSet(IpUtils.parseIp("10.0.0.1")).toByteArray(),
                IpUtils.toBitSet(IpUtils.parseIp("10.0.0.1")).toByteArray());
    }
}