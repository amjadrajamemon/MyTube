package com.drivedeck.tubedeck.data.repository

import com.drivedeck.tubedeck.data.model.OfflineMedia
import java.util.Locale

/** Pure vault presentation rules; kept free of Android dependencies for unit tests. */
internal object OfflineVaultPolicy {
    const val MIN_FREE_BYTES = 192L * 1024L * 1024L

    fun select(
        items: List<OfflineMedia>,
        query: String,
        category: String,
        order: String,
        missingIds: Set<String> = emptySet()
    ): List<OfflineMedia> {
        val search = query.trim()
        val filtered = items.filter { media ->
            val categoryMatch = when (category) {
                "Videos" -> !media.isAudioOnly
                "Audio" -> media.isAudioOnly
                "Pinned" -> media.isPinned
                "Missing" -> media.id in missingIds
                else -> true
            }
            categoryMatch && (
                search.isEmpty() ||
                    media.title.contains(search, ignoreCase = true) ||
                    media.collection.contains(search, ignoreCase = true) ||
                    media.qualityLabel.contains(search, ignoreCase = true)
                )
        }
        val sorted = when (order) {
            "Title" -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            "Largest" -> filtered.sortedByDescending { it.fileSizeBytes }
            "Duration" -> filtered.sortedByDescending { it.durationSeconds }
            "Oldest" -> filtered.sortedBy { it.downloadDate }
            else -> filtered.sortedByDescending { it.downloadDate }
        }
        return sorted.sortedWith(compareByDescending<OfflineMedia> { it.isPinned })
    }

    fun readableSize(bytes: Long): String {
        if (bytes < 0) return "Unknown"
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var i = -1
        do { value /= 1024.0; i++ } while (value >= 1024.0 && i < units.lastIndex)
        return String.format(Locale.US, "%.1f %s", value, units[i])
    }

    /** Only direct, unprotected progressive URLs deliberately enabled by the user. */
    fun supportsAuthorizedCache(url: String, enabled: Boolean): Boolean {
        if (!enabled) return false
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() !in listOf("http", "https")) return false
        val host = uri.host?.lowercase().orEmpty()
        if (host.isBlank() || host.endsWith("youtube.com") || host == "youtu.be" ||
            host.endsWith(".youtube.com") || host.endsWith(".googlevideo.com") ||
            host == "googlevideo.com") return false
        val path = uri.path?.lowercase().orEmpty()
        return path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".m4v")
    }

    fun shouldSaveFreeSpace(freeBytes: Long, requiredBytes: Long): Boolean =
        freeBytes >= MIN_FREE_BYTES && requiredBytes >= 0L &&
            freeBytes - MIN_FREE_BYTES >= requiredBytes
}
