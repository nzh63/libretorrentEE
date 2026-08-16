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

import java.util.HashSet;
import java.util.Set;

/*
 * Port of PeerBanHelper's "AutoRangeBan" module ("连坐"): a peer is banned
 * when its IP shares the configured prefix (IPv4 /30, IPv6 /48 by default)
 * with an address that is already banned. Fast-PCB disconnect probes are not
 * fed into this module (upstream skips ban-for-disconnect entries too).
 *
 * The banned-address set is pushed by the caller before each engine pass via
 * updateBannedAddresses(); only the compiled matcher is read on the hot path.
 */
public class AutoRangeBanModule implements BanModule {
    private volatile IpUtils.CidrMatcher matcher =
            IpUtils.CidrMatcher.compile(java.util.Collections.emptySet());

    @NonNull
    @Override
    public String name() {
        return "auto-range-ban";
    }

    /*
     * Rebuilds the prefix-block matcher from the currently banned addresses
     * (manual + active automatic bans, no disconnect probes). Invalid or
     * non-IP entries are ignored.
     */
    public void updateBannedAddresses(@NonNull Set<String> bannedIps,
                                      int ipv4PrefixLength,
                                      int ipv6PrefixLength) {
        Set<String> blocks = new HashSet<>();
        for (String ip : bannedIps) {
            byte[] bytes = IpUtils.parseIp(IpUtils.stripPort(ip == null ? "" : ip));
            if (bytes == null)
                continue; // e.g. a CIDR manual-blacklist entry
            int prefix = IpUtils.isIpv4(bytes) ? ipv4PrefixLength : ipv6PrefixLength;
            blocks.add(IpUtils.formatIp(IpUtils.toPrefixBlock(bytes, prefix))
                    + "/" + prefix);
        }
        matcher = IpUtils.CidrMatcher.compile(blocks);
    }

    @NonNull
    @Override
    public BanResult check(@NonNull TorrentSnapshot torrent,
                           @NonNull PeerSnapshot peer,
                           @NonNull PbhSettings settings) {
        if (!settings.rangeBanEnabled)
            return BanResult.pass(name(), peer.ip);

        IpUtils.CidrMatcher m = matcher;
        if (m.isEmpty())
            return BanResult.pass(name(), peer.ip);

        byte[] ipBytes = IpUtils.parseIp(peer.ip);
        if (ipBytes == null)
            return BanResult.pass(name(), peer.ip);
        String addressType = IpUtils.isIpv4(ipBytes)
                ? "IPv4/" + settings.rangeBanIpv4PrefixLength
                : "IPv6/" + settings.rangeBanIpv6PrefixLength;

        if (m.matches(ipBytes)) {
            return BanResult.ban(name(), peer.ip,
                    "peer is in the same " + addressType
                            + " range as a banned address");
        }
        return BanResult.pass(name(), peer.ip);
    }
}
