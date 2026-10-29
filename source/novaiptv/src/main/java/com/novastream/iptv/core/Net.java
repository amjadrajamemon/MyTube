package com.novastream.iptv.core;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/**
 * Resilient, bounded HTTP ingestion for playlists and EPG.
 * OkHttp retries across resolved IP routes on connection failures, follows only
 * carefully checked redirects, and never logs playlist URLs or credentials.
 */
public final class Net {
    public static final int CONNECT_TIMEOUT_MS = 20000;
    public static final int READ_TIMEOUT_MS = 180000;
    public static final int MAX_PLAYLIST_BYTES = 96 * 1024 * 1024;
    public static final String USER_AGENT = "NovaIPTVPro/0.2.0 (Android; IPTV Playlist Client)";
    private static final int MAX_REDIRECTS = 5;

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            // Redirects are followed manually so query credentials cannot be
            // forwarded to a different hostname without an explicit user action.
            .followRedirects(false)
            .followSslRedirects(false)
            .build();

    private Net() {}

    /** Normalizes a pasted URL without dropping its path or query parameters. */
    public static String normalizeHttpUrl(String rawUrl) throws IOException {
        if (rawUrl == null) throw new IOException("Enter a full HTTP(S) playlist URL.");
        String candidate = rawUrl.trim()
                .replace("\\&", "&")
                .replace("\u200B", "")
                .replace("\uFEFF", "");
        if (candidate.isEmpty() || candidate.indexOf('\r') >= 0 || candidate.indexOf('\n') >= 0) {
            throw new IOException("Enter a full HTTP(S) playlist URL on one line.");
        }
        if (!(candidate.regionMatches(true, 0, "http://", 0, 7)
                || candidate.regionMatches(true, 0, "https://", 0, 8))) {
            throw new IOException("Enter a full HTTP(S) playlist URL, including its port if required.");
        }
        HttpUrl parsed = HttpUrl.parse(candidate);
        if (parsed == null || !(parsed.isHttps() || "http".equals(parsed.scheme()))) {
            throw new IOException("Playlist URL is malformed. Check the server name, port and URL separators.");
        }
        return parsed.toString();
    }

    public static InputStream open(String url, int maxBytes) throws IOException {
        if (maxBytes <= 0) throw new IOException("Invalid playlist size limit.");
        String normalized = normalizeHttpUrl(url);
        HttpUrl parsed = HttpUrl.parse(normalized);
        if (parsed == null) throw new IOException("Playlist URL is malformed.");
        Response response = executeWithSafeRedirects(buildRequest(parsed));
        if (!response.isSuccessful()) {
            int code = response.code();
            String reason = response.message();
            response.close();
            throw new IOException("Playlist server returned HTTP " + code
                    + (reason == null || reason.isEmpty() ? "" : " " + reason)
                    + ". Check the playlist URL and provider access.");
        }

        ResponseBody body = response.body();
        if (body == null) {
            response.close();
            throw new IOException("Playlist server returned an empty HTTP response.");
        }

        InputStream input = body.byteStream();
        String encoding = response.header("Content-Encoding", "");
        String path = response.request().url().encodedPath().toLowerCase(Locale.ROOT);
        boolean gzip = encoding.toLowerCase(Locale.ROOT).contains("gzip") || path.endsWith(".gz");
        try {
            if (gzip) input = new GZIPInputStream(input);
        } catch (IOException error) {
            response.close();
            throw new IOException("Playlist response was marked as gzip but could not be decompressed.", error);
        }

        final Response finalResponse = response;
        return new FilterInputStream(input) {
            private long count;
            private boolean closed;

            private void check(int n) throws IOException {
                if (n > 0) {
                    count += n;
                    if (count > maxBytes) throw new IOException("Playlist exceeds the permitted download size.");
                }
            }

            @Override public int read() throws IOException {
                int value = super.read();
                if (value != -1) check(1);
                return value;
            }

            @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                int n = super.read(buffer, offset, length);
                check(n);
                return n;
            }

            @Override public void close() throws IOException {
                if (closed) return;
                closed = true;
                try {
                    super.close();
                } finally {
                    finalResponse.close();
                }
            }
        };
    }

    private static Request buildRequest(HttpUrl url) {
        return new Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                // Explicit gzip lets us safely support both HTTP gzip encoding
                // and providers that serve a raw .gz file without Content-Encoding.
                .header("Accept-Encoding", "gzip")
                .build();
    }

    private static Response executeWithSafeRedirects(Request first) throws IOException {
        Request current = first;
        Set<String> visited = new HashSet<>();
        for (int redirects = 0; ; redirects++) {
            String identity = current.url().toString();
            if (!visited.add(identity)) throw new IOException("Playlist server returned a redirect loop.");

            final Response response;
            try {
                response = CLIENT.newCall(current).execute();
            } catch (IOException error) {
                throw classifyNetworkFailure(error);
            }

            int code = response.code();
            if (!(code == 301 || code == 302 || code == 303 || code == 307 || code == 308)) {
                return response;
            }

            String location = response.header("Location");
            if (location == null || location.trim().isEmpty()) return response;
            if (redirects >= MAX_REDIRECTS) {
                response.close();
                throw new IOException("Playlist server redirected too many times.");
            }

            HttpUrl next = response.request().url().resolve(location);
            if (next == null) {
                response.close();
                throw new IOException("Playlist server returned an invalid redirect address.");
            }

            // Build the redirected request solely from the server's Location URL.
            // Do not copy the original URL's query to a different host: this permits
            // common provider/CDN redirects without forwarding the original credentials.
            response.close();
            current = buildRequest(next);
        }
    }

    private static IOException classifyNetworkFailure(IOException error) {
        Throwable unknownHost = findCause(error, UnknownHostException.class);
        if (unknownHost != null) {
            return new IOException("Could not resolve the playlist server hostname. Check the source address and DNS.", error);
        }
        Throwable timeout = findCause(error, SocketTimeoutException.class);
        if (timeout != null) {
            return new IOException("The playlist connection or response timed out on the configured endpoint. Nova retried available network routes; verify the exact server and port or test another network.", error);
        }
        Throwable connect = findCause(error, ConnectException.class);
        if (connect != null) {
            return new IOException("Could not connect to the playlist server on the configured port. The M3U has not reached the parsing stage; verify endpoint reachability.", error);
        }
        return new IOException("Could not download the playlist from the configured endpoint (" 
                + error.getClass().getSimpleName() + "). Check the network and provider address.", error);
    }

    private static Throwable findCause(Throwable error, Class<?> wanted) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = error; current != null && seen.add(current); current = current.getCause()) {
            if (wanted.isInstance(current)) return current;
        }
        return null;
    }

    public static byte[] bytes(String url, int maxBytes) throws IOException {
        try (InputStream input = open(url, maxBytes);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
            return output.toByteArray();
        }
    }
}
