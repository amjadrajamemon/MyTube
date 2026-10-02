package com.drivedeck.tubedeck.auth

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GoogleAccountProfile(
    val uniqueId: String,
    val email: String,
    val displayName: String,
    val photoUrl: String?
)

data class YoutubeAuthorizationOutcome(
    val profile: GoogleAccountProfile? = null,
    val resolution: IntentSenderRequest? = null
)

/**
 * Real YouTube account authorization.
 *
 * A Google profile or ID-token alone is deliberately not treated as a YouTube session. The
 * public account state becomes non-null only after Google AuthorizationClient grants the
 * youtube.readonly OAuth scope and an authenticated YouTube Data API request succeeds.
 */
class GoogleAccountSession private constructor(context: Context) {
    companion object {
        private const val YOUTUBE_READONLY =
            "https://www.googleapis.com/auth/youtube.readonly"

        private val YOUTUBE_SCOPES = listOf(Scope(YOUTUBE_READONLY))

        @Volatile private var instance: GoogleAccountSession? = null

        fun get(context: Context): GoogleAccountSession =
            instance ?: synchronized(this) {
                instance ?: GoogleAccountSession(context.applicationContext).also { instance = it }
            }
    }

    private val appContext = context.applicationContext
    private val _account = MutableStateFlow<GoogleAccountProfile?>(null)
    val account: StateFlow<GoogleAccountProfile?> = _account.asStateFlow()

    @Volatile private var youtubeAccessToken: String? = null
    @Volatile private var authorizedAndroidAccount: Account? = null

    suspend fun restoreYoutubeAuthorization(): Result<Boolean> = runCatching {
        val result = Identity.getAuthorizationClient(appContext)
            .authorize(
                AuthorizationRequest.builder()
                    .setRequestedScopes(YOUTUBE_SCOPES)
                    .build()
            )
            .await()

        if (result.hasResolution()) {
            clearLocalSession()
            false
        } else {
            completeAuthorizationResult(result)
            true
        }
    }

    suspend fun beginYoutubeAuthorization(
        activity: Activity
    ): Result<YoutubeAuthorizationOutcome> = runCatching {
        val result = Identity.getAuthorizationClient(activity)
            .authorize(
                AuthorizationRequest.builder()
                    .setRequestedScopes(YOUTUBE_SCOPES)
                    .setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
                    .build()
            )
            .await()

        if (result.hasResolution()) {
            val pendingIntent = requireNotNull(result.pendingIntent) {
                "Google authorization requires interaction but returned no authorization screen."
            }
            YoutubeAuthorizationOutcome(
                resolution = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            )
        } else {
            YoutubeAuthorizationOutcome(profile = completeAuthorizationResult(result))
        }
    }

    suspend fun completeYoutubeAuthorization(
        activity: Activity,
        data: Intent?
    ): Result<GoogleAccountProfile> = runCatching {
        val resultIntent = requireNotNull(data) { "YouTube authorization was cancelled." }
        val result = Identity.getAuthorizationClient(activity)
            .getAuthorizationResultFromIntent(resultIntent)
        completeAuthorizationResult(result)
    }

    fun currentYoutubeAccessToken(): String? = youtubeAccessToken

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
        clearLocalSession()
    }

    private suspend fun completeAuthorizationResult(
        result: AuthorizationResult
    ): GoogleAccountProfile {
        require(result.grantedScopes.contains(YOUTUBE_READONLY)) {
            "Google did not grant YouTube account access."
        }

        val token = result.accessToken?.takeIf { it.isNotBlank() }
            ?: error("Google authorized the account but did not return a YouTube access token.")

        @Suppress("DEPRECATION")
        val googleAccount = result.toGoogleSignInAccount()
        authorizedAndroidAccount = googleAccount?.account

        val verified = fetchYoutubeIdentity(token)
        val email = googleAccount?.email.orEmpty()
        val googleId = googleAccount?.id.orEmpty()

        val profile = GoogleAccountProfile(
            uniqueId = verified.channelId
                .ifBlank { googleId }
                .ifBlank { email.lowercase() }
                .ifBlank { error("The authorized YouTube account has no stable identity.") },
            email = email,
            displayName = verified.channelTitle
                .ifBlank { googleAccount?.displayName.orEmpty() }
                .ifBlank { email.substringBefore('@') }
                .ifBlank { "YouTube Account" },
            photoUrl = verified.photoUrl ?: googleAccount?.photoUrl?.toString()
        )

        youtubeAccessToken = token
        _account.value = profile
        return profile
    }

    private suspend fun fetchYoutubeIdentity(accessToken: String): VerifiedYoutubeIdentity =
        withContext(Dispatchers.IO) {
            val connection = (URL(
                "https://www.googleapis.com/youtube/v3/channels?part=snippet&mine=true&maxResults=1"
            ).openConnection() as HttpURLConnection).apply {
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
                        JSONObject(body)
                            .optJSONObject("error")
                            ?.optString("message")
                    }.getOrNull().orEmpty()

                    error(
                        apiMessage.ifBlank {
                            "YouTube authorization verification failed (HTTP $code). " +
                                "Confirm the Android OAuth client and YouTube Data API are enabled for this app."
                        }
                    )
                }

                val item = JSONObject(body)
                    .optJSONArray("items")
                    ?.optJSONObject(0)

                val snippet = item?.optJSONObject("snippet")
                val thumbnails = snippet?.optJSONObject("thumbnails")
                val photoUrl = listOf("high", "medium", "default")
                    .firstNotNullOfOrNull { size ->
                        thumbnails
                            ?.optJSONObject(size)
                            ?.optString("url")
                            ?.takeIf { it.isNotBlank() }
                    }

                VerifiedYoutubeIdentity(
                    channelId = item?.optString("id").orEmpty(),
                    channelTitle = snippet?.optString("title").orEmpty(),
                    photoUrl = photoUrl
                )
            } finally {
                connection.disconnect()
            }
        }

    private fun clearLocalSession() {
        youtubeAccessToken = null
        authorizedAndroidAccount = null
        _account.value = null
    }

    private data class VerifiedYoutubeIdentity(
        val channelId: String,
        val channelTitle: String,
        val photoUrl: String?
    )
}
