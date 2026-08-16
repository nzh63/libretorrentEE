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

import androidx.annotation.NonNull;

import java.util.Objects;

/*
 * Read-only view of a single connected peer, decoupled from libtorrent so the
 * detection modules are pure and unit-testable.
 */
public class PeerSnapshot {
    @NonNull
    public final String ip;
    public final int port;
    /* Reported client name / user agent, may be empty */
    public final String client;
    /* Total bytes uploaded to this peer over the whole connection */
    public final long totalUpload;
    /* Total bytes downloaded from this peer over the whole connection */
    public final long totalDownload;
    /* Peer's reported progress in parts per million (0..1_000_000) */
    public final int progressPpm;
    /* Current upload speed in bytes/s */
    public final int upSpeed;

    public PeerSnapshot(@NonNull String ip,
                        int port,
                        String client,
                        long totalUpload,
                        long totalDownload,
                        int progressPpm,
                        int upSpeed) {
        this.ip = Objects.requireNonNull(ip);
        this.port = port;
        this.client = client == null ? "" : client;
        this.totalUpload = totalUpload;
        this.totalDownload = totalDownload;
        this.progressPpm = progressPpm;
        this.upSpeed = upSpeed;
    }

    /* Peer's reported progress as a fraction in [0, 1] */
    public double progress() {
        if (progressPpm <= 0)
            return 0.0d;
        return Math.min(1.0d, progressPpm / 1_000_000.0d);
    }

    /* Whether we are currently uploading (or ever uploaded) to this peer */
    public boolean isUploadingToPeer() {
        return upSpeed > 0 || totalUpload > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PeerSnapshot that)) return false;
        return port == that.port
                && totalUpload == that.totalUpload
                && totalDownload == that.totalDownload
                && progressPpm == that.progressPpm
                && upSpeed == that.upSpeed
                && ip.equals(that.ip)
                && client.equals(that.client);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, port, client, totalUpload, totalDownload, progressPpm, upSpeed);
    }

    @Override
    public String toString() {
        return "PeerSnapshot{ip='" + ip + '\'' +
                ", port=" + port +
                ", client='" + client + '\'' +
                ", totalUpload=" + totalUpload +
                ", totalDownload=" + totalDownload +
                ", progressPpm=" + progressPpm +
                ", upSpeed=" + upSpeed + '}';
    }
}