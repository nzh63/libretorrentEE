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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Persistent storage for automatic ban records (separate from the manual
 * peerIpBlacklist preference). Each record carries the metadata shown in the
 * ban-list UI: banning module, reason, torrent name and expiry.
 *
 * The records are kept as one JSON file guarded by a monitor; all mutations
 * rewrite the file atomically enough for our single-writer usage (the PBH
 * scan thread records/unbans, the UI thread reads/unbans).
 */
public class BanRecordStore {
    private static final String FILE_NAME = "pbh_ban_records.json";

    private final File file;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    /* ip -> record; LinkedHashMap keeps insertion (ban) order for the UI */
    private final Map<String, BanRecord> records = new LinkedHashMap<>();

    public BanRecordStore(@NonNull Context context) {
        this(new File(context.getDir("pbh", Context.MODE_PRIVATE), FILE_NAME));
    }

    public BanRecordStore(@NonNull File file) {
        this.file = file;
        load();
    }

    private synchronized void load() {
        records.clear();
        if (!file.exists())
            return;
        try {
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            List<BanRecord> loaded = gson.fromJson(json,
                    new TypeToken<List<BanRecord>>() {
                    }.getType());
            if (loaded != null) {
                for (BanRecord r : loaded)
                    if (r != null && r.ip != null && !r.ip.isEmpty())
                        records.put(r.ip, r);
            }
        } catch (Exception e) {
            // Corrupt store: start fresh rather than crash the engine
            records.clear();
        }
    }

    private synchronized void persist() {
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists())
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            Files.write(file.toPath(),
                    gson.toJson(new ArrayList<>(records.values())).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // Best-effort persistence; bans stay effective in-memory this session
        }
    }

    /* All records in ban order (a copy; safe to iterate). */
    @NonNull
    public synchronized List<BanRecord> all() {
        return new ArrayList<>(records.values());
    }

    @Nullable
    public synchronized BanRecord get(@NonNull String ip) {
        return records.get(ip);
    }

    /*
     * Adds a record. An existing record for the same IP keeps its original
     * expiry (re-detection must not prolong a ban, matching the pre-store
     * semantics); the reason/module metadata is refreshed.
     */
    public synchronized void record(@NonNull BanRecord record) {
        BanRecord existing = records.get(record.ip);
        if (existing != null) {
            records.put(record.ip, new BanRecord(
                    record.ip, record.module, record.reason, record.torrentName,
                    existing.bannedAtMs, existing.expireAtMs));
        } else {
            records.put(record.ip, record);
        }
        persist();
    }

    /* Removes the record for the IP. Returns the removed record, or null. */
    @Nullable
    public synchronized BanRecord remove(@NonNull String ip) {
        BanRecord removed = records.remove(ip);
        if (removed != null)
            persist();
        return removed;
    }

    /* IPs of the records that have not expired at nowMs. */
    @NonNull
    public synchronized Set<String> activeIps(long nowMs) {
        Set<String> ips = new HashSet<>();
        for (BanRecord r : records.values())
            if (!r.isExpired(nowMs))
                ips.add(r.ip);
        return ips;
    }

    /* Drops all expired records and returns them. */
    @NonNull
    public synchronized List<BanRecord> removeExpired(long nowMs) {
        List<BanRecord> expired = new ArrayList<>();
        for (BanRecord r : new ArrayList<>(records.values()))
            if (r.isExpired(nowMs)) {
                expired.add(r);
                records.remove(r.ip);
            }
        if (!expired.isEmpty())
            persist();
        return expired;
    }

    public synchronized void clear() {
        records.clear();
        persist();
    }

    public synchronized int size() {
        return records.size();
    }
}
