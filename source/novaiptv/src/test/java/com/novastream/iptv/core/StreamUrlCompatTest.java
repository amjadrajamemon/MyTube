package com.novastream.iptv.core;
import org.junit.Test;
import static org.junit.Assert.*;

public class StreamUrlCompatTest {
    @Test public void swapsXtreamLiveTsToHls() {
        assertEquals("http://example.org/live/a/b/123.m3u8",
            StreamUrlCompat.alternateLive("live","News","http://example.org/live/a/b/123.ts","http://example.org/live/a/b/123.ts"));
    }
    @Test public void keepsQueryTokensAndFragments() {
        assertEquals("https://host/live/a/b/99.ts?token=xyz#1",
            StreamUrlCompat.alternateLive("live","News","https://host/live/a/b/99.m3u8?token=xyz#1","https://host/live/a/b/99.m3u8?token=xyz#1"));
    }
    @Test public void neverRewritesMoviesOrUnrelatedStreams() {
        assertNull(StreamUrlCompat.alternateLive("movie","Movies","https://host/movie/a/b/1.ts","https://host/movie/a/b/1.ts"));
        assertNull(StreamUrlCompat.alternateLive("live","News","https://host/video/1.ts","https://host/video/1.ts"));
    }
}
