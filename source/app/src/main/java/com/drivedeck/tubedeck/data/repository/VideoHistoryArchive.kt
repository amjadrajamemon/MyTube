package com.drivedeck.tubedeck.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.drivedeck.tubedeck.data.model.HomeVideo

/**
 * Local video history archive with real LIMIT/OFFSET pagination and no fixed
 * lifetime-entry limit. Only the visible window is kept in StateFlow/Compose.
 * SQLite bounds RAM irrespective of how many old videos exist on disk.
 */
internal class VideoHistoryArchive(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "mytube_watch_archive.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS videos(
               video_id TEXT PRIMARY KEY NOT NULL,
               title TEXT NOT NULL,
               url TEXT NOT NULL,
               thumb TEXT NOT NULL,
               channel TEXT NOT NULL,
               duration TEXT NOT NULL,
               views INTEGER NOT NULL,
               watched_at INTEGER NOT NULL
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_recent_watch ON videos(watched_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun upsert(video: HomeVideo) {
        if (video.videoId.isBlank()) return
        writableDatabase.insertWithOnConflict(
            "videos", null,
            ContentValues().apply {
                put("video_id", video.videoId)
                put("title", video.title)
                put("url", video.url)
                put("thumb", video.thumbnailUrl)
                put("channel", video.channelTitle)
                put("duration", video.durationText)
                put("views", video.viewCount)
                put("watched_at", video.watchedAt)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun importLegacyIfEmpty(legacy: List<HomeVideo>) {
        if (legacy.isEmpty() || count() != 0L) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            legacy.forEach { upsert(it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun count(): Long = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM videos", null
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else 0L
    }

    fun page(offset: Int, limit: Int = 50): List<HomeVideo> {
        val size = limit.coerceIn(1, 100)
        val start = offset.coerceAtLeast(0)
        return readableDatabase.query(
            "videos",
            arrayOf("video_id","title","url","thumb","channel","duration","views","watched_at"),
            null, null, null, null,
            "watched_at DESC, video_id ASC",
            "$size OFFSET $start"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(HomeVideo(
                        videoId = cursor.getString(0).orEmpty(),
                        title = cursor.getString(1).orEmpty(),
                        url = cursor.getString(2).orEmpty(),
                        thumbnailUrl = cursor.getString(3).orEmpty(),
                        channelTitle = cursor.getString(4).orEmpty(),
                        durationText = cursor.getString(5).orEmpty(),
                        viewCount = cursor.getLong(6),
                        watchedAt = cursor.getLong(7)
                    ))
                }
            }
        }
    }

    fun clear() {
        writableDatabase.delete("videos", null, null)
    }
}
