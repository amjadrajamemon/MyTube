package com.novastream.iptv.core;
import com.novastream.iptv.model.Channel;
import java.lang.reflect.Constructor;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class MultiPlaylistTest {
    private Catalog blank() throws Exception {
        Constructor<Catalog> cc=Catalog.class.getDeclaredConstructor();
        cc.setAccessible(true);return cc.newInstance();
    }
    private Channel stream(String source, String id) {
        return new Channel(id,"Channel "+id,"https://test.example/"+id+".m3u8",
                "News","","","live",source);
    }
    @Test public void twoSourcesCoexistAndRefreshDoesNotReplaceAnother() throws Exception {
        Catalog c=blank();
        c.replaceSource("https://one.example/a.m3u",Arrays.asList(stream("https://one.example/a.m3u","a")));
        c.replaceSource("https://two.example/b.m3u",Arrays.asList(stream("https://two.example/b.m3u","b")));
        assertEquals(2,c.sources().size());assertEquals(2,c.channels.size());
        c.addFavorite("b");
        c.replaceSource("https://one.example/a.m3u",Arrays.asList(stream("https://one.example/a.m3u","a2")));
        assertEquals(2,c.channels.size());assertNotNull(c.find("b"));assertTrue(c.favorites.contains("b"));
    }
    @Test public void toggleDoesNotEraseChannelsOrFavorites() throws Exception {
        Catalog c=blank();String a="https://one.example/playlist";
        c.replaceSource(a,Arrays.asList(stream(a,"a")));
        c.addFavorite("a");
        c.setSourceEnabled(a,false);
        assertEquals(0,c.filtered("Live","","").size());
        assertEquals(1,c.channels.size());assertTrue(c.favorites.contains("a"));
        c.setSourceEnabled(a,true);assertEquals(1,c.filtered("Live","","").size());
    }
    @Test public void removeOnlySelectedSource() throws Exception {
        Catalog c=blank();
        c.replaceSource("s1",Arrays.asList(stream("s1","1")));
        c.replaceSource("s2",Arrays.asList(stream("s2","2")));
        c.addFavorite("1");c.addFavorite("2");
        c.removeSource("s1");
        assertNull(c.find("1"));assertNotNull(c.find("2"));
        assertFalse(c.favorites.contains("1"));assertTrue(c.favorites.contains("2"));
    }
    @Test public void sourceLabelsDoNotExposeFullCredentials() throws Exception {
        Catalog c=blank();
        String key="https://provider.example/list.m3u?username=private&password=hidden";
        c.replaceSource(key,Arrays.asList(stream(key,"1")));
        assertEquals("provider.example",c.sourceName(key));
        assertTrue(c.renameSource(key,"Family TV"));
        assertEquals("Family TV",c.sourceName(key));
    }
}
