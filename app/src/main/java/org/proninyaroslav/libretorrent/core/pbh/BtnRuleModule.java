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
 *
 * CIDR lists are compiled once per rule-set update; client-name rules apply
 * their BTN match method (STARTS_WITH / ENDS_WITH / CONTAINS / EQUALS /
 * REGEX / LENGTH).
 */
public class BtnRuleModule implements BanModule {
    private volatile BtnRuleSet rules = BtnRuleSet.EMPTY;
    /* Derived from `rules` on update; safe to read without a lock */
    private volatile IpUtils.CidrMatcher denylistMatcher =
            IpUtils.CidrMatcher.compile(java.util.Collections.emptySet());
    private volatile IpUtils.CidrMatcher allowlistMatcher =
            IpUtils.CidrMatcher.compile(java.util.Collections.emptySet());

    @NonNull
    @Override
    public String name() {
        return "BTN";
    }

    public void setRules(@NonNull BtnRuleSet rules) {
        this.rules = rules;
        this.denylistMatcher = IpUtils.CidrMatcher.compile(rules.ipDenylist);
        this.allowlistMatcher = IpUtils.CidrMatcher.compile(rules.ipAllowlist);
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
        if (!allowlistMatcher.isEmpty() && allowlistMatcher.matches(peer.ip)) {
            return BanResult.pass(name(), peer.ip);
        }

        if (!denylistMatcher.isEmpty() && denylistMatcher.matches(peer.ip)) {
            return BanResult.ban(name(), peer.ip, "IP matches a BTN denylist rule");
        }

        if (!r.clientNameRules.isEmpty()) {
            for (BtnRuleSet.ClientNameRule rule : r.clientNameRules) {
                if (rule.matches(peer.client)) {
                    return BanResult.ban(name(), peer.ip,
                            "client name matches a BTN peer-identity rule ("
                                    + rule.method + ")");
                }
            }
        }

        return BanResult.pass(name(), peer.ip);
    }
}
