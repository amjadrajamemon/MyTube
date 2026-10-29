package com.novastream.iptv.core;

import java.io.*;
import java.net.*;
import java.util.zip.GZIPInputStream;

/** Bounded network ingestion; use background threads only. Never logs credentials or response bodies. */
public final class Net {
    public static final int CONNECT_TIMEOUT_MS=30000, READ_TIMEOUT_MS=90000;
    public static final int MAX_PLAYLIST_BYTES=96*1024*1024;
    public static final String USER_AGENT="NovaIPTVPro/0.1.7 (Android; IPTV Playlist Client)";
    public static InputStream open(String url, int maxBytes) throws IOException {
        URL parsed = new URL(url);
        if (!"http".equalsIgnoreCase(parsed.getProtocol()) && !"https".equalsIgnoreCase(parsed.getProtocol()))
            throw new IOException("Only HTTP(S) playlist/guide URLs are allowed");
        URLConnection c = parsed.openConnection();
        c.setConnectTimeout(CONNECT_TIMEOUT_MS); c.setReadTimeout(READ_TIMEOUT_MS);
        c.setRequestProperty("User-Agent", USER_AGENT);
        c.setRequestProperty("Accept-Encoding", "gzip");
        c.setRequestProperty("Accept", "*/*");
        InputStream input = c.getInputStream();
        String enc = c.getContentEncoding();
        if ("gzip".equalsIgnoreCase(enc) || parsed.getPath().toLowerCase().endsWith(".gz")) input = new GZIPInputStream(input);
        return new FilterInputStream(input) {
            long read = 0;
            private void check(int n) throws IOException { if (n > 0 && (read += n) > maxBytes) throw new IOException("File exceeds permitted size"); }
            @Override public int read() throws IOException { int n=super.read(); if(n != -1) check(1); return n; }
            @Override public int read(byte[] b,int o,int l) throws IOException { int n=super.read(b,o,l); check(n); return n; }
        };
    }
    public static byte[] bytes(String url, int maxBytes) throws IOException {
        try(InputStream in=open(url,maxBytes); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buf=new byte[8192]; int n; while((n=in.read(buf))!=-1) out.write(buf,0,n); return out.toByteArray();
        }
    }
}
