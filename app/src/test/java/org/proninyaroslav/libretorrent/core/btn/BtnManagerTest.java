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

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/*
 * Verifies BtnManager ban/swarm submission: gating on settings, config fetch
 * on first submission, and the HTTP POST being issued.
 */
public class BtnManagerTest {

    /* Fake HTTP client: serves config and records POSTs. */
    private static class FakeHttpClient extends BtnHttpClient {
        final List<String> posts = new ArrayList<>();

        FakeHttpClient() {
            super("test-agent");
        }

        @Override
        @Nullable
        public GetResult get(@NonNull String urlStr,
                             @NonNull BtnSettings settings,
                             @Nullable String rev,
                             @Nullable java.util.Map<String, String> extraHeaders) throws IOException {
            // Serve the config document for any GET.
            String body = """
                    {"min_protocol_version":20,"max_protocol_version":20,
                     "ability":{
                       "submit_bans":{"endpoint":"https://btn/syncBanHistory"},
                       "submit_swarm":{"endpoint":"https://btn/syncSwarm"}
                     }}
                    """;
            return new GetResult(200, body.getBytes(java.nio.charset.StandardCharsets.UTF_8), null);
        }

        @Override
        public int postGzipJson(@NonNull String urlStr,
                                    @NonNull BtnSettings settings,
                                    @NonNull byte[] jsonBody,
                                    @Nullable java.util.Map<String, String> extraHeaders) throws IOException {
            posts.add(urlStr);
            return 200;
        }
    }

    private static BtnSettings settings(boolean submitBans, boolean submitSwarm) {
        return BtnSettings.builder()
                .enabled(true)
                .configUrl("https://btn/ping/config")
                .appId("app-id")
                .appSecret("app-secret")
                .installationId("inst-id")
                .submitBansEnabled(submitBans)
                .submitSwarmEnabled(submitSwarm)
                .build();
    }

    private static List<BtnPayload.BanEntry> bans(int count) {
        List<BtnPayload.BanEntry> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BtnPayload.BanEntry e = new BtnPayload.BanEntry();
            e.banAtMs = 1000;
            e.peerIp = "10.0.0." + i;
            e.peerPort = 6881;
            e.peerClientName = "test";
            e.torrentIdentifier = "abc";
            e.module = "AntiVampire";
            e.rule = "AntiVampire";
            out.add(e);
        }
        return out;
    }

    @Test
    public void submitBans_disabled_returnsFalseWithoutRequest() throws Exception {
        FakeHttpClient http = new FakeHttpClient();
        BtnManager manager = new BtnManager(new BtnClient(http), null);
        assertFalse(manager.submitBans(settings(false, false), bans(1)));
        assertTrue(http.posts.isEmpty());
    }

    @Test
    public void submitBans_emptyList_returnsFalse() throws Exception {
        FakeHttpClient http = new FakeHttpClient();
        BtnManager manager = new BtnManager(new BtnClient(http), null);
        assertFalse(manager.submitBans(settings(true, false), new ArrayList<>()));
        assertTrue(http.posts.isEmpty());
    }

    @Test
    public void submitBans_success_postsToEndpoint() throws Exception {
        FakeHttpClient http = new FakeHttpClient();
        BtnManager manager = new BtnManager(new BtnClient(http), null);
        assertTrue(manager.submitBans(settings(true, false), bans(2)));
        assertEquals(1, http.posts.size());
        assertTrue(http.posts.get(0).startsWith("https://btn/syncBanHistory"));
    }

    @Test
    public void submitSwarm_success_postsToEndpoint() throws Exception {
        FakeHttpClient http = new FakeHttpClient();
        BtnManager manager = new BtnManager(new BtnClient(http), null);
        BtnPayload.SwarmEntry s = new BtnPayload.SwarmEntry();
        s.torrentIdentifier = "abc";
        s.peerIp = "10.0.0.1";
        s.peerPort = 6881;
        s.peerClientName = "test";
        List<BtnPayload.SwarmEntry> swarm = new ArrayList<>();
        swarm.add(s);
        assertTrue(manager.submitSwarm(settings(true, true), swarm));
        assertEquals(1, http.posts.size());
        assertTrue(http.posts.get(0).startsWith("https://btn/syncSwarm"));
    }
}