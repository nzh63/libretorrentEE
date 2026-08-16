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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/*
 * Immutable snapshot of all PeerBanHelper-compatible anti-leech options.
 *
 * Defaults are chosen so the feature works out-of-the-box (auto bans malicious
 * peers) while remaining conservative enough not to ban legitimate peers.
 */
public class PbhSettings {
    /* Master switch */
    public final boolean enabled;
    /* How often (seconds) the engine scans connected peers */
    public final long checkIntervalSec;

    /*
     * Ban duration in milliseconds. 0 means permanent ban (IP stays in the
     * session IP filter until manually removed).
     */
    public final long banDurationMs;

    /* ---- AntiVampire (PBH "AntiVampire" module) ---- */
    public final boolean antiVampireEnabled;
    /* Ban a peer that we uploaded at least this many bytes to... */
    public final long antiVampireUploadThreshold;
    /* ...while it still reports a progress (PPM) below this value */
    public final long antiVampireMinProgressPpm;

    /* ---- Client name blacklist (PBH "ClientNameBlacklist" module) ---- */
    public final boolean clientNameBlacklistEnabled;
    /* Substrings (case-insensitive) matched against the reported client name */
    public final Set<String> clientNameBlacklist;

    /* ---- IP / CIDR blacklist (PBH "IPAddressBlocker" module) ---- */
    public final Set<String> ipCidrBlacklist;

    /* ---- Progress Cheat Blocker (PBH "ProgressCheatBlocker" module) ---- */
    public final boolean pcbEnabled;
    /* Torrents smaller than this are ignored by the PCB */
    public final long pcbTorrentMinimumSize;
    /* Ban peers that report more upload than the torrent size allows */
    public final boolean pcbBlockExcessiveClients;
    /* Max allowed ratio of "fake" upload to torrent size before banning */
    public final double pcbExcessiveThreshold;
    /* Max allowed absolute difference between reported and computed progress */
    public final double pcbMaximumDifference;
    /* Max allowed progress rewind (peer reports lower progress than before) */
    public final double pcbRewindMaximumDifference;
    /* How long a peer suspected of cheating is observed before banning */
    public final long pcbBanDelayDurationMs;
    public final int pcbIpv4PrefixLength;
    public final int pcbIpv6PrefixLength;
    /* If > 0, briefly disconnect a peer once it reaches this fraction of
     * the torrent size in upload, to probe whether it reports a real client */
    public final double pcbFastPcbTestPercentage;
    public final long pcbFastPcbTestBlockingDurationMs;

    public static final long DEFAULT_BAN_DURATION_MS = 0; /* permanent */
    public static final long DEFAULT_ANTI_VAMPIRE_UPLOAD_THRESHOLD = 50L * 1024 * 1024; /* 50 MiB */
    public static final long DEFAULT_ANTI_VAMPIRE_MIN_PROGRESS_PPM = 1000; /* 0.1 % */
    public static final long DEFAULT_PCB_TORRENT_MINIMUM_SIZE = 10L * 1024 * 1024; /* 10 MiB */
    public static final double DEFAULT_PCB_EXCESSIVE_THRESHOLD = 1.2d;
    public static final double DEFAULT_PCB_MAXIMUM_DIFFERENCE = 0.1d; /* 10 % */
    public static final double DEFAULT_PCB_REWIND_MAXIMUM_DIFFERENCE = 0.05d; /* 5 % */
    public static final long DEFAULT_PCB_BAN_DELAY_DURATION_MS = 30_000L; /* 30 s */
    public static final int DEFAULT_PCB_IPV4_PREFIX_LENGTH = 32;
    public static final int DEFAULT_PCB_IPV6_PREFIX_LENGTH = 64;
    public static final double DEFAULT_PCB_FAST_PCB_TEST_PERCENTAGE = 0.1d;
    public static final long DEFAULT_PCB_FAST_PCB_TEST_BLOCKING_DURATION_MS = 15_000L; /* 15 s */

    private PbhSettings(Builder b) {
        this.enabled = b.enabled;
        this.checkIntervalSec = b.checkIntervalSec;
        this.banDurationMs = b.banDurationMs;
        this.antiVampireEnabled = b.antiVampireEnabled;
        this.antiVampireUploadThreshold = b.antiVampireUploadThreshold;
        this.antiVampireMinProgressPpm = b.antiVampireMinProgressPpm;
        this.clientNameBlacklistEnabled = b.clientNameBlacklistEnabled;
        this.clientNameBlacklist = immutableCopy(b.clientNameBlacklist);
        this.ipCidrBlacklist = immutableCopy(b.ipCidrBlacklist);
        this.pcbEnabled = b.pcbEnabled;
        this.pcbTorrentMinimumSize = b.pcbTorrentMinimumSize;
        this.pcbBlockExcessiveClients = b.pcbBlockExcessiveClients;
        this.pcbExcessiveThreshold = b.pcbExcessiveThreshold;
        this.pcbMaximumDifference = b.pcbMaximumDifference;
        this.pcbRewindMaximumDifference = b.pcbRewindMaximumDifference;
        this.pcbBanDelayDurationMs = b.pcbBanDelayDurationMs;
        this.pcbIpv4PrefixLength = b.pcbIpv4PrefixLength;
        this.pcbIpv6PrefixLength = b.pcbIpv6PrefixLength;
        this.pcbFastPcbTestPercentage = b.pcbFastPcbTestPercentage;
        this.pcbFastPcbTestBlockingDurationMs = b.pcbFastPcbTestBlockingDurationMs;
    }

    private static Set<String> immutableCopy(Set<String> src) {
        if (src == null || src.isEmpty())
            return Collections.emptySet();
        return Collections.unmodifiableSet(new HashSet<>(src));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean enabled = true;
        private long checkIntervalSec = 2;
        private long banDurationMs = DEFAULT_BAN_DURATION_MS;
        private boolean antiVampireEnabled = true;
        private long antiVampireUploadThreshold = DEFAULT_ANTI_VAMPIRE_UPLOAD_THRESHOLD;
        private long antiVampireMinProgressPpm = DEFAULT_ANTI_VAMPIRE_MIN_PROGRESS_PPM;
        private boolean clientNameBlacklistEnabled = true;
        private Set<String> clientNameBlacklist = new HashSet<>();
        private Set<String> ipCidrBlacklist = new HashSet<>();
        private boolean pcbEnabled = true;
        private long pcbTorrentMinimumSize = DEFAULT_PCB_TORRENT_MINIMUM_SIZE;
        private boolean pcbBlockExcessiveClients = true;
        private double pcbExcessiveThreshold = DEFAULT_PCB_EXCESSIVE_THRESHOLD;
        private double pcbMaximumDifference = DEFAULT_PCB_MAXIMUM_DIFFERENCE;
        private double pcbRewindMaximumDifference = DEFAULT_PCB_REWIND_MAXIMUM_DIFFERENCE;
        private long pcbBanDelayDurationMs = DEFAULT_PCB_BAN_DELAY_DURATION_MS;
        private int pcbIpv4PrefixLength = DEFAULT_PCB_IPV4_PREFIX_LENGTH;
        private int pcbIpv6PrefixLength = DEFAULT_PCB_IPV6_PREFIX_LENGTH;
        private double pcbFastPcbTestPercentage = DEFAULT_PCB_FAST_PCB_TEST_PERCENTAGE;
        private long pcbFastPcbTestBlockingDurationMs = DEFAULT_PCB_FAST_PCB_TEST_BLOCKING_DURATION_MS;

        public Builder enabled(boolean v) { this.enabled = v; return this; }
        public Builder checkIntervalSec(long v) { this.checkIntervalSec = v; return this; }
        public Builder banDurationMs(long v) { this.banDurationMs = v; return this; }
        public Builder antiVampireEnabled(boolean v) { this.antiVampireEnabled = v; return this; }
        public Builder antiVampireUploadThreshold(long v) { this.antiVampireUploadThreshold = v; return this; }
        public Builder antiVampireMinProgressPpm(long v) { this.antiVampireMinProgressPpm = v; return this; }
        public Builder clientNameBlacklistEnabled(boolean v) { this.clientNameBlacklistEnabled = v; return this; }
        public Builder clientNameBlacklist(Set<String> v) { this.clientNameBlacklist = v; return this; }
        public Builder ipCidrBlacklist(Set<String> v) { this.ipCidrBlacklist = v; return this; }
        public Builder pcbEnabled(boolean v) { this.pcbEnabled = v; return this; }
        public Builder pcbTorrentMinimumSize(long v) { this.pcbTorrentMinimumSize = v; return this; }
        public Builder pcbBlockExcessiveClients(boolean v) { this.pcbBlockExcessiveClients = v; return this; }
        public Builder pcbExcessiveThreshold(double v) { this.pcbExcessiveThreshold = v; return this; }
        public Builder pcbMaximumDifference(double v) { this.pcbMaximumDifference = v; return this; }
        public Builder pcbRewindMaximumDifference(double v) { this.pcbRewindMaximumDifference = v; return this; }
        public Builder pcbBanDelayDurationMs(long v) { this.pcbBanDelayDurationMs = v; return this; }
        public Builder pcbIpv4PrefixLength(int v) { this.pcbIpv4PrefixLength = v; return this; }
        public Builder pcbIpv6PrefixLength(int v) { this.pcbIpv6PrefixLength = v; return this; }
        public Builder pcbFastPcbTestPercentage(double v) { this.pcbFastPcbTestPercentage = v; return this; }
        public Builder pcbFastPcbTestBlockingDurationMs(long v) { this.pcbFastPcbTestBlockingDurationMs = v; return this; }

        public PbhSettings build() {
            return new PbhSettings(this);
        }
    }
}