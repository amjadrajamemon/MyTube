package com.drivedeck.tubedeck.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.drivedeck.tubedeck.data.model.OfflineMedia
import com.drivedeck.tubedeck.data.model.OfflineVaultHealth
import java.io.File

/** Checks real storage rather than trusting a historical database entry. */
internal object OfflineVaultStorage {
    fun isReadable(context: Context, media: OfflineMedia): Boolean = runCatching {
        val uri = Uri.parse(media.mediaUri)
        when (uri.scheme) {
            "file" -> uri.path?.let { path ->
                val f = File(path)
                f.isFile && f.canRead() && f.length() > 0L
            } == true
            "content" -> {
                // SAF documents can be on a disconnected USB drive. Open the actual
                // descriptor rather than relying on stale persisted display metadata.
                context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    descriptor.statSize != 0L
                } == true
            }
            else -> false
        }
    }.getOrDefault(false)

    fun inspect(context: Context, items: List<OfflineMedia>, folderName: String): OfflineVaultHealth {
        val missing = items.filterNot { isReadable(context, it) }.mapTo(mutableSetOf()) { it.id }
        val valid = items.filterNot { it.id in missing }
        val root = MediaDownloader.folder(context, folderName)
        return OfflineVaultHealth(
            readyCount = valid.size,
            missingIds = missing,
            savedBytes = valid.sumOf { it.fileSizeBytes.coerceAtLeast(0L) },
            freeBytes = root.usableSpace,
            storageLocation = root.absolutePath
        )
    }
}
