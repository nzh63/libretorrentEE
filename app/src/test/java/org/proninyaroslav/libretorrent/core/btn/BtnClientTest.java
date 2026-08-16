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
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/*
 * Verifies that the BTN client sends rev=initial when the local cache has no
 * content version, per BTN-Spec ("如果本地未缓存，此值固定为 initial").
 */
public class BtnClientTest {

    /* Captures requested rev params and returns a canned 200 response. */
    private static class RecordingHttpClient extends BtnHttpClient {
        final List<String> revs = new ArrayList<>();
        final String body;
        final String contentVersion;

        RecordingHttpClient(String body, String contentVersion) {
            super("test-agent");
            this.body = body;
            this.contentVersion = contentVersion;
        }

        @Override
        @Nullable
        public GetResult get(@NonNull String urlStr,
                             @NonNull BtnSettings settings,
                             @Nullable String rev) throws IOException {
            revs.add(rev == null ? "null" : rev);
            return new GetResult(200, body.getBytes(java.nio.charset.StandardCharsets.UTF_8), contentVersion);
        }
    }

    private static BtnSettings settings() {
        return BtnSettings.builder()
                .enabled(true)
                .configUrl("https://btn/ping/config")
                .appId("app-id")
                .appSecret("app-secret")
                .installationId("inst-id")
                .build();
    }

    @Test
    public void denylist_usesInitialRev_whenNoCachedVersion() throws Exception {
        RecordingHttpClient http = new RecordingHttpClient("# IPV4\n1.2.3.4\n", "rev-abc");
        BtnClient client = new BtnClient(http);

        BtnConfig config = BtnConfig.parse(
                """
                {"min_protocol_version":20,"max_protocol_version":20,
                 "ability":{"ip_denylist":{"endpoint":"https://btn/ruleIpDenylist","interval":600000}}}
                """, 20);
        assertTrue(config != BtnConfig.INVALID);

        BtnClient.IpListResult res = client.fetchIpDenylist(settings(), config, "");
        assertNotNull(res);
        assertEquals(Set.of("1.2.3.4"), res.ips);
        assertEquals("rev-abc", res.rev);
        assertEquals(1, http.revs.size());
        assertEquals("expected rev=initial when no cached version, got " + http.revs.get(0),
                "initial", http.revs.get(0));
    }

    @Test
    public void peerIdentity_usesInitialRev_whenNoCachedVersion() throws Exception {
        RecordingHttpClient http = new RecordingHttpClient(
                "{\"version\":\"v1\",\"client_name\":{\"x\":[\"y\"]}}",
                "rev-xyz");
        BtnClient client = new BtnClient(http);

        BtnConfig config = BtnConfig.parse(
                """
                {"min_protocol_version":20,"max_protocol_version":20,
                 "ability":{"rule_peer_identity":{"endpoint":"https://btn/rulePeerIdentity","interval":2700000}}}
                """, 20);
        assertTrue(config != BtnConfig.INVALID);

        BtnRuleSet rules = client.fetchPeerIdentityRules(settings(), config, "");
        assertNotNull(rules);
        assertEquals(1, http.revs.size());
        assertEquals("expected rev=initial when no cached version, got " + http.revs.get(0),
                "initial", http.revs.get(0));
    }

    @Test
    public void peerIdentity_unparseableBody_returnsNull_toKeepCache() throws Exception {
        RecordingHttpClient http = new RecordingHttpClient(
                "<html>502 Bad Gateway</html>", null);
        BtnClient client = new BtnClient(http);

        BtnConfig config = BtnConfig.parse(
                """
                {"min_protocol_version":20,"max_protocol_version":20,
                 "ability":{"rule_peer_identity":{"endpoint":"https://btn/rulePeerIdentity","interval":2700000}}}
                """, 20);
        assertTrue(config != BtnConfig.INVALID);

        // An unparseable body must not be reported as a successful (empty)
        // rule set, otherwise the caller would wipe its cached rules.
        BtnRuleSet rules = client.fetchPeerIdentityRules(settings(), config, "cached-rev");
        assertNull(rules);
    }

    @Test
    public void ipDenylist_nonIpBody_returnsNull_toKeepCache() throws Exception {
        RecordingHttpClient http = new RecordingHttpClient(
                "<html>502 Bad Gateway</html>", null);
        BtnClient client = new BtnClient(http);

        BtnConfig config = BtnConfig.parse(
                """
                {"min_protocol_version":20,"max_protocol_version":20,
                 "ability":{"ip_denylist":{"endpoint":"https://btn/ruleIpDenylist","interval":600000}}}
                """, 20);
        assertTrue(config != BtnConfig.INVALID);

        BtnClient.IpListResult res = client.fetchIpDenylist(settings(), config, "cached-rev");
        assertNull(res);
    }

    @Test
    public void ipDenylist_emptyBody_returnsEmptyResult() throws Exception {
        RecordingHttpClient http = new RecordingHttpClient("", null);
        BtnClient client = new BtnClient(http);

        BtnConfig config = BtnConfig.parse(
                """
                {"min_protocol_version":20,"max_protocol_version":20,
                 "ability":{"ip_denylist":{"endpoint":"https://btn/ruleIpDenylist","interval":600000}}}
                """, 20);
        assertTrue(config != BtnConfig.INVALID);

        // An empty body is a legitimate "the list is now empty" response;
        // it must be delivered to the caller so the cached list can be cleared.
        BtnClient.IpListResult res = client.fetchIpDenylist(settings(), config, "cached-rev");
        assertNotNull(res);
        assertTrue(res.ips.isEmpty());
    }
}