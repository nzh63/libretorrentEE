package org.proninyaroslav.libretorrent.core.pbh;

import org.junit.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PeerIdBlacklistModuleTest {
    private final PeerIdBlacklistModule module = new PeerIdBlacklistModule();
    private final TorrentSnapshot torrent = new TorrentSnapshot("t1", "T", 1000, 0, false,
            Collections.emptyList());
    private final PbhSettings settings = PbhSettings.builder().build();

    private PbhSettings rules(Set<String> peerIds) {
        return PbhSettings.builder().peerIdBlacklist(peerIds).build();
    }

    private PeerSnapshot peer(String peerId) {
        return new PeerSnapshot("1.2.3.4", 6881, "client", peerId, 0, 0, 0, 0, 0);
    }

    @Test
    public void startsWithRule_banned() {
        module.check(torrent, peer(""), rules(Set.of())); // prime the cache
        PbhSettings s = rules(Set.of("STARTS_WITH|-xl"));
        assertTrue(module.check(torrent, peer("-xl0019abc"), s).shouldBan());
        assertFalse(module.check(torrent, peer("-TR0019abc"), s).shouldBan());
    }

    @Test
    public void bareString_fallsBackToContains() {
        PbhSettings s = rules(Set.of("cacao"));
        assertTrue(module.check(torrent, peer("-XXcacao1234"), s).shouldBan());
        assertFalse(module.check(torrent, peer("-XX12345678"), s).shouldBan());
    }

    @Test
    public void equalsRule_requiresExactMatch() {
        PbhSettings s = rules(Set.of("EQUALS|-abcdefg"));
        assertTrue(module.check(torrent, peer("-abcdefg"), s).shouldBan());
        assertFalse(module.check(torrent, peer("-abcdefgX"), s).shouldBan());
    }

    @Test
    public void emptyPeerId_passes() {
        PbhSettings s = rules(Set.of("STARTS_WITH|-xl"));
        assertFalse(module.check(torrent, peer(""), s).shouldBan());
    }

    @Test
    public void emptyRules_passes() {
        PbhSettings s = rules(Collections.emptySet());
        assertFalse(module.check(torrent, peer("-xl0019"), s).shouldBan());
    }
}
