package com.novastream.iptv.core;

import java.util.Locale;

/** Keeps movies and series visible even if providers mix legacy live paths and labels. */
public final class StreamKind {
    private StreamKind() {}
    public static String detect(String kind,String group,String url) {
        if("movie".equalsIgnoreCase(kind)||"vod".equalsIgnoreCase(kind))return "movie";
        if("series".equalsIgnoreCase(kind)||"episode".equalsIgnoreCase(kind))return "series";
        String u=url==null?"":url.toLowerCase(Locale.ROOT);
        String g=group==null?"":group.toLowerCase(Locale.ROOT);
        // Explicit playlist group names override providers' reused /live/ links.
        if(g.matches(".*\\b(vod|movie|movies|film|films|cinema|on[ -]?demand|video[ -]?on[ -]?demand)\\b.*"))return "movie";
        if(g.matches(".*\\b(series|seasons?|episodes?|tv[ -]?shows?|box[ -]?sets?)\\b.*"))return "series";
        if(u.contains("/movie/")||u.contains("/vod/")||u.matches(".*[?&]type=(movie|vod)(?:&.*)?$"))return "movie";
        if(u.contains("/series/"))return "series";
        if(u.contains("/live/"))return "live";
        if(u.matches(".*\\.(mp4|mkv|avi|webm|mov|flv)(?:[?#].*)?$"))return "movie";
        return "live";
    }
}