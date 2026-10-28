package com.novastream.iptv.core;

import org.junit.Test;
import java.lang.reflect.Constructor;
import java.util.*;
import static org.junit.Assert.*;

public final class FavoritesManagementTest {
    private Catalog empty() throws Exception {
        Constructor<Catalog> constructor=Catalog.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
    @Test public void orderedFavoritesAndCollections() throws Exception {
        Catalog c=empty();
        c.addFavorite("a");c.addFavorite("b");c.addFavorite("c");c.addFavorite("b");
        assertEquals(Arrays.asList("a","b","c"),c.favoriteOrder);
        assertTrue(c.createFavoriteFolder("Sports"));
        assertFalse(c.createFavoriteFolder("sports"));
        assertTrue(c.assignFavorite("Sports","b"));
        assertTrue(c.assignFavorite("Sports","c"));
        assertTrue(c.moveFavorite("c","Sports",-1));
        assertEquals(Arrays.asList("c","b"),c.favoriteFolders.get("Sports"));
        c.removeFavorite("c");
        assertFalse(c.favoriteFolders.get("Sports").contains("c"));
        assertEquals(Arrays.asList("a","b"),c.favoriteOrder);
    }
    @Test public void renameDeleteAndClear() throws Exception {
        Catalog c=empty();
        assertTrue(c.createFavoriteFolder("Family"));
        assertTrue(c.renameFavoriteFolder("Family","Kids"));
        assertFalse(c.favoriteFolders.containsKey("Family"));
        assertTrue(c.favoriteFolders.containsKey("Kids"));
        assertFalse(c.createFavoriteFolder("All Favorites"));
        assertFalse(c.createFavoriteFolder(" "));
        c.assignFavorite("Kids","kid1");c.addFavorite("news");
        c.clearAllFavorites();
        assertTrue(c.favorites.isEmpty());
        assertTrue(c.favoriteOrder.isEmpty());
        assertTrue(c.favoriteFolders.get("Kids").isEmpty());
    }
    @Test public void missingItemsRemainSafe() throws Exception {
        Catalog c=empty();
        c.addFavorite("orphan");
        assertTrue(c.favoriteEntries("","All","").isEmpty());
        assertFalse(c.moveFavorite("orphan","",-1));
        c.unassignFavorite("missing","orphan");
        assertEquals(1,c.favorites.size());
    }
}
