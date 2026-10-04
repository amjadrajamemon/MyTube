package com.drivedeck.tubedeck.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeSearchPolicyTest {
    @Test fun aryNewsLiveUsesLiveFeatureFilter() {
        val url = YoutubeSearchPolicy.buildSearchUrl("ARY News live")
        assertTrue(url.contains("search_query=ARY+News+live"))
        assertTrue(url.contains("sp=EgJAAQ%253D%253D"))
    }

    @Test fun geoNewsLiveStreamingUsesLiveFeatureFilter() {
        val url = YoutubeSearchPolicy.buildSearchUrl("Geo News live streaming")
        assertTrue(url.contains("sp=EgJAAQ%253D%253D"))
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
