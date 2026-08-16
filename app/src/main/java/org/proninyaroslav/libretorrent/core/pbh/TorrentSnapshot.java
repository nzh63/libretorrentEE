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

import java.util.List;
import java.util.Objects;

/*
 * Read-only view of a single torrent and its currently connected peers.
 */
public class TorrentSnapshot {
    @NonNull
    public final String id;
    @NonNull
    public final String name;
    /* Total size of the torrent in bytes */
    public final long totalSize;
    /* Bytes downloaded so far by us */
    public final long completedSize;
    /* Whether the torrent is private (affects BTN reporting) */
    public final boolean privateTorrent;
    /* Currently connected peers */
    public final List<PeerSnapshot> peers;

    public TorrentSnapshot(@NonNull String id,
                           @NonNull String name,
                           long totalSize,
                           long completedSize,
                           boolean privateTorrent,
                           @NonNull List<PeerSnapshot> peers) {
        this.id = Objects.requireNonNull(id);
        this.name = name == null ? id : name;
        this.totalSize = totalSize;
        this.completedSize = completedSize;
        this.privateTorrent = privateTorrent;
        this.peers = Objects.requireNonNull(peers);
    }

    @Override
    public String toString() {
        return "TorrentSnapshot{id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", totalSize=" + totalSize +
                ", completedSize=" + completedSize +
                ", peers=" + peers.size() + '}';
    }
}