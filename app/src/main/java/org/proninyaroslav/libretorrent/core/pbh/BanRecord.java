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
 * Metadata of a single automatic (engine-issued) ban, mirroring
 * PeerBanHelper's BanMetadata: what banned the peer, when, and when the ban
 * expires (0 = permanent). Manual bans are NOT recorded here - they live in
 * the user-managed peerIpBlacklist preference.
 */
public final class BanRecord {
    /* Bare IP address of the banned peer */
    @NonNull
    public final String ip;
    /* Module that issued the ban (e.g. "progress-cheat", "auto-range-ban") */
    @NonNull
    public final String module;
    /* Human-readable ban reason, may be empty */
    @NonNull
    public final String reason;
    /* Name of the torrent the peer belonged to, may be empty */
    @NonNull
    public final String torrentName;
    /* Wall-clock time the ban was issued */
    public final long bannedAtMs;
    /* Wall-clock time the ban expires, 0 = permanent */
    public final long expireAtMs;

    public BanRecord(@NonNull String ip,
                     @NonNull String module,
                     @NonNull String reason,
                     @NonNull String torrentName,
                     long bannedAtMs,
                     long expireAtMs) {
        this.ip = ip == null ? "" : ip;
        this.module = module == null ? "" : module;
        this.reason = reason == null ? "" : reason;
        this.torrentName = torrentName == null ? "" : torrentName;
        this.bannedAtMs = bannedAtMs;
        this.expireAtMs = expireAtMs;
    }

    public boolean isExpired(long nowMs) {
        return expireAtMs > 0 && expireAtMs <= nowMs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BanRecord that)) return false;
        return bannedAtMs == that.bannedAtMs
                && expireAtMs == that.expireAtMs
                && ip.equals(that.ip)
                && module.equals(that.module)
                && reason.equals(that.reason)
                && torrentName.equals(that.torrentName);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(ip, module, reason, torrentName, bannedAtMs, expireAtMs);
    }

    @Override
    public String toString() {
        return "BanRecord{ip='" + ip + '\'' +
                ", module='" + module + '\'' +
                ", reason='" + reason + '\'' +
                ", torrent='" + torrentName + '\'' +
                ", bannedAt=" + bannedAtMs +
                ", expireAt=" + expireAtMs + '}';
    }
}
