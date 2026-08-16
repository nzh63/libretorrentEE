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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BtnConfigTest {

    private final String valid =
            """
                    {
                      "min_protocol_version": 20,
                      "max_protocol_version": 20,
                      "ability": {
                        "ip_denylist": { "endpoint": "https://btn/ruleIpDenylist", "interval": 600000 },
                        "ip_allowlist": { "endpoint": "https://btn/ruleIpAllowlist", "interval": 600000 },
                        "rule_peer_identity": { "endpoint": "https://btn/rulePeerIdentity", "interval": 2700000 },
                        "submit_bans": { "endpoint": "https://btn/syncBanHistory" },
                        "submit_swarm": { "endpoint": "https://btn/syncSwarm" }
                      }
                    }
                    """;

    @Test
    public void parsesExtraAbilities() {
        String json =
                """
                {
                  "min_protocol_version": 20,
                  "max_protocol_version": 20,
                  "ability": {
                    "heartbeat": { "endpoint": "https://btn/heartbeat", "interval": 1800000,
                                   "multi_if": true, "random_initial_delay": 3000 },
                    "submit_histories": { "endpoint": "https://btn/submitHistory", "interval": 900000 },
                    "ip_query": { "endpoint": "https://btn/queryIp", "pow_captcha": false,
                                  "iframe_endpoint": "https://btn/queryIp/widget" }
                  }
                }
                """;
        BtnConfig cfg = BtnConfig.parse(json, 20);
        assertEquals("https://btn/heartbeat", cfg.heartbeatEndpoint);
        assertEquals(1800000, cfg.heartbeatInterval);
        assertEquals("https://btn/submitHistory", cfg.submitHistoryEndpoint);
        assertEquals(900000, cfg.submitHistoryInterval);
        assertEquals("https://btn/queryIp", cfg.ipQueryEndpoint);
        assertEquals("https://btn/queryIp/widget", cfg.ipQueryIframeEndpoint);
    }

    @Test
    public void extraAbilitiesDefaultInterval_whenMissing() {
        String json =
                """
                {
                  "min_protocol_version": 20,
                  "max_protocol_version": 20,
                  "ability": {
                    "heartbeat": { "endpoint": "https://btn/heartbeat" },
                    "submit_histories": { "endpoint": "https://btn/submitHistory" }
                  }
                }
                """;
        BtnConfig cfg = BtnConfig.parse(json, 20);
        assertEquals(BtnConfig.DEFAULT_HEARTBEAT_INTERVAL, cfg.heartbeatInterval);
        assertEquals(BtnConfig.DEFAULT_SUBMIT_HISTORY_INTERVAL, cfg.submitHistoryInterval);
    }

    @Test
    public void extraAbilitiesAbsent_nullEndpoints() {
        assertNull(BtnConfig.parse(valid, 20).heartbeatEndpoint);
        assertNull(BtnConfig.parse(valid, 20).submitHistoryEndpoint);
        assertNull(BtnConfig.parse(valid, 20).ipQueryEndpoint);
        assertNull(BtnConfig.parse(valid, 20).ipQueryIframeEndpoint);
    }

    @Test
    public void parsesEndpoints() {
        BtnConfig cfg = BtnConfig.parse(valid, 20);
        assertTrue(cfg != BtnConfig.INVALID);
        assertEquals("https://btn/ruleIpDenylist", cfg.ipDenylistEndpoint);
        assertEquals("https://btn/ruleIpAllowlist", cfg.ipAllowlistEndpoint);
        assertEquals("https://btn/rulePeerIdentity", cfg.peerIdentityEndpoint);
        assertEquals("https://btn/syncBanHistory", cfg.submitBansEndpoint);
        assertEquals("https://btn/syncSwarm", cfg.submitSwarmEndpoint);
    }

    @Test
    public void parsesIntervals() {
        BtnConfig cfg = BtnConfig.parse(valid, 20);
        assertEquals(600_000L, cfg.ipDenylistInterval);
        assertEquals(2_700_000L, cfg.peerIdentityInterval);
    }

    @Test
    public void protocolMismatch_invalid() {
        assertTrue(BtnConfig.parse(valid, 999) == BtnConfig.INVALID);
        assertTrue(BtnConfig.parse(valid, 1) == BtnConfig.INVALID);
    }

    @Test
    public void invalidJson_invalid() {
        assertTrue(BtnConfig.parse("garbage", 20) == BtnConfig.INVALID);
    }

    @Test
    public void missingAbilityEndpoints_null() {
        BtnConfig cfg = BtnConfig.parse("{\"min_protocol_version\":20,\"max_protocol_version\":20}", 20);
        assertNull(cfg.ipDenylistEndpoint);
        assertNull(cfg.peerIdentityEndpoint);
    }
}