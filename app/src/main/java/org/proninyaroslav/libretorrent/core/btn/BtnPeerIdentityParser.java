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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.proninyaroslav.libretorrent.core.pbh.IpUtils;

import java.util.HashSet;
import java.util.Locale;
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
 *  - client-name patterns (STARTS_WITH / CONTAINS rules) -> ban by client name;
 *  - IP CIDR blocks -> ban by IP.
 * peer_id rules are ignored because libtorrent4j does not expose peer_id.
 */
public final class BtnPeerIdentityParser {
    private BtnPeerIdentityParser() {
    }

    /*
     * Parses the JSON body and returns a BtnRuleSet holding the extracted IP
     * denylist and client-name patterns. The returned rev is taken from the
     * top-level "version" field.
     */
    @NonNull
    public static BtnRuleSet parse(@NonNull String body) {
        Set<String> ips = new HashSet<>();
        Set<String> clientNames = new HashSet<>();
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
        if (obj.has("client_name") && obj.get("client_name").isJsonObject()) {
            collectClientNameRules(obj.getAsJsonObject("client_name"), clientNames);
        }

        return new BtnRuleSet(ips, new HashSet<>(), clientNames, "", "", rev);
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

    private static void collectClientNameRules(JsonObject cnObj, Set<String> out) {
        for (Map.Entry<String, JsonElement> e : cnObj.entrySet()) {
            JsonElement value = e.getValue();
            if (!value.isJsonArray())
                continue;
            for (JsonElement item : value.getAsJsonArray()) {
                if (!item.isJsonPrimitive())
                    continue;
                String ruleJson = item.getAsString();
                String content = extractRuleContent(ruleJson);
                if (content != null && !content.isEmpty())
                    out.add(content.toLowerCase(Locale.ROOT));
            }
        }
    }

    /*
     * Rules are embedded as JSON strings like
     * "{\"method\":\"STARTS_WITH\",\"content\":\"xm/torrent\"}".
     * Extract the "content" value, or return null if unparseable.
     */
    @org.jetbrains.annotations.Nullable
    private static String extractRuleContent(String ruleJson) {
        try {
            JsonElement rule = JsonParser.parseString(ruleJson);
            if (rule.isJsonObject() && rule.getAsJsonObject().has("content")
                    && rule.getAsJsonObject().get("content").isJsonPrimitive()) {
                return rule.getAsJsonObject().get("content").getAsString();
            }
        } catch (Exception ignored) {
        }
        // Fallback: search for a "content" field with a simple regex.
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