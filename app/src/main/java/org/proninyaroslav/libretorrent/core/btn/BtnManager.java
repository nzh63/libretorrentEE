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

/*
 * Orchestrates BTN rule refresh: fetches the config (once), then periodically
 * refreshes the IP allow/deny lists and peer-identity rules, merging them into
 * a single BtnRuleSet that is persisted and exposed to the ban engine.
 *
 * The caller invokes refresh() on the configured interval and supplies the
 * current settings. Config is lazily fetched on the first refresh.
 */
public class BtnManager {
    private final BtnClient client;
    private final BtnRuleStore store;

    @Nullable private BtnConfig config;
    private long lastConfigFetchMs;
    private long lastDenylistFetchMs;
    private long lastAllowlistFetchMs;
    private long lastPeerIdentityFetchMs;

    public BtnManager(@NonNull BtnClient client, @NonNull BtnRuleStore store) {
        this.client = client;
        this.store = store;
    }

    /*
     * Returns the currently persisted rules without any network access. Safe
     * to call from any thread (including the main/UI thread at startup).
     */
    @NonNull
    public BtnRuleSet load() {
        return store.load();
    }

    /*
     * Performs a refresh cycle. Returns the merged rules (persisted). If BTN
     * is disabled or config is missing, returns the currently stored rules.
     *
     * NOTE: performs network I/O and MUST NOT be called from the main thread.
     */
    @NonNull
    public synchronized BtnRuleSet refresh(@NonNull BtnSettings settings) {
        if (!settings.complete()) {
            BtnRuleSet stored = store.load();
            return stored;
        }

        long now = System.currentTimeMillis();
        BtnRuleSet base = store.load();

        // Fetch config lazily (and re-fetch every 6h as a safety net).
        if (config == null || now - lastConfigFetchMs > 6 * 3600_000L) {
            config = client.fetchConfig(settings);
            lastConfigFetchMs = now;
        }
        if (config == null || config == BtnConfig.INVALID)
            return base;

        java.util.Set<String> denylist = new java.util.HashSet<>(base.ipDenylist);
        java.util.Set<String> allowlist = new java.util.HashSet<>(base.ipAllowlist);
        java.util.List<BtnRuleSet.ClientNameRule> clientNames =
                new java.util.ArrayList<>(base.clientNameRules);
        String denylistRev = base.denylistRev;
        String allowlistRev = base.allowlistRev;
        String peerIdentityRev = base.peerIdentityRev;

        // Denylist
        if (config.ipDenylistEndpoint != null && now - lastDenylistFetchMs > config.ipDenylistInterval) {
            BtnClient.IpListResult res = client.fetchIpDenylist(settings, config, denylistRev);
            if (res != null) {
                denylist = new java.util.HashSet<>(res.ips);
                denylistRev = res.rev;
                lastDenylistFetchMs = now;
            }
        }

        // Allowlist
        if (config.ipAllowlistEndpoint != null && now - lastAllowlistFetchMs > config.ipAllowlistInterval) {
            BtnClient.IpListResult res = client.fetchIpAllowlist(settings, config, allowlistRev);
            if (res != null) {
                allowlist = new java.util.HashSet<>(res.ips);
                allowlistRev = res.rev;
                lastAllowlistFetchMs = now;
            }
        }

        // Peer identity rules
        if (config.peerIdentityEndpoint != null && now - lastPeerIdentityFetchMs > config.peerIdentityInterval) {
            BtnRuleSet res = client.fetchPeerIdentityRules(settings, config, peerIdentityRev);
            if (res != null) {
                clientNames = new java.util.ArrayList<>(res.clientNameRules);
                denylist.addAll(res.ipDenylist);
                peerIdentityRev = res.peerIdentityRev;
                lastPeerIdentityFetchMs = now;
            }
        }

        BtnRuleSet merged = new BtnRuleSet(
                denylist, allowlist, clientNames,
                denylistRev, allowlistRev, peerIdentityRev);
        store.save(merged);
        return merged;
    }

    public void clear() {
        store.clear();
        config = null;
        lastConfigFetchMs = 0;
        lastDenylistFetchMs = 0;
        lastAllowlistFetchMs = 0;
        lastPeerIdentityFetchMs = 0;
    }

    /*
     * Submits a batch of bans to the BTN instance. Returns true when the
     * server acknowledged the submission (2xx). If the server config has not
     * been fetched yet, tries to fetch it first. No-op when BTN is disabled,
     * the config is unavailable or the submit_bans ability is not offered.
     *
     * NOTE: performs network I/O and MUST NOT be called from the main thread.
     */
    public boolean submitBans(@NonNull BtnSettings settings,
                              @NonNull java.util.List<BtnPayload.BanEntry> bans) {
        if (!settings.complete() || !settings.submitBansEnabled)
            return false;
        if (bans.isEmpty())
            return false;
        if (config == null || config == BtnConfig.INVALID) {
            config = client.fetchConfig(settings);
            if (config == null || config == BtnConfig.INVALID)
                return false;
        }
        if (config.submitBansEndpoint == null || config.submitBansEndpoint.isEmpty())
            return false;
        return client.submitBans(settings, config, BtnPayload.buildSubmitBans(bans));
    }

    /*
     * Submits a batch of swarm snapshots to the BTN instance. Returns true
     * when the server acknowledged the submission (2xx).
     *
     * NOTE: performs network I/O and MUST NOT be called from the main thread.
     */
    public boolean submitSwarm(@NonNull BtnSettings settings,
                               @NonNull java.util.List<BtnPayload.SwarmEntry> swarm) {
        if (!settings.complete() || !settings.submitSwarmEnabled)
            return false;
        if (swarm.isEmpty())
            return false;
        if (config == null || config == BtnConfig.INVALID) {
            config = client.fetchConfig(settings);
            if (config == null || config == BtnConfig.INVALID)
                return false;
        }
        if (config.submitSwarmEndpoint == null || config.submitSwarmEndpoint.isEmpty())
            return false;
        return client.submitSwarm(settings, config, BtnPayload.buildSubmitSwarm(swarm));
    }
}