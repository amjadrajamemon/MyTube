package com.drivedeck.tubedeck.data.repository

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFailureClassifierTest {
    @Test
    fun networkFailuresRemainRecoverable() {
        assertTrue(DownloadFailureClassifier.isRecoverable(UnknownHostException("offline")))
        assertTrue(DownloadFailureClassifier.isRecoverable(SocketTimeoutException("timeout")))
        assertTrue(DownloadFailureClassifier.isRecoverable(IOException("connection reset")))
    }

    @Test
    fun localStorageExhaustionIsNotRetriedForever() {
        assertFalse(DownloadFailureClassifier.isRecoverable(IOException("No space left on device")))
        assertFalse(DownloadFailureClassifier.isRecoverable(IOException("ENOSPC")))
        assertFalse(DownloadFailureClassifier.isRecoverable(IOException("Permission denied")))
    }

    @Test
    fun knownUnavailableVideosNeedAttention() {
        assertFalse(DownloadFailureClassifier.isRecoverable(IllegalStateException("Private video")))
        assertFalse(DownloadFailureClassifier.isRecoverable(IllegalStateException("Video unavailable")))
    }

    @Test
    fun expiredCdnFailuresCanBeRetriedWithFreshUrls() {
        assertTrue(DownloadFailureClassifier.isRecoverable(IllegalStateException("Media server returned HTTP 403")))
        assertTrue(DownloadFailureClassifier.isRecoverable(IllegalStateException("Media server returned HTTP 429")))
    }
}
