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
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
 * Parsed result of the BTN ip_query ability: the BTN network's aggregated
 * risk information about one IP address. Only the fields the ban-list UI
 * shows are extracted (risk color, labels, ban/swarm counters, traffic).
 */
public final class BtnIpQueryResult {
    /* red / orange / green / gray */
    @NonNull
    public final String color;
    @NonNull
    public final List<String> labels;
    /* Total ban records in the BTN network, -1 if the server sent none */
    public final long totalBans;
    /* Torrents the IP is concurrently downloading / seeding, -1 if unknown */
    public final long concurrentDownloads;
    public final long concurrentSeeds;
    /* Traffic the BTN network uploaded to / downloaded from this IP, -1 if unknown */
    public final long toPeerTraffic;
    public final long fromPeerTraffic;

    private BtnIpQueryResult(@NonNull String color,
                             @NonNull List<String> labels,
                             long totalBans,
                             long concurrentDownloads,
                             long concurrentSeeds,
                             long toPeerTraffic,
                             long fromPeerTraffic) {
        this.color = color;
        this.labels = labels;
        this.totalBans = totalBans;
        this.concurrentDownloads = concurrentDownloads;
        this.concurrentSeeds = concurrentSeeds;
        this.toPeerTraffic = toPeerTraffic;
        this.fromPeerTraffic = fromPeerTraffic;
    }

    /*
     * Parses the ip_query JSON body:
     *  { "color": "red", "labels": [...],
     *    "bans": { "total": N, ... }, "swarms": { "concurrent_download_torrents_count": N,
     *    "concurrent_seeding_torrents_count": N, ... },
     *    "traffic": { "to_peer_traffic": N, "from_peer_traffic": N, ... } }
     * Returns null if the body is not a JSON object.
     */
    @Nullable
    public static BtnIpQueryResult parse(@NonNull String body) {
        JsonElement root;
        try {
            root = JsonParser.parseString(body);
        } catch (Exception e) {
            return null;
        }
        if (!root.isJsonObject())
            return null;

        JsonObject obj = root.getAsJsonObject();
        String color = getString(obj, "color", "gray");

        List<String> labels = new ArrayList<>();
        if (obj.has("labels") && obj.get("labels").isJsonArray()) {
            for (JsonElement e : obj.getAsJsonArray("labels")) {
                if (e.isJsonPrimitive())
                    labels.add(e.getAsString());
            }
        }

        long totalBans = -1;
        if (obj.has("bans") && obj.get("bans").isJsonObject())
            totalBans = getLong(obj.getAsJsonObject("bans"), "total", -1);

        long concurrentDownloads = -1;
        long concurrentSeeds = -1;
        if (obj.has("swarms") && obj.get("swarms").isJsonObject()) {
            JsonObject swarms = obj.getAsJsonObject("swarms");
            concurrentDownloads = getLong(swarms, "concurrent_download_torrents_count", -1);
            concurrentSeeds = getLong(swarms, "concurrent_seeding_torrents_count", -1);
        }

        long toPeer = -1;
        long fromPeer = -1;
        if (obj.has("traffic") && obj.get("traffic").isJsonObject()) {
            JsonObject traffic = obj.getAsJsonObject("traffic");
            toPeer = getLong(traffic, "to_peer_traffic", -1);
            fromPeer = getLong(traffic, "from_peer_traffic", -1);
        }

        return new BtnIpQueryResult(color, Collections.unmodifiableList(labels),
                totalBans, concurrentDownloads, concurrentSeeds, toPeer, fromPeer);
    }

    private static String getString(JsonObject obj, String key, String def) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive())
            return obj.get(key).getAsString();
        return def;
    }

    private static long getLong(JsonObject obj, String key, long def) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            try {
                return obj.get(key).getAsLong();
            } catch (Exception ignored) {
            }
        }
        return def;
    }
}
