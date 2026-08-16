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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BtnPayloadTest {

    @Test
    public void buildSubmitBans_containsRequiredFields() {
        BtnPayload.BanEntry ban = new BtnPayload.BanEntry();
        ban.banAtMs = 1000;
        ban.peerIp = "10.0.0.1";
        ban.peerPort = 6881;
        ban.peerId = "-qB0000-";
        ban.peerClientName = "qBittorrent/4.5";
        ban.peerProgress = 0.5;
        ban.peerFlag = "d U";
        ban.torrentIdentifier = "abcdef";
        ban.torrentIsPrivate = false;
        ban.torrentSize = 1000;
        ban.fromPeerTraffic = 10;
        ban.toPeerTraffic = 20;
        ban.downloaderProgress = 0.3;
        ban.module = "LibreTorrent/AntiVampire";
        ban.rule = "AntiVampire";
        ban.description = "vampire";
        ban.structuredData = new JsonObject();

        byte[] payload = BtnPayload.buildSubmitBans(Collections.singletonList(ban));
        JsonObject root = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray bans = root.getAsJsonArray("bans");
        assertEquals(1, bans.size());
        JsonObject o = bans.get(0).getAsJsonObject();
        assertEquals("10.0.0.1", o.get("peer_ip").getAsString());
        assertEquals("abcdef", o.get("torrent_identifier").getAsString());
        assertEquals("LibreTorrent/AntiVampire", o.get("module").getAsString());
        assertTrue(o.has("structured_data"));
    }

    @Test
    public void buildSubmitSwarm_containsFields() {
        BtnPayload.SwarmEntry s = new BtnPayload.SwarmEntry();
        s.torrentIdentifier = "ident";
        s.torrentSize = 500;
        s.downloader = "anon";
        s.peerIp = "1.2.3.4";
        s.peerPort = 6881;
        s.peerProgress = 0.1;
        s.toPeerTraffic = 100;
        s.fromPeerTraffic = 200;
        s.firstTimeSeenMs = 1;
        s.lastTimeSeenMs = 2;

        byte[] payload = BtnPayload.buildSubmitSwarm(Collections.singletonList(s));
        JsonObject root = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray swarms = root.getAsJsonArray("swarms");
        assertEquals(1, swarms.size());
        JsonObject o = swarms.get(0).getAsJsonObject();
        assertEquals("1.2.3.4", o.get("peer_ip").getAsString());
        assertEquals(100, o.get("to_peer_traffic").getAsLong());
        assertEquals(1, o.get("first_time_seen").getAsLong());
    }
}