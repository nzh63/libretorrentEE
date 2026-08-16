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

import androidx.annotation.NonNull;

import org.proninyaroslav.libretorrent.core.btn.BtnRuleSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/*
 * Port of PeerBanHelper's "PeerIdBlacklist" module: bans peers whose peer id
 * matches one of the user-configured rules. Rules use the same typed match
 * methods as the BTN peer-identity rules (STARTS_WITH / ENDS_WITH / CONTAINS /
 * EQUALS / REGEX / LENGTH); plain strings fall back to containment, matching
 * the legacy behaviour of the other manual blacklists.
 */
public class PeerIdBlacklistModule implements BanModule {
    /* Raw rule strings of the last compilation, to avoid recompiling */
    private Set<String> lastRaw = null;
    private List<BtnRuleSet.ClientNameRule> compiled = new ArrayList<>();

    @NonNull
    @Override
    public String name() {
        return "peer-id-blacklist";
    }

    @NonNull
    @Override
    public BanResult check(@NonNull TorrentSnapshot torrent,
                           @NonNull PeerSnapshot peer,
                           @NonNull PbhSettings settings) {
        if (peer.peerId.isEmpty())
            return BanResult.pass(name(), peer.ip);

        for (BtnRuleSet.ClientNameRule rule : compile(settings.peerIdBlacklist)) {
            if (rule.matches(peer.peerId)) {
                return BanResult.ban(name(), peer.ip,
                        "peer id matches rule " + rule.method + " \"" + rule.content + "\"");
            }
        }
        return BanResult.pass(name(), peer.ip);
    }

    private synchronized List<BtnRuleSet.ClientNameRule> compile(Set<String> raw) {
        if (raw == lastRaw || (raw != null && raw.equals(lastRaw)))
            return compiled;
        List<BtnRuleSet.ClientNameRule> out = new ArrayList<>();
        if (raw != null)
            for (String entry : raw) {
                BtnRuleSet.ClientNameRule rule = BtnRuleSet.decodeRule(entry);
                if (rule != null)
                    out.add(rule);
            }
        lastRaw = raw;
        compiled = out;
        return out;
    }
}
