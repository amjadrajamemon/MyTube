package com.novastream.iptv.core;

import com.novastream.iptv.model.Channel;
import org.junit.Test;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import static org.junit.Assert.*;

public class LiveCatalogRecoveryTest {
    private Catalog catalog() throws Exception {
        Constructor<Catalog> constructor=Catalog.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
    private Channel item(String id,String group,String url,String type,String source){
        return new Channel(id,"Item "+id,url,group,"","",type,source);
    }
    @Test public void moviesDoNotHideLiveAfterSameAccountRefresh() throws Exception {
        Catalog catalog=catalog();
        String source="portal_one";
        catalog.replaceSource(source,Arrays.asList(
            item("news","News","http://host.test/live/u/p/1.ts","live",source),
            item("film","Movies","http://host.test/movie/u/p/2.mp4","movie",source)
        ));
        assertEquals(1,catalog.filtered("Live","","").size());
        assertEquals(1,catalog.filtered("Movies","","").size());
        assertEquals(2,catalog.channels.size());
    }
    @Test public void liveChannelSurvivesDisablingOtherPlaylist() throws Exception {
        Catalog c=catalog();
        c.replaceSource("one",Arrays.asList(item("channel","News","http://host.test/live/u/p/1.ts","live","one")));
        c.replaceSource("two",Arrays.asList(item("movie","Movies","http://host.test/movie/u/p/2.mp4","movie","two")));
        c.setSourceEnabled("two",false);
        assertEquals(1,c.filtered("Live","","").size());
        assertTrue(c.filtered("Movies","","").isEmpty());
        c.setSourceEnabled("two",true);
        assertEquals(1,c.filtered("Movies","","").size());
    }
}