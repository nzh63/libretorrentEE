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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.BitSet;

/*
 * Pure-Java IP / CIDR helpers used by the anti-leech modules. No Android or
 * libtorrent dependencies so it is fully unit-testable on the JVM.
 */
public final class IpUtils {
    private IpUtils() {
    }

    /*
     * Parse a single IP address to a byte array, or null if invalid.
     * Accepts IPv4 and IPv6. Strict: does not resolve hostnames.
     */
    @Nullable
    public static byte[] parseIp(@NonNull String ip) {
        String s = ip.trim();
        if (s.isEmpty())
            return null;

        // IPv4 literal: exactly four dot-separated decimal octets in [0, 255].
        if (s.indexOf('.') >= 0 && s.indexOf(':') < 0) {
            String[] parts = s.split("\\.", -1);
            if (parts.length != 4)
                return null;
            byte[] out = new byte[4];
            for (int i = 0; i < 4; i++) {
                String p = parts[i];
                if (p.isEmpty() || !p.chars().allMatch(Character::isDigit))
                    return null;
                int v;
                try {
                    v = Integer.parseInt(p);
                } catch (NumberFormatException e) {
                    return null;
                }
                if (v < 0 || v > 255)
                    return null;
                out[i] = (byte) v;
            }
            return out;
        }

        // IPv6 literal: must contain ':' and only hex digits, colons and dots.
        if (s.indexOf(':') >= 0) {
            if (!s.chars().allMatch(c ->
                    Character.digit(c, 16) >= 0 || c == ':' || c == '.'))
                return null;
            try {
                InetAddress addr = InetAddress.getByName(s);
                if (addr.getAddress().length == 16)
                    return addr.getAddress();
                return null;
            } catch (Exception e) {
                return null;
            }
        }

        return null;
    }

    /*
     * Normalize a peer IP string by stripping the transport prefix and port
     * if present. libtorrent exposes peer endpoints like "tcp://1.2.3.4:6881"
     * or "utp://[2001:db8::1]:6881"; the ban engine and IP filter need the
     * bare address ("1.2.3.4" / "2001:db8::1").
     */
    @NonNull
    public static String stripPort(@NonNull String ip) {
        String s = ip.trim();

        // Strip a transport prefix, e.g. "tcp://", "udp://", "utp://".
        int scheme = s.indexOf("://");
        if (scheme > 0) {
            String schemeName = s.substring(0, scheme);
            if (schemeName.chars().allMatch(Character::isLetter))
                s = s.substring(scheme + 3);
        }

        int idx = s.lastIndexOf(':');
        if (idx >= 0) {
            // Only strip if the part after ':' is a numeric port and the
            // part before parses as an IP (avoids mangling bare IPv6).
            String port = s.substring(idx + 1);
            if (port.length() > 0 && port.chars().allMatch(Character::isDigit)) {
                String candidate = s.substring(0, idx);
                // Strip surrounding brackets from IPv6 literals, e.g.
                // "[2001:db8::1]:6881" -> "2001:db8::1".
                if (candidate.startsWith("[") && candidate.endsWith("]"))
                    candidate = candidate.substring(1, candidate.length() - 1);
                if (parseIp(candidate) != null)
                    return candidate;
            }
        }
        return s;
    }

    /*
     * Whether the given IP is contained in any of the CIDR blocks. Each entry
     * may be "1.2.3.4", "1.2.3.0/24" or "2001:db8::/32". Bare IPs are treated
     * as a /32 (IPv4) or /128 (IPv6) prefix.
     */
    public static boolean matchesAnyCidr(@NonNull String ip, @NonNull Iterable<String> cidrs) {
        byte[] ipBytes = parseIp(ip);
        if (ipBytes == null)
            return false;

        for (String cidr : cidrs) {
            if (cidr == null || cidr.trim().isEmpty())
                continue;
            if (matchesCidr(ipBytes, cidr.trim()))
                return true;
        }

        return false;
    }

    /*
     * Whether ipBytes belongs to the given CIDR block described as a string.
     */
    public static boolean matchesCidr(@NonNull byte[] ipBytes, @NonNull String cidr) {
        String[] parts = cidr.split("/");
        String networkStr = parts[0].trim();
        byte[] networkBytes = parseIp(networkStr);
        if (networkBytes == null || networkBytes.length != ipBytes.length)
            return false;

        int prefixLength;
        if (parts.length == 1) {
            prefixLength = ipBytes.length * 8; /* bare IP == full prefix */
        } else {
            try {
                prefixLength = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                return false;
            }
            int maxBits = ipBytes.length * 8;
            if (prefixLength < 0 || prefixLength > maxBits)
                return false;
        }

        return prefixMatches(ipBytes, networkBytes, prefixLength);
    }

    private static boolean prefixMatches(byte[] ip, byte[] network, int prefixLength) {
        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;

        for (int i = 0; i < fullBytes; i++) {
            if (ip[i] != network[i])
                return false;
        }

        if (remainingBits > 0) {
            int mask = 0xFF << (8 - remainingBits);
            if ((ip[fullBytes] & mask) != (network[fullBytes] & mask))
                return false;
        }

        return true;
    }

    /*
     * Whether the string is a syntactically valid bare IP or CIDR block.
     * Used to validate cloud rule entries before they are stored.
     */
    public static boolean matchesCidrSyntax(@NonNull String entry) {
        byte[] prefix = parseIp(entry);
        if (prefix != null)
            return true; // bare IP
        int slash = entry.indexOf('/');
        if (slash <= 0 || entry.indexOf('/', slash + 1) >= 0)
            return false;
        byte[] network = parseIp(entry.substring(0, slash).trim());
        if (network == null)
            return false;
        String prefixStr = entry.substring(slash + 1).trim();
        if (prefixStr.isEmpty() || !prefixStr.chars().allMatch(Character::isDigit))
            return false;
        int bits;
        try {
            bits = Integer.parseInt(prefixStr);
        } catch (NumberFormatException e) {
            return false;
        }
        int maxBits = network.length * 8;
        return bits >= 0 && bits <= maxBits;
    }

    /*
     * Compute a prefix block (network address) for an IP. Returns a byte array
     * of the same length as the input. prefixLength must be in range.
     */
    @NonNull
    public static byte[] toPrefixBlock(@NonNull byte[] ip, int prefixLength) {
        if (prefixLength < 0)
            prefixLength = 0;
        int maxBits = ip.length * 8;
        if (prefixLength > maxBits)
            prefixLength = maxBits;

        byte[] out = ip.clone();
        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;

        // Zero out all bytes beyond the last fully-or-partially covered byte.
        for (int i = fullBytes + 1; i < out.length; i++)
            out[i] = 0;

        if (remainingBits > 0 && fullBytes < out.length) {
            // Partially cover the 'fullBytes' byte: mask off the low bits.
            int mask = 0xFF << (8 - remainingBits);
            out[fullBytes] = (byte) (out[fullBytes] & mask);
        } else if (fullBytes < out.length) {
            // Prefix ends exactly on a byte boundary: the byte at 'fullBytes'
            // is not covered at all, so clear it entirely.
            out[fullBytes] = 0;
        }

        return out;
    }

    /*
     * Format a byte array back to a dotted / colon string.
     */
    @NonNull
    public static String formatIp(@NonNull byte[] bytes) {
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (Exception e) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < bytes.length; i++) {
                if (i > 0)
                    sb.append('.');
                sb.append(bytes[i] & 0xFF);
            }
            return sb.toString();
        }
    }

    /*
     * Whether the address encoded by ipBytes is IPv4.
     */
    public static boolean isIpv4(@NonNull byte[] ipBytes) {
        return ipBytes.length == 4;
    }

    /*
     * Whether the address encoded by ipBytes is IPv6.
     */
    public static boolean isIpv6(@NonNull byte[] ipBytes) {
        return ipBytes.length == 16;
    }

    /*
     * Whether an address is a global unicast / not a loopback, link-local or
     * unique-local address. Used to skip addresses that the PCB should not
     * track (e.g. loopback).
     */
    public static boolean isTrackableAddress(@NonNull byte[] ipBytes) {
        try {
            InetAddress addr = InetAddress.getByAddress(ipBytes);
            if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isAnyLocalAddress())
                return false;
            if (addr instanceof Inet4Address ipv4)
                return !ipv4.isSiteLocalAddress();
            if (addr instanceof Inet6Address ipv6)
                return !ipv6.isSiteLocalAddress();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /*
     * Convert a prefix block byte array to a BitSet for easy hashing/equality.
     */
    @NonNull
    public static BitSet toBitSet(@NonNull byte[] bytes) {
        BitSet bs = new BitSet(bytes.length * 8);
        for (int i = 0; i < bytes.length; i++) {
            for (int b = 0; b < 8; b++) {
                if ((bytes[i] & (1 << (7 - b))) != 0)
                    bs.set(i * 8 + b);
            }
        }
        return bs;
    }
}