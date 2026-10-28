package com.novastream.iptv.core;

import java.util.Locale;

/** Pure, unit-tested Xtream live stream URL compatibility rules. */
public final class StreamUrlCompat {
    private StreamUrlCompat(){}
    public static String alternateLive(String kind,String group,String savedUrl,String attempt) {
        if(!"live".equals(StreamKind.detect(kind,group,savedUrl))||attempt==null)return null;
        String lower=attempt.toLowerCase(Locale.ROOT);
        if(!lower.contains("/live/"))return null;
        if(lower.matches(".*\\.ts(?:[?#].*)?$"))
            return attempt.replaceFirst("(?i)\\.ts(?=\\?|#|$)",".m3u8");
        if(lower.matches(".*\\.m3u8(?:[?#].*)?$"))
            return attempt.replaceFirst("(?i)\\.m3u8(?=\\?|#|$)",".ts");
        return null;
    }
}
