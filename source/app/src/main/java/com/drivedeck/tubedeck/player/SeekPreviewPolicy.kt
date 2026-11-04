package com.drivedeck.tubedeck.player

/** Fast pure mapping between a seek position and a sprite-sheet thumbnail frame. */
internal object SeekPreviewPolicy {
    data class Frame(val page: Int, val row: Int, val column: Int)

    fun frame(
        positionMs: Long,
        frameDurationMs: Int,
        totalFrames: Int,
        columns: Int,
        rows: Int,
        pageCount: Int
    ): Frame? {
        if (frameDurationMs <= 0 || totalFrames <= 0 ||
            columns <= 0 || rows <= 0 || pageCount <= 0) return null
        val index = (positionMs.coerceAtLeast(0L) / frameDurationMs)
            .coerceIn(0L, (totalFrames - 1).toLong()).toInt()
        val perPage = columns * rows
        val page = (index / perPage).coerceAtMost(pageCount - 1)
        val relative = index % perPage
        return Frame(page, relative / columns, relative % columns)
    }

    fun fraction(x: Float, width: Float): Float =
        if (width <= 0f) 0f else (x / width).coerceIn(0f, 1f)
}
