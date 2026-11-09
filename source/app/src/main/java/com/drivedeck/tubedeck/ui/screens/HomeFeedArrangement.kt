package com.drivedeck.tubedeck.ui.screens

/**
 * Strictly normalize user-defined Home rows without losing an existing feed.
 * Unknown/duplicate names cannot result in missing or blank columns.
 */
object HomeFeedArrangement {
    private val defaultOrder = listOf("History", "Relevant", "Trending")

    fun normalize(raw: String): List<String> {
        val valid = raw.split(',').map(String::trim)
            .filter { it in defaultOrder }.distinct()
        return (valid + defaultOrder.filterNot(valid::contains)).take(defaultOrder.size)
    }

    /** Omit disabled shelves completely: no empty headings or focus targets. */
    fun visible(raw: String, history: Boolean, relevant: Boolean, trending: Boolean): List<String> =
        normalize(raw).filter { feed ->
            when (feed) {
                "History" -> history
                "Relevant" -> relevant
                "Trending" -> trending
                else -> false
            }
        }

    fun move(raw: String, name: String, direction: Int): String {
        val result = normalize(raw).toMutableList()
        val from = result.indexOf(name)
        val to = from + direction.coerceIn(-1, 1)
        if (from >= 0 && to in result.indices) {
            val removed = result.removeAt(from)
            result.add(to, removed)
        }
        return result.joinToString(",")
    }
}
