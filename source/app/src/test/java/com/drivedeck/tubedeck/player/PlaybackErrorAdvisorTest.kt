package com.drivedeck.tubedeck.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackErrorAdvisorTest {
    @Test fun networkTimeoutHasRecoverableMessage() {
        val advice = PlaybackErrorAdvisor.analyze("UnknownHostException: DNS timeout")
        assertEquals(PlaybackErrorAdvisor.Cause.NETWORK, advice.cause)
        assertTrue(advice.suggestion.contains("Retry"))
    }
    @Test fun expiredStreamDoesNotRecommendEndlessRetry() {
        assertEquals(PlaybackErrorAdvisor.Cause.EXPIRED_STREAM,
            PlaybackErrorAdvisor.analyze("Media3: HTTP 403 signature expired").cause)
    }
    @Test fun codecAndRestrictionsAreDistinct() {
        assertEquals(PlaybackErrorAdvisor.Cause.DECODER,
            PlaybackErrorAdvisor.analyze("MediaCodec decoder failed").cause)
        assertEquals(PlaybackErrorAdvisor.Cause.RESTRICTED,
            PlaybackErrorAdvisor.analyze("Private video").cause)
    }
    @Test fun rateLimitsWarnAgainstRetryStorms() {
        assertEquals(PlaybackErrorAdvisor.Cause.RATE_LIMIT,
            PlaybackErrorAdvisor.analyze("HTTP 429").cause)
    }
}
