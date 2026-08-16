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
        assertTrue(rules.clientNameRules.contains(new BtnRuleSet.ClientNameRule(
                BtnRuleSet.ClientNameRule.Method.STARTS_WITH, "xm/torrent")));
        assertTrue(rules.clientNameRules.contains(new BtnRuleSet.ClientNameRule(
                BtnRuleSet.ClientNameRule.Method.EQUALS, "gopeed dev")));
    }

    @Test
    public void parsesPeerIdRules() {
        BtnRuleSet rules = BtnPeerIdentityParser.parse(json);
        assertEquals(1, rules.peerIdRules.size());
        BtnRuleSet.ClientNameRule rule = rules.peerIdRules.get(0);
        assertEquals(BtnRuleSet.ClientNameRule.Method.STARTS_WITH, rule.method);
        assertEquals("-xm", rule.content);
        assertTrue(rule.matches("-xm0019abcdefghijk"));
        assertTrue(!rule.matches("-TR0019abcdefghijk"));
    }

    @Test
    public void preservesMatchMethod() {
        BtnRuleSet rules = BtnPeerIdentityParser.parse(json);
        for (BtnRuleSet.ClientNameRule rule : rules.clientNameRules) {
            if (rule.content.equals("xm/torrent"))
                assertEquals(BtnRuleSet.ClientNameRule.Method.STARTS_WITH, rule.method);
            if (rule.content.equals("gopeed dev"))
                assertEquals(BtnRuleSet.ClientNameRule.Method.EQUALS, rule.method);
        }
    }

    @Test
    public void methodApplied_semantics() {
        // STARTS_WITH must not behave like CONTAINS
        BtnRuleSet.ClientNameRule startsWith =
                new BtnRuleSet.ClientNameRule(BtnRuleSet.ClientNameRule.Method.STARTS_WITH, "xm");
        assertTrue(startsWith.matches("xm/torrent 1.0"));
        assertTrue(!startsWith.matches("fake-xm-clone"));

        BtnRuleSet.ClientNameRule endsWith =
                new BtnRuleSet.ClientNameRule(BtnRuleSet.ClientNameRule.Method.ENDS_WITH, "dev");
        assertTrue(endsWith.matches("Gopeed DEV"));

        BtnRuleSet.ClientNameRule equalsRule =
                new BtnRuleSet.ClientNameRule(BtnRuleSet.ClientNameRule.Method.EQUALS, "gopeed dev");
        assertTrue(equalsRule.matches("GoPeed Dev"));
        assertTrue(!equalsRule.matches("gopeed dev 2"));

        BtnRuleSet.ClientNameRule regex =
                new BtnRuleSet.ClientNameRule(BtnRuleSet.ClientNameRule.Method.REGEX, "^xm.*7\\.[0-9]$");
        assertTrue(regex.matches("xm/torrent 7.2"));
        assertTrue(!regex.matches("xm/torrent 8.0"));

        BtnRuleSet.ClientNameRule length =
                new BtnRuleSet.ClientNameRule(BtnRuleSet.ClientNameRule.Method.LENGTH, "8");
        assertTrue(length.matches("12345678"));
        assertTrue(!length.matches("1234567"));

        // Invalid regex / length must never match instead of crashing
        assertTrue(!new BtnRuleSet.ClientNameRule(
                BtnRuleSet.ClientNameRule.Method.REGEX, "([unclosed").matches("anything"));
        assertTrue(!new BtnRuleSet.ClientNameRule(
                BtnRuleSet.ClientNameRule.Method.LENGTH, "not-a-number").matches("anything"));
    }

    @Test
    public void ruleWithoutMethod_fallsBackToContains() {
        BtnRuleSet rules = BtnPeerIdentityParser.parse(
                "{\"client_name\":{\"r\":[\"{\\\"content\\\":\\\"XmCliEnT\\\"}\"]}}");
        assertEquals(1, rules.clientNameRules.size());
        BtnRuleSet.ClientNameRule rule = rules.clientNameRules.get(0);
        assertEquals(BtnRuleSet.ClientNameRule.Method.CONTAINS, rule.method);
        assertTrue(rule.matches("fake XMCLIENT name"));
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
    public void encodedRules_roundTrip() {
        BtnRuleSet.ClientNameRule rule = new BtnRuleSet.ClientNameRule(
                BtnRuleSet.ClientNameRule.Method.STARTS_WITH, "xm/torrent");
        String encoded = BtnRuleSet.encodeRule(rule);
        assertEquals(rule, BtnRuleSet.decodeRule(encoded));
        // Legacy bare entries decode as CONTAINS
        assertEquals(new BtnRuleSet.ClientNameRule(
                        BtnRuleSet.ClientNameRule.Method.CONTAINS, "xl0019"),
                BtnRuleSet.decodeRule("xl0019"));
        // Unknown method prefix decodes the whole string as CONTAINS content
        assertEquals(new BtnRuleSet.ClientNameRule(
                        BtnRuleSet.ClientNameRule.Method.CONTAINS, "FOO|bar"),
                BtnRuleSet.decodeRule("FOO|bar"));
    }
}
