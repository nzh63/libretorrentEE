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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/*
 * Holds the set of rules fetched from a BTN instance:
 *  - IP denylist (peers to ban) and allowlist (peers to exempt);
 *  - IP CIDR sets from the peer-identity cloud rules;
 *  - client-name patterns from the peer-identity cloud rules.
 *
 * The BTN-Spec defines these as plain lines (for the allow/deny lists) or as
 * a JSON structure (rule_peer_identity). This container is agnostic to the
 * source and is consumed by the ban engine.
 */
public class BtnRuleSet {
    /* Peers whose IP is in this set must be banned */
    public final Set<String> ipDenylist;
    /* Peers whose IP is in this set must be exempted from banning */
    public final Set<String> ipAllowlist;
    /* Client-name substrings (lowercased) to ban, from cloud rules */
    public final Set<String> clientNamePatterns;
    /* Content version tokens for incremental refresh ("" = never fetched) */
    public final String denylistRev;
    public final String allowlistRev;
    public final String peerIdentityRev;

    public static final BtnRuleSet EMPTY = new BtnRuleSet(
            Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
            "", "", "");

    public BtnRuleSet(@NonNull Set<String> ipDenylist,
                      @NonNull Set<String> ipAllowlist,
                      @NonNull Set<String> clientNamePatterns,
                      @NonNull String denylistRev,
                      @NonNull String allowlistRev,
                      @NonNull String peerIdentityRev) {
        this.ipDenylist = Collections.unmodifiableSet(new HashSet<>(ipDenylist));
        this.ipAllowlist = Collections.unmodifiableSet(new HashSet<>(ipAllowlist));
        this.clientNamePatterns = Collections.unmodifiableSet(new HashSet<>(clientNamePatterns));
        this.denylistRev = denylistRev;
        this.allowlistRev = allowlistRev;
        this.peerIdentityRev = peerIdentityRev;
    }

    public boolean isEmpty() {
        return ipDenylist.isEmpty() && ipAllowlist.isEmpty() && clientNamePatterns.isEmpty();
    }
}