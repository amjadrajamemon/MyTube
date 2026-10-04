package com.drivedeck.tubedeck.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRetryPolicyTest {
    @Test fun automaticRetriesStopAfterSeven() {
        (1..DownloadRetryPolicy.MAX_AUTO_RETRIES).forEach {
            assertTrue(DownloadRetryPolicy.shouldAutoRetry(it))
        }
        assertFalse(
            DownloadRetryPolicy.shouldAutoRetry(
                DownloadRetryPolicy.MAX_AUTO_RETRIES + 1
            )
        )
    }
}
