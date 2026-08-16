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
import androidx.annotation.Nullable;

import org.proninyaroslav.libretorrent.core.pbh.IpUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/*
 * Holds the set of rules fetched from a BTN instance:
 *  - IP denylist (peers to ban) and allowlist (peers to exempt);
 *  - IP CIDR sets from the peer-identity cloud rules;
 *  - client-name rules from the peer-identity cloud rules, including their
 *    match method (STARTS_WITH / ENDS_WITH / CONTAINS / EQUALS / REGEX /
 *    LENGTH), mirroring PeerBanHelper's RuleParser.
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
    /* Client-name rules to ban, from cloud rules */
    public final List<ClientNameRule> clientNameRules;
    /* Content version tokens for incremental refresh ("" = never fetched) */
    public final String denylistRev;
    public final String allowlistRev;
    public final String peerIdentityRev;

    public static final BtnRuleSet EMPTY = new BtnRuleSet(
            Collections.emptySet(), Collections.emptySet(), Collections.emptyList(),
            "", "", "");

    public BtnRuleSet(@NonNull Set<String> ipDenylist,
                      @NonNull Set<String> ipAllowlist,
                      @NonNull List<ClientNameRule> clientNameRules,
                      @NonNull String denylistRev,
                      @NonNull String allowlistRev,
                      @NonNull String peerIdentityRev) {
        this.ipDenylist = Collections.unmodifiableSet(new HashSet<>(ipDenylist));
        this.ipAllowlist = Collections.unmodifiableSet(new HashSet<>(ipAllowlist));
        this.clientNameRules = Collections.unmodifiableList(new ArrayList<>(clientNameRules));
        this.denylistRev = denylistRev;
        this.allowlistRev = allowlistRev;
        this.peerIdentityRev = peerIdentityRev;
    }

    public boolean isEmpty() {
        return ipDenylist.isEmpty() && ipAllowlist.isEmpty() && clientNameRules.isEmpty();
    }

    /*
     * A single client-name match rule from the BTN peer-identity rules.
     * String methods compare case-insensitively.
     */
    public static final class ClientNameRule {
        public enum Method {STARTS_WITH, ENDS_WITH, CONTAINS, EQUALS, REGEX, LENGTH}

        public final Method method;
        public final String content;
        @Nullable
        private final Pattern regex;
        @Nullable
        private final Integer length;

        public ClientNameRule(@NonNull Method method, @NonNull String content) {
            this.method = method;
            this.content = content;
            Pattern compiled = null;
            Integer len = null;
            switch (method) {
                case REGEX -> {
                    try {
                        compiled = Pattern.compile(content, Pattern.CASE_INSENSITIVE);
                    } catch (Exception ignored) {
                        compiled = null; /* invalid pattern: never matches */
                    }
                }
                case LENGTH -> {
                    try {
                        len = Integer.parseInt(content.trim());
                    } catch (NumberFormatException ignored) {
                        len = null; /* non-numeric length: never matches */
                    }
                }
            }
            this.regex = compiled;
            this.length = len;
        }

        public boolean matches(@Nullable String clientName) {
            if (clientName == null)
                return false;
            return switch (method) {
                case STARTS_WITH -> clientName.toLowerCase(Locale.ROOT)
                        .startsWith(content.toLowerCase(Locale.ROOT));
                case ENDS_WITH -> clientName.toLowerCase(Locale.ROOT)
                        .endsWith(content.toLowerCase(Locale.ROOT));
                case CONTAINS -> clientName.toLowerCase(Locale.ROOT)
                        .contains(content.toLowerCase(Locale.ROOT));
                case EQUALS -> clientName.toLowerCase(Locale.ROOT)
                        .equals(content.toLowerCase(Locale.ROOT));
                case REGEX -> regex != null && regex.matcher(clientName).find();
                case LENGTH -> length != null && clientName.length() == length;
            };
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ClientNameRule that)) return false;
            return method == that.method && content.equals(that.content);
        }

        @Override
        public int hashCode() {
            return 31 * method.hashCode() + content.hashCode();
        }

        @Override
        public String toString() {
            return method + "|" + content;
        }
    }

    /*
     * Encodes a rule for persistence as "METHOD|content". Entries without a
     * valid method prefix are decoded as legacy CONTAINS rules.
     */
    @NonNull
    public static String encodeRule(@NonNull ClientNameRule rule) {
        return rule.method.name() + "|" + rule.content;
    }

    @Nullable
    public static ClientNameRule decodeRule(@Nullable String entry) {
        if (entry == null)
            return null;
        String s = entry.trim();
        if (s.isEmpty())
            return null;
        int idx = s.indexOf('|');
        if (idx > 0) {
            String methodStr = s.substring(0, idx);
            String content = s.substring(idx + 1);
            if (!content.isEmpty()) {
                for (ClientNameRule.Method m : ClientNameRule.Method.values()) {
                    if (m.name().equals(methodStr))
                        return new ClientNameRule(m, content);
                }
            }
        }
        /* Legacy entry: bare content string, matched by containment */
        return new ClientNameRule(ClientNameRule.Method.CONTAINS, s);
    }

    /*
     * Whether the given IP is covered by any entry of the list. Convenience
     * wrapper around the compiled matcher for callers outside the scan hot
     * path; hot paths should reuse IpUtils.CidrMatcher directly.
     */
    public static boolean matchesIp(@NonNull String ip, @NonNull Set<String> cidrs) {
        return IpUtils.matchesAnyCidr(ip, cidrs);
    }
}
