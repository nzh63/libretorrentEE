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
import static org.junit.Assert.assertTrue;

public class BtnPeerIdentityParserTest {

    private final String json =
            """
                    {
                      "version": "1981c7af",
                      "peer_id": {
                        "hp/torrent new variant": [ "{\\"method\\":\\"STARTS_WITH\\",\\"content\\":\\"-xm\\"}" ]
                      },
                      "client_name": {
                        "hp/torrent new variant": [ "{\\"method\\":\\"STARTS_WITH\\",\\"content\\":\\"xm/torrent\\"}" ],
                        "gopeed": [ "{\\"method\\":\\"EQUALS\\",\\"content\\":\\"gopeed dev\\"}" ]
                      },
                      "ip": {
                        "multi-dial-2024": [ "42.248.192.0/24", "110.185.22.124/30" ]
                      },
                      "port": {},
                      "script": {}
                    }
                    """;

    @Test
    public void parsesVersion_clientNames_andIps() {
        BtnRuleSet rules = BtnPeerIdentityParser.parse(json);
        assertEquals("1981c7af", rules.peerIdentityRev);
        assertTrue(rules.ipDenylist.contains("42.248.192.0/24"));
        assertTrue(rules.ipDenylist.contains("110.185.22.124/30"));
        assertTrue(rules.clientNamePatterns.contains("xm/torrent"));
        assertTrue(rules.clientNamePatterns.contains("gopeed dev"));
    }

    @Test
    public void invalidJson_returnsEmpty() {
        BtnRuleSet rules = BtnPeerIdentityParser.parse("not json");
        assertTrue(rules.isEmpty());
        assertEquals("", rules.peerIdentityRev);
    }

    @Test
    public void emptyJson_returnsEmpty() {
        assertTrue(BtnPeerIdentityParser.parse("{}").isEmpty());
    }

    @Test
    public void clientNamePatterns_areLowercased() {
        BtnRuleSet rules = BtnPeerIdentityParser.parse(
                "{\"client_name\":{\"r\":[\"{\\\"content\\\":\\\"XmCliEnT\\\"}\"]}}");
        assertTrue(rules.clientNamePatterns.contains("xmclient"));
    }
}