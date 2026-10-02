package com.drivedeck.tubedeck.data.repository

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.drivedeck.tubedeck.data.model.DownloadMode
import com.drivedeck.tubedeck.data.model.DownloadTask
import com.drivedeck.tubedeck.data.model.OfflineMedia
import com.drivedeck.tubedeck.data.model.TubeDeckSettings
import com.drivedeck.tubedeck.player.YoutubeStreamResolver
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/** Downloads complete streams, or combines compatible separate video and audio tracks. */
internal object MediaDownloader {
    data class Progress(
        val bytes: Long,
        val total: Long,
        val speed: String,
        val title: String,
        val quality: String,
        val format: String
    )

    fun folder(context: Context, name: String): File {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        val safeName = name.trim().takeIf { it.matches(Regex("[a-zA-Z0-9 _-]{1,40}")) } ?: "MyTube"
        return File(root, safeName).apply { if (!exists() && !mkdirs()) error("Cannot create download folder") }
    }

    suspend fun download(
        context: Context,
        task: DownloadTask,
        settings: TubeDeckSettings,
        onProgress: (Progress) -> Unit
    ): OfflineMedia = withContext(Dispatchers.IO) {
        val resolved = YoutubeStreamResolver.resolve(context, task.url)
        require(resolved.durationSeconds > 0) { "Live or unbounded streams are not supported for offline downloads" }

        val selectedUrl: String
        val selectedAudioUrl: String?
        val format: String
        val quality: String
        val extension: String
        if (task.mode == DownloadMode.VIDEO) {
            val available = resolved.downloadOptions
            require(available.isNotEmpty()) { "No compatible video download stream is available" }
            val ceiling = when (task.quality) {
                "8K" -> 4320
                "4K" -> 2160
                "Best" -> Int.MAX_VALUE
                else -> task.quality.removeSuffix("p").toIntOrNull() ?: Int.MAX_VALUE
            }
            val option = available.filter { it.height <= ceiling }.maxByOrNull { it.height }
                ?: available.minBy { it.height }
            selectedUrl = option.videoUrl
            selectedAudioUrl = option.audioUrl
            quality = option.label
            format = if (option.videoMimeType == "video/webm") "WebM" else "MP4"
            extension = if (format == "WebM") "webm" else "mp4"
        } else {
            val option = resolved.audioOptions.firstOrNull { it.mimeType == "audio/mp4" }
                ?: resolved.audioOptions.firstOrNull()
                ?: error("This video has no downloadable audio stream")
            selectedUrl = option.url
            selectedAudioUrl = null
            format = if (option.mimeType == "audio/mp4") "M4A" else "WebM"
            extension = if (format == "M4A") "m4a" else "webm"
            quality = if (option.bitrate > 0) "${option.bitrate} kbps" else "Best available"
        }

        val title = resolved.title.ifBlank { task.title }
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9 _.-]"), "_").trim('.', ' ').take(72).ifBlank { "media" }
        val dir = folder(context, settings.downloadDir)
        val partial = File(dir, "${task.id}.video.part")
        val target = File(dir, "${safeTitle}_${task.id}.$extension")
        val audioPartial = File(dir, "${task.id}.audio.part")
        var videoBytes = partial.takeIf { it.exists() }?.length() ?: 0L
        var videoTotal = 0L
        transfer(selectedUrl, partial) { bytes, total, speed ->
            videoBytes = bytes
            videoTotal = total
            onProgress(Progress(bytes, total, speed, title, quality, format))
        }
        val completedStage = if (selectedAudioUrl != null) {
            transfer(selectedAudioUrl, audioPartial) { bytes, total, speed ->
                val combinedTotal = if (videoTotal > 0 && total > 0) videoTotal + total else 0L
                onProgress(Progress(videoBytes + bytes, combinedTotal, speed, title, quality, format))
            }
            val muxed = File(dir, "${task.id}.mux.part")
            onProgress(Progress(videoBytes + audioPartial.length(), 0, "Combining tracks…", title, quality, format))
            muxTracks(partial, audioPartial, muxed, format)
            muxed
        } else partial
        currentCoroutineContext().ensureActive()
        val finalSize = completedStage.length()
        val treeUri = settings.downloadTreeUri
            ?.takeIf { it.isNotBlank() }
            ?: error("Choose a download folder in Settings first")
        val mediaUri = copyToChosenFolder(
            context,
            treeUri,
            target.name,
            extension,
            task.mode == DownloadMode.AUDIO,
            completedStage
        )
        completedStage.delete()
        if (selectedAudioUrl != null) { partial.delete(); audioPartial.delete() }
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

    fun deletePartials(context: Context, id: String) {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        root.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            dir.listFiles()?.filter { it.name.startsWith("$id.") && it.name.endsWith(".part") }
                ?.forEach { it.delete() }
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

    private suspend fun copyToChosenFolder(context: Context, treeUri: String, name: String, extension: String, isAudioOnly: Boolean, source: File): String {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
            ?: error("Chosen download folder is unavailable; select it again in Settings")
        require(tree.isDirectory && tree.canWrite()) { "Chosen download folder is no longer writable; select it again in Settings" }
        val mime = when (extension) {
            "mp4" -> "video/mp4"
            "m4a" -> "audio/mp4"
            "webm" -> if (isAudioOnly) "audio/webm" else "video/webm"
            else -> "application/octet-stream"
        }
        tree.findFile(name)?.let { existing ->
            require(existing.delete()) { "Could not replace previous output in chosen folder" }
        }
        val document = tree.createFile(mime, name) ?: error("Could not create file in chosen folder")
        try {
            val output = context.contentResolver.openOutputStream(document.uri, "w") ?: error("Could not open chosen folder for writing")
            output.use { target ->
                source.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        target.write(buffer, 0, count)
                    }
                }
            }
            return document.uri.toString()
        } catch (error: Exception) {
            document.delete()
            throw error
        }
    }

    private suspend fun muxTracks(video: File, audio: File, output: File, format: String) {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        var finished = false
        try {
            videoExtractor.setDataSource(video.absolutePath)
            audioExtractor.setDataSource(audio.absolutePath)
            val videoIndex = (0 until videoExtractor.trackCount).firstOrNull {
                videoExtractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: error("Cannot read the selected video track")
            val audioIndex = (0 until audioExtractor.trackCount).firstOrNull {
                audioExtractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("Cannot read the selected audio track")
            videoExtractor.selectTrack(videoIndex)
            audioExtractor.selectTrack(audioIndex)
            val videoFormat = videoExtractor.getTrackFormat(videoIndex)
            val audioFormat = audioExtractor.getTrackFormat(audioIndex)
            val muxFormat = if (format == "WebM") MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            val activeMuxer = MediaMuxer(output.absolutePath, muxFormat)
            muxer = activeMuxer
            val videoTrack = activeMuxer.addTrack(videoFormat)
            val audioTrack = activeMuxer.addTrack(audioFormat)
            activeMuxer.start()
            started = true
            val maxSample = listOf(videoFormat, audioFormat).map {
                if (it.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) it.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
            }.maxOrNull() ?: 0
            val buffer = ByteBuffer.allocateDirect(maxOf(4 * 1024 * 1024, maxSample).coerceAtMost(16 * 1024 * 1024))
            val sample = MediaCodec.BufferInfo()
            while (true) {
                currentCoroutineContext().ensureActive()
                val videoTime = videoExtractor.sampleTime
                val audioTime = audioExtractor.sampleTime
                if (videoTime < 0 && audioTime < 0) break
                val useVideo = videoTime >= 0 && (audioTime < 0 || videoTime <= audioTime)
                val extractor = if (useVideo) videoExtractor else audioExtractor
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                require(size in 1..buffer.capacity()) { "Media sample exceeds supported buffer size" }
                val extractorFlags = extractor.sampleFlags
                require(extractorFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) {
                    "Encrypted media samples cannot be muxed offline"
                }
                val muxerFlags =
                    if (extractorFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }
                sample.set(0, size, extractor.sampleTime, muxerFlags)
                activeMuxer.writeSampleData(if (useVideo) videoTrack else audioTrack, buffer, sample)
                extractor.advance()
            }
            activeMuxer.stop()
            started = false
            finished = true
        } finally {
            if (started) runCatching { muxer?.stop() }
            muxer?.release()
            videoExtractor.release()
            audioExtractor.release()
            if (!finished) output.delete()
        }
    }

    private suspend fun transfer(url: String, partial: File, onProgress: (Long, Long, String) -> Unit) {
        require(URL(url).protocol == "https") { "Only HTTPS media streams are supported" }
        var offset = partial.takeIf { it.exists() }?.length() ?: 0L
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/140.0 Mobile Safari/537.36")
            setRequestProperty("Accept-Encoding", "identity")
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
        }
        try {
            val code = connection.responseCode
            if (connection.url.protocol != "https") error("Unsafe media redirect")
            if (code == 416 && offset > 0L) {
                val remoteTotal = connection.getHeaderField("Content-Range")
                    ?.substringAfter("*/")
                    ?.toLongOrNull()
                if (remoteTotal != null && remoteTotal == offset) {
                    onProgress(offset, remoteTotal, "Done")
                    return
                }
                // A stale/corrupt partial should not be retried forever from the same bad offset.
                partial.delete()
                throw java.io.IOException("Resume position no longer matches media stream")
            }
            if (code != 200 && code != 206) error("Media server returned HTTP $code")
            val rangeStart = connection.getHeaderField("Content-Range")?.substringAfter("bytes ")?.substringBefore('-')?.toLongOrNull()
            val append = offset > 0 && code == 206 && rangeStart == offset
            if (code == 206 && !append && rangeStart != 0L) error("Unexpected partial media response")
            if (!append) offset = 0L
            val length = connection.contentLengthLong
            val total = if (code == 206) {
                connection.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                    ?: if (length > 0) offset + length else -1L
            } else length

            var bytes = offset
            var windowBytes = 0L
            var last = System.currentTimeMillis()
            connection.inputStream.use { input ->
                FileOutputStream(partial, append).buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        bytes += read
                        windowBytes += read
                        val now = System.currentTimeMillis()
                        if (now - last >= 500L) {
                            val mbps = (windowBytes * 1000.0 / (now - last) / 1_048_576.0)
                            onProgress(bytes, total, "${(mbps * 10).roundToInt() / 10.0} MB/s")
                            windowBytes = 0
                            last = now
                        }
                    }
                }
            }
            require(bytes > 0 && (total <= 0 || bytes == total)) { "Incomplete media transfer" }
            onProgress(bytes, bytes, "Done")
        } finally {
            connection.disconnect()
        }
    }
}
