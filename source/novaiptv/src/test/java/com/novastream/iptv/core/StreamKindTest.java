package com.novastream.iptv.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class StreamKindTest {
    @Test public void moviePath(){assertEquals("movie",StreamKind.detect("live","Other","https://provider.test/movie/a/b/1.mp4"));}
    @Test public void liveWinsOverGroup(){assertEquals("live",StreamKind.detect("","Movies","https://provider.test/live/a/b/1.m3u8"));}
    @Test public void groups(){assertEquals("movie",StreamKind.detect("","VOD Films","https://provider.test/video"));assertEquals("series",StreamKind.detect("","TV Series","https://provider.test/video"));}
}
