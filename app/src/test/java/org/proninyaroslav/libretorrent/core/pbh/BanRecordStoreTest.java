package org.proninyaroslav.libretorrent.core.pbh;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BanRecordStoreTest {
    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private BanRecordStore store() throws Exception {
        File f = tmp.newFile();
        //noinspection ResultOfMethodCallIgnored
        f.delete();
        return new BanRecordStore(f);
    }

    private BanRecord record(String ip, long bannedAt, long expireAt) {
        return new BanRecord(ip, "progress-cheat", "excessive upload",
                "torrent.bin", bannedAt, expireAt);
    }

    @Test
    public void record_andGet() throws Exception {
        BanRecordStore s = store();
        s.record(record("1.2.3.4", 100, 200));
        BanRecord r = s.get("1.2.3.4");
        assertNotNull(r);
        assertEquals("progress-cheat", r.module);
        assertEquals("excessive upload", r.reason);
        assertEquals("torrent.bin", r.torrentName);
        assertEquals(100, r.bannedAtMs);
        assertEquals(200, r.expireAtMs);
    }

    @Test
    public void record_doesNotExtendExpiry() throws Exception {
        BanRecordStore s = store();
        s.record(record("1.2.3.4", 100, 200));
        // Re-ban attempt with a later expiry must keep the original expiry
        s.record(record("1.2.3.4", 300, 999));
        BanRecord r = s.get("1.2.3.4");
        assertNotNull(r);
        assertEquals(100, r.bannedAtMs);
        assertEquals(200, r.expireAtMs);
    }

    @Test
    public void activeIps_excludesExpired() throws Exception {
        BanRecordStore s = store();
        s.record(record("1.2.3.4", 100, 150));
        s.record(record("5.6.7.8", 100, 0)); // permanent
        assertEquals(Set.of("1.2.3.4", "5.6.7.8"), s.activeIps(140));
        assertEquals(Set.of("5.6.7.8"), s.activeIps(150));
        assertEquals(Set.of("5.6.7.8"), s.activeIps(10_000));
    }

    @Test
    public void removeExpired_returnsExpiredOnly() throws Exception {
        BanRecordStore s = store();
        s.record(record("1.2.3.4", 100, 150));
        s.record(record("5.6.7.8", 100, 0));
        List<BanRecord> expired = s.removeExpired(200);
        assertEquals(1, expired.size());
        assertEquals("1.2.3.4", expired.get(0).ip);
        assertNull(s.get("1.2.3.4"));
        assertNotNull(s.get("5.6.7.8"));
    }

    @Test
    public void remove_unbans() throws Exception {
        BanRecordStore s = store();
        s.record(record("1.2.3.4", 100, 0));
        assertNotNull(s.remove("1.2.3.4"));
        assertNull(s.get("1.2.3.4"));
        assertNull(s.remove("1.2.3.4"));
    }

    @Test
    public void persistsAcrossRestart() throws Exception {
        File f = tmp.newFile();
        //noinspection ResultOfMethodCallIgnored
        f.delete();
        BanRecordStore first = new BanRecordStore(f);
        first.record(record("1.2.3.4", 100, 0));
        first.record(record("5.6.7.8", 100, 200));
        BanRecordStore second = new BanRecordStore(f);
        assertNotNull(second.get("1.2.3.4"));
        assertNotNull(second.get("5.6.7.8"));
        assertEquals(2, second.size());
        assertTrue(second.activeIps(300).contains("1.2.3.4"));
        assertEquals(Set.of("1.2.3.4"), second.activeIps(300));
    }
}
