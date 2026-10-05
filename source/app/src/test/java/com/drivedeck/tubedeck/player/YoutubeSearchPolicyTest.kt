package com.drivedeck.tubedeck.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeSearchPolicyTest {
    @Test fun aryNewsLiveIsRecognizedWithoutVolatileWebFilter() {
        val query = "ARY News live"
        val url = YoutubeSearchPolicy.buildSearchUrl(query)
        assertTrue(YoutubeSearchPolicy.isLiveIntent(query))
        assertTrue(url.contains("search_query=ARY+News+live"))
        assertFalse(url.contains("sp=EgJAAQ%253D%253D"))
    }

    @Test fun geoNewsLiveStreamingIsRecognizedWithoutVolatileWebFilter() {
        val query = "Geo News live streaming"
        val url = YoutubeSearchPolicy.buildSearchUrl(query)
        assertTrue(YoutubeSearchPolicy.isLiveIntent(query))
        assertFalse(url.contains("sp=EgJAAQ%253D%253D"))
    }

    @Test fun ordinarySearchDoesNotForceLiveFilter() {
        val url = YoutubeSearchPolicy.buildSearchUrl("Geo News headlines")
        assertFalse(url.contains("sp=EgJAAQ%253D%253D"))
    }

    @Test fun playlistModeStillWinsOverLiveIntent() {
        val url = YoutubeSearchPolicy.buildSearchUrl("live playlist", playlistOnly = true)
        assertTrue(url.contains("sp=EgIQAw%253D%253D"))
        assertFalse(url.contains("sp=EgJAAQ%253D%253D"))
    }

    @Test fun liveWordBoundaryDoesNotMatchDelivery() {
        assertFalse(YoutubeSearchPolicy.isLiveIntent("food delivery"))
        assertTrue(YoutubeSearchPolicy.isLiveIntent("news livestream"))
    }
}
