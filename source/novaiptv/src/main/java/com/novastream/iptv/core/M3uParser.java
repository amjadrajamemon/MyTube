package com.novastream.iptv.core;

import com.novastream.iptv.model.Channel;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

/** Android-independent, bounded, streaming M3U parser. Does not print URLs (may contain secrets). */
public final class M3uParser {
    private static final Pattern ATTR = Pattern.compile("([A-Za-z][A-Za-z0-9_-]*)\\s*=\\s*(?:\"([^\"]*)\"|([^\\s]+))");
    public static final int MAX_CHANNELS = 250000;
    public static class Result {
        public final List<Channel> channels = new ArrayList<>();
        public String epgUrl = "";
        public int rejected = 0;
    }
    public static Result parse(InputStream stream, String source, String baseUrl) throws IOException {
        Result result = new Result();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line, title = "", group = "", logo = "", tvg = "", userAgent = "", referrer = "";
            boolean pending = false;
            int lines = 0;
            while ((line = br.readLine()) != null) {
                if (++lines > 1000000) throw new IOException("Playlist exceeds 1000000 lines; split unusually large playlists to protect device memory");
                if (line.length() > 12000) { result.rejected++; continue; }
                line = line.replace("\ufeff", "").trim();
                if (line.startsWith("#EXTM3U")) {
                    Map<String,String> a = attrs(line);
                    result.epgUrl = a.getOrDefault("x-tvg-url", a.getOrDefault("url-tvg", result.epgUrl));
                } else if (line.startsWith("#EXTINF")) {
                    Map<String,String> a = attrs(line);
                    int comma = commaOutsideQuotes(line);
                    title = comma >= 0 ? line.substring(comma+1).trim() : "";
                    title = !title.isEmpty() ? title : a.getOrDefault("tvg-name", "Unnamed");
                    group = a.getOrDefault("group-title", "Other");
                    logo = a.getOrDefault("tvg-logo", "");
                    tvg = a.getOrDefault("tvg-id", "");
                    userAgent="";referrer="";
                    pending = true;
                } else if (line.startsWith("#EXTGRP:") && pending) {
                    group = line.substring(8).trim();
                } else if (line.startsWith("#EXTVLCOPT:") && pending) {
                    String value=line.substring(11);int at=value.indexOf('=');
                    if(at>0){
                        String key=value.substring(0,at).trim().toLowerCase(Locale.ROOT);
                        String data=value.substring(at+1).trim();
                        if(data.length()<256 && !data.contains("\r") && !data.contains("\n")){
                            if(key.equals("http-user-agent"))userAgent=data;
                            if(key.equals("http-referrer")||key.equals("http-referer"))referrer=data;
                        }
                    }
                } else if (!line.isEmpty() && !line.startsWith("#") && pending) {
                    String url = resolve(baseUrl, line);
                    if (validStream(url)) {
                        if (result.channels.size() >= MAX_CHANNELS) throw new IOException("Playlist exceeds 250000 streams; split unusually large playlists to protect device memory");
                        String id = hash(source+"|"+url);
                        result.channels.add(new Channel(id, title, url, group, logo, tvg, StreamKind.detect("",group,url), source,userAgent,referrer));
                    } else { result.rejected++; }
                    pending = false;
                }
            }
        }
        return result;
    }
    private static int commaOutsideQuotes(String s) {
        boolean quote = false;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '"') quote = !quote;
            if (s.charAt(i) == ',' && !quote) return i;
        }
        return -1;
    }
    private static Map<String,String> attrs(String s) {
        Map<String,String> out = new HashMap<>();
        Matcher m = ATTR.matcher(s);
        while (m.find()) out.put(m.group(1).toLowerCase(Locale.ROOT), m.group(2) != null ? m.group(2) : m.group(3));
        return out;
    }
    private static String resolve(String base, String ref) {
        try { return new URI(base == null ? "" : base).resolve(ref).toString(); }
        catch (Exception e) { return ref; }
    }
    public static boolean validStream(String url) {
        try { String s = new URI(url).getScheme(); return s != null && (s.equalsIgnoreCase("http") || s.equalsIgnoreCase("https") || s.equalsIgnoreCase("rtsp")); }
        catch (Exception e) { return false; }
    }
    public static String hash(String s) {
        try {
            byte[] v = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder(); for (int i=0;i<12;i++) b.append(String.format(Locale.ROOT, "%02x",v[i])); return b.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
