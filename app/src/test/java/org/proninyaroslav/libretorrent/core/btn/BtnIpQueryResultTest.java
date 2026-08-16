package org.proninyaroslav.libretorrent.core.btn;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BtnIpQueryResultTest {
    @Test
    public void parse_fullBody() {
        String json = "{"
                + "\"color\":\"red\","
                + "\"labels\":[\"PT-lamer\",\"Fake Progress\"],"
                + "\"bans\":{\"duration\":86400000,\"total\":42,\"records\":[]},"
                + "\"swarms\":{\"duration\":86400000,\"total\":3,\"records\":[],"
                + "\"concurrent_download_torrents_count\":2,"
                + "\"concurrent_seeding_torrents_count\":5},"
                + "\"traffic\":{\"duration\":86400000,\"to_peer_traffic\":1024,"
                + "\"from_peer_traffic\":512,\"share_ratio\":2.0},"
                + "\"torrents\":{\"duration\":86400000,\"count\":7}"
                + "}";
        BtnIpQueryResult r = BtnIpQueryResult.parse(json);
        assertNotNull(r);
        assertEquals("red", r.color);
        assertEquals(2, r.labels.size());
        assertTrue(r.labels.contains("PT-lamer"));
        assertEquals(42, r.totalBans);
        assertEquals(2, r.concurrentDownloads);
        assertEquals(5, r.concurrentSeeds);
        assertEquals(1024, r.toPeerTraffic);
        assertEquals(512, r.fromPeerTraffic);
    }

    @Test
    public void parse_minimalBody_defaults() {
        BtnIpQueryResult r = BtnIpQueryResult.parse("{\"color\":\"gray\"}");
        assertNotNull(r);
        assertEquals("gray", r.color);
        assertTrue(r.labels.isEmpty());
        assertEquals(-1, r.totalBans);
        assertEquals(-1, r.concurrentDownloads);
    }

    @Test
    public void parse_garbage_returnsNull() {
        assertNull(BtnIpQueryResult.parse("not json"));
        assertNull(BtnIpQueryResult.parse("[1,2,3]"));
        assertNull(BtnIpQueryResult.parse(""));
    }
}
