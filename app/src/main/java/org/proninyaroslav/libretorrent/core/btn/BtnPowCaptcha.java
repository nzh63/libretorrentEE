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

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/*
 * Solver for the BTN proof-of-work captcha, a port of PeerBanHelper's
 * PoWClient: find an 8-byte (big-endian long) nonce such that
 * H(challenge || nonce) has at least difficultyBits leading zero bits.
 * The nonce is reported base64-encoded in the X-BTN-PowSolution header.
 *
 * The search is parallelised across the available cores the same way
 * upstream does it: every worker starts at a random nonce offset by its
 * thread id and strides by the thread count.
 */
public final class BtnPowCaptcha {
    private BtnPowCaptcha() {
    }

    /*
     * Solves the challenge. Returns the 8-byte nonce. Throws on invalid
     * algorithm or if the solving thread is interrupted.
     */
    @NonNull
    public static byte[] solve(@NonNull byte[] challenge, int difficultyBits,
                               @NonNull String algorithm) {
        if (difficultyBits < 0)
            difficultyBits = 0;
        // Fail fast for an unsupported digest so callers can skip the request
        try {
            MessageDigest.getInstance(algorithm);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unknown PoW algorithm: " + algorithm, e);
        }
        if (difficultyBits == 0)
            return new byte[8]; // trivial: any nonce satisfies 0 zero bits

        final int bits = difficultyBits;
        final int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
        final ExecutorService executor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "BtnPow");
            t.setDaemon(true);
            return t;
        });
        AtomicBoolean found = new AtomicBoolean(false);
        CompletableFuture<byte[]> result = new CompletableFuture<>();
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>(threads);
        try {
            for (int t = 0; t < threads; t++) {
                final int threadId = t;
                futures.add(executor.submit(() -> {
                    try {
                        MessageDigest digest;
                        try {
                            digest = MessageDigest.getInstance(algorithm);
                        } catch (Exception e) {
                            result.completeExceptionally(e);
                            return;
                        }
                        ByteBuffer buffer = ByteBuffer.allocate(8);
                        long nonce = new SecureRandom().nextLong() + threadId;
                        while (!found.get()) {
                            if (Thread.currentThread().isInterrupted())
                                return;
                            digest.reset();
                            digest.update(challenge);
                            buffer.clear();
                            buffer.putLong(nonce);
                            byte[] nonceBytes = buffer.array();
                            digest.update(nonceBytes);
                            if (hasLeadingZeroBits(digest.digest(), bits)) {
                                if (found.compareAndSet(false, true))
                                    result.complete(nonceBytes.clone());
                                return;
                            }
                            nonce += threads;
                        }
                    } catch (Exception e) {
                        result.completeExceptionally(e);
                    }
                }));
            }
            return result.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            for (java.util.concurrent.Future<?> f : futures)
                f.cancel(true);
            throw new IllegalStateException("PoW solving interrupted", e);
        } catch (Exception e) {
            for (java.util.concurrent.Future<?> f : futures)
                f.cancel(true);
            throw new IllegalStateException("PoW solving failed", e);
        } finally {
            executor.shutdownNow();
        }
    }

    /* Whether the hash has at least the given number of leading zero bits. */
    public static boolean hasLeadingZeroBits(@NonNull byte[] hash, int bits) {
        if (bits == 0)
            return true;
        int fullBytes = bits / 8;
        int remainingBits = bits % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (hash[i] != 0)
                return false;
        }
        if (remainingBits > 0) {
            if (fullBytes >= hash.length)
                return false;
            int mask = 0xFF << (8 - remainingBits);
            return (hash[fullBytes] & mask) == 0;
        }
        return true;
    }
}
