package com.novastream.iptv.core;

import com.novastream.iptv.model.Channel;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Regression checks: VOD must not disappear from the Movies/Series tabs. */
public class V014VodVisibilityTest {
    @Test public void vodGroupTakesPrecedenceOverReusedLiveUrl() {
        assertEquals("movie",StreamKind.detect("live","VOD | Hollywood","http://example.test/live/u/p/999.ts"));
        assertEquals("movie",StreamKind.detect("","Action Movies","https://example.test/channel/film.m3u8"));
        assertEquals("series",StreamKind.detect("","TV Series | Dramas","https://example.test/channel/episode.ts"));
    }
    @Test public void movieExtensionsAndXtreamRoutesAreRecognized() {
        assertEquals("movie",StreamKind.detect("","Other","https://example.test/media/film.mkv?token=abc"));
        assertEquals("movie",StreamKind.detect("live","Other","https://example.test/movie/u/p/11.mp4"));
        assertEquals("series",StreamKind.detect("series","Other","https://example.test/player_api.php"));
        assertEquals("live",StreamKind.detect("live","News","https://example.test/live/u/p/4.m3u8"));
    }
    @Test public void moviesAndSeriesAppearInCatalogFilters() {
        Catalog catalog=Catalog.get();
        catalog.channels.clear();catalog.disabledSources.clear();
        catalog.channels.add(new Channel("1","Film Alpha","https://example.test/live/u/p/1.ts","Movies","", "", "live","fixture"));
        catalog.channels.add(new Channel("2","Show Beta","https://example.test/series/u/p/2.mp4","Series","", "", "series","fixture"));
        catalog.channels.add(new Channel("3","News Gamma","https://example.test/live/u/p/3.ts","News","", "", "live","fixture"));
        List<Channel> movies=catalog.filtered("Movies","","");
        List<Channel> series=catalog.filtered("Series","","");
        assertEquals(1,movies.size());assertEquals("Film Alpha",movies.get(0).name);
        assertEquals(1,series.size());assertEquals("Show Beta",series.get(0).name);
        assertEquals(1,catalog.filtered("Live","","").size());
        catalog.channels.clear();
    }
}