package com.novastream.iptv.ui;
import org.junit.Test;
import java.util.HashSet;
import static org.junit.Assert.*;
/** A preset must change geometry and navigation, not only its palette. */
public class ThemeIdentityTest {
    @Test public void allSixteenHaveUniqueStructuralOrVariantIdentity(){
        HashSet<String> fingerprints=new HashSet<>();
        for(int i=0;i<17;i++){
            ThemeIdentity t=ThemeIdentity.of(i);
            assertTrue(t.radius>=2);
            assertTrue(t.gutter>=7);
            assertTrue(t.heroHeight>=50);
            assertTrue(t.imageHeight>=110);
            String f=t.family+":"+t.radius+":"+t.gutter+":"+t.heroHeight+":"+
                     t.imageHeight+":"+t.titleMultiplier+":"+t.navigationAtBottom;
            assertTrue("Duplicate theme "+i,fingerprints.add(f));
        }
    }
    @Test public void designFamiliesChangeActualNavigationAndLayout(){
        assertTrue(ThemeIdentity.of(12).navigationAtBottom);
        assertFalse(ThemeIdentity.of(0).navigationAtBottom);
        assertNotEquals(ThemeIdentity.of(0).radius,ThemeIdentity.of(8).radius);
        assertNotEquals(ThemeIdentity.of(0).imageHeight,ThemeIdentity.of(12).imageHeight);
        assertNotEquals(ThemeIdentity.of(1).gutter,ThemeIdentity.of(3).gutter);
    }
    @Test public void labelsExposeFourCompleteDesignFamilies(){
        String[] labels=ThemeIdentity.labels(null);
        assertEquals(17,labels.length);
        assertTrue(labels[0].contains("CINEMA"));
        assertTrue(labels[4].contains("GLASS"));
        assertTrue(labels[8].contains("EDITORIAL"));
        assertTrue(labels[12].contains("TV DASHBOARD"));
        assertTrue(labels[16].contains("XStreamity"));
    }
}