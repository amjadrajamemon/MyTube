package com.novastream.iptv.core;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class M3uParserTest {
    private M3uParser.Result parse(String s) throws Exception {
        return M3uParser.parse(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)),"test","https://cdn.example/iptv/list.m3u");
    }
    @Test public void parsesM3uAttributesAndGuide() throws Exception {
        M3uParser.Result r=parse("#EXTM3U x-tvg-url=\"https://e.example/guide.xml\"\n"+
          "#EXTINF:-1 tvg-id=\"abc\" tvg-logo=\"https://a.example/x.png\" group-title=\"News International\",World News\n"+
          "https://cdn.example/hls/index.m3u8\n");
        assertEquals(1,r.channels.size());assertEquals("World News",r.channels.get(0).name);
        assertEquals("News International",r.channels.get(0).group);assertEquals("abc",r.channels.get(0).tvgId);
        assertEquals("https://e.example/guide.xml",r.epgUrl);
    }
    @Test public void relativeAndUnsafeLinks() throws Exception {
        M3uParser.Result r=parse("#EXTM3U\n#EXTINF:-1,Local\nchannels/tv.m3u8\n"+
            "#EXTINF:-1,Unsafe\nfile:///sdcard/secrets\n");
        assertEquals(1,r.channels.size());assertEquals("https://cdn.example/iptv/channels/tv.m3u8",r.channels.get(0).url);
        assertEquals(1,r.rejected);
    }
    @Test public void categorizesMovies() throws Exception {
        M3uParser.Result r=parse("#EXTINF:-1 group-title=\"Movies 4K\",Film\nhttps://a.example/1.mp4\n");
        assertEquals("movie",r.channels.get(0).kind);
    }
    @Test public void ignoresHeaderOnly() throws Exception {assertTrue(parse("#EXTM3U\n").channels.isEmpty());}
    @Test public void hashStable() {assertEquals(M3uParser.hash("abc"),M3uParser.hash("abc"));assertNotEquals(M3uParser.hash("abc"),M3uParser.hash("abd"));}
}
