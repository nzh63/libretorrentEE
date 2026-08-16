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

/*
 * PBH "IPAddressBlocker" equivalent: bans a peer whose IP falls into any of
 * the configured CIDR blocks or exact IPs.
 *
 * The CIDR strings are compiled into byte-prefix form once per rule-set
 * change and cached, so a scan over many peers does not re-parse the rule
 * strings on every check.
 */
public final class IpAddressBlacklistModule implements BanModule {
    /* Guarded by this; only mutated when the rule set actually changed */
    private java.util.Set<String> lastRules;
    private IpUtils.CidrMatcher matcher = IpUtils.CidrMatcher.compile(java.util.Collections.emptySet());

    @NonNull
    @Override
    public String name() {
        return "IPAddressBlocker";
    }

    @NonNull
    @Override
    public synchronized BanResult check(@NonNull TorrentSnapshot torrent,
                                        @NonNull PeerSnapshot peer,
                                        @NonNull PbhSettings settings) {
        java.util.Set<String> rules = settings.ipCidrBlacklist;
        if (rules == null || rules.isEmpty()) {
            lastRules = null;
            matcher = IpUtils.CidrMatcher.compile(java.util.Collections.emptySet());
            return BanResult.pass(name(), peer.ip);
        }
        if (!rules.equals(lastRules)) {
            lastRules = new java.util.HashSet<>(rules);
            matcher = IpUtils.CidrMatcher.compile(rules);
        }

        if (matcher.matches(peer.ip)) {
            return BanResult.ban(name(), peer.ip,
                    "IP address matches a blacklisted CIDR block");
        }

        return BanResult.pass(name(), peer.ip);
    }
}
