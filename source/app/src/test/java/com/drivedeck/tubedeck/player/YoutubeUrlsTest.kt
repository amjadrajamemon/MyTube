package com.drivedeck.tubedeck.player

import org.junit.Assert.*
import org.junit.Test

class YoutubeUrlsTest {
    @Test fun acceptsCanonicalVideoLinks() {
        assertEquals("dQw4w9WgXcQ", YoutubeUrls.videoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10"))
        assertEquals("dQw4w9WgXcQ", YoutubeUrls.videoId("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YoutubeUrls.videoId("https://m.youtube.com/shorts/dQw4w9WgXcQ"))
    }

    @Test fun rejectsSpoofedHostsAndMalformedIds() {
        assertNull(YoutubeUrls.videoId("https://example.com/watch?v=dQw4w9WgXcQ"))
        assertNull(YoutubeUrls.videoId("https://youtube.com.evil.test/watch?v=dQw4w9WgXcQ"))
        assertNull(YoutubeUrls.videoId("https://www.youtube.com/watch?v=too-short"))
        assertNull(YoutubeUrls.videoId("http://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertNull(YoutubeUrls.videoId("javascript:alert(1)"))
    }

    @Test fun validatesRealPlaylistLinks() {
        assertTrue(YoutubeUrls.playlist("https://www.youtube.com/playlist?list=PL123"))
        assertTrue(YoutubeUrls.playlist("https://m.youtube.com/watch?v=dQw4w9WgXcQ&list=PL123"))
        assertFalse(YoutubeUrls.playlist("http://www.youtube.com/playlist?list=PL123"))
        assertFalse(YoutubeUrls.playlist("https://example.com/playlist?list=PL123"))
    }
}
