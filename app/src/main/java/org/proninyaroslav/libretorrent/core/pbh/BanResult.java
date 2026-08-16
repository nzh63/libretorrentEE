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

/*
 * Outcome of running a detection module against a single peer.
 */
public class BanResult {
    public enum Action { PASS, BAN, BAN_FOR_DISCONNECT }

    public final Action action;
    public final String module;
    public final String peerIp;
    /* Null for PASS */
    public final String reason;
    /* Id of the torrent the peer belonged to when the check ran; set by the
     * engine so callers can attribute the ban without re-searching snapshots */
    public final String torrentId;

    private BanResult(Action action, String module, String peerIp, String reason) {
        this(action, module, peerIp, reason, null);
    }

    private BanResult(Action action, String module, String peerIp, String reason, String torrentId) {
        this.action = action;
        this.module = module;
        this.peerIp = peerIp;
        this.reason = reason;
        this.torrentId = torrentId;
    }

    public static BanResult pass(String module, String peerIp) {
        return new BanResult(Action.PASS, module, peerIp, null);
    }

    public static BanResult ban(String module, String peerIp, String reason) {
        return new BanResult(Action.BAN, module, peerIp, reason);
    }

    public static BanResult banForDisconnect(String module, String peerIp, String reason) {
        return new BanResult(Action.BAN_FOR_DISCONNECT, module, peerIp, reason);
    }

    /* Returns a copy of this result tagged with the torrent it belongs to. */
    public BanResult withTorrentId(String torrentId) {
        return new BanResult(action, module, peerIp, reason, torrentId);
    }

    public boolean shouldBan() {
        return action == Action.BAN || action == Action.BAN_FOR_DISCONNECT;
    }

    @Override
    public String toString() {
        return "BanResult{action=" + action +
                ", module='" + module + '\'' +
                ", peerIp='" + peerIp + '\'' +
                (torrentId == null ? "" : ", torrentId='" + torrentId + '\'') +
                (reason == null ? "" : ", reason='" + reason + '\'') + '}';
    }
}