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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/*
 * Builds the JSON payloads for the BTN submit_bans and submit_swarm endpoints.
 * See BTN-Spec "提交封禁列表" and "提交种群信息".
 */
public final class BtnPayload {
    private BtnPayload() {
    }

    /*
     * Builds a submit_bans payload. Bans over 1000 are split by the caller.
     */
    @NonNull
    public static byte[] buildSubmitBans(@NonNull Iterable<BanEntry> bans) {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (BanEntry b : bans) {
            JsonObject o = new JsonObject();
            o.addProperty("ban_at", b.banAtMs);
            o.addProperty("peer_ip", b.peerIp);
            o.addProperty("peer_port", b.peerPort);
            o.addProperty("peer_id", b.peerId == null ? "" : b.peerId);
            o.addProperty("peer_client_name", b.peerClientName == null ? "" : b.peerClientName);
            o.addProperty("peer_progress", b.peerProgress);
            o.addProperty("peer_flag", b.peerFlag == null ? "" : b.peerFlag);
            o.addProperty("torrent_identifier", b.torrentIdentifier);
            o.addProperty("torrent_is_private", b.torrentIsPrivate);
            o.addProperty("torrent_size", b.torrentSize);
            o.addProperty("from_peer_traffic", b.fromPeerTraffic);
            o.addProperty("to_peer_traffic", b.toPeerTraffic);
            o.addProperty("downloader_progress", b.downloaderProgress);
            o.addProperty("module", b.module);
            o.addProperty("rule", b.rule);
            o.addProperty("description", b.description);
            if (b.structuredData != null)
                o.add("structured_data", b.structuredData);
            arr.add(o);
        }
        root.add("bans", arr);
        return root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /*
     * Builds a submit_swarm payload.
     */
    @NonNull
    public static byte[] buildSubmitSwarm(@NonNull Iterable<SwarmEntry> swarms) {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (SwarmEntry s : swarms) {
            JsonObject o = new JsonObject();
            o.addProperty("torrent_identifier", s.torrentIdentifier);
            o.addProperty("torrent_is_private", s.torrentIsPrivate);
            o.addProperty("torrent_size", s.torrentSize);
            o.addProperty("downloader", s.downloader);
            o.addProperty("downloader_progress", s.downloaderProgress);
            o.addProperty("peer_ip", s.peerIp);
            o.addProperty("peer_port", s.peerPort);
            o.addProperty("peer_id", s.peerId == null ? "" : s.peerId);
            o.addProperty("peer_client_name", s.peerClientName == null ? "" : s.peerClientName);
            o.addProperty("peer_progress", s.peerProgress);
            o.addProperty("to_peer_traffic", s.toPeerTraffic);
            o.addProperty("to_peer_traffic_offset", s.toPeerTrafficOffset);
            o.addProperty("from_peer_traffic", s.fromPeerTraffic);
            o.addProperty("from_peer_traffic_offset", s.fromPeerTrafficOffset);
            o.addProperty("first_time_seen", s.firstTimeSeenMs);
            o.addProperty("last_time_seen", s.lastTimeSeenMs);
            o.addProperty("peer_last_flags", s.peerLastFlags == null ? "" : s.peerLastFlags);
            o.addProperty("download_speed", s.downloadSpeed);
            o.addProperty("upload_speed", s.uploadSpeed);
            arr.add(o);
        }
        root.add("swarms", arr);
        return root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /* One ban entry for submit_bans. */
    public static class BanEntry {
        public long banAtMs;
        public String peerIp;
        public int peerPort;
        public String peerId;
        public String peerClientName;
        public double peerProgress;
        public String peerFlag;
        public String torrentIdentifier;
        public boolean torrentIsPrivate;
        public long torrentSize;
        public long fromPeerTraffic;
        public long toPeerTraffic;
        public double downloaderProgress;
        public String module;
        public String rule;
        public String description;
        public JsonObject structuredData;
    }

    /* One swarm entry for submit_swarm. */
    public static class SwarmEntry {
        public String torrentIdentifier;
        public boolean torrentIsPrivate;
        public long torrentSize;
        public String downloader;
        public double downloaderProgress;
        public String peerIp;
        public int peerPort;
        public String peerId;
        public String peerClientName;
        public double peerProgress;
        public long toPeerTraffic;
        public long toPeerTrafficOffset;
        public long fromPeerTraffic;
        public long fromPeerTrafficOffset;
        public long firstTimeSeenMs;
        public long lastTimeSeenMs;
        public String peerLastFlags;
        public long downloadSpeed;
        public long uploadSpeed;
    }
}