/*
 * Copyright (C) 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import org.libtorrent4j.PeerInfo;
import org.libtorrent4j.PieceIndexBitfield;
import org.libtorrent4j.swig.byte_vector;
import org.libtorrent4j.swig.peer_info;

import java.nio.charset.StandardCharsets;

/*
 * Extension of org.libtorrent4j.PeerInfo class with additional information
 */

public class AdvancedPeerInfo extends PeerInfo
{
    protected int port;
    protected PieceIndexBitfield pieces;
    protected boolean isUtp;
    /*
     * The raw 20-byte peer id decoded as ISO-8859-1 (one char per byte) -
     * the same representation PeerBanHelper matches its peer-id rules
     * against. The base PeerInfo class does not expose the pid at all.
     */
    protected String peerId;

    public AdvancedPeerInfo(peer_info p)
    {
        super(p);

        port = p.remote_endpoint().port();
        pieces = new PieceIndexBitfield(p.get_pieces());
        isUtp = p.getFlags().and_(peer_info.utp_socket).non_zero();
        peerId = decodePid(p.getPid().to_bytes());
    }

    private static String decodePid(byte_vector pid)
    {
        if (pid == null)
            return "";
        byte[] bytes = new byte[pid.size()];
        for (int i = 0; i < bytes.length; i++)
            bytes[i] = pid.get(i);
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    public int port()
    {
        return port;
    }

    public String peerId()
    {
        return peerId;
    }

    /*
     * A bitfield, with one bit per piece in the torrent. Each bit tells you
     * if the peer has that piece (if it's set to 1) or if the peer miss that
     * piece (set to 0).
     */

    public PieceIndexBitfield pieces()
    {
        return pieces;
    }

    public boolean isUtp()
    {
        return isUtp;
    }
}
