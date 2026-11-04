package com.drivedeck.tubedeck.data.repository

import java.io.File
import java.security.MessageDigest

/**
 * Finalize a completed download into app-private storage without a SAF picker.
 * Files remain accessible through MyTube's Downloads screen and can later be
 * copied/moved to a user-selected folder. Keep the previous file on conflicts.
 */
internal object LocalDownloadCommit {
    fun commit(source: File, destination: File): File {
        val expectedBytes = source.length()
        require(source.isFile && expectedBytes > 0L) {
            "Downloaded media is missing or empty"
        }
        require(source.canonicalFile != destination.canonicalFile) {
            "Download staging path must differ from destination"
        }

        if (destination.exists()) {
            check(
                destination.isFile &&
                    destination.length() == expectedBytes &&
                    sha256(source).contentEquals(sha256(destination))
            ) {
                "A different file already exists at the completed download destination"
            }
            // Reuse an identical completed output after process death between
            // saving media and recording it in the offline catalog.
            return destination
        }

        if (!source.renameTo(destination)) {
            // The two paths normally share a filesystem, but some OEM/USB storage
            // providers reject rename. Copy to a new file instead.
            try {
                source.inputStream().use { input ->
                    destination.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 128 * 1024)
                        output.flush()
                    }
                }
                check(destination.length() == expectedBytes) {
                    "Incomplete app-storage file copy"
                }
                source.delete()
            } catch (error: Throwable) {
                destination.delete()
                throw error
            }
        }

        check(destination.length() == expectedBytes) {
            "Incomplete completed download file"
        }
        return destination
    }

    private fun sha256(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest()
    }
}
