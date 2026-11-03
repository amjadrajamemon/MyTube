package com.drivedeck.tubedeck.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.drivedeck.tubedeck.data.model.DownloadMode
import com.drivedeck.tubedeck.data.model.DownloadTask
import com.drivedeck.tubedeck.data.model.OfflineMedia
import com.drivedeck.tubedeck.data.model.TubeDeckSettings
import com.drivedeck.tubedeck.player.YoutubeStreamResolver
import com.drivedeck.tubedeck.player.YtDlpUpdateManager
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * Offline downloader.
 *
 * Adaptive YouTube tracks are downloaded and merged by the bundled Download Engine + FFmpeg.
 * Android MediaExtractor/MediaMuxer is intentionally not used here: several valid YouTube
 * fragmented MP4/WebM tracks cannot be opened reliably by OEM MediaExtractor implementations.
 */
internal object MediaDownloader {
    data class Progress(
        val bytes: Long,
        val total: Long,
        val speed: String,
        val title: String,
        val quality: String,
        val format: String
    )

    private fun processId(id: String) = "mytube-download-$id"

    fun folder(context: Context, name: String): File {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        val safeName = name.trim().takeIf { it.matches(Regex("[a-zA-Z0-9 _-]{1,40}")) } ?: "MyTube"
        return File(root, safeName).apply {
            if (!exists() && !mkdirs()) error("Cannot create download folder")
        }
    }

    suspend fun download(
        context: Context,
        task: DownloadTask,
        settings: TubeDeckSettings,
        onProgress: (Progress) -> Unit
    ): OfflineMedia = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        // Check the actual Storage Access Framework destination before expensive
        // extraction, downloading or muxing. A saved URI may be revoked or a USB
        // folder may have disappeared after the user selected it.
        val treeUri = settings.downloadTreeUri?.takeIf { it.isNotBlank() }
            ?: error("Choose a download folder in Settings first")
        val chosenTree = runCatching {
            DocumentFile.fromTreeUri(app, Uri.parse(treeUri))
        }.getOrNull()
        require(chosenTree?.isDirectory == true && chosenTree.canWrite()) {
            "Chosen download folder is unavailable or no longer writable; select it again in Settings"
        }
        val resolved = YoutubeStreamResolver.resolveForDownload(app, task.url)
        require(resolved.durationSeconds > 0) {
            "Live or unbounded streams are not supported for offline downloads"
        }

        val ceiling = videoCeiling(task.quality)
        val quality = if (task.mode == DownloadMode.VIDEO) {
            val option = resolved.downloadOptions
                .filter { ceiling == Int.MAX_VALUE || it.height <= ceiling }
                .maxByOrNull { it.height }
                ?: resolved.downloadOptions.minByOrNull { it.height }
            option?.label ?: task.quality
        } else {
            val option = resolved.audioOptions.firstOrNull { it.mimeType == "audio/mp4" }
                ?: resolved.audioOptions.firstOrNull()
                ?: error("This video has no downloadable audio stream")
            if (option.bitrate > 0) "${option.bitrate} kbps" else "Best available"
        }

        val title = resolved.title.ifBlank { task.title }
        val safeTitle = title
            .replace(Regex("[^a-zA-Z0-9 _.-]"), "_")
            .trim('.', ' ')
            .take(72)
            .ifBlank { "media" }

        val dir = folder(app, settings.downloadDir)
        val outputTemplate = File(dir, "${task.id}.%(ext)s").absolutePath
        val formatHint = if (task.mode == DownloadMode.VIDEO) "MP4" else "M4A/WebM"

        val request = YoutubeDLRequest(task.url)
            .addOption("--no-playlist")
            .addOption("--no-mtime")
            .addOption("--newline")
            .addOption("--retries", "5")
            .addOption("--fragment-retries", "5")
            .addOption("--socket-timeout", "30")
            .addOption("-o", outputTemplate)

        if (task.mode == DownloadMode.VIDEO) {
            request
                .addOption("-f", videoFormatSelector(ceiling))
                .addOption("--merge-output-format", "mp4")
        } else {
            // Prefer an already-compatible M4A track; retain WebM/Opus when that is the only
            // available audio rather than needlessly transcoding it.
            request.addOption("-f", "bestaudio[ext=m4a]/bestaudio")
        }

        YtDlpUpdateManager.ensureInitialized(app)
        onProgress(Progress(0L, 0L, "Starting…", title, quality, formatHint))

        val executeOnce: () -> Unit = {
            YoutubeDL.execute(
                request,
                processId(task.id)
            ) { progress, etaSeconds, line ->
                val currentBytes = taskTempFiles(dir, task.id).sumOf { it.length().coerceAtLeast(0L) }
                val bounded = progress.coerceIn(0f, 100f)
                val estimatedTotal = if (bounded > 0.5f && currentBytes > 0L) {
                    (currentBytes * 100.0 / bounded).toLong().coerceAtLeast(currentBytes)
                } else {
                    0L
                }
                val reportedSpeed = Regex("""at\s+(\S+/s)""")
                    .find(line)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?: if (etaSeconds >= 0L) {
                        "${bounded.roundToInt()}% · ETA ${etaSeconds}s"
                    } else {
                        "${bounded.roundToInt()}%"
                    }

                onProgress(
                    Progress(
                        bytes = currentBytes,
                        total = estimatedTotal,
                        speed = reportedSpeed,
                        title = title,
                        quality = quality,
                        format = formatHint
                    )
                )
            }
        }

        try {
            executeOnce()
        } catch (first: Throwable) {
            currentCoroutineContext().ensureActive()
            // If YouTube changed its extractor/player scripts, refresh yt-dlp once before the
            // repository's longer retry loop gets involved.
            YtDlpUpdateManager.updateNow(app).getOrThrow()
            executeOnce()
        }

        currentCoroutineContext().ensureActive()

        val completedStage = findCompletedOutput(dir, task.id)
            ?: error("Download Engine completed without producing a media file")
        require(completedStage.length() > 0L) { "Downloaded media file is empty" }

        val extension = completedStage.extension.lowercase().ifBlank {
            if (task.mode == DownloadMode.AUDIO) "m4a" else "mp4"
        }
        val format = formatLabel(extension, task.mode == DownloadMode.AUDIO)
        val targetName = "${safeTitle}_${task.id}.$extension"
        val finalSize = completedStage.length()

        onProgress(
            Progress(
                bytes = finalSize,
                total = finalSize,
                speed = "Saving…",
                title = title,
                quality = quality,
                format = format
            )
        )

        val mediaUri = copyToChosenFolder(
            context = app,
            treeUri = treeUri,
            name = targetName,
            extension = extension,
            isAudioOnly = task.mode == DownloadMode.AUDIO,
            source = completedStage
        )

        cleanupTaskFiles(dir, task.id)

        OfflineMedia(
            id = task.id,
            title = title,
            videoId = task.videoId,
            thumbnailUrl = "https://i.ytimg.com/vi/${task.videoId}/hqdefault.jpg",
            mediaUri = mediaUri,
            isAudioOnly = task.mode == DownloadMode.AUDIO,
            qualityLabel = quality,
            format = format,
            fileSizeBytes = finalSize,
            durationSeconds = resolved.durationSeconds
        )
    }

    fun cancel(id: String) {
        runCatching { YoutubeDL.destroyProcessById(processId(id)) }
    }

    fun deletePartials(context: Context, id: String) {
        cancel(id)
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        root.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            cleanupTaskFiles(dir, id)
        }
    }

    fun deleteOutput(context: Context, media: OfflineMedia): Boolean {
        val uri = Uri.parse(media.mediaUri)
        if (uri.scheme == "content") {
            return runCatching {
                val document = DocumentFile.fromSingleUri(context, uri) ?: return@runCatching false
                !document.exists() || document.delete()
            }.getOrDefault(false)
        }
        if (uri.scheme != "file") return false
        val file = File(uri.path ?: return false).canonicalFile
        val root = (context.getExternalFilesDir(null) ?: context.filesDir).canonicalFile
        if (!file.path.startsWith(root.path + File.separator)) return false
        return !file.exists() || file.delete()
    }

    private fun videoCeiling(quality: String): Int = when (quality) {
        "8K" -> 4320
        "4K" -> 2160
        "Best" -> Int.MAX_VALUE
        else -> quality.removeSuffix("p").toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun videoFormatSelector(ceiling: Int): String =
        if (ceiling == Int.MAX_VALUE) {
            "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
        } else {
            "bestvideo[height<=$ceiling][ext=mp4]+bestaudio[ext=m4a]/" +
                "best[height<=$ceiling][ext=mp4]/best[height<=$ceiling]"
        }

    private fun taskTempFiles(dir: File, id: String): List<File> =
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("$id.") }
            .orEmpty()

    private fun findCompletedOutput(dir: File, id: String): File? {
        val supported = setOf("mp4", "m4a", "webm", "mkv", "opus", "mp3")
        return taskTempFiles(dir, id)
            .filter { file ->
                val suffix = file.name.removePrefix("$id.")
                !suffix.contains(".") &&
                    file.extension.lowercase() in supported &&
                    file.length() > 0L
            }
            .maxByOrNull { it.lastModified() }
    }

    private fun cleanupTaskFiles(dir: File, id: String) {
        taskTempFiles(dir, id).forEach { runCatching { it.delete() } }
    }

    private fun formatLabel(extension: String, audioOnly: Boolean): String = when (extension) {
        "mp4" -> if (audioOnly) "M4A" else "MP4"
        "m4a" -> "M4A"
        "webm" -> "WebM"
        "mkv" -> "MKV"
        "opus" -> "Opus"
        "mp3" -> "MP3"
        else -> extension.uppercase().ifBlank { if (audioOnly) "Audio" else "Video" }
    }

    private suspend fun copyToChosenFolder(
        context: Context,
        treeUri: String,
        name: String,
        extension: String,
        isAudioOnly: Boolean,
        source: File
    ): String {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
            ?: error("Chosen download folder is unavailable; select it again in Settings")
        require(tree.isDirectory && tree.canWrite()) {
            "Chosen download folder is no longer writable; select it again in Settings"
        }

        val mime = when (extension) {
            "mp4" -> if (isAudioOnly) "audio/mp4" else "video/mp4"
            "m4a" -> "audio/mp4"
            "webm" -> if (isAudioOnly) "audio/webm" else "video/webm"
            "mkv" -> "video/x-matroska"
            "opus" -> "audio/opus"
            "mp3" -> "audio/mpeg"
            else -> "application/octet-stream"
        }

        // Never destroy the previous good file before the replacement is proven complete.
        // Some SAF/USB providers can disappear during a long write, so stage beside the target,
        // verify byte count, and only then commit/rename.
        val existing = tree.findFile(name)
        val stagedName = if (existing == null) name else "${name}.mytube-part-${System.currentTimeMillis()}"
        val document = tree.createFile(mime, stagedName)
            ?: error("Could not create file in chosen folder")

        try {
            var copied = 0L
            val output = context.contentResolver.openOutputStream(document.uri, "w")
                ?: error("Could not open chosen folder for writing")
            output.use { target ->
                source.inputStream().use { input ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        target.write(buffer, 0, count)
                        copied += count
                    }
                    target.flush()
                }
            }
            require(copied == source.length() && copied > 0L) { "Incomplete file copy" }
            val reportedSize = document.length()
            require(reportedSize <= 0L || reportedSize == copied) { "Destination file is incomplete" }

            if (existing != null) {
                require(existing.delete()) { "Could not replace previous output in chosen folder" }
                if (document.renameTo(name)) {
                    return (tree.findFile(name)?.uri ?: document.uri).toString()
                }
                // Rename is optional on some document providers. The staged file is already a
                // verified complete replacement, so keep it rather than deleting valid media.
            }
            return document.uri.toString()
        } catch (error: Exception) {
            document.delete()
            throw error
        }
    }
}
