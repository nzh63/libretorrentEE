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

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/*
 * Persists the fetched BTN rules and their content versions locally, so that
 * the app can resume incremental refreshes (rev query param) after a restart.
 * Per BTN-Spec: "客户端每次获取 BTN 响应后，都应该持久化缓存本地".
 */
public class BtnRuleStore {
    private static final String PREFS = "btn_rule_store";
    private static final String KEY_DENYLIST = "denylist";
    private static final String KEY_ALLOWLIST = "allowlist";
    private static final String KEY_CLIENT_NAMES = "client_names";
    private static final String KEY_DENYLIST_REV = "denylist_rev";
    private static final String KEY_ALLOWLIST_REV = "allowlist_rev";
    private static final String KEY_PEER_IDENTITY_REV = "peer_identity_rev";

    private final SharedPreferences prefs;

    public BtnRuleStore(@NonNull Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @NonNull
    public BtnRuleSet load() {
        return new BtnRuleSet(
                new HashSet<>(prefs.getStringSet(KEY_DENYLIST, new HashSet<>())),
                new HashSet<>(prefs.getStringSet(KEY_ALLOWLIST, new HashSet<>())),
                new HashSet<>(prefs.getStringSet(KEY_CLIENT_NAMES, new HashSet<>())),
                prefs.getString(KEY_DENYLIST_REV, ""),
                prefs.getString(KEY_ALLOWLIST_REV, ""),
                prefs.getString(KEY_PEER_IDENTITY_REV, ""));
    }

    public void save(@NonNull BtnRuleSet rules) {
        prefs.edit()
                .putStringSet(KEY_DENYLIST, new HashSet<>(rules.ipDenylist))
                .putStringSet(KEY_ALLOWLIST, new HashSet<>(rules.ipAllowlist))
                .putStringSet(KEY_CLIENT_NAMES, new HashSet<>(rules.clientNamePatterns))
                .putString(KEY_DENYLIST_REV, rules.denylistRev)
                .putString(KEY_ALLOWLIST_REV, rules.allowlistRev)
                .putString(KEY_PEER_IDENTITY_REV, rules.peerIdentityRev)
                .apply();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}