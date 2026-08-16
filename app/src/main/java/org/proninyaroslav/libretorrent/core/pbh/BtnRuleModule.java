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

/*
 * Bans peers whose IP matches a BTN denylist rule and exempts peers that
 * match the BTN allowlist. The rule set is supplied by the BTN client and is
 * refreshed periodically.
 */
public class BtnRuleModule implements BanModule {
    private volatile BtnRuleSet rules = BtnRuleSet.EMPTY;

    @NonNull
    @Override
    public String name() {
        return "BTN";
    }

    public void setRules(@NonNull BtnRuleSet rules) {
        this.rules = rules;
    }

    @NonNull
    public BtnRuleSet getRules() {
        return rules;
    }

    @NonNull
    @Override
    public BanResult check(@NonNull TorrentSnapshot torrent,
                           @NonNull PeerSnapshot peer,
                           @NonNull PbhSettings settings) {
        BtnRuleSet r = rules;
        if (r.isEmpty())
            return BanResult.pass(name(), peer.ip);

        // Allowlist always wins: exempted peers are never banned.
        if (!r.ipAllowlist.isEmpty() &&
                org.proninyaroslav.libretorrent.core.pbh.IpUtils.matchesAnyCidr(peer.ip, r.ipAllowlist)) {
            return BanResult.pass(name(), peer.ip);
        }

        if (!r.ipDenylist.isEmpty() &&
                org.proninyaroslav.libretorrent.core.pbh.IpUtils.matchesAnyCidr(peer.ip, r.ipDenylist)) {
            return BanResult.ban(name(), peer.ip, "IP matches a BTN denylist rule");
        }

        if (!r.clientNamePatterns.isEmpty()) {
            String clientLower = peer.client.toLowerCase(java.util.Locale.ROOT);
            for (String pattern : r.clientNamePatterns) {
                if (pattern != null && !pattern.isEmpty() && clientLower.contains(pattern)) {
                    return BanResult.ban(name(), peer.ip,
                            "client name matches a BTN peer-identity rule");
                }
            }
        }

        return BanResult.pass(name(), peer.ip);
    }
}