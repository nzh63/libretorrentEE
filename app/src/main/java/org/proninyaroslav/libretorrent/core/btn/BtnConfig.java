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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/*
 * Parsed result of the BTN "让服务器配置你" (config) response. Extracts the
 * endpoints and intervals for the abilities we support: ip_denylist,
 * ip_allowlist, rule_peer_identity, submit_bans, submit_swarm, heartbeat,
 * submit_histories (legacy peer-history reporting) and ip_query.
 */
public class BtnConfig {
    public final int minProtocolVersion;
    public final int maxProtocolVersion;

    /* Ability endpoints (URL), or null if the server does not offer them */
    @Nullable public final String ipDenylistEndpoint;
    @Nullable public final String ipAllowlistEndpoint;
    @Nullable public final String peerIdentityEndpoint;
    @Nullable public final String submitBansEndpoint;
    @Nullable public final String submitSwarmEndpoint;
    @Nullable public final String heartbeatEndpoint;
    @Nullable public final String submitHistoryEndpoint;
    @Nullable public final String ipQueryEndpoint;
    /* Optional iframe widget for browsing an IP's BTN record in a browser */
    @Nullable public final String ipQueryIframeEndpoint;
    /*
     * Proof-of-work captcha endpoint (root-level "proof_of_work_captcha"
     * block), or null when the server does not challenge this client. Only
     * abilities whose config sets "pow_captcha": true require a solution.
     */
    @Nullable public final String powCaptchaEndpoint;
    public final boolean ipDenylistPow;
    public final boolean ipAllowlistPow;
    public final boolean peerIdentityPow;
    public final boolean submitBansPow;
    public final boolean submitSwarmPow;
    public final boolean heartbeatPow;
    public final boolean submitHistoryPow;
    public final boolean ipQueryPow;

    /* Refresh intervals in ms (defaults per spec) */
    public final long ipDenylistInterval;
    public final long ipAllowlistInterval;
    public final long peerIdentityInterval;
    public final long heartbeatInterval;
    public final long submitHistoryInterval;

    public static final long DEFAULT_IP_LIST_INTERVAL = 600_000L; /* 10 min */
    public static final long DEFAULT_PEER_IDENTITY_INTERVAL = 2_700_000L; /* 45 min */
    public static final long DEFAULT_HEARTBEAT_INTERVAL = 1_800_000L; /* 30 min */
    public static final long DEFAULT_SUBMIT_HISTORY_INTERVAL = 1_800_000L; /* 30 min */

    private BtnConfig(int minProtocolVersion, int maxProtocolVersion,
                      @Nullable String ipDenylistEndpoint,
                      @Nullable String ipAllowlistEndpoint,
                      @Nullable String peerIdentityEndpoint,
                      @Nullable String submitBansEndpoint,
                      @Nullable String submitSwarmEndpoint,
                      @Nullable String heartbeatEndpoint,
                      @Nullable String submitHistoryEndpoint,
                      @Nullable String ipQueryEndpoint,
                      @Nullable String ipQueryIframeEndpoint,
                      @Nullable String powCaptchaEndpoint,
                      boolean ipDenylistPow,
                      boolean ipAllowlistPow,
                      boolean peerIdentityPow,
                      boolean submitBansPow,
                      boolean submitSwarmPow,
                      boolean heartbeatPow,
                      boolean submitHistoryPow,
                      boolean ipQueryPow,
                      long ipDenylistInterval,
                      long ipAllowlistInterval,
                      long peerIdentityInterval,
                      long heartbeatInterval,
                      long submitHistoryInterval) {
        this.minProtocolVersion = minProtocolVersion;
        this.maxProtocolVersion = maxProtocolVersion;
        this.ipDenylistEndpoint = ipDenylistEndpoint;
        this.ipAllowlistEndpoint = ipAllowlistEndpoint;
        this.peerIdentityEndpoint = peerIdentityEndpoint;
        this.submitBansEndpoint = submitBansEndpoint;
        this.submitSwarmEndpoint = submitSwarmEndpoint;
        this.heartbeatEndpoint = heartbeatEndpoint;
        this.submitHistoryEndpoint = submitHistoryEndpoint;
        this.ipQueryEndpoint = ipQueryEndpoint;
        this.ipQueryIframeEndpoint = ipQueryIframeEndpoint;
        this.powCaptchaEndpoint = powCaptchaEndpoint;
        this.ipDenylistPow = ipDenylistPow;
        this.ipAllowlistPow = ipAllowlistPow;
        this.peerIdentityPow = peerIdentityPow;
        this.submitBansPow = submitBansPow;
        this.submitSwarmPow = submitSwarmPow;
        this.heartbeatPow = heartbeatPow;
        this.submitHistoryPow = submitHistoryPow;
        this.ipQueryPow = ipQueryPow;
        this.ipDenylistInterval = ipDenylistInterval;
        this.ipAllowlistInterval = ipAllowlistInterval;
        this.peerIdentityInterval = peerIdentityInterval;
        this.heartbeatInterval = heartbeatInterval;
        this.submitHistoryInterval = submitHistoryInterval;
    }

    public static final BtnConfig INVALID = new BtnConfig(0, 0,
            null, null, null, null, null, null, null, null, null,
            null,
            false, false, false, false, false, false, false, false,
            DEFAULT_IP_LIST_INTERVAL, DEFAULT_IP_LIST_INTERVAL,
            DEFAULT_PEER_IDENTITY_INTERVAL,
            DEFAULT_HEARTBEAT_INTERVAL, DEFAULT_SUBMIT_HISTORY_INTERVAL);

    /*
     * Parses the config JSON body. Returns INVALID if unparseable or if the
     * protocol version is unsatisfiable.
     */
    @NonNull
    public static BtnConfig parse(@NonNull String body, int clientProtocolVersion) {
        JsonElement root;
        try {
            root = JsonParser.parseString(body);
        } catch (Exception e) {
            return INVALID;
        }
        if (!root.isJsonObject())
            return INVALID;

        JsonObject obj = root.getAsJsonObject();
        int minV = getInt(obj, "min_protocol_version", 0);
        int maxV = getInt(obj, "max_protocol_version", 0);
        if (clientProtocolVersion < minV || clientProtocolVersion > maxV)
            return INVALID;

        JsonObject ability = obj.has("ability") && obj.get("ability").isJsonObject()
                ? obj.getAsJsonObject("ability") : new JsonObject();

        String denylist = getEndpoint(ability, "ip_denylist");
        String allowlist = getEndpoint(ability, "ip_allowlist");
        String peerIdentity = getEndpoint(ability, "rule_peer_identity");
        String submitBans = getEndpoint(ability, "submit_bans");
        String submitSwarm = getEndpoint(ability, "submit_swarm");
        /*
         * Per BTN-Spec/PeerBanHelper, these three abilities may be offered by
         * servers of any protocol generation and are keyed "heartbeat",
         * "submit_histories" (legacy naming) and "ip_query".
         */
        String heartbeat = getEndpoint(ability, "heartbeat");
        String submitHistory = getEndpoint(ability, "submit_histories");
        String ipQuery = getEndpoint(ability, "ip_query");
        String ipQueryIframe = getOptionalString(ability, "ip_query", "iframe_endpoint");

        String powEndpoint = null;
        if (obj.has("proof_of_work_captcha") && obj.get("proof_of_work_captcha").isJsonObject()) {
            JsonObject pow = obj.getAsJsonObject("proof_of_work_captcha");
            if (pow.has("endpoint") && pow.get("endpoint").isJsonPrimitive())
                powEndpoint = pow.get("endpoint").getAsString();
        }
        boolean denylistPow = getBool(ability, "ip_denylist", "pow_captcha");
        boolean allowlistPow = getBool(ability, "ip_allowlist", "pow_captcha");
        boolean peerIdentityPow = getBool(ability, "rule_peer_identity", "pow_captcha");
        boolean submitBansPow = getBool(ability, "submit_bans", "pow_captcha");
        boolean submitSwarmPow = getBool(ability, "submit_swarm", "pow_captcha");
        boolean heartbeatPow = getBool(ability, "heartbeat", "pow_captcha");
        boolean submitHistoryPow = getBool(ability, "submit_histories", "pow_captcha");
        boolean ipQueryPow = getBool(ability, "ip_query", "pow_captcha");

        long denylistInterval = getInterval(ability, "ip_denylist", DEFAULT_IP_LIST_INTERVAL);
        long allowlistInterval = getInterval(ability, "ip_allowlist", DEFAULT_IP_LIST_INTERVAL);
        long peerIdentityInterval = getInterval(ability, "rule_peer_identity", DEFAULT_PEER_IDENTITY_INTERVAL);
        long heartbeatInterval = getInterval(ability, "heartbeat", DEFAULT_HEARTBEAT_INTERVAL);
        long submitHistoryInterval = getInterval(ability, "submit_histories", DEFAULT_SUBMIT_HISTORY_INTERVAL);

        return new BtnConfig(minV, maxV,
                denylist, allowlist, peerIdentity, submitBans, submitSwarm,
                heartbeat, submitHistory, ipQuery, ipQueryIframe,
                powEndpoint,
                denylistPow, allowlistPow, peerIdentityPow, submitBansPow,
                submitSwarmPow, heartbeatPow, submitHistoryPow, ipQueryPow,
                denylistInterval, allowlistInterval, peerIdentityInterval,
                heartbeatInterval, submitHistoryInterval);
    }

    private static int getInt(JsonObject obj, String key, int def) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive())
            return obj.get(key).getAsInt();
        return def;
    }

    private static String getEndpoint(JsonObject ability, String key) {
        if (ability.has(key) && ability.get(key).isJsonObject()) {
            JsonObject mod = ability.getAsJsonObject(key);
            if (mod.has("endpoint") && mod.get("endpoint").isJsonPrimitive())
                return mod.get("endpoint").getAsString();
        }
        return null;
    }

    /* Reads a boolean field from an ability block. */
    private static boolean getBool(JsonObject ability, String key, String field) {
        if (ability.has(key) && ability.get(key).isJsonObject()) {
            JsonObject mod = ability.getAsJsonObject(key);
            if (mod.has(field) && mod.get(field).isJsonPrimitive()) {
                try {
                    return mod.get(field).getAsBoolean();
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    /* Reads a non-endpoint string field from an ability block, or null. */
    @Nullable
    private static String getOptionalString(JsonObject ability, String key, String field) {
        if (ability.has(key) && ability.get(key).isJsonObject()) {
            JsonObject mod = ability.getAsJsonObject(key);
            if (mod.has(field) && mod.get(field).isJsonPrimitive())
                return mod.get(field).getAsString();
        }
        return null;
    }

    private static long getInterval(JsonObject ability, String key, long def) {
        if (ability.has(key) && ability.get(key).isJsonObject()) {
            JsonObject mod = ability.getAsJsonObject(key);
            if (mod.has("interval") && mod.get("interval").isJsonPrimitive())
                return mod.get("interval").getAsLong();
        }
        return def;
    }
}