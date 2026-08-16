/*
 * Copyright (C) 2019-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.core.model.session;

import androidx.annotation.NonNull;

import org.libtorrent4j.alerts.Alert;
import org.libtorrent4j.alerts.DhtLogAlert;
import org.libtorrent4j.alerts.LogAlert;
import org.libtorrent4j.alerts.PeerLogAlert;
import org.libtorrent4j.alerts.PortmapLogAlert;
import org.libtorrent4j.alerts.TorrentLogAlert;
import org.proninyaroslav.libretorrent.core.logger.LogEntry;
import org.proninyaroslav.libretorrent.core.logger.LogFilter;
import org.proninyaroslav.libretorrent.core.logger.Logger;

public class SessionLogger extends Logger {
    private static final java.util.concurrent.atomic.AtomicInteger nextLogEntryId =
            new java.util.concurrent.atomic.AtomicInteger(0);

    public enum SessionLogEntryType {
        /*
         * Posts some session events
         */
        SESSION_LOG,

        /*
         * Posts PeerBanHelper anti-leech events
         */
        PBH_LOG,

        /*
         * Posts DHT events
         */
        DHT_LOG,

        /*
         * Posts events specific to a peer
         */
        PEER_LOG,

        /*
         * Posts informational events related to either
         * UPnP or NAT-PMP
         */
        PORTMAP_LOG,

        /*
         * Posts torrent events
         */
        TORRENT_LOG,
    }

    public enum SessionLogFilter {
        SESSION((entry) -> entry == null || entry.getTag().equals(SessionLogEntryType.SESSION_LOG.name())),

        PBH((entry) -> entry == null || entry.getTag().equals(SessionLogEntryType.PBH_LOG.name())),

        DHT((entry) -> entry == null || entry.getTag().equals(SessionLogEntryType.DHT_LOG.name())),

        PEER((entry) -> entry == null || entry.getTag().equals(SessionLogEntryType.PEER_LOG.name())),

        PORTMAP((entry) -> entry == null || entry.getTag().equals(SessionLogEntryType.PORTMAP_LOG.name())),

        TORRENT((entry) -> entry == null || entry.getTag().equals(SessionLogEntryType.TORRENT_LOG.name()));

        private final NewFilter filter;

        SessionLogFilter(LogFilter filter) {
            this.filter = new NewFilter(name(), filter);
        }

        public NewFilter filter() {
            return filter;
        }
    }

    public static class SessionFilterParams {
        public final boolean filterSessionLog;
        public final boolean filterPbhLog;
        public final boolean filterDhtLog;
        public final boolean filterPeerLog;
        public final boolean filterPortmapLog;
        public final boolean filterTorrentLog;

        public SessionFilterParams(boolean filterSessionLog,
                                   boolean filterPbhLog,
                                   boolean filterDhtLog,
                                   boolean filterPeerLog,
                                   boolean filterPortmapLog,
                                   boolean filterTorrentLog) {
            this.filterSessionLog = filterSessionLog;
            this.filterPbhLog = filterPbhLog;
            this.filterDhtLog = filterDhtLog;
            this.filterPeerLog = filterPeerLog;
            this.filterPortmapLog = filterPortmapLog;
            this.filterTorrentLog = filterTorrentLog;
        }
    }

    SessionLogger() {
        /* Default stub */
        super(1);
    }

    void send(Alert<?> alert) {
        long time = System.currentTimeMillis();
        String msg;
        LogEntry entry = null;

        switch (alert.type()) {
            case LOG -> entry = new LogEntry(nextLogEntryId.getAndIncrement(),
                    SessionLogEntryType.SESSION_LOG.name(),
                    ((LogAlert) alert).logMessage(),
                    time);
            case DHT_LOG -> {
                DhtLogAlert dhtLogAlert = (DhtLogAlert) alert;
                msg = "[" + dhtLogAlert.module().name() + "] " + dhtLogAlert.logMessage();
                entry = new LogEntry(nextLogEntryId.getAndIncrement(),
                        SessionLogEntryType.DHT_LOG.name(),
                        msg,
                        time);
            }
            case PEER_LOG -> {
                PeerLogAlert peerLogAlert = (PeerLogAlert) alert;

                msg = "[" + peerLogAlert.direction() + "] " +
                        "[" + peerLogAlert.eventType() + "] " +
                        peerLogAlert.logMessage();

                entry = new LogEntry(nextLogEntryId.getAndIncrement(),
                        SessionLogEntryType.PEER_LOG.name(),
                        msg,
                        time);
            }
            case PORTMAP_LOG -> {
                PortmapLogAlert portmapLogAlert = (PortmapLogAlert) alert;
                msg = "[" + portmapLogAlert.mapType().name() + "] " + portmapLogAlert.logMessage();
                entry = new LogEntry(nextLogEntryId.getAndIncrement(),
                        SessionLogEntryType.PORTMAP_LOG.name(),
                        msg,
                        time);
            }
            case TORRENT_LOG -> entry = new LogEntry(nextLogEntryId.getAndIncrement(),
                    SessionLogEntryType.TORRENT_LOG.name(),
                    ((TorrentLogAlert) alert).logMessage(),
                    time);
        }

        if (entry != null) {
            send(entry);
        }
    }

    /*
     * Posts a PeerBanHelper anti-leech entry into the session log with the
     * PBH_LOG tag, so it shows up in the log page under its own filter.
     */
    public void logPbh(@NonNull String msg) {
        send(new LogEntry(nextLogEntryId.getAndIncrement(),
                SessionLogEntryType.PBH_LOG.name(),
                msg,
                System.currentTimeMillis()));
    }

    public void applyFilterParams(SessionFilterParams params) {
        Logger.NewFilter[] addFilters = new Logger.NewFilter[6];
        String[] removeFilters = new String[6];

        if (params.filterSessionLog) {
            addFilters[0] = SessionLogFilter.SESSION.filter();
        } else {
            removeFilters[0] = SessionLogFilter.SESSION.name();
        }

        if (params.filterPbhLog) {
            addFilters[1] = SessionLogFilter.PBH.filter();
        } else {
            removeFilters[1] = SessionLogFilter.PBH.name();
        }

        if (params.filterDhtLog) {
            addFilters[2] = SessionLogFilter.DHT.filter();
        } else {
            removeFilters[2] = SessionLogFilter.DHT.name();
        }

        if (params.filterPeerLog) {
            addFilters[3] = SessionLogFilter.PEER.filter();
        } else {
            removeFilters[3] = SessionLogFilter.PEER.name();
        }

        if (params.filterPortmapLog) {
            addFilters[4] = SessionLogFilter.PORTMAP.filter();
        } else {
            removeFilters[4] = SessionLogFilter.PORTMAP.name();
        }

        if (params.filterTorrentLog) {
            addFilters[5] = SessionLogFilter.TORRENT.filter();
        } else {
            removeFilters[5] = SessionLogFilter.TORRENT.name();
        }

        removeFilter(removeFilters);
        addFilter(addFilters);
    }
}
