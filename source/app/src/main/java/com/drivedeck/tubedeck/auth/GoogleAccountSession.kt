package com.drivedeck.tubedeck.auth

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.IntentSenderRequest
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.drivedeck.tubedeck.BuildConfig
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class GoogleAccountProfile(
    val uniqueId: String,
    val email: String,
    val displayName: String,
    val photoUrl: String?,
    val youtubeChannelId: String = "",
    val subscriptionCount: Long? = null
)

data class YoutubeAuthorizationOutcome(
    val profile: GoogleAccountProfile? = null,
    val resolution: IntentSenderRequest? = null
)

enum class YoutubeAuthorizationState {
    SIGNED_OUT,
    CONNECTING,
    CONNECTED,
    CONFIGURATION_REQUIRED,
    ERROR
}

data class YoutubeAuthorizationStatus(
    val state: YoutubeAuthorizationState = YoutubeAuthorizationState.SIGNED_OUT,
    val message: String = ""
)

/**
 * Two-stage Google / YouTube session:
 * 1) explicit Google identity through Credential Manager,
 * 2) YouTube Data API authorization through AuthorizationClient.
 *
 * CONNECTED is published only after youtube.readonly is granted and authenticated YouTube API
 * calls succeed. A profile/ID token alone is never presented as a working YouTube session.
 */
class GoogleAccountSession private constructor(context: Context) {
    companion object {
        private const val YOUTUBE_READONLY =
            "https://www.googleapis.com/auth/youtube.readonly"
        private const val PREFS = "youtube_account_session"
        private const val KEY_UNIQUE_ID = "google_unique_id"
        private const val KEY_EMAIL = "google_email"
        private const val KEY_NAME = "google_name"
        private const val KEY_PHOTO = "google_photo"

        private val YOUTUBE_SCOPES = listOf(Scope(YOUTUBE_READONLY))

        @Volatile private var instance: GoogleAccountSession? = null

        fun get(context: Context): GoogleAccountSession =
            instance ?: synchronized(this) {
                instance ?: GoogleAccountSession(context.applicationContext).also { instance = it }
            }
    }

    private data class SignedInIdentity(
        val uniqueId: String,
        val email: String,
        val displayName: String,
        val photoUrl: String?
    )

    private data class VerifiedYoutubeIdentity(
        val channelId: String,
        val channelTitle: String,
        val photoUrl: String?,
        val subscriptionCount: Long?
    )

    private val appContext = context.applicationContext
    private val credentialManager = CredentialManager.create(appContext)
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _account = MutableStateFlow<GoogleAccountProfile?>(null)
    val account: StateFlow<GoogleAccountProfile?> = _account.asStateFlow()

    private val _status = MutableStateFlow(initialStatus())
    val status: StateFlow<YoutubeAuthorizationStatus> = _status.asStateFlow()

    @Volatile private var youtubeAccessToken: String? = null
    @Volatile private var authorizedAndroidAccount: Account? = null
    @Volatile private var pendingIdentity: SignedInIdentity? = null

    fun isIdentityConfigured(): Boolean = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    suspend fun restoreYoutubeAuthorization(): Result<Boolean> {
        val identity = loadPersistedIdentity()
        _status.value = YoutubeAuthorizationStatus(YoutubeAuthorizationState.CONNECTING)
        pendingIdentity = identity
        return runCatching {
            val builder = AuthorizationRequest.builder()
                .setRequestedScopes(YOUTUBE_SCOPES)
            if (!identity?.email.isNullOrBlank()) {
                builder.setAccount(Account(identity!!.email, "com.google"))
            }
            val result = Identity.getAuthorizationClient(appContext)
                .authorize(builder.build())
                .await()

            if (result.hasResolution()) {
                clearRuntimeSession()
                false
            } else {
                completeAuthorizationResult(result)
                true
            }
        }.onFailure(::recordAuthorizationFailure)
            .recoverCatching { throw decorateAuthorizationError(it) }
    }

    suspend fun beginYoutubeAuthorization(
        activity: Activity
    ): Result<YoutubeAuthorizationOutcome> {
        _status.value = YoutubeAuthorizationStatus(YoutubeAuthorizationState.CONNECTING)
        return runCatching {
            val builder = AuthorizationRequest.builder()
                .setRequestedScopes(YOUTUBE_SCOPES)

            if (isIdentityConfigured()) {
                val identity = signInGoogleIdentity(activity)
                pendingIdentity = identity
                builder.setAccount(Account(identity.email, "com.google"))
            } else {
                // AuthorizationClient remains a genuine Google/YouTube account connection on
                // Android. The Android OAuth client (package + signing SHA-1) is authoritative.
                builder.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
            }

            val result = Identity.getAuthorizationClient(activity)
                .authorize(builder.build())
                .await()

            if (result.hasResolution()) {
                val pendingIntent = requireNotNull(result.pendingIntent) {
                    "YouTube authorization unavailable"
                }
                YoutubeAuthorizationOutcome(
                    resolution = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                )
            } else {
                YoutubeAuthorizationOutcome(profile = completeAuthorizationResult(result))
            }
        }.onFailure(::recordAuthorizationFailure)
            .recoverCatching { throw decorateAuthorizationError(it) }
    }

    suspend fun completeYoutubeAuthorization(
        activity: Activity,
        data: Intent?
    ): Result<GoogleAccountProfile> {
        _status.value = YoutubeAuthorizationStatus(YoutubeAuthorizationState.CONNECTING)
        return runCatching {
            val resultIntent = requireNotNull(data) { "Authorization cancelled" }
            val result = Identity.getAuthorizationClient(activity)
                .getAuthorizationResultFromIntent(resultIntent)
            completeAuthorizationResult(result)
        }.onFailure(::recordAuthorizationFailure)
            .recoverCatching { throw decorateAuthorizationError(it) }
    }

    fun currentYoutubeAccessToken(): String? = youtubeAccessToken

    fun oauthRegistrationInfo(): String {
        val webClient = if (isIdentityConfigured()) "configured" else "missing"
        return "Package ${appContext.packageName} • SHA-1 ${signingCertificateSha1()} • Web client $webClient"
    }

    suspend fun signOut() {
        val request = RevokeAccessRequest.builder()
            .setScopes(YOUTUBE_SCOPES)
            .apply {
                authorizedAndroidAccount?.let(::setAccount)
            }
            .build()

        runCatching {
            Identity.getAuthorizationClient(appContext)
                .revokeAccess(request)
                .await()
        }
        runCatching {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        }

        prefs.edit().clear().apply()
        pendingIdentity = null
        clearRuntimeSession()
    }

    private suspend fun signInGoogleIdentity(activity: Activity): SignedInIdentity {
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val response = credentialManager.getCredential(activity, request)
        val custom = response.credential as? CustomCredential
            ?: error("Google credential unavailable")
        require(custom.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected Google credential"
        }

        val google = GoogleIdTokenCredential.createFrom(custom.data)
        val email = google.email?.takeIf { it.isNotBlank() }
            ?: error("Google email unavailable")

        return SignedInIdentity(
            uniqueId = google.uniqueId,
            email = email,
            displayName = google.displayName.orEmpty(),
            photoUrl = google.profilePictureUri?.toString()
        )
    }

    private suspend fun completeAuthorizationResult(
        result: AuthorizationResult
    ): GoogleAccountProfile {
        require(result.grantedScopes.contains(YOUTUBE_READONLY)) {
            "YouTube permission denied"
        }

        val token = result.accessToken?.takeIf { it.isNotBlank() }
            ?: error("YouTube access token unavailable")

        val identity = pendingIdentity ?: loadPersistedIdentity()

        @Suppress("DEPRECATION")
        val legacy = result.toGoogleSignInAccount()
        authorizedAndroidAccount =
            identity?.email?.takeIf { it.isNotBlank() }?.let { Account(it, "com.google") }
                ?: legacy?.account

        val verified = fetchYoutubeIdentity(token)
        val email = identity?.email.orEmpty().ifBlank { legacy?.email.orEmpty() }
        val googleUniqueId = identity?.uniqueId.orEmpty().ifBlank { legacy?.id.orEmpty() }
        val displayName = identity?.displayName.orEmpty()
            .ifBlank { verified.channelTitle }
            .ifBlank { legacy?.displayName.orEmpty() }
            .ifBlank { email.substringBefore('@') }
            .ifBlank { "YouTube" }

        val profile = GoogleAccountProfile(
            uniqueId = googleUniqueId
                .ifBlank { verified.channelId }
                .ifBlank { email.lowercase() }
                .ifBlank { error("Google identity unavailable") },
            email = email,
            displayName = displayName,
            photoUrl = identity?.photoUrl
                ?: verified.photoUrl
                ?: legacy?.photoUrl?.toString(),
            youtubeChannelId = verified.channelId,
            subscriptionCount = verified.subscriptionCount
        )

        persistIdentity(
            SignedInIdentity(
                uniqueId = profile.uniqueId,
                email = profile.email,
                displayName = profile.displayName,
                photoUrl = profile.photoUrl
            )
        )
        pendingIdentity = null
        youtubeAccessToken = token
        _account.value = profile
        _status.value = YoutubeAuthorizationStatus(
            YoutubeAuthorizationState.CONNECTED,
            "Connected"
        )
        return profile
    }

    private suspend fun fetchYoutubeIdentity(accessToken: String): VerifiedYoutubeIdentity =
        withContext(Dispatchers.IO) {
            val channelJson = getYoutubeJson(
                "https://www.googleapis.com/youtube/v3/channels?part=snippet&mine=true&maxResults=1",
                accessToken
            )
            val item = channelJson.optJSONArray("items")?.optJSONObject(0)
            val snippet = item?.optJSONObject("snippet")
            val thumbnails = snippet?.optJSONObject("thumbnails")
            val photoUrl = listOf("high", "medium", "default")
                .firstNotNullOfOrNull { size ->
                    thumbnails?.optJSONObject(size)?.optString("url")?.takeIf { it.isNotBlank() }
                }

            val subscriptionsJson = getYoutubeJson(
                "https://www.googleapis.com/youtube/v3/subscriptions?part=id&mine=true&maxResults=1",
                accessToken
            )
            val subscriptionCount = subscriptionsJson
                .optJSONObject("pageInfo")
                ?.optLong("totalResults", -1L)
                ?.takeIf { it >= 0L }

            VerifiedYoutubeIdentity(
                channelId = item?.optString("id").orEmpty(),
                channelTitle = snippet?.optString("title").orEmpty(),
                photoUrl = photoUrl,
                subscriptionCount = subscriptionCount
            )
        }

    private fun getYoutubeJson(url: String, accessToken: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }

        try {
            val code = connection.responseCode
            val bodyStream =
                if (code in 200..299) connection.inputStream else connection.errorStream
            val body = bodyStream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                val apiMessage = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty()
                error(apiMessage.ifBlank { "YouTube API HTTP $code" })
            }

            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun initialStatus(): YoutubeAuthorizationStatus =
        YoutubeAuthorizationStatus()

    private fun recordAuthorizationFailure(error: Throwable) {
        val decorated = decorateAuthorizationError(error)
        val message = decorated.message.orEmpty()
        val state = if (
            message.contains("client ID", ignoreCase = true) ||
            message.contains("unregistered", ignoreCase = true) ||
            message.contains("developer", ignoreCase = true) ||
            message.contains("Google Cloud", ignoreCase = true) ||
            message.contains("accessNotConfigured", ignoreCase = true) ||
            message.contains("API has not been used", ignoreCase = true) ||
            message.contains("not configured", ignoreCase = true)
        ) {
            YoutubeAuthorizationState.CONFIGURATION_REQUIRED
        } else {
            YoutubeAuthorizationState.ERROR
        }
        clearRuntimeSession()
        _status.value = YoutubeAuthorizationStatus(state, message)
    }

    private fun decorateAuthorizationError(error: Throwable): Throwable {
        val registration = oauthRegistrationInfo()
        if (error is ApiException && error.statusCode == CommonStatusCodes.DEVELOPER_ERROR) {
            return IllegalStateException(
                "Google OAuth client rejected • $registration",
                error
            )
        }
        val message = error.message.orEmpty()
        if (
            message.contains("unregistered", ignoreCase = true) ||
            message.contains("developer", ignoreCase = true) ||
            message.contains("accessNotConfigured", ignoreCase = true)
        ) {
            return IllegalStateException("$message • $registration", error)
        }
        return error
    }

    private fun persistIdentity(identity: SignedInIdentity) {
        prefs.edit()
            .putString(KEY_UNIQUE_ID, identity.uniqueId)
            .putString(KEY_EMAIL, identity.email)
            .putString(KEY_NAME, identity.displayName)
            .putString(KEY_PHOTO, identity.photoUrl)
            .apply()
    }

    private fun loadPersistedIdentity(): SignedInIdentity? {
        val email = prefs.getString(KEY_EMAIL, null)?.takeIf { it.isNotBlank() } ?: return null
        return SignedInIdentity(
            uniqueId = prefs.getString(KEY_UNIQUE_ID, "").orEmpty(),
            email = email,
            displayName = prefs.getString(KEY_NAME, "").orEmpty(),
            photoUrl = prefs.getString(KEY_PHOTO, null)
        )
    }

    private fun signingCertificateSha1(): String = runCatching {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            appContext.packageManager.getPackageInfo(
                appContext.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageInfo(
                appContext.packageName,
                PackageManager.GET_SIGNATURES
            )
        }

        val signatureBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures?.firstOrNull()?.toByteArray()
        } ?: error("Signing certificate unavailable")

        MessageDigest.getInstance("SHA-1")
            .digest(signatureBytes)
            .joinToString(":") { "%02X".format(it) }
    }.getOrElse { "unavailable" }

    private fun clearRuntimeSession() {
        youtubeAccessToken = null
        authorizedAndroidAccount = null
        _account.value = null
        if (_status.value.state != YoutubeAuthorizationState.CONFIGURATION_REQUIRED) {
            _status.value = YoutubeAuthorizationStatus(YoutubeAuthorizationState.SIGNED_OUT)
        }
    }
}
