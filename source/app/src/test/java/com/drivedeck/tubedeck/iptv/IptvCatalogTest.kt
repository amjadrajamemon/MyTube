package com.drivedeck.tubedeck.iptv

import org.junit.Assert.*
import org.junit.Test

class IptvCatalogTest {
    @Test fun presetsAreTenDistinctCompleteThemes() {
        assertEquals(10, IptvPalettes.all.size)
        assertEquals(10, IptvPalettes.all.map { it.name }.distinct().size)
        assertTrue(IptvPalettes.all.all { it.wallpaper.isNotBlank() && it.accent != it.background })
        assertEquals("Ocean Glass", IptvPalettes.byName("unknown").name)
    }

    @Test fun m3uImportsGroupsAndLogos() {
        val source = """
            #EXTM3U
            #EXTINF:-1 tvg-id="news.01" tvg-logo="https://images.example/logo.png" group-title="News",News One
            https://stream.example/one.m3u8
            #EXTINF:-1 group-title="Sports",Sport Two
            /live/two.ts
            #EXTINF:-1,Unsafe
            javascript:alert(1)
        """.trimIndent()
        val channels = IptvCatalog.parseM3u(source, "https://stream.example/guide.m3u")
        assertEquals(2, channels.size)
        assertEquals("News One", channels[0].name)
        assertEquals("News", channels[0].group)
        assertEquals("news.01", channels[0].tvgId)
        assertEquals("https://images.example/logo.png", channels[0].logo)
        assertEquals("https://stream.example/live/two.ts", channels[1].url)
        assertEquals("Sports", channels[1].group)
    }

    @Test fun unsupportedSchemesAndBadUrlsAreRejected() {
        assertNull(IptvCatalog.resolveStream("", "file:///etc/passwd"))
        assertNull(IptvCatalog.resolveStream("", "javascript:alert(1)"))
        assertNull(IptvCatalog.resolveStream("", "http:/broken"))
        assertNotNull(IptvCatalog.resolveStream("", "rtsp://camera.example/live"))
    }

    @Test fun emptyPlaylistAndInvalidThemeAreSafe() {
        assertTrue(IptvCatalog.parseM3u("#EXTM3U\n").isEmpty())
        assertEquals("Ocean Glass", IptvPalettes.byName("").name)
    }
}
