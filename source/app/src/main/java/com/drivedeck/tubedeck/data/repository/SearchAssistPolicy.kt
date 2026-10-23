package com.drivedeck.tubedeck.data.repository

import java.util.Locale

internal object SearchAssistPolicy {
    fun clean(query: String): String = query.trim().replace(Regex("\\s+"), " ").take(120)

    fun record(query: String, recent: List<String>): List<String> {
        val clean = clean(query)
        if (clean.isBlank() || isUrl(clean)) return recent
        return (listOf(clean) + recent.filterNot { it.equals(clean, ignoreCase = true) }).take(20)
    }

    fun isUrl(query: String): Boolean = query.contains("://") || query.startsWith("www.", true)

    fun matches(query: String, recent: List<String>): List<String> {
        val clean = clean(query)
        return recent.filter { clean.isBlank() || it.contains(clean, ignoreCase = true) }.take(6)
    }

    fun merge(query: String, recent: List<String>, remote: List<String>): List<String> =
        (matches(query, recent) + remote.map(::clean).filter { it.isNotBlank() && !isUrl(it) })
            .distinctBy { it.lowercase(Locale.ROOT) }.take(8)
}
