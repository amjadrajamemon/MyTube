package com.drivedeck.tubedeck.data.repository

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.drivedeck.tubedeck.MainActivity
import com.drivedeck.tubedeck.data.model.DownloadTask
import com.drivedeck.tubedeck.data.model.TaskStatus
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One live notification summarizing the complete MyTube download queue.
 *
 * The same notification is also used by DownloadForegroundService, so background downloads keep
 * one stable notification instead of producing a duplicate "service running" notification.
 */
internal object DownloadQueueNotifier {
    private const val CHANNEL_ID = "mytube_downloads"
    internal const val NOTIFICATION_ID = 42017
    private var lastUpdateAt = 0L

    fun update(context: Context, tasks: List<DownloadTask>, force: Boolean = false) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        createChannel(manager)

        if (tasks.isEmpty()) {
            manager.cancel(NOTIFICATION_ID)
            return
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val now = System.currentTimeMillis()
        val hasActive = tasks.any {
            it.status == TaskStatus.DOWNLOADING || it.status == TaskStatus.QUEUED
        }
        if (!force && hasActive && now - lastUpdateAt < 1_000L) return
        lastUpdateAt = now
        manager.notify(NOTIFICATION_ID, buildNotification(context, tasks, now))
    }

    fun buildNotification(
        context: Context,
        tasks: List<DownloadTask>,
        now: Long = System.currentTimeMillis()
    ): Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager != null) createChannel(manager)

        val active = tasks.count { it.status == TaskStatus.DOWNLOADING }
        val queued = tasks.count { it.status == TaskStatus.QUEUED }
        val finished = tasks.count { it.status == TaskStatus.COMPLETED }
        val paused = tasks.count { it.status == TaskStatus.PAUSED }
        val failed = tasks.count { it.status == TaskStatus.FAILED }
        val retrying = tasks.count {
            it.retryCount > 0 &&
                (it.status == TaskStatus.DOWNLOADING || it.status == TaskStatus.QUEUED)
        }
        val retriedTasks = tasks.count { it.retryCount > 0 }
        val totalRetryAttempts = tasks.sumOf { it.retryCount }

        val known = tasks.filter { it.totalBytes > 0L }
        val totalBytes = known.sumOf { it.totalBytes }
        val downloadedBytes = known.sumOf { task ->
            task.downloadedBytes.coerceIn(0L, task.totalBytes)
        }
        val progressPercent = if (totalBytes > 0L) {
            ((downloadedBytes.toDouble() / totalBytes.toDouble()) * 100.0)
                .roundToInt()
                .coerceIn(0, 100)
        } else {
            0
        }

        val elapsedStart = tasks
            .filter { it.status != TaskStatus.COMPLETED }
            .minOfOrNull { it.createdAt }
            ?: tasks.minOfOrNull { it.createdAt }
            ?: now
        val elapsed = (now - elapsedStart).coerceAtLeast(0L)

        val bytesPerSecond = tasks
            .filter { it.status == TaskStatus.DOWNLOADING }
            .sumOf { parseBytesPerSecond(it.speed) }

        val remainingKnownBytes = known.sumOf { task ->
            (task.totalBytes - task.downloadedBytes).coerceAtLeast(0L)
        }
        val etaMillis = if (bytesPerSecond > 1.0 && remainingKnownBytes > 0L) {
            ((remainingKnownBytes / bytesPerSecond) * 1_000.0).toLong()
        } else {
            null
        }

        val hasActive = active > 0 || queued > 0
        val title = when {
            active > 0 -> "MyTube downloads • $active active"
            queued > 0 -> "MyTube downloads • $queued queued"
            paused > 0 -> "MyTube downloads • paused"
            failed > 0 -> "MyTube downloads need attention"
            else -> "MyTube downloads finished"
        }

        val compact = buildString {
            append("Queued ").append(queued)
            append(" • Done ").append(finished)
            append(" • Active ").append(active)
            if (retrying > 0) append(" • Retrying ").append(retrying)
            if (failed > 0) append(" • Failed ").append(failed)
        }

        val expanded = buildString {
            append("Queued: ").append(queued)
            append("   Active: ").append(active)
            append("   Finished: ").append(finished)
            append("   Paused: ").append(paused)
            append("   Failed: ").append(failed)
            append("\nRetried downloads: ").append(retriedTasks)
            append("   Retry attempts: ").append(totalRetryAttempts)
            append("\nElapsed: ").append(formatDuration(elapsed))
            append("   Remaining: ")
            append(etaMillis?.let(::formatDuration) ?: "calculating…")
            if (totalBytes > 0L) {
                append("\nProgress: ").append(progressPercent).append("%")
                append("   ")
                append(formatBytes(downloadedBytes))
                append(" / ")
                append(formatBytes(totalBytes))
            }
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_downloads_queue", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            710,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        builder
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(compact)
            .setStyle(Notification.BigTextStyle().bigText(expanded))
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(hasActive)
            .setAutoCancel(!hasActive)

        if (totalBytes > 0L) {
            builder.setProgress(100, progressPercent, false)
        } else if (hasActive) {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun createChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Download progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live progress and status for MyTube download queues"
                setShowBadge(false)
            }
        )
    }

    private fun parseBytesPerSecond(speed: String): Double {
        val normalized = speed.trim().lowercase(Locale.ROOT)
        return when {
            normalized.endsWith("mb/s") ->
                normalized.removeSuffix("mb/s").trim().toDoubleOrNull()?.times(1_048_576.0) ?: 0.0
            normalized.endsWith("kb/s") ->
                normalized.removeSuffix("kb/s").trim().toDoubleOrNull()?.times(1_024.0) ?: 0.0
            normalized.endsWith("b/s") ->
                normalized.removeSuffix("b/s").trim().toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    private fun formatDuration(milliseconds: Long): String {
        val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    private fun formatBytes(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        if (gb >= 1.0) return String.format(Locale.US, "%.1f GB", gb)
        val mb = bytes / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f MB", mb)
    }
}
