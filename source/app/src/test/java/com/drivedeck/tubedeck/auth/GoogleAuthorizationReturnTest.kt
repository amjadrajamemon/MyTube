package com.drivedeck.tubedeck.auth

import org.junit.Assert.*
import org.junit.Test

class GoogleAuthorizationReturnTest {
    @Test fun canceledAndroidCodeStillParsesProviderData() {
        assertEquals("verified", parseGoogleAuthorizationReturn("google-result", 0) { "verified" })
    }

    @Test fun providerFailureIsPreservedForCanceledAndroidReturn() {
        val providerError = IllegalStateException("Google status 10")
        val result = runCatching {
            parseGoogleAuthorizationReturn("google-error", 0) { throw providerError }
        }
        assertSame(providerError, result.exceptionOrNull())
    }

    @Test fun absentResultIsReportedWithoutClaimingTheUserCanceled() {
        val result = runCatching {
            parseGoogleAuthorizationReturn<String, String>(null, 0) { it }
        }
        assertEquals(
            "Google authorization closed without a result (Android code 0). Retry sign-in.",
            result.exceptionOrNull()?.message
        )
    }
}
