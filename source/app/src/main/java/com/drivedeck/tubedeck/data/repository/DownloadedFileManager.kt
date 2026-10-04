package com.drivedeck.tubedeck.data.repository

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.drivedeck.tubedeck.data.model.OfflineMedia
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.util.UUID

internal object DownloadedFileManager {
    suspend fun transfer(context: Context, media: OfflineMedia, tree: Uri, move: Boolean): OfflineMedia {
        val destination = DocumentFile.fromTreeUri(context, tree) ?: error("Choose a writable folder")
        check(destination.isDirectory && destination.canWrite()) { "Folder is not writable" }
        val source = Uri.parse(media.mediaUri)
        val sourceFile = source.path?.let(::File)?.takeIf { source.scheme == "file" }
        val sourceDocument = if (source.scheme == "content") DocumentFile.fromSingleUri(context, source) else null
        val originalName = sourceFile?.name ?: sourceDocument?.name ?: "${media.title}.${media.format.ifBlank { "mp4" }.lowercase()}"
        val safeName = originalName.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(180).ifBlank { "video.mp4" }
        val extension = safeName.substringAfterLast('.', "")
        val base = safeName.substringBeforeLast('.', safeName)
        var name = safeName
        var index = 1
        while (destination.findFile(name) != null) {
            name = "$base (${index++})" + if (extension.isNotBlank()) ".$extension" else ""
        }
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            ?: if (media.isAudioOnly) "audio/mpeg" else "video/mp4"
        val expected = sourceFile?.length() ?: context.contentResolver.openFileDescriptor(source, "r")?.use { it.statSize } ?: -1L
        val target = destination.createFile(mime, name) ?: error("Cannot create destination file")
        val coroutine = currentCoroutineContext()
        val bytes = VerifiedFileTransfer.copyThenCommit(
            openSource = { sourceFile?.inputStream() ?: context.contentResolver.openInputStream(source) ?: error("Cannot open source file") },
            openDestination = { context.contentResolver.openOutputStream(target.uri, "w") ?: error("Cannot write destination file") },
            expectedBytes = expected,
            destinationSize = { context.contentResolver.openFileDescriptor(target.uri, "r")?.use { it.statSize } ?: -1L },
            move = move,
            deleteSource = { MediaDownloader.deleteOutput(context, media) },
            removeDestination = { target.delete() },
            checkActive = { coroutine.ensureActive() }
        )
        return media.copy(id = if (move) media.id else UUID.randomUUID().toString(), mediaUri = target.uri.toString(), fileSizeBytes = bytes)
    }
}
