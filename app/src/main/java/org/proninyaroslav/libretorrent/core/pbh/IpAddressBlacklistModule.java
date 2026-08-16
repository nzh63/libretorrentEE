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
 */
public final class IpAddressBlacklistModule implements BanModule {
    @NonNull
    @Override
    public String name() {
        return "IPAddressBlocker";
    }

    @NonNull
    @Override
    public BanResult check(@NonNull TorrentSnapshot torrent,
                           @NonNull PeerSnapshot peer,
                           @NonNull PbhSettings settings) {
        if (settings.ipCidrBlacklist == null || settings.ipCidrBlacklist.isEmpty())
            return BanResult.pass(name(), peer.ip);

        if (IpUtils.matchesAnyCidr(peer.ip, settings.ipCidrBlacklist)) {
            return BanResult.ban(name(), peer.ip,
                    "IP address matches a blacklisted CIDR block");
        }

        return BanResult.pass(name(), peer.ip);
    }
}