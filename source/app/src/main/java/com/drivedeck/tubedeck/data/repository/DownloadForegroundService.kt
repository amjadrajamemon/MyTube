package com.drivedeck.tubedeck.data.repository

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.drivedeck.tubedeck.data.model.TaskStatus
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps active download/retry work alive when the MyTube UI is backgrounded or removed.
 *
 * Queue state itself remains owned by TubeDeckRepository; this service only supplies foreground
 * process priority and reuses the existing rich queue notification.
 */
class DownloadForegroundService : Service() {
    companion object {
        private val running = AtomicBoolean(false)

        fun ensureRunning(context: Context) {
            if (running.get()) return
            // Android 12+ can reject a brand-new FGS start while the app is fully backgrounded.
            // Never let that platform restriction crash the queue; persisted work will resume on
            // the next user-visible/app-allowed start.
            runCatching {
                ContextCompat.startForegroundService(
                    context.applicationContext,
                    Intent(context.applicationContext, DownloadForegroundService::class.java)
                )
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: TubeDeckRepository
    @Volatile private var timingOut = false

    override fun onCreate() {
        super.onCreate()
        running.set(true)
        repository = TubeDeckRepository.get(applicationContext)

        // startForeground must happen promptly after startForegroundService().
        startAsForeground(repository.downloadQueue.value)

        serviceScope.launch {
            repository.downloadQueue.collectLatest { tasks ->
                if (timingOut) return@collectLatest
                val hasWork = tasks.any {
                    it.status == TaskStatus.QUEUED || it.status == TaskStatus.DOWNLOADING
                }
                if (hasWork) {
                    startAsForeground(tasks)
                } else {
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
            }
        }
    }

    private fun startAsForeground(tasks: List<com.drivedeck.tubedeck.data.model.DownloadTask>) {
        val notification = DownloadQueueNotifier.buildNotification(this, tasks)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                DownloadQueueNotifier.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(DownloadQueueNotifier.NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        timingOut = false
        running.set(true)
        val tasks = repository.downloadQueue.value
        if (tasks.any { it.status == TaskStatus.QUEUED || it.status == TaskStatus.DOWNLOADING }) {
            startAsForeground(tasks)
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Active downloads intentionally continue; the foreground service owns their process life.
        super.onTaskRemoved(rootIntent)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15+ can time-limit dataSync foreground services. Stop active I/O promptly,
        // preserve partials, and mark it for automatic resume when the app is next user-visible.
        timingOut = true
        running.set(false)
        repository.suspendDownloadsForSystemTimeout()
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf(startId)
    }

    override fun onDestroy() {
        running.set(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
