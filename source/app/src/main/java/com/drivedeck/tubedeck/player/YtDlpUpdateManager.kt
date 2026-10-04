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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

data class YtDlpUpdateState(
    val initialized: Boolean = false,
    val updating: Boolean = false,
    val version: String? = null,
    val latestVersion: String? = null,
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
    private const val KEY_LATEST_VERSION = "latest_version"
    private const val UNIQUE_WEEKLY_WORK = "mytube-ytdlp-weekly-stable-update"
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"
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
        val latest = prefs.getString(KEY_LATEST_VERSION, null)
        _state.value = _state.value.copy(
            version = version ?: _state.value.version,
            latestVersion = latest ?: _state.value.latestVersion,
            lastCheckedAt = prefs.getLong(KEY_LAST_CHECK, 0L),
            message = when {
                version.isNullOrBlank() -> _state.value.message
                !latest.isNullOrBlank() -> "Installed $version • Latest $latest"
                else -> "Download Engine $version ready"
            }
        )
    }

    suspend fun refreshLatestVersion(context: Context): kotlin.Result<String> =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            runCatching {
                val latest = fetchLatestStableVersion()
                val now = System.currentTimeMillis()
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LATEST_VERSION, latest)
                    .putLong(KEY_LAST_CHECK, now)
                    .apply()
                val installed = runCatching {
                    YoutubeDL.versionName(app) ?: YoutubeDL.version(app)
                }.getOrNull()
                _state.value = _state.value.copy(
                    version = installed ?: _state.value.version,
                    latestVersion = latest,
                    lastCheckedAt = now,
                    message = if (installed.isNullOrBlank()) {
                        "Latest $latest"
                    } else {
                        "Installed $installed • Latest $latest"
                    }
                )
                latest
            }
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
                message = if (version.isNullOrBlank()) "Download Engine ready" else "Download Engine $version ready"
            )
        }
    }

    suspend fun updateNow(context: Context): kotlin.Result<YoutubeDL.UpdateStatus?> =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            runCatching {
                updateMutex.withLock {
                    ensureInitialized(app)
                    _state.value = _state.value.copy(
                        updating = true,
                        message = "Checking latest Download Engine…"
                    )
                    try {
                        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        val latestBefore = runCatching { fetchLatestStableVersion() }.getOrNull()
                            ?: prefs.getString(KEY_LATEST_VERSION, null)
                        val result = YoutubeDL.updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
                        val checkedAt = System.currentTimeMillis()
                        val version = YoutubeDL.versionName(app) ?: YoutubeDL.version(app)
                        val latest = runCatching { fetchLatestStableVersion() }.getOrNull()
                            ?: latestBefore
                            ?: version
                        prefs.edit()
                            .putLong(KEY_LAST_CHECK, checkedAt)
                            .apply {
                                if (!latest.isNullOrBlank()) putString(KEY_LATEST_VERSION, latest)
                            }
                            .apply()
                        val versionSummary = listOfNotNull(
                            version?.takeIf { it.isNotBlank() }?.let { "Installed $it" },
                            latest?.takeIf { it.isNotBlank() }?.let { "Latest $it" }
                        ).joinToString(" • ")
                        val message = when (result) {
                            YoutubeDL.UpdateStatus.DONE -> if (versionSummary.isBlank()) {
                                "Download Engine updated"
                            } else {
                                "Updated • $versionSummary"
                            }
                            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> if (versionSummary.isBlank()) {
                                "Download Engine is already up to date"
                            } else {
                                "Current • $versionSummary"
                            }
                            null -> if (versionSummary.isBlank()) {
                                "Download Engine update check completed"
                            } else {
                                versionSummary
                            }
                        }
                        _state.value = YtDlpUpdateState(
                            initialized = true,
                            updating = false,
                            version = version,
                            latestVersion = latest,
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

    private fun fetchLatestStableVersion(): String {
        val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "MyTube-Download-Engine")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) error("Latest-version service returned HTTP $code")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).optString("tag_name").trim().removePrefix("v")
                .takeIf { it.isNotBlank() }
                ?: error("Latest Download Engine version was not returned")
        } finally {
            connection.disconnect()
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
