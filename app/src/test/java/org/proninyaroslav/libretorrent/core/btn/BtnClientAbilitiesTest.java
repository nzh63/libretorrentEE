package org.proninyaroslav.libretorrent.core.btn;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/*
 * Tests the heartbeat / submit_histories / ip_query ability methods of
 * BtnClient against a fake HTTP layer.
 */
public class BtnClientAbilitiesTest {

    /* Fake HTTP layer capturing requests and returning canned responses. */
    private static class FakeHttpClient extends BtnHttpClient {
        @Nullable String getBody;
        @Nullable String postBody;
        int postStatus = 200;
        @Nullable String lastPostUrl;
        @Nullable byte[] lastPostPayload;
        @Nullable String lastGetUrl;
        boolean failPost;

        FakeHttpClient() {
            super("test-agent");
        }

        @Override
        @Nullable
        public GetResult get(@NonNull String urlStr,
                             @NonNull BtnSettings settings,
                             @Nullable String rev) throws IOException {
            lastGetUrl = urlStr;
            if (getBody == null)
                return null;
            return new GetResult(200, getBody.getBytes(StandardCharsets.UTF_8), null);
        }

        @Override
        @Nullable
        public GetResult postJson(@NonNull String urlStr,
                                  @NonNull BtnSettings settings,
                                  @NonNull byte[] jsonBody) {
            lastPostUrl = urlStr;
            lastPostPayload = jsonBody;
            if (failPost)
                return null;
            return new GetResult(postStatus,
                    postBody == null ? null : postBody.getBytes(StandardCharsets.UTF_8),
                    null);
        }

        @Override
        public int postGzipJson(@NonNull String urlStr,
                                @NonNull BtnSettings settings,
                                @NonNull byte[] jsonBody) {
            lastPostUrl = urlStr;
            lastPostPayload = jsonBody;
            return failPost ? 500 : postStatus;
        }
    }

    private static BtnSettings settings() {
        return BtnSettings.builder()
                .enabled(true)
                .configUrl("https://btn/ping/config")
                .installationId("inst-id")
                .build();
    }

    private static BtnConfig config(String json) {
        BtnConfig cfg = BtnConfig.parse(json, 20);
        assertTrue(cfg != BtnConfig.INVALID);
        return cfg;
    }

    @Test
    public void heartbeat_returnsExternalIp() {
        FakeHttpClient http = new FakeHttpClient();
        http.postBody = "{\"external_ip\":\"42.42.42.42\"}";
        BtnClient client = new BtnClient(http);
        BtnConfig config = config("""
                {"min_protocol_version":20,"max_protocol_version":20,
                 "ability":{"heartbeat":{"endpoint":"https://btn/heartbeat","interval":1800000}}}
                """);
        assertEquals("42.42.42.42", client.heartbeat(settings(), config));
        assertEquals("https://btn/heartbeat", http.lastPostUrl);
        assertNotNull(http.lastPostPayload);
        assertTrue(new String(http.lastPostPayload, StandardCharsets.UTF_8)
                .contains("\"ifaddr\":\"default\""));
    }

    @Test
    public void heartbeat_missingAbility_returnsNull() {
        FakeHttpClient http = new FakeHttpClient();
        BtnClient client = new BtnClient(http);
        BtnConfig config = config("""
                {"min_protocol_version":20,"max_protocol_version":20,"ability":{}}
                """);
        assertNull(client.heartbeat(settings(), config));
        assertNull(http.lastPostUrl);
    }

    @Test
    public void heartbeat_httpFailure_returnsNull() {
        FakeHttpClient http = new FakeHttpClient();
        http.failPost = true;
        BtnClient client = new BtnClient(http);
        BtnConfig config = config("""
                {"min_protocol_version":20,"max_protocol_version":20,
                 "ability":{"heartbeat":{"endpoint":"https://btn/heartbeat","interval":1800000}}}
                """);
        assertNull(client.heartbeat(settings(), config));
    }

    @Test
    public void submitHistory_postsGzipPayload() {
        FakeHttpClient http = new FakeHttpClient();
        BtnClient client = new BtnClient(http);
        BtnConfig config = config("""
                {"min_protocol_version":20,"max_protocol_version":20,
                 "ability":{"submit_histories":{"endpoint":"https://btn/submitHistory","interval":900000}}}
                """);
        byte[] payload = "{\"populate_time\":1}".getBytes(StandardCharsets.UTF_8);
        assertTrue(client.submitHistory(settings(), config, payload));
        assertEquals("https://btn/submitHistory", http.lastPostUrl);
        assertEquals(payload, http.lastPostPayload);
    }

    @Test
    public void queryIp_getsWithUrlEncodedIp() {
        FakeHttpClient http = new FakeHttpClient();
        http.getBody = "{\"color\":\"green\",\"labels\":[],\"bans\":{\"total\":0}}";
        BtnClient client = new BtnClient(http);
        BtnConfig config = config("""
                {"min_protocol_version":20,"max_protocol_version":20,
                 "ability":{"ip_query":{"endpoint":"https://btn/queryIp"}}}
                """);
        BtnIpQueryResult result = client.queryIp(settings(), config, "2001:db8::1");
        assertNotNull(result);
        assertEquals("green", result.color);
        assertEquals(0, result.totalBans);
        assertNotNull(http.lastGetUrl);
        assertTrue(http.lastGetUrl.contains("ip=2001%3Adb8%3A%3A1"));
    }

    @Test
    public void queryIp_unparseableBody_returnsNull() {
        FakeHttpClient http = new FakeHttpClient();
        http.getBody = "garbage";
        BtnClient client = new BtnClient(http);
        BtnConfig config = config("""
                {"min_protocol_version":20,"max_protocol_version":20,
                 "ability":{"ip_query":{"endpoint":"https://btn/queryIp"}}}
                """);
        assertNull(client.queryIp(settings(), config, "1.2.3.4"));
    }

    @Test
    public void queryIp_missingAbility_returnsNull() {
        FakeHttpClient http = new FakeHttpClient();
        BtnClient client = new BtnClient(http);
        BtnConfig config = config("""
                {"min_protocol_version":20,"max_protocol_version":20,"ability":{}}
                """);
        assertNull(client.queryIp(settings(), config, "1.2.3.4"));
        assertNull(http.lastGetUrl);
    }

    @Test
    public void submitHistory_settingsDefaultOptIn() {
        // Sanity: submit flows must stay opt-in by default
        assertTrue(!BtnSettings.builder().build().submitHistoryEnabled);
    }
}
