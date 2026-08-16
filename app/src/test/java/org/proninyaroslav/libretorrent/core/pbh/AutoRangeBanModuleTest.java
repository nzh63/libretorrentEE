package org.proninyaroslav.libretorrent.core.pbh;

import org.junit.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/*
 * AutoRangeBan ("连坐"): a peer is banned when its IP shares the configured
 * prefix with an address that is already banned.
 */
public class AutoRangeBanModuleTest {
    private final AutoRangeBanModule module = new AutoRangeBanModule();
    private final TorrentSnapshot torrent = new TorrentSnapshot("t1", "T", 1000, 0, false,
            Collections.emptyList());

    private PbhSettings settings(boolean enabled, int v4, int v6) {
        return PbhSettings.builder()
                .rangeBanEnabled(enabled)
                .rangeBanIpv4PrefixLength(v4)
                .rangeBanIpv6PrefixLength(v6)
                .build();
    }

    private PeerSnapshot peer(String ip) {
        return new PeerSnapshot(ip, 6881, "client", "", 0, 0, 0, 0, 0);
    }

    @Test
    public void samePrefix_banned() {
        module.updateBannedAddresses(Set.of("1.2.3.4"), 24, 64);
        assertTrue(module.check(torrent, peer("1.2.3.99"),
                settings(true, 24, 64)).shouldBan());
    }

    @Test
    public void differentPrefix_passes() {
        module.updateBannedAddresses(Set.of("1.2.3.4"), 24, 64);
        assertFalse(module.check(torrent, peer("1.2.4.4"),
                settings(true, 24, 64)).shouldBan());
    }

    @Test
    public void disabled_passes() {
        module.updateBannedAddresses(Set.of("1.2.3.4"), 24, 64);
        assertFalse(module.check(torrent, peer("1.2.3.99"),
                settings(false, 24, 64)).shouldBan());
    }

    @Test
    public void customPrefixLength_respected() {
        // /30 covers 1.2.3.4 - 1.2.3.7
        module.updateBannedAddresses(Set.of("1.2.3.4"), 30, 48);
        assertTrue(module.check(torrent, peer("1.2.3.5"),
                settings(true, 30, 48)).shouldBan());
        assertFalse(module.check(torrent, peer("1.2.3.8"),
                settings(true, 30, 48)).shouldBan());
    }

    @Test
    public void ipv6Prefix_banned() {
        module.updateBannedAddresses(Set.of("2001:db8:1:2::1"), 24, 48);
        assertTrue(module.check(torrent, peer("2001:db8:1:2:8000::9"),
                settings(true, 24, 48)).shouldBan());
        assertFalse(module.check(torrent, peer("2001:db8:2:3::1"),
                settings(true, 24, 48)).shouldBan());
    }

    @Test
    public void exactBannedIp_itself_banned() {
        // The banned address itself falls into its own prefix block
        module.updateBannedAddresses(Set.of("1.2.3.4"), 24, 64);
        assertTrue(module.check(torrent, peer("1.2.3.4"),
                settings(true, 24, 64)).shouldBan());
    }

    @Test
    public void emptyBannedSet_passes() {
        module.updateBannedAddresses(Collections.emptySet(), 24, 64);
        assertFalse(module.check(torrent, peer("1.2.3.4"),
                settings(true, 24, 64)).shouldBan());
    }

    @Test
    public void nonIpEntries_ignored() {
        module.updateBannedAddresses(Set.of("not-an-ip", "10.0.0.0/8"), 24, 64);
        assertFalse(module.check(torrent, peer("10.1.2.3"),
                settings(true, 24, 64)).shouldBan());
    }
}
