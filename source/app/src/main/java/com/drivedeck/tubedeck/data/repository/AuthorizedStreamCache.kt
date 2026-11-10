package com.drivedeck.tubedeck.data.repository

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.drivedeck.tubedeck.data.model.OfflineMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Optional persistent cache for progressive URLs the user is authorized to store.
 * YouTube/Googlevideo URLs, WebView sessions, DASH/HLS and protected streams never
 * enter this cache. There is NO 2 GB cap and NO automatic eviction of completed files.
 *
 * One SimpleCache per app process/directory. Playback reads and writes the same
 * cache used by the post-watch promotion step.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal object AuthorizedStreamCache {
    @Volatile private var instance: SimpleCache? = null
    private val gate = Any()

    private fun cache(context: Context): SimpleCache = synchronized(gate) {
        instance ?: SimpleCache(
            File(context.applicationContext.getExternalFilesDir(null)
                ?: context.applicationContext.filesDir, "tubedeck-authorized-playback-cache")
                .apply { if (!exists()) check(mkdirs()) },
            NoOpCacheEvictor(),
            StandaloneDatabaseProvider(context.applicationContext)
        ).also { instance = it }
    }

    fun sourceFactory(context: Context, upstream: DataSource.Factory): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** Cached data is not labelled Offline Ready until every byte is present. */
    suspend fun promoteIfComplete(
        context: Context, media: OfflineMedia, folderName: String,
        selectedTreeUri: String? = null
    ): OfflineMedia? = withContext(Dispatchers.IO) {
        val key = media.mediaUri
        val cache = cache(context)
        val sourceLength = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        if (sourceLength <= 0L || sourceLength > Long.MAX_VALUE / 2L) return@withContext null
        if (!cache.isCached(key, 0L, sourceLength)) return@withContext null

        if (selectedTreeUri != null) {
            val tree = runCatching {
                androidx.documentfile.provider.DocumentFile.fromTreeUri(
                    context, android.net.Uri.parse(selectedTreeUri)
                )
            }.getOrNull()
            if (tree?.isDirectory != true || !tree.canWrite()) return@withContext null
        }
        val folder = MediaDownloader.folder(context, folderName)
        if (!OfflineVaultPolicy.shouldSaveFreeSpace(folder.usableSpace, sourceLength)) {
            return@withContext null
        }

        val extension = media.mediaUri.substringBefore('?').substringAfterLast('.', "mp4")
            .lowercase().takeIf { it in setOf("mp4", "webm", "m4v") } ?: "mp4"
        val safeTitle = media.title.replace(Regex("[^a-zA-Z0-9 _.-]"), "_")
            .take(72).trim(' ', '.').ifBlank { "offline_video" }
        val id = UUID.randomUUID().toString()
        val stage = File(folder, "$id.vault-part")
        val final = File(folder, "${safeTitle}_$id.$extension")
        var committed: File? = null
        try {
            // Lock spans while copying so no concurrent cache removal can invalidate a file.
            val coroutine = currentCoroutineContext()
            synchronized(gate) {
                val spans = cache.getCachedSpans(key).sortedBy { it.position }
                FileOutputStream(stage).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var offset = 0L
                    for (span in spans) {
                        if (offset >= sourceLength) break
                        if (span.position > offset) error("Cache has an uncached gap")
                        val part = span.file ?: error("Cache span has no file")
                        val skip = (offset - span.position).coerceAtLeast(0L)
                        val needed = minOf(span.length - skip, sourceLength - offset)
                        if (needed <= 0L) continue
                        part.inputStream().buffered().use { input ->
                            var skipped = 0L
                            while (skipped < skip) {
                                val n = input.skip(skip - skipped)
                                if (n <= 0L) error("Cannot seek cached segment")
                                skipped += n
                            }
                            var left = needed
                            while (left > 0L) {
                                coroutine.ensureActive()
                                val n = input.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
                                if (n < 0) error("Truncated cache span")
                                output.write(buffer, 0, n)
                                left -= n
                                offset += n
                            }
                        }
                    }
                    output.fd.sync()
                    require(offset == sourceLength) { "Cached file is incomplete" }
                }
            }
            committed = LocalDownloadCommit.commit(stage, final)
            check(committed?.isFile == true && committed?.length() == sourceLength)
            val localMedia = OfflineMedia(
                id = id,
                title = media.title,
                videoId = media.videoId,
                thumbnailUrl = media.thumbnailUrl,
                mediaUri = android.net.Uri.fromFile(committed).toString(),
                isAudioOnly = false,
                qualityLabel = media.qualityLabel.takeUnless { it == "Auto" } ?: "Original",
                format = extension.uppercase(),
                fileSizeBytes = sourceLength,
                durationSeconds = media.durationSeconds
            )
            // A selected SAF/USB target is authoritative; never silently retain
            // a completed file in a different folder if copying to USB failed.
            val readyMedia = if (selectedTreeUri != null) {
                DownloadedFileManager.transfer(
                    context, localMedia, android.net.Uri.parse(selectedTreeUri), move = true
                )
            } else localMedia
            synchronized(gate) { cache.removeResource(key) }
            readyMedia
        } catch (_: Exception) {
            stage.delete()
            committed?.delete()
            // Cache remains available for a later permitted promotion retry.
            null
        }
    }

    fun retainedCacheBytes(context: Context): Long =
        runCatching { cache(context).cacheSpace }.getOrDefault(0L)

    /** Explicit user-requested cleanup of unfinished progressive playback cache. */
    fun clearIncompleteCache(context: Context): Long = synchronized(gate) {
        val cache = cache(context)
        val before = cache.cacheSpace
        cache.keys.toList().forEach { key -> cache.removeResource(key) }
        before - cache.cacheSpace
    }
}
