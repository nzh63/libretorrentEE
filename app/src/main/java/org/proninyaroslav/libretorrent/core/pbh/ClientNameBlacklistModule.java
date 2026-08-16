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

import java.util.Locale;

/*
 * PBH "ClientNameBlacklist" equivalent: bans a peer whose reported client
 * name (user agent) contains any blacklisted substring (case-insensitive).
 */
public final class ClientNameBlacklistModule implements BanModule {
    @NonNull
    @Override
    public String name() {
        return "ClientNameBlacklist";
    }

    @NonNull
    @Override
    public BanResult check(@NonNull TorrentSnapshot torrent,
                           @NonNull PeerSnapshot peer,
                           @NonNull PbhSettings settings) {
        if (!settings.clientNameBlacklistEnabled)
            return BanResult.pass(name(), peer.ip);
        if (settings.clientNameBlacklist == null || settings.clientNameBlacklist.isEmpty())
            return BanResult.pass(name(), peer.ip);

        String clientLower = peer.client.toLowerCase(Locale.ROOT);
        for (String pattern : settings.clientNameBlacklist) {
            if (pattern == null || pattern.isEmpty())
                continue;
            if (clientLower.contains(pattern.toLowerCase(Locale.ROOT))) {
                return BanResult.ban(name(), peer.ip,
                        "client name matches blacklist pattern '" + pattern + "'");
            }
        }

        return BanResult.pass(name(), peer.ip);
    }
}