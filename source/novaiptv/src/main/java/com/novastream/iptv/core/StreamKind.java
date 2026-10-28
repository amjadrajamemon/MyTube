package com.novastream.iptv.core;
import java.util.Locale;
/** Infers content type from stream path and playlist group, including older saved entries. */
public final class StreamKind {
    private StreamKind(){}
    public static String detect(String kind,String group,String url){
        if("movie".equals(kind)||"series".equals(kind)||"episode".equals(kind))return kind;
        String u=url==null?"":url.toLowerCase(Locale.ROOT);
        String g=group==null?"":group.toLowerCase(Locale.ROOT);
        if(u.contains("/live/"))return "live";
        if(u.contains("/movie/")||u.contains("/vod/")||u.matches(".*[?&]type=(movie|vod)(?:&.*)?$"))return "movie";
        if(u.contains("/series/"))return "series";
        if(g.matches(".*\\b(vod|movies?|films?|cinema|on[ -]?demand)\\b.*"))return "movie";
        if(g.matches(".*\\b(series|seasons?|episodes?|tv[ -]?shows?)\\b.*"))return "series";
        return "live";
    }
}
