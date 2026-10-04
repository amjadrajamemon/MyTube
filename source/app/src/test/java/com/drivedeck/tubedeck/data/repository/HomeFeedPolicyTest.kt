package com.drivedeck.tubedeck.data.repository

import org.junit.Assert.*
import org.junit.Test

class HomeFeedPolicyTest {
    @Test fun countryUsesCurrentNetworkBeforeSimAndLocale() {
        assertEquals("PK", HomeFeedPolicy.country("pk", "gb", "US"))
        assertEquals("GB", HomeFeedPolicy.country("", "gb", "US"))
        assertEquals("AE", HomeFeedPolicy.country("invalid", "001", "AE"))
        assertEquals("US", HomeFeedPolicy.country(null, null, null))
    }
    @Test fun disabledFeedsNeverRequestEvenOnRefresh() {
        assertFalse(HomeFeedPolicy.shouldRefresh(false, 9000000, 0, true))
        assertTrue(HomeFeedPolicy.shouldRefresh(true, 100, 0, false))
        assertFalse(HomeFeedPolicy.shouldRefresh(true, 1000, 100, false))
        assertTrue(HomeFeedPolicy.shouldRefresh(true, HomeFeedPolicy.CACHE_MS + 100, 100, false))
        assertTrue(HomeFeedPolicy.shouldRefresh(true, 1000, 100, true))
    }
}
