package com.novastream.iptv.core;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

public class NetTransportTest {
    @Test public void normalizesEscapedAmpersandsWithoutDroppingQuery() throws Exception {
        String url = Net.normalizeHttpUrl("http://127.0.0.1:8080/get.php?username=u\\&password=p\\&type=m3u");
        assertTrue(url.contains("username=u&password=p&type=m3u"));
        assertFalse(url.contains("\\&"));
    }

    @Test public void rejectsNonHttpSchemes() throws Exception {
        try {
            Net.normalizeHttpUrl("ftp://127.0.0.1/list.m3u");
            fail("FTP should not be accepted");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("HTTP(S)"));
        }
    }

    @Test public void followsSameHostRedirectAndDecompressesGzip() throws Exception {
        byte[] body = gzip("#EXTM3U\n#EXTINF:-1,Test channel\nhttp://example.invalid/live.ts\n");
        try (LocalServer server = new LocalServer((path, headers) -> {
            if (path.equals("/start")) return new Reply(302, Collections.singletonMap("Location", "/playlist.gz"), new byte[0]);
            return new Reply(200, Collections.singletonMap("Content-Encoding", "gzip"), body);
        })) {
            String text = new String(Net.bytes(server.url("/start"), 4096), StandardCharsets.UTF_8);
            assertTrue(text.startsWith("#EXTM3U"));
            assertTrue(text.contains("Test channel"));
            assertTrue(server.lastHeaders.get().getOrDefault("user-agent", "").contains("NovaIPTVPro/0.2.1"));
            assertTrue(server.lastHeaders.get().getOrDefault("accept-encoding", "").contains("gzip"));
        }
    }

    @Test public void retriesAnAbortedPlaylistTransferFromTheBeginning() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        byte[] playlist = "#EXTM3U\n#EXTINF:-1,Retry channel\nhttp://example.invalid/live.ts\n".getBytes(StandardCharsets.UTF_8);
        try (LocalServer server = new LocalServer((path, headers) -> {
            if (requests.incrementAndGet() == 1) return Reply.aborted();
            return new Reply(200, Collections.emptyMap(), playlist);
        })) {
            M3uParser.Result result = Net.parsePlaylistWithRetry(server.url("/retry"), 4096);
            assertEquals(1, result.channels.size());
            assertEquals(2, requests.get());
        }
    }

    @Test public void retriesOnlyTransientTransportFailures() {
        assertTrue(Net.isRetryableTransferFailure(
                new IOException("aborted", new SocketException("Software caused connection abort"))));
        assertTrue(Net.isRetryableTransferFailure(new IOException("truncated", new java.io.EOFException())));
        assertFalse(Net.isRetryableTransferFailure(new IOException("HTTP 403")));
        assertFalse(Net.isRetryableTransferFailure(new IOException("Playlist exceeds the permitted download size.")));
    }

    @Test public void reportsHttpStatusWithoutLeakingResponseBody() throws Exception {
        try (LocalServer server = new LocalServer((path, headers) ->
                new Reply(503, Collections.emptyMap(), "secret-provider-body".getBytes(StandardCharsets.UTF_8)))) {
            try {
                Net.bytes(server.url("/unavailable"), 1024);
                fail("Expected HTTP failure");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("HTTP 503"));
                assertFalse(expected.getMessage().contains("secret-provider-body"));
            }
        }
    }

    @Test public void followsProviderRedirectWithoutForwardingOriginalCredentials() throws Exception {
        AtomicReference<LocalServer> holder = new AtomicReference<>();
        byte[] playlist = "#EXTM3U\n#EXTINF:-1,Test\nhttp://example.invalid/live.ts\n".getBytes(StandardCharsets.UTF_8);
        try (LocalServer server = new LocalServer((path, headers) -> {
            if (path.equals("/get.php")) {
                String target = holder.get().url("/target?redirect_token=issued").replace("127.0.0.1", "localhost");
                return new Reply(302, Collections.singletonMap("Location", target), new byte[0]);
            }
            return new Reply(200, Collections.emptyMap(), playlist);
        })) {
            holder.set(server);
            byte[] result = Net.bytes(server.url("/get.php?username=demo&password=secret&type=m3u"), 4096);
            assertTrue(new String(result, StandardCharsets.UTF_8).startsWith("#EXTM3U"));
            assertEquals("/target?redirect_token=issued", server.lastRequestTarget.get());
            assertFalse(server.lastRequestTarget.get().contains("username="));
            assertFalse(server.lastRequestTarget.get().contains("password="));
        }
    }

    @Test public void enforcesMaximumResponseBytes() throws Exception {
        try (LocalServer server = new LocalServer((path, headers) ->
                new Reply(200, Collections.emptyMap(), "12345678".getBytes(StandardCharsets.UTF_8)))) {
            try {
                Net.bytes(server.url("/large"), 3);
                fail("Expected size limit");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("permitted download size"));
            }
        }
    }

    private static byte[] gzip(String value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    private interface Handler { Reply respond(String path, Map<String, String> headers); }

    private static final class Reply {
        final int code;
        final Map<String, String> headers;
        final byte[] body;
        final boolean abort;
        Reply(int code, Map<String, String> headers, byte[] body) {
            this(code, headers, body, false);
        }
        Reply(int code, Map<String, String> headers, byte[] body, boolean abort) {
            this.code = code; this.headers = headers; this.body = body; this.abort = abort;
        }
        static Reply aborted() {
            return new Reply(200, Collections.emptyMap(), new byte[0], true);
        }
    }

    private static final class LocalServer implements Closeable {
        final ServerSocket server;
        final Handler handler;
        final AtomicReference<Map<String, String>> lastHeaders = new AtomicReference<>(Collections.emptyMap());
        final AtomicReference<String> lastRequestTarget = new AtomicReference<>("");
        final Thread thread;

        LocalServer(Handler handler) throws IOException {
            this.handler = handler;
            server = new ServerSocket(0, 8, InetAddress.getByName("0.0.0.0"));
            thread = new Thread(this::serve, "nova-test-http-server");
            thread.setDaemon(true);
            thread.start();
        }

        String url(String path) { return "http://127.0.0.1:" + server.getLocalPort() + path; }

        private void serve() {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    String requestLine = reader.readLine();
                    if (requestLine == null) continue;
                    String[] parts = requestLine.split(" ", 3);
                    lastRequestTarget.set(parts.length > 1 ? parts[1] : "");
                    Map<String, String> headers = new LinkedHashMap<>();
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        int colon = line.indexOf(':');
                        if (colon > 0) headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
                    }
                    lastHeaders.set(headers);
                    String path = parts.length > 1 ? parts[1].split("\\?", 2)[0] : "/";
                    Reply reply = handler.respond(path, headers);
                    if (reply.abort) {
                        socket.setSoLinger(true, 0);
                        continue;
                    }
                    String reason = reply.code == 200 ? "OK" : reply.code == 302 ? "Found" : reply.code == 503 ? "Service Unavailable" : "Response";
                    OutputStream output = socket.getOutputStream();
                    output.write(("HTTP/1.1 " + reply.code + " " + reason + "\r\n").getBytes(StandardCharsets.US_ASCII));
                    for (Map.Entry<String, String> header : reply.headers.entrySet()) {
                        output.write((header.getKey() + ": " + header.getValue() + "\r\n").getBytes(StandardCharsets.US_ASCII));
                    }
                    output.write(("Content-Length: " + reply.body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    output.write(reply.body);
                    output.flush();
                } catch (IOException ignored) {
                    if (!server.isClosed()) { /* A client may cancel after a test assertion. */ }
                }
            }
        }

        @Override public void close() throws IOException {
            server.close();
            try { thread.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}
