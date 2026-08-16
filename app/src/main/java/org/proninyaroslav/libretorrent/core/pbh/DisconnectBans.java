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

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/*
 * Registry of short-lived "disconnect" bans produced by the fast-PCB probe.
 *
 * Unlike real bans, a disconnect ban is a temporary block (typically seconds)
 * used to drop a suspected cheater and observe whether it reconnects with a
 * truthful progress report. It therefore must not be persisted to the user
 * visible blacklist and must expire on its own, without touching the
 * auto-ban expiry bookkeeping.
 */
public final class DisconnectBans {
    /* ip -> expiry time (epoch millis) */
    private final ConcurrentHashMap<String, Long> bans = new ConcurrentHashMap<>();

    /* Registers (or refreshes) a disconnect ban for the given duration. */
    public void add(@NonNull String ip, long durationMs, long nowMs) {
        long expiry = nowMs + Math.max(0, durationMs);
        bans.merge(ip, expiry, (oldV, newV) -> Math.max(oldV, newV));
    }

    /* The set of disconnect bans that are still active at nowMs. */
    @NonNull
    public Set<String> active(long nowMs) {
        Set<String> out = new HashSet<>();
        for (Map.Entry<String, Long> e : bans.entrySet()) {
            if (e.getValue() > nowMs)
                out.add(e.getKey());
        }
        return out;
    }

    /*
     * Whether at least one registered ban has already expired at nowMs
     * (i.e. the caller should re-apply the effective ban set).
     */
    public boolean hasExpired(long nowMs) {
        for (Long expiry : bans.values()) {
            if (expiry <= nowMs)
                return true;
        }
        return false;
    }

    /* Drops expired entries and returns the removed IPs. */
    @NonNull
    public Set<String> removeExpired(long nowMs) {
        Set<String> expired = new HashSet<>();
        bans.entrySet().removeIf(e -> {
            if (e.getValue() <= nowMs) {
                expired.add(e.getKey());
                return true;
            }
            return false;
        });
        return expired;
    }

    public void clear() {
        bans.clear();
    }

    public int size() {
        return bans.size();
    }

    @NonNull
    public Set<String> snapshotIps() {
        return Collections.unmodifiableSet(bans.keySet());
    }
}
