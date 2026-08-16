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

import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/*
 * BTN-Spec "Torrent Identifier" algorithm.
 *
 * To anonymise the user's content, all BTN clients must compute an
 * irreversible identifier for each torrent:
 *
 *   torrentInfoHandled = torrentInfoHash.toLowerCase()
 *   salt               = crc32(torrentInfoHandled)   (big-endian hex)
 *   identifier         = sha256(torrentInfoHandled + salt).toLowerCase()
 *
 * See https://github.com/PBH-BTN/BTN-Spec (Torrent Identifier algorithm).
 */
public final class TorrentIdentifier {
    private TorrentIdentifier() {
    }

    @NonNull
    public static String getHashedIdentifier(@NonNull String torrentInfoHash) {
        String handled = torrentInfoHash.toLowerCase(Locale.ROOT);
        String salt = Hashing.crc32()
                .hashString(handled, StandardCharsets.UTF_8)
                .toString();
        return Hashing.sha256()
                .hashString(handled + salt, StandardCharsets.UTF_8)
                .toString()
                .toLowerCase(Locale.ROOT);
    }
}