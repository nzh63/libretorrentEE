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
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Orchestrates the anti-leech detection modules against a snapshot of all
 * torrents and their peers. Returns the set of bans to apply.
 *
 * This is the in-process equivalent of PeerBanHelper's pipeline: each module
 * inspects each peer and the engine collects the union of ban decisions.
 */
public class PeerBanHelperEngine {
    private final List<BanModule> modules;
    private final BtnRuleModule btnRuleModule;
    @Nullable
    private final ProgressCheatModule progressCheatModule;
    @Nullable
    private final AutoRangeBanModule rangeBanModule;

    public PeerBanHelperEngine() {
        this.btnRuleModule = new BtnRuleModule();
        this.progressCheatModule = new ProgressCheatModule();
        this.rangeBanModule = new AutoRangeBanModule();
        this.modules = new ArrayList<>();
        this.modules.add(progressCheatModule);
        this.modules.add(new AntiVampireModule());
        this.modules.add(new IpAddressBlacklistModule());
        this.modules.add(new PeerIdBlacklistModule());
        this.modules.add(btnRuleModule);
        this.modules.add(rangeBanModule);
    }

    public PeerBanHelperEngine(List<BanModule> modules) {
        this.btnRuleModule = new BtnRuleModule();
        ProgressCheatModule pcb = null;
        AutoRangeBanModule rangeBan = null;
        for (BanModule m : modules) {
            if (m instanceof ProgressCheatModule)
                pcb = (ProgressCheatModule) m;
            if (m instanceof AutoRangeBanModule)
                rangeBan = (AutoRangeBanModule) m;
        }
        this.progressCheatModule = pcb;
        this.rangeBanModule = rangeBan;
        this.modules = new ArrayList<>(modules);
    }

    /*
     * Updates the BTN cloud rules used by the BTN module. Call this after the
     * BTN client has refreshed the rules.
     */
    public void updateBtnRules(@NonNull org.proninyaroslav.libretorrent.core.btn.BtnRuleSet rules) {
        btnRuleModule.setRules(rules);
    }

    @NonNull
    public BtnRuleModule getBtnRuleModule() {
        return btnRuleModule;
    }

    /* The ProgressCheatBlocker module, or null if not configured. */
    @Nullable
    public ProgressCheatModule getProgressCheatModule() {
        return progressCheatModule;
    }

    /*
     * Evicts ProgressCheatBlocker tracking state that has not been updated
     * for longer than maxAgeMs, so the state maps cannot grow without bound.
     * No-op when no PCB module is configured.
     */
    public void evictStaleState(long maxAgeMs, long nowMs) {
        if (progressCheatModule != null)
            progressCheatModule.evictStale(maxAgeMs, nowMs);
    }

    /*
     * Drops all ProgressCheatBlocker tracking state of the given torrent
     * (e.g. after the torrent was deleted).
     */
    public void evictTorrentState(String torrentId) {
        if (progressCheatModule != null)
            progressCheatModule.evictTorrent(torrentId);
    }

    /*
     * Feeds the currently banned addresses (manual + active auto bans, without
     * disconnect probes) to the AutoRangeBan module before an engine pass.
     * No-op when no AutoRangeBan module is configured.
     */
    public void updateRangeBanAddresses(@NonNull Set<String> bannedIps,
                                        @NonNull PbhSettings settings) {
        if (rangeBanModule != null)
            rangeBanModule.updateBannedAddresses(bannedIps,
                    settings.rangeBanIpv4PrefixLength,
                    settings.rangeBanIpv6PrefixLength);
    }

    /*
     * Run all modules against every torrent/peer. Returns the set of ban
     * decisions (in insertion order), each tagged with the torrent it
     * belongs to. If the master switch is off, returns an empty set.
     * BAN_FOR_DISCONNECT results are temporary probes and must be handled
     * separately from real bans by the caller.
     */
    @NonNull
    public List<BanResult> evaluate(@NonNull List<TorrentSnapshot> torrents,
                                    @NonNull PbhSettings settings) {
        if (!settings.enabled)
            return Collections.emptyList();

        List<BanResult> results = new ArrayList<>();
        for (TorrentSnapshot torrent : torrents) {
            for (PeerSnapshot peer : torrent.peers) {
                if (peer == null || peer.ip == null || peer.ip.isEmpty())
                    continue;
                for (BanModule module : modules) {
                    BanResult result = module.check(torrent, peer, settings);
                    if (result.shouldBan())
                        results.add(result.withTorrentId(torrent.id));
                }
            }
        }

        return results;
    }

    /*
     * Convenience: evaluate and return the distinct set of banned IPs.
     */
    @NonNull
    public Set<String> evaluateBanSet(@NonNull List<TorrentSnapshot> torrents,
                                      @NonNull PbhSettings settings) {
        List<BanResult> results = evaluate(torrents, settings);
        LinkedHashSet<String> ips = new LinkedHashSet<>();
        for (BanResult r : results) {
            if (r.peerIp != null)
                ips.add(r.peerIp);
        }
        return ips;
    }

    /*
     * Returns a map of module name -> list of bans, for diagnostics/UI.
     */
    @NonNull
    public Map<String, List<BanResult>> evaluateGrouped(@NonNull List<TorrentSnapshot> torrents,
                                                        @NonNull PbhSettings settings) {
        Map<String, List<BanResult>> grouped = new LinkedHashMap<>();
        for (BanResult r : evaluate(torrents, settings)) {
            grouped.computeIfAbsent(r.module, k -> new ArrayList<>()).add(r);
        }
        return grouped;
    }
}