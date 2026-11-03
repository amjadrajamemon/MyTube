package com.drivedeck.tubedeck.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreatorLinksPolicyTest {
    @Test fun extractsGenuineCreatorSuppliedVideoAndPlaylistLinks() {
        val links = CreatorLinksPolicy.extract(
            "Next watch https://youtu.be/AbC_defG123 and list " +
                "https://www.youtube.com/playlist?list=PL0123 and " +
                "https://www.youtube.com/@MyChannel"
        )
        assertEquals(3, links.size)
        assertEquals(CreatorLinksPolicy.Kind.VIDEO, links[0].kind)
        assertEquals("AbC_defG123", links[0].videoId)
        assertEquals(CreatorLinksPolicy.Kind.PLAYLIST, links[1].kind)
        assertEquals(CreatorLinksPolicy.Kind.CHANNEL, links[2].kind)
    }
    @Test fun rejectsUntrustedOrNonHttpsLinksAndDeduplicates() {
        val links = CreatorLinksPolicy.extract(
            "http://youtu.be/AbC_defG123 " +
                "https://youtube.com.evil.org/watch?v=AbC_defG123 " +
                "https://www.youtube.com/watch?v=AbC_defG123 " +
                "https://youtu.be/AbC_defG123"
        )
        assertEquals(1, links.size)
        assertEquals("https://www.youtube.com/watch?v=AbC_defG123", links[0].url)
        assertFalse(links.any { it.url.contains("evil") })
    }
    @Test fun outputIsStrictlyBounded() {
        val many = (1..30).joinToString(" ") { i ->
            "https://youtu.be/" + i.toString().padStart(11, 'A')
        }
        assertTrue(CreatorLinksPolicy.extract(many, 5).size <= 5)
    }
}
