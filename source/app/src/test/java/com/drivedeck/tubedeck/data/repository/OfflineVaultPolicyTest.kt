package com.drivedeck.tubedeck.data.repository

import com.drivedeck.tubedeck.data.model.OfflineMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineVaultPolicyTest {
    private fun video(
        id: String, title: String, size: Long, pinned: Boolean = false,
        collection: String = "", audio: Boolean = false
    ) = OfflineMedia(
        id = id, title = title, videoId = id, thumbnailUrl = "",
        mediaUri = "file:///test/$id.mp4", fileSizeBytes = size,
        durationSeconds = size, isPinned = pinned, collection = collection,
        isAudioOnly = audio, downloadDate = size
    )

    @Test fun vaultSearchSortAndPinnedFirst() {
        val items = listOf(
            video("x", "Sky", 200),
            video("y", "Arctic", 900, pinned = true, collection = "Travel"),
            video("z", "Beach", 500, collection = "Travel")
        )
        assertEquals(listOf("y", "z", "x"),
            OfflineVaultPolicy.select(items, "", "All", "Largest").map { it.id })
        assertEquals(listOf("y", "z"),
            OfflineVaultPolicy.select(items, "travel", "All", "Recent").map { it.id })
        assertEquals(listOf("y"),
            OfflineVaultPolicy.select(items, "", "Pinned", "Recent").map { it.id })
        assertEquals(listOf("x"),
            OfflineVaultPolicy.select(items, "", "Missing", "Recent", setOf("x")).map { it.id })
    }

    @Test fun audioAndVideoFilters() {
        val items = listOf(video("a", "Song", 10, audio = true), video("v", "Film", 100))
        assertEquals(1, OfflineVaultPolicy.select(items, "", "Audio", "Recent").size)
        assertEquals("v", OfflineVaultPolicy.select(items, "", "Videos", "Recent").single().id)
    }

    @Test fun preventCachingYouTubeAndAdaptiveManifests() {
        assertFalse(OfflineVaultPolicy.supportsAuthorizedCache("https://www.youtube.com/watch?v=Ab", true))
        assertFalse(OfflineVaultPolicy.supportsAuthorizedCache("https://rr.googlevideo.com/videoplayback.mp4", true))
        assertFalse(OfflineVaultPolicy.supportsAuthorizedCache("https://me.test/movie.m3u8", true))
        assertFalse(OfflineVaultPolicy.supportsAuthorizedCache("https://me.test/movie.mpd", true))
        assertFalse(OfflineVaultPolicy.supportsAuthorizedCache("https://me.test/movie.mp4", false))
        assertTrue(OfflineVaultPolicy.supportsAuthorizedCache("https://me.test/movie.mp4?auth=abc", true))
        assertTrue(OfflineVaultPolicy.supportsAuthorizedCache("https://me.test/movie.webm", true))
    }

    @Test fun noTwoGigQuotaOnlySpaceReserve() {
        val gb = 1024L * 1024L * 1024L
        assertTrue(OfflineVaultPolicy.shouldSaveFreeSpace(40L * gb, 35L * gb))
        assertFalse(OfflineVaultPolicy.shouldSaveFreeSpace(40L * gb, 41L * gb))
        assertFalse(OfflineVaultPolicy.shouldSaveFreeSpace(0L, 0L))
        assertEquals("Unknown", OfflineVaultPolicy.readableSize(-1L))
        assertEquals("2.0 GB", OfflineVaultPolicy.readableSize(2L * gb))
    }
}
