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

import java.util.Locale;

/*
 * Case-insensitive substring matching of the user-agent / client-name
 * blacklist (the LibreTorrent equivalent of PBH's "ClientNameBlacklist"
 * module; the list is edited on the anti-leech settings page).
 */
public final class ClientNameMatcher {
    private ClientNameMatcher() {
    }

    /* Whether the reported client name contains any blacklisted pattern. */
    public static boolean matches(@Nullable String client,
                                  @Nullable Iterable<String> patterns) {
        if (client == null || patterns == null)
            return false;

        String clientLower = client.toLowerCase(Locale.ROOT);
        for (String pattern : patterns) {
            if (pattern == null || pattern.isEmpty())
                continue;
            if (clientLower.contains(pattern.toLowerCase(Locale.ROOT)))
                return true;
        }

        return false;
    }
}
