package com.drivedeck.tubedeck.data.repository

internal object DownloadRetryPolicy {
    const val MAX_AUTO_RETRIES = 7

    fun shouldAutoRetry(retryNumber: Int): Boolean =
        retryNumber in 1..MAX_AUTO_RETRIES

    fun delaySeconds(retryNumber: Int): Long = when {
        retryNumber <= 1 -> 3L
        retryNumber == 2 -> 5L
        retryNumber == 3 -> 10L
        retryNumber == 4 -> 20L
        retryNumber == 5 -> 30L
        else -> 60L
    }
}
