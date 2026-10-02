package com.drivedeck.tubedeck.data.repository

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal object DownloadFailureClassifier {
    fun isRecoverable(error: Throwable): Boolean {
        val message = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()

        val permanentMarkers = listOf(
            "live or unbounded streams are not supported",
            "no compatible video download stream",
            "no downloadable audio",
            "this video has no downloadable audio",
            "private video",
            "video is private",
            "video unavailable",
            "has been removed",
            "deleted video",
            "members-only",
            "members only",
            "sign in to confirm your age",
            "chosen download folder is unavailable",
            "chosen download folder is no longer writable",
            "enter a valid youtube",
            "open a valid youtube",
            "no space left on device",
            "enospc",
            "disk quota",
            "quota exceeded",
            "read-only file system",
            "permission denied",
            "eacces",
            "cannot create download folder",
            "could not create file in chosen folder",
            "could not open chosen folder for writing",
            "cannot read the selected video track",
            "cannot read the selected audio track",
            "media sample exceeds supported buffer size",
            "encrypted media samples cannot be muxed offline",
            "only https media streams are supported",
            "unsafe media redirect"
        )
        if (permanentMarkers.any { it in message }) return false

        if (error is UnknownHostException || error is SocketTimeoutException) return true
        if (error is IOException) return true

        // Expired YouTube CDN URLs and transient extractor/HTTP failures are normally recoverable
        // because each retry resolves fresh media URLs.
        return true
    }
}
