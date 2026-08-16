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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.proninyaroslav.libretorrent.core.pbh.IpUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/*
 * Parses the BTN rule_peer_identity response (PBH v1 structure):
 *
 *  {
 *    "version": "1981c7af",
 *    "peer_id": { "<rule>": [ "{\"method\":\"STARTS_WITH\",\"content\":\"-xm\"}" ] },
 *    "client_name": { "<rule>": [ "{\"method\":\"STARTS_WITH\",\"content\":\"xm/torrent\"}" ] },
 *    "ip": { "<rule>": [ "42.248.192.0/24", ... ] },
 *    "port": {},
 *    "script": {}
 *  }
 *
 * We extract:
 *  - peer-id and client-name rules with their match method
 *    (STARTS_WITH / ENDS_WITH / CONTAINS / EQUALS / REGEX / LENGTH);
 *  - IP CIDR blocks -> ban by IP.
 */
public final class BtnPeerIdentityParser {
    private BtnPeerIdentityParser() {
    }

    /*
     * Parses the JSON body and returns a BtnRuleSet holding the extracted IP
     * denylist and peer-id/client-name rules. The returned rev is taken from
     * the top-level "version" field.
     */
    @NonNull
    public static BtnRuleSet parse(@NonNull String body) {
        Set<String> ips = new HashSet<>();
        Set<BtnRuleSet.ClientNameRule> clientNames = new HashSet<>();
        Set<BtnRuleSet.ClientNameRule> peerIds = new HashSet<>();
        String rev = "";

        JsonElement root;
        try {
            root = JsonParser.parseString(body);
        } catch (Exception e) {
            return BtnRuleSet.EMPTY;
        }
        if (!root.isJsonObject())
            return BtnRuleSet.EMPTY;

        JsonObject obj = root.getAsJsonObject();
        if (obj.has("version") && obj.get("version").isJsonPrimitive()) {
            rev = obj.get("version").getAsString();
        }

        if (obj.has("ip") && obj.get("ip").isJsonObject()) {
            collectIpRules(obj.getAsJsonObject("ip"), ips);
        }
        if (obj.has("peer_id") && obj.get("peer_id").isJsonObject()) {
            collectMatchRules(obj.getAsJsonObject("peer_id"), peerIds);
        }
        if (obj.has("client_name") && obj.get("client_name").isJsonObject()) {
            collectMatchRules(obj.getAsJsonObject("client_name"), clientNames);
        }

        return new BtnRuleSet(ips, new HashSet<>(),
                new java.util.ArrayList<>(clientNames),
                new java.util.ArrayList<>(peerIds),
                "", "", rev);
    }

    private static void collectIpRules(JsonObject ipObj, Set<String> out) {
        for (Map.Entry<String, JsonElement> e : ipObj.entrySet()) {
            JsonElement value = e.getValue();
            if (value.isJsonArray()) {
                for (JsonElement item : value.getAsJsonArray()) {
                    if (item.isJsonPrimitive()) {
                        String s = item.getAsString().trim();
                        if (IpUtils.matchesCidrSyntax(s))
                            out.add(s);
                    }
                }
            }
        }
    }

    private static void collectMatchRules(JsonObject ruleObj,
                                           Set<BtnRuleSet.ClientNameRule> out) {
        for (Map.Entry<String, JsonElement> e : ruleObj.entrySet()) {
            JsonElement value = e.getValue();
            if (!value.isJsonArray())
                continue;
            for (JsonElement item : value.getAsJsonArray()) {
                if (!item.isJsonPrimitive())
                    continue;
                BtnRuleSet.ClientNameRule rule =
                        parseRule(item.getAsString());
                if (rule != null)
                    out.add(rule);
            }
        }
    }

    /*
     * Rules are embedded as JSON strings like
     * "{\"method\":\"STARTS_WITH\",\"content\":\"xm/torrent\"}". Returns a
     * typed rule (method + content), or null if no content can be extracted.
     */
    @Nullable
    private static BtnRuleSet.ClientNameRule parseRule(String ruleJson) {
        String methodStr = null;
        String content = null;
        try {
            JsonElement rule = JsonParser.parseString(ruleJson);
            if (rule.isJsonObject()) {
                JsonObject o = rule.getAsJsonObject();
                if (o.has("method") && o.get("method").isJsonPrimitive())
                    methodStr = o.get("method").getAsString();
                if (o.has("content") && o.get("content").isJsonPrimitive())
                    content = o.get("content").getAsString();
            }
        } catch (Exception ignored) {
        }
        if (content == null || content.isEmpty()) {
            content = extractContentFallback(ruleJson);
            if (content == null || content.isEmpty())
                return null;
        }

        BtnRuleSet.ClientNameRule.Method method =
                parseMethod(methodStr);
        if (method == null) {
            /* Unknown method: fall back to containment so the rule still
             * contributes, mirroring the pre-method behaviour */
            method = BtnRuleSet.ClientNameRule.Method.CONTAINS;
        }
        return new BtnRuleSet.ClientNameRule(method, content);
    }

    @Nullable
    private static BtnRuleSet.ClientNameRule.Method parseMethod(@Nullable String methodStr) {
        if (methodStr == null)
            return null;
        for (BtnRuleSet.ClientNameRule.Method m : BtnRuleSet.ClientNameRule.Method.values()) {
            if (m.name().equalsIgnoreCase(methodStr.trim()))
                return m;
        }
        return null;
    }

    /*
     * Fallback: search for a "content" field with a simple scan, for rule
     * strings whose JSON is malformed but still carries a content value.
     */
    @Nullable
    private static String extractContentFallback(String ruleJson) {
        int idx = ruleJson.indexOf("\"content\"");
        if (idx < 0)
            return null;
        int colon = ruleJson.indexOf(':', idx);
        if (colon < 0)
            return null;
        int startQuote = ruleJson.indexOf('"', colon + 1);
        if (startQuote < 0)
            return null;
        int endQuote = ruleJson.indexOf('"', startQuote + 1);
        if (endQuote < 0)
            return null;
        return ruleJson.substring(startQuote + 1, endQuote);
    }
}
