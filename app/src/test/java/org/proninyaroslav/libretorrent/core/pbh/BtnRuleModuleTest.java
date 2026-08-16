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

package org.proninyaroslav.libretorrent.core.pbh;

import org.junit.Test;
import org.proninyaroslav.libretorrent.core.btn.BtnRuleSet;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BtnRuleModuleTest {
    private final BtnRuleModule module = new BtnRuleModule();
    private final TorrentSnapshot torrent = new TorrentSnapshot("t1", "T", 1000, 0, false,
            Collections.emptyList());
    private final PbhSettings settings = PbhSettings.builder().build();

    private PeerSnapshot peer(String ip, String client) {
        return new PeerSnapshot(ip, 6881, client, "", 0, 0, 0, 0, 0);
    }

    @Test
    public void emptyRules_passes() {
        module.setRules(BtnRuleSet.EMPTY);
        assertFalse(module.check(torrent, peer("1.2.3.4", "client"), settings).shouldBan());
    }

    @Test
    public void denylistMatch_banned() {
        module.setRules(new BtnRuleSet(
                Collections.singleton("10.0.0.0/8"), Collections.emptySet(),
                Collections.emptyList(), Collections.emptyList(), "r1", "", ""));
        assertTrue(module.check(torrent, peer("10.1.2.3", "client"), settings).shouldBan());
        assertFalse(module.check(torrent, peer("8.8.8.8", "client"), settings).shouldBan());
    }

    @Test
    public void allowlistOverridesDenylist() {
        module.setRules(new BtnRuleSet(
                Collections.singleton("10.0.0.0/8"), Collections.singleton("10.1.2.3"),
                Collections.emptyList(), Collections.emptyList(), "r1", "r2", ""));
        // 10.1.2.3 is in both allowlist and denylist -> allowlist wins.
        assertFalse(module.check(torrent, peer("10.1.2.3", "client"), settings).shouldBan());
        // 10.9.9.9 only in denylist -> banned.
        assertTrue(module.check(torrent, peer("10.9.9.9", "client"), settings).shouldBan());
    }

    @Test
    public void clientNameRuleMatch_banned_byMethod() {
        // STARTS_WITH only matches at the beginning, unlike the old
        // contains-everything behaviour.
        module.setRules(new BtnRuleSet(
                Collections.emptySet(), Collections.emptySet(),
                Collections.singletonList(new BtnRuleSet.ClientNameRule(
                        BtnRuleSet.ClientNameRule.Method.STARTS_WITH, "gopeed")),
                Collections.emptyList(), "", "", "r1"));
        assertTrue(module.check(torrent, peer("1.2.3.4", "gopeed dev"), settings).shouldBan());
        assertFalse(module.check(torrent, peer("1.2.3.4", "fake gopeed clone"), settings).shouldBan());
        assertFalse(module.check(torrent, peer("1.2.3.4", "qBittorrent"), settings).shouldBan());
    }

    @Test
    public void peerIdRuleMatch_banned() {
        module.setRules(new BtnRuleSet(
                Collections.emptySet(), Collections.emptySet(), Collections.emptyList(),
                Collections.singletonList(new BtnRuleSet.ClientNameRule(
                        BtnRuleSet.ClientNameRule.Method.STARTS_WITH, "-xl")),
                "", "", "r1"));
        PeerSnapshot xlPeer = new PeerSnapshot("1.2.3.4", 6881, "xl0019", "-xl0019abc",
                0, 0, 0, 0, 0);
        PeerSnapshot normalPeer = new PeerSnapshot("1.2.3.4", 6881, "client", "-TR0019abc",
                0, 0, 0, 0, 0);
        PeerSnapshot noPeerId = new PeerSnapshot("1.2.3.4", 6881, "client", "",
                0, 0, 0, 0, 0);
        assertTrue(module.check(torrent, xlPeer, settings).shouldBan());
        assertFalse(module.check(torrent, normalPeer, settings).shouldBan());
        assertFalse(module.check(torrent, noPeerId, settings).shouldBan());
    }

    @Test
    public void clientNameRegexRule_banned() {
        module.setRules(new BtnRuleSet(
                Collections.emptySet(), Collections.emptySet(),
                Collections.singletonList(new BtnRuleSet.ClientNameRule(
                        BtnRuleSet.ClientNameRule.Method.REGEX, "^xl.*")),
                Collections.emptyList(), "", "", "r1"));
        assertTrue(module.check(torrent, peer("1.2.3.4", "xl0019"), settings).shouldBan());
        assertFalse(module.check(torrent, peer("1.2.3.4", "qBittorrent 4.6"), settings).shouldBan());
    }
}