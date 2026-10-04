package com.drivedeck.tubedeck.data.repository

import java.util.Locale

internal object HomeFeedPolicy {
    const val CACHE_MS = 30 * 60 * 1000L
    fun country(network: String?, sim: String?, locale: String?): String =
        listOf(network, sim, locale).mapNotNull { value ->
            value?.uppercase(Locale.ROOT)?.takeIf { it in Locale.getISOCountries() }
        }.firstOrNull() ?: "US"

    fun shouldRefresh(enabled: Boolean, now: Long, lastAttempt: Long, force: Boolean): Boolean =
        enabled && (force || lastAttempt == 0L || now - lastAttempt >= CACHE_MS)
}
