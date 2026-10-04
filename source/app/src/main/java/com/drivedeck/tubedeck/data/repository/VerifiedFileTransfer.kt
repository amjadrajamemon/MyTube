package com.drivedeck.tubedeck.data.repository

import java.io.InputStream
import java.io.OutputStream

internal object VerifiedFileTransfer {
    fun copyThenCommit(
        openSource: () -> InputStream,
        openDestination: () -> OutputStream,
        expectedBytes: Long,
        destinationSize: () -> Long,
        move: Boolean,
        deleteSource: () -> Boolean,
        removeDestination: () -> Unit,
        checkActive: () -> Unit = {}
    ): Long {
        try {
            var copied = 0L
            openSource().use { input ->
                openDestination().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        checkActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                    }
                    output.flush()
                }
            }
            check(copied > 0L && (expectedBytes < 0 || copied == expectedBytes)) { "Incomplete file copy" }
            val size = destinationSize()
            check(size <= 0 || size == copied) { "Destination file is incomplete" }
            checkActive()
            if (move) check(deleteSource()) { "Source could not be deleted; file was not moved" }
            return copied
        } catch (error: Throwable) {
            runCatching(removeDestination)
            throw error
        }
    }
}
