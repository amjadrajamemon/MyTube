package com.drivedeck.tubedeck.player

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

data class YtDlpUpdateState(
    val initialized: Boolean = false,
    val updating: Boolean = false,
    val version: String? = null,
    val lastCheckedAt: Long = 0L,
    val message: String = "Download Engine not initialized"
) {
    val lastCheckedLabel: String
        get() = if (lastCheckedAt <= 0L) "Never"
        else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(lastCheckedAt))
}

object YtDlpUpdateManager {
    private const val PREFS = "mytube_ytdlp_update"
    private const val KEY_LAST_CHECK = "last_check"
    private const val UNIQUE_WEEKLY_WORK = "mytube-ytdlp-weekly-stable-update"
    private val initMutex = Mutex()
    private val updateMutex = Mutex()

    private val _state = MutableStateFlow(YtDlpUpdateState())
    val state: StateFlow<YtDlpUpdateState> = _state.asStateFlow()

    fun scheduleWeekly(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<YtDlpUpdateWorker>(7, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WEEKLY_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        refreshState(context)
    }

    fun refreshState(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val version = runCatching { YoutubeDL.versionName(app) ?: YoutubeDL.version(app) }.getOrNull()
        _state.value = _state.value.copy(
            version = version ?: _state.value.version,
            lastCheckedAt = prefs.getLong(KEY_LAST_CHECK, 0L),
            message = if (version.isNullOrBlank()) _state.value.message else "Download Engine " + version + " ready"
        )
    }

    suspend fun ensureInitialized(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        initMutex.withLock {
            if (_state.value.initialized) return@withLock
            YoutubeDL.init(app)
            val version = YoutubeDL.versionName(app) ?: YoutubeDL.version(app)
            _state.value = _state.value.copy(
                initialized = true,
                version = version,
                message = if (version.isNullOrBlank()) "Download Engine ready" else "Download Engine " + version + " ready"
            )
        }
    }

    suspend fun updateNow(context: Context): kotlin.Result<YoutubeDL.UpdateStatus?> =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            runCatching {
                updateMutex.withLock {
                    ensureInitialized(app)
                    _state.value = _state.value.copy(updating = true, message = "Checking Download Engine stable channel…")
                    try {
                        val result = YoutubeDL.updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
                        val checkedAt = System.currentTimeMillis()
                        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit()
                            .putLong(KEY_LAST_CHECK, checkedAt)
                            .apply()
                        val version = YoutubeDL.versionName(app) ?: YoutubeDL.version(app)
                        val message = when (result) {
                            YoutubeDL.UpdateStatus.DONE -> "Download Engine updated to " + (version ?: "latest stable")
                            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Download Engine is already up to date"
                            null -> "Download Engine update check completed"
                        }
                        _state.value = YtDlpUpdateState(
                            initialized = true,
                            updating = false,
                            version = version,
                            lastCheckedAt = checkedAt,
                            message = message
                        )
                        result
                    } catch (t: Throwable) {
                        _state.value = _state.value.copy(
                            updating = false,
                            message = "Download Engine update failed: " + (t.message ?: t.javaClass.simpleName)
                        )
                        throw t
                    }
                }
            }
        }
}

class YtDlpUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val result = YtDlpUpdateManager.updateNow(applicationContext)
        return if (result.isSuccess) Result.success() else Result.retry()
    }
}
