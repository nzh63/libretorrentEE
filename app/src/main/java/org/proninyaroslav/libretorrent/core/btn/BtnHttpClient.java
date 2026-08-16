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
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/*
 * Minimal BTN HTTP client built on HttpURLConnection. Handles:
 *  - auth headers (X-BTN-AppID / X-BTN-AppSecret / X-BTN-InstallationID);
 *  - a BTN-marked User-Agent;
 *  - redirect following;
 *  - transparent gzip response decompression;
 *  - gzip request bodies (required by submit_bans / submit_swarm).
 */
public class BtnHttpClient {
    public static final String BTN_PROTOCOL_VERSION = "20";
    public static final String BTN_PROTOCOL_TAG = "BTN-Protocol/v2.0.1 BTN-Protocol-Version/" + BTN_PROTOCOL_VERSION;

    private static final int MAX_REDIRECTS = 5;
    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int READ_TIMEOUT_MS = 20_000;

    @NonNull
    private final String userAgent;

    public BtnHttpClient(@NonNull String userAgent) {
        this.userAgent = userAgent;
    }

    public static BtnHttpClient defaultClient() {
        return new BtnHttpClient("LibreTorrent/4.1.1 " + BTN_PROTOCOL_TAG);
    }

    /*
     * Result of a GET request.
     */
    public static class GetResult {
        public final int statusCode;
        @Nullable public final byte[] body;
        /* Value of the X-BTN-ContentVersion response header, if present */
        @Nullable public final String contentVersion;

        GetResult(int statusCode, @Nullable byte[] body, @Nullable String contentVersion) {
            this.statusCode = statusCode;
            this.body = body;
            this.contentVersion = contentVersion;
        }

        public boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300;
        }

        /* 204 = no change (per BTN-Spec) */
        public boolean isNoContent() {
            return statusCode == HttpURLConnection.HTTP_NO_CONTENT;
        }

        @Nullable
        public String bodyAsUtf8() {
            return body == null ? null : new String(body, StandardCharsets.UTF_8);
        }
    }

    /*
     * Performs a GET, following redirects and setting the auth headers.
     * Returns null on I/O error.
     */
    @Nullable
    public GetResult get(@NonNull String urlStr,
                         @NonNull BtnSettings settings,
                         @Nullable String rev) throws IOException {
        String current = urlStr;
        if (rev != null && !rev.isEmpty()) {
            current = current + (current.contains("?") ? "&" : "?") + "rev=" + rev;
        }

        for (int redirects = 0; ; redirects++) {
            if (redirects > MAX_REDIRECTS)
                return null;
            HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            applyAuthHeaders(conn, settings);
            conn.setRequestProperty("Accept-Encoding", "gzip");

            int code = conn.getResponseCode();
            if (isRedirect(code)) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null)
                    return null;
                current = new URL(new URL(current), location).toString();
                continue;
            }

            byte[] body = (code >= 200 && code < 300) ? readBody(conn) : readError(conn);
            String contentEncoding = conn.getHeaderField("Content-Encoding");
            String contentVersion = conn.getHeaderField("X-BTN-ContentVersion");
            conn.disconnect();
            body = maybeDecompress(body, contentEncoding);
            return new GetResult(code, body, contentVersion);
        }
    }

    /*
     * POSTs a gzip-compressed JSON body. Returns the HTTP status code, or -1
     * on I/O error. Per HTTP semantics, 301/302/303 redirects after a POST
     * are followed with a GET (without the body); 307/308 re-POST it.
     */
    public int postGzipJson(@NonNull String urlStr,
                            @NonNull BtnSettings settings,
                            @NonNull byte[] jsonBody) throws IOException {
        String current = urlStr;
        boolean rePostBody = true;
        for (int redirects = 0; ; redirects++) {
            if (redirects > MAX_REDIRECTS)
                return -1;
            HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod(rePostBody ? "POST" : "GET");
            applyAuthHeaders(conn, settings);
            if (rePostBody) {
                conn.setRequestProperty("Content-Encoding", "gzip");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
            }

            if (rePostBody) {
                byte[] gzipped = gzip(jsonBody);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(gzipped);
                    os.flush();
                }
            }
            int code = conn.getResponseCode();
            if (isRedirect(code)) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null)
                    return -1;
                if (code != 307 && code != 308)
                    rePostBody = false;
                current = new URL(new URL(current), location).toString();
                continue;
            }
            conn.disconnect();
            return code;
        }
    }

    /*
     * POSTs a plain (uncompressed) JSON body, e.g. for the heartbeat ability.
     * Returns the status code plus the (decompressed) response body, or null
     * on I/O error. Redirects after a POST are followed with a GET (without
     * the body) except 307/308, which re-POST it.
     */
    @Nullable
    public GetResult postJson(@NonNull String urlStr,
                              @NonNull BtnSettings settings,
                              @NonNull byte[] jsonBody) throws IOException {
        String current = urlStr;
        boolean rePostBody = true;
        for (int redirects = 0; ; redirects++) {
            if (redirects > MAX_REDIRECTS)
                return null;
            HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod(rePostBody ? "POST" : "GET");
            applyAuthHeaders(conn, settings);
            conn.setRequestProperty("Accept-Encoding", "gzip");
            if (rePostBody) {
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody);
                    os.flush();
                }
            }
            int code = conn.getResponseCode();
            if (isRedirect(code)) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null)
                    return null;
                if (code != 307 && code != 308)
                    rePostBody = false;
                current = new URL(new URL(current), location).toString();
                continue;
            }
            byte[] body = (code >= 200 && code < 300) ? readBody(conn) : readError(conn);
            String contentEncoding = conn.getHeaderField("Content-Encoding");
            conn.disconnect();
            body = maybeDecompress(body, contentEncoding);
            return new GetResult(code, body, null);
        }
    }

    private void applyAuthHeaders(@NonNull HttpURLConnection conn, @NonNull BtnSettings s) {
        if (s.appId != null && !s.appId.isEmpty())
            conn.setRequestProperty("X-BTN-AppID", s.appId);
        if (s.appSecret != null && !s.appSecret.isEmpty())
            conn.setRequestProperty("X-BTN-AppSecret", s.appSecret);
        if (s.installationId != null && !s.installationId.isEmpty())
            conn.setRequestProperty("X-BTN-InstallationID", s.installationId);
        conn.setRequestProperty("User-Agent", userAgent);
    }

    private static boolean isRedirect(int code) {
        return code == HttpURLConnection.HTTP_MOVED_PERM
                || code == HttpURLConnection.HTTP_MOVED_TEMP
                || code == HttpURLConnection.HTTP_SEE_OTHER
                || code == 307 || code == 308;
    }

    private static byte[] readBody(HttpURLConnection conn) throws IOException {
        try (InputStream in = conn.getInputStream()) {
            return readAll(in);
        } catch (IOException e) {
            return readError(conn);
        }
    }

    private static byte[] readError(HttpURLConnection conn) {
        try (InputStream err = conn.getErrorStream()) {
            if (err == null)
                return null;
            return readAll(err);
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(in);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = buffered.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /* Decompresses a gzip body if the connection used gzip. */
    @Nullable
    public static byte[] maybeDecompress(@Nullable byte[] body, @Nullable String contentEncoding) {
        if (body == null)
            return null;
        if (contentEncoding != null && contentEncoding.toLowerCase().contains("gzip")) {
            try (GZIPInputStream gz = new GZIPInputStream(new java.io.ByteArrayInputStream(body))) {
                return readAll(gz);
            } catch (IOException e) {
                return body; // fall back to raw
            }
        }
        return body;
    }

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
            gz.write(data);
        }
        return bos.toByteArray();
    }
}