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

public class BtnSettingsTest {

    @Test
    public void defaultConfigUrl_isSparklePublicEndpoint() {
        BtnSettings s = BtnSettings.builder().build();
        assertEquals("https://sparkle.pbh-btn.com/ping/config", s.configUrl);
    }

    @Test
    public void builderOverridesConfigUrl() {
        BtnSettings s = BtnSettings.builder().configUrl("https://example.com/config").build();
        assertEquals("https://example.com/config", s.configUrl);
    }

    @Test
    public void complete_requiresEnabledAndConfigUrl() {
        BtnSettings s = BtnSettings.builder().enabled(true).build();
        // enabled but no custom config URL -> still complete (default URL present)
        assertEquals(true, s.complete());
    }
}