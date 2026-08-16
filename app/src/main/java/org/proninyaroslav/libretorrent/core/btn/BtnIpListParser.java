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

import androidx.annotation.NonNull;

import org.proninyaroslav.libretorrent.core.pbh.IpUtils;

import java.util.HashSet;
import java.util.Set;

/*
 * Parses BTN IP allow/deny list bodies. Per BTN-Spec:
 *  - comments start with '#' or '//';
 *  - each non-comment line is an IPv4/IPv6 address or a CIDR block.
 */
public final class BtnIpListParser {
    private BtnIpListParser() {
    }

    @NonNull
    public static Set<String> parse(@NonNull String body) {
        Set<String> out = new HashSet<>();
        if (body == null || body.isEmpty())
            return out;

        for (String rawLine : body.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//"))
                continue;
            // Strip trailing inline comment if any.
            int hash = line.indexOf('#');
            int slashSlash = line.indexOf("//");
            int cut = -1;
            if (hash >= 0)
                cut = hash;
            if (slashSlash >= 0 && (cut < 0 || slashSlash < cut))
                cut = slashSlash;
            String entry = (cut >= 0 ? line.substring(0, cut) : line).trim();
            if (entry.isEmpty())
                continue;
            if (isValidIpOrCidr(entry))
                out.add(entry);
        }

        return out;
    }

    private static boolean isValidIpOrCidr(@NonNull String entry) {
        String[] parts = entry.split("/");
        if (parts.length > 2)
            return false;
        if (IpUtils.parseIp(parts[0]) == null)
            return false;
        if (parts.length == 2) {
            String prefix = parts[1].trim();
            if (prefix.isEmpty() || !prefix.chars().allMatch(Character::isDigit))
                return false;
            int bits;
            try {
                bits = Integer.parseInt(prefix);
            } catch (NumberFormatException e) {
                return false;
            }
            int maxBits = IpUtils.isIpv4(IpUtils.parseIp(parts[0])) ? 32 : 128;
            if (bits < 0 || bits > maxBits)
                return false;
        }
        return true;
    }
}