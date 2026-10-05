package com.drivedeck.tubedeck.data.repository

import android.content.Context
import android.util.AtomicFile
import java.io.File

/**
 * Small crash-safe store for larger JSON state.
 *
 * SharedPreferences is retained for scalar settings only. Queue/history/offline catalogs are
 * written through AtomicFile so process death or storage interruption cannot leave half-written
 * JSON that makes the whole catalog disappear on the next launch.
 */
internal class AtomicJsonStore(context: Context) {
    private val root = File(context.filesDir, "state").apply { mkdirs() }

    fun read(name: String): String? {
        val file = AtomicFile(File(root, name))
        return runCatching {
            file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
                .takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun write(name: String, text: String) {
        val file = AtomicFile(File(root, name))
        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(text.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (error: Throwable) {
            stream?.let(file::failWrite)
            throw error
        }
    }

    fun delete(name: String) {
        runCatching { AtomicFile(File(root, name)).delete() }
    }
}
