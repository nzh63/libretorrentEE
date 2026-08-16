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

/*
 * PBH "AntiVampire" equivalent: bans a peer that we have uploaded a large
 * amount of data to, but which still reports (nearly) zero progress. Such a
 * peer is a typical leech / "vampire" that never returns pieces.
 */
public final class AntiVampireModule implements BanModule {
    @NonNull
    @Override
    public String name() {
        return "AntiVampire";
    }

    @NonNull
    @Override
    public BanResult check(@NonNull TorrentSnapshot torrent,
                           @NonNull PeerSnapshot peer,
                           @NonNull PbhSettings settings) {
        if (!settings.antiVampireEnabled)
            return BanResult.pass(name(), peer.ip);

        if (peer.totalUpload > settings.antiVampireUploadThreshold &&
                peer.progressPpm < settings.antiVampireMinProgressPpm) {
            return BanResult.ban(name(), peer.ip,
                    "uploaded " + peer.totalUpload + " bytes but reported progress only "
                            + peer.progressPpm + " ppm (vampire)");
        }

        return BanResult.pass(name(), peer.ip);
    }
}