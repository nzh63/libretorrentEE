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

import java.io.IOException;
import java.util.Set;

/*
 * High-level BTN client that fetches the server config and the rules
 * (IP allow/deny lists and peer-identity rules) and merges them into a
 * BtnRuleSet. Stateless: the caller is responsible for persisting the rule
 * revs between refreshes.
 */
public class BtnClient {
    public static final int CLIENT_PROTOCOL_VERSION = 20; /* BTN-Spec internal version */
    /* Per BTN-Spec, "rev" must be "initial" when the client has no cached copy */
    public static final String INITIAL_REV = "initial";

    private final BtnHttpClient http;

    public BtnClient() {
        this(BtnHttpClient.defaultClient());
    }

    public BtnClient(@NonNull BtnHttpClient http) {
        this.http = http;
    }

    @NonNull
    public BtnHttpClient getHttp() {
        return http;
    }

    /*
     * Fetches the server config and returns it, or BtnConfig.INVALID on
     * protocol mismatch or network failure.
     */
    @NonNull
    public BtnConfig fetchConfig(@NonNull BtnSettings settings) {
        if (!settings.complete())
            return BtnConfig.INVALID;
        try {
            BtnHttpClient.GetResult res = http.get(settings.configUrl, settings, null);
            if (res == null || !res.isSuccessful() || res.body == null)
                return BtnConfig.INVALID;
            return BtnConfig.parse(res.bodyAsUtf8(), CLIENT_PROTOCOL_VERSION);
        } catch (IOException e) {
            return BtnConfig.INVALID;
        }
    }

    /*
     * Fetches the IP denylist. Returns null if the server has no changes (204)
     * or on failure; otherwise returns the parsed IP set and the new rev.
     */
    @Nullable
    public IpListResult fetchIpDenylist(@NonNull BtnSettings settings,
                                        @NonNull BtnConfig config,
                                        @NonNull String rev) {
        return fetchIpList(settings, config, config.ipDenylistEndpoint, rev,
                config.ipDenylistPow ? "ip_denylist" : null);
    }

    @Nullable
    public IpListResult fetchIpAllowlist(@NonNull BtnSettings settings,
                                         @NonNull BtnConfig config,
                                         @NonNull String rev) {
        return fetchIpList(settings, config, config.ipAllowlistEndpoint, rev,
                config.ipAllowlistPow ? "ip_allowlist" : null);
    }

    @Nullable
    private IpListResult fetchIpList(@NonNull BtnSettings settings,
                                     @NonNull BtnConfig config,
                                     @Nullable String endpoint,
                                     @NonNull String rev,
                                     @Nullable String powType) {
        if (endpoint == null || endpoint.isEmpty())
            return null;
        if (rev == null || rev.isEmpty())
            rev = INITIAL_REV;
        try {
            BtnHttpClient.GetResult res = http.get(endpoint, settings, rev,
                    powHeaders(settings, config, powType));
            if (res == null || res.isNoContent())
                return null; // no change
            if (!res.isSuccessful() || res.body == null)
                return null;
            String body = res.bodyAsUtf8();
            if (body == null)
                return null;
            Set<String> ips = BtnIpListParser.parse(body);
            /*
             * An empty body is a legitimate "the list is now empty" signal
             * from the server, but a non-empty body that yields zero entries
             * means the response is not a valid IP list (e.g. an HTML error
             * page). Treat the latter as a failure so we don't wipe the
             * locally cached rules.
             */
            if (ips.isEmpty() && !body.trim().isEmpty())
                return null;
            String newRev = res.contentVersion != null ? res.contentVersion : rev;
            return new IpListResult(ips, newRev);
        } catch (IOException e) {
            return null;
        }
    }

    /*
     * Fetches the peer-identity cloud rules. Returns null on no-change/error.
     */
    @Nullable
    public BtnRuleSet fetchPeerIdentityRules(@NonNull BtnSettings settings,
                                             @NonNull BtnConfig config,
                                             @NonNull String rev) {
        if (config.peerIdentityEndpoint == null || config.peerIdentityEndpoint.isEmpty())
            return null;
        if (rev == null || rev.isEmpty())
            rev = INITIAL_REV;
        try {
            BtnHttpClient.GetResult res = http.get(config.peerIdentityEndpoint, settings, rev,
                    powHeaders(settings, config,
                            config.peerIdentityPow ? "rule_peer_identity" : null));
            if (res == null || res.isNoContent())
                return null;
            if (!res.isSuccessful() || res.body == null)
                return null;
            String body = res.bodyAsUtf8();
            if (body == null)
                return null;
            BtnRuleSet parsed = BtnPeerIdentityParser.parse(body);
            /*
             * Distinguish "server has genuinely no rules" from "the response
             * could not be parsed". A successful parse always yields a rev
             * (the top-level "version" field), while the EMPTY sentinel has
             * none; treating an unparseable body as a successful empty result
             * would wipe the locally cached rules and reset the rev, causing
             * an infinite re-fetch loop.
             */
            if (parsed == BtnRuleSet.EMPTY || parsed.peerIdentityRev.isEmpty())
                return null;
            return parsed;
        } catch (IOException e) {
            return null;
        }
    }

    /*
     * Submits a ban list to the server. Returns true on a 2xx response.
     */
    public boolean submitBans(@NonNull BtnSettings settings,
                              @NonNull BtnConfig config,
                              @NonNull byte[] payload) {
        if (config.submitBansEndpoint == null || config.submitBansEndpoint.isEmpty())
            return false;
        return submit(settings, config, config.submitBansEndpoint, payload,
                config.submitBansPow ? "submit_bans" : null);
    }

    /*
     * Submits swarm data to the server. Returns true on a 2xx response.
     */
    public boolean submitSwarm(@NonNull BtnSettings settings,
                               @NonNull BtnConfig config,
                               @NonNull byte[] payload) {
        if (config.submitSwarmEndpoint == null || config.submitSwarmEndpoint.isEmpty())
            return false;
        return submit(settings, config, config.submitSwarmEndpoint, payload,
                config.submitSwarmPow ? "submit_swarm" : null);
    }

    private boolean submit(@NonNull BtnSettings settings,
                           @NonNull BtnConfig config,
                           @NonNull String endpoint,
                           @NonNull byte[] payload,
                           @Nullable String powType) {
        try {
            int code = http.postGzipJson(endpoint, settings, payload,
                    powHeaders(settings, config, powType));
            return code >= 200 && code < 300;
        } catch (IOException e) {
            return false;
        }
    }

    /*
     * Solves the proof-of-work captcha for the given ability type when the
     * server requires one. Returns null when no captcha is needed or the
     * challenge could not be solved (the request is then sent without the
     * headers, mirroring upstream's fallback behaviour).
     */
    @Nullable
    private java.util.Map<String, String> powHeaders(@NonNull BtnSettings settings,
                                                     @NonNull BtnConfig config,
                                                     @Nullable String powType) {
        if (powType == null)
            return null;
        if (config.powCaptchaEndpoint == null || config.powCaptchaEndpoint.isEmpty())
            return null;
        return http.gatherPowHeaders(config.powCaptchaEndpoint, powType, settings);
    }

    /*
     * Sends a heartbeat to the server (ability "heartbeat"). The body is
     * {"ifaddr":"default"} - like upstream with multi_if disabled, the request
     * always leaves through the default network interface. Returns the
     * external IP the server observed in the response (may be an empty
     * string), or null when the ability is missing or the request failed.
     */
    @Nullable
    public String heartbeat(@NonNull BtnSettings settings,
                            @NonNull BtnConfig config) {
        if (config.heartbeatEndpoint == null || config.heartbeatEndpoint.isEmpty())
            return null;
        try {
            byte[] body = "{\"ifaddr\":\"default\"}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            BtnHttpClient.GetResult res =
                    http.postJson(config.heartbeatEndpoint, settings, body,
                            powHeaders(settings, config,
                                    config.heartbeatPow ? "heartbeat" : null));
            if (res == null || !res.isSuccessful())
                return null;
            if (res.body == null || res.isNoContent())
                return "";
            String json = res.bodyAsUtf8();
            if (json == null)
                return "";
            com.google.gson.JsonElement root =
                    com.google.gson.JsonParser.parseString(json);
            if (!root.isJsonObject())
                return "";
            com.google.gson.JsonObject obj = root.getAsJsonObject();
            if (obj.has("external_ip") && obj.get("external_ip").isJsonPrimitive())
                return obj.get("external_ip").getAsString();
            return "";
        } catch (Exception e) {
            return null;
        }
    }

    /*
     * Submits peer history records (legacy ability "submit_histories") as a
     * gzip JSON payload. Returns true on a 2xx response.
     */
    public boolean submitHistory(@NonNull BtnSettings settings,
                                 @NonNull BtnConfig config,
                                 @NonNull byte[] payload) {
        if (config.submitHistoryEndpoint == null || config.submitHistoryEndpoint.isEmpty())
            return false;
        // Upstream challenges submit_histories under the singular type name
        return submit(settings, config, config.submitHistoryEndpoint, payload,
                config.submitHistoryPow ? "submit_history" : null);
    }

    /*
     * Queries the BTN network's aggregated information about one IP
     * (ability "ip_query"). Returns null when the ability is missing, the
     * request failed or the body was unparseable.
     */
    @Nullable
    public BtnIpQueryResult queryIp(@NonNull BtnSettings settings,
                                    @NonNull BtnConfig config,
                                    @NonNull String ip) {
        if (config.ipQueryEndpoint == null || config.ipQueryEndpoint.isEmpty())
            return null;
        try {
            String url = config.ipQueryEndpoint
                    + (config.ipQueryEndpoint.contains("?") ? "&" : "?")
                    + "ip=" + java.net.URLEncoder.encode(ip, "UTF-8");
            BtnHttpClient.GetResult res = http.get(url, settings, null,
                    powHeaders(settings, config, config.ipQueryPow ? "ip_query" : null));
            if (res == null || !res.isSuccessful() || res.body == null)
                return null;
            String body = res.bodyAsUtf8();
            return body == null ? null : BtnIpQueryResult.parse(body);
        } catch (Exception e) {
            return null;
        }
    }

    /* Result of an IP list fetch. */
    public static class IpListResult {
        public final Set<String> ips;
        public final String rev;

        IpListResult(Set<String> ips, String rev) {
            this.ips = ips;
            this.rev = rev;
        }
    }
}