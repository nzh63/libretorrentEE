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
 * A single anti-leech detection module. Mirrors the role of a PeerBanHelper
 * "RuleFeatureModule": inspect a peer and decide whether to ban it.
 */
public interface BanModule {
    @NonNull
    String name();

    /*
     * Inspect one peer. Must be side-effect free w.r.t. the ban list; the
     * engine collects results and applies them afterward.
     */
    @NonNull
    BanResult check(@NonNull TorrentSnapshot torrent, @NonNull PeerSnapshot peer, @NonNull PbhSettings settings);
}