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

package org.proninyaroslav.libretorrent.core.pbh;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/*
 * Regression tests for the fast-PCB disconnect-probe registry: probes are
 * short-lived blocks that expire on their own and must never be treated as
 * permanent bans.
 */
public class DisconnectBansTest {

    @Test
    public void active_beforeExpiry() {
        DisconnectBans bans = new DisconnectBans();
        long now = 1_000_000L;
        bans.add("1.2.3.4", 15_000L, now);

        assertFalse(bans.hasExpired(now + 14_999L));
        assertEquals(Set.of("1.2.3.4"), bans.active(now + 14_999L));
        // Expiry boundary: at exactly expiry time the ban counts as expired
        assertTrue(bans.hasExpired(now + 15_000L));
        assertTrue(bans.active(now + 15_000L).isEmpty());
    }

    @Test
    public void removeExpired_returnsExpiredAndDropsThem() {
        DisconnectBans bans = new DisconnectBans();
        long now = 1_000_000L;
        bans.add("1.2.3.4", 10_000L, now);
        bans.add("5.6.7.8", 60_000L, now);

        Set<String> expired = bans.removeExpired(now + 20_000L);
        assertEquals(Set.of("1.2.3.4"), expired);
        assertEquals(Set.of("5.6.7.8"), bans.active(now + 20_000L));
        // Second sweep: nothing left to expire
        assertTrue(bans.removeExpired(now + 20_000L).isEmpty());
    }

    @Test
    public void reAdd_extendsToLongestDuration() {
        DisconnectBans bans = new DisconnectBans();
        long now = 1_000_000L;
        bans.add("1.2.3.4", 10_000L, now);
        bans.add("1.2.3.4", 60_000L, now);

        assertEquals(Set.of("1.2.3.4"), bans.active(now + 50_000L));
    }

    @Test
    public void zeroDuration_expiresImmediately() {
        DisconnectBans bans = new DisconnectBans();
        bans.add("1.2.3.4", 0, 1_000_000L);
        assertTrue(bans.active(1_000_000L).isEmpty());
    }
}
