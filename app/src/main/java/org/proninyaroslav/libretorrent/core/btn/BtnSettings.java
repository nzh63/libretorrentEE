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

/*
 * BTN connection and data-sharing settings. All data flows are opt-in: the
 * user must explicitly enable each capability before it runs.
 */
public class BtnSettings {
    /* Master switch */
    public final boolean enabled;
    /* BTN config URL (the "让服务器配置你" endpoint) */
    public final String configUrl;
    public final String appId;
    public final String appSecret;
    /* Random, persisted installation id used for anonymous auth */
    public final String installationId;

    /* Whether user consented to submit bans / swarm / history data to the server */
    public final boolean submitBansEnabled;
    public final boolean submitSwarmEnabled;
    public final boolean submitHistoryEnabled;

    private BtnSettings(Builder b) {
        this.enabled = b.enabled;
        this.configUrl = b.configUrl;
        this.appId = b.appId;
        this.appSecret = b.appSecret;
        this.installationId = b.installationId;
        this.submitBansEnabled = b.submitBansEnabled;
        this.submitSwarmEnabled = b.submitSwarmEnabled;
        this.submitHistoryEnabled = b.submitHistoryEnabled;
    }

    public boolean complete() {
        return enabled && configUrl != null && !configUrl.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        /* Default BTN config endpoint (Sparkle public instance) */
        public static final String DEFAULT_CONFIG_URL = "https://sparkle.pbh-btn.com/ping/config";

        private boolean enabled = false;
        private String configUrl = DEFAULT_CONFIG_URL;
        private String appId = "";
        private String appSecret = "";
        private String installationId = "";
        private boolean submitBansEnabled = false;
        private boolean submitSwarmEnabled = false;
        private boolean submitHistoryEnabled = false;

        public Builder enabled(boolean v) { this.enabled = v; return this; }
        public Builder configUrl(String v) { this.configUrl = v; return this; }
        public Builder appId(String v) { this.appId = v; return this; }
        public Builder appSecret(String v) { this.appSecret = v; return this; }
        public Builder installationId(String v) { this.installationId = v; return this; }
        public Builder submitBansEnabled(boolean v) { this.submitBansEnabled = v; return this; }
        public Builder submitSwarmEnabled(boolean v) { this.submitSwarmEnabled = v; return this; }
        public Builder submitHistoryEnabled(boolean v) { this.submitHistoryEnabled = v; return this; }

        public BtnSettings build() {
            return new BtnSettings(this);
        }
    }
}