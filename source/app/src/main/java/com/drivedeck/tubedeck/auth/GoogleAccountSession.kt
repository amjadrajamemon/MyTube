package com.drivedeck.tubedeck.auth

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import androidx.activity.result.IntentSenderRequest
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.drivedeck.tubedeck.BuildConfig
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
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
import java.security.SecureRandom

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
    val resolution: IntentSenderRequest? = null,
    val recoveryIntent: Intent? = null
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
    val message: String = "",
    val youtubeAuthorized: Boolean = false
)

/**
 * Deterministic Google identity + optional YouTube authorization.
 *
 * Authentication and authorization are deliberately separated:
 * 1) Credential Manager is preferred when a valid Web/server client ID is available.
 * 2) Google Play Services DEFAULT_SIGN_IN is the interactive fallback when no Web client ID exists.
 * 3) youtube.readonly authorization is attempted only after a real Google identity is established.
 *
 * Cached SharedPreferences are migration hints only. They never make the app appear signed in.
 */
class GoogleAccountSession private constructor(context: Context) {
    companion object {
        private const val YOUTUBE_READONLY =
            "https://www.googleapis.com/auth/youtube.readonly"
        private const val PREFS = "youtube_account_session"
        private const val LEGACY_SETTINGS_PREFS = "tubedeck_prefs"
        private const val LEGACY_IDENTITY_PREFS = "google_account_session"
        private const val LEGACY_CLIENT_ID_KEY = "googleWebClientId"

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
    private val legacySettingsPrefs =
        appContext.getSharedPreferences(LEGACY_SETTINGS_PREFS, Context.MODE_PRIVATE)
    private val legacyIdentityPrefs =
        appContext.getSharedPreferences(LEGACY_IDENTITY_PREFS, Context.MODE_PRIVATE)

    // A persisted profile alone is not authentication. Only a current Google Play Services
    // session is restored as connected without user interaction.
    private val startupPlayServicesAccount = GoogleSignIn.getLastSignedInAccount(appContext)
    private val rememberedIdentity = loadPersistedIdentity()

    private val _account = MutableStateFlow(
        startupPlayServicesAccount?.let { profileFromIdentity(identityFromGoogleSignIn(it)) }
    )
    val account: StateFlow<GoogleAccountProfile?> = _account.asStateFlow()

    private val _status = MutableStateFlow(
        if (_account.value != null) {
            YoutubeAuthorizationStatus(
                YoutubeAuthorizationState.CONNECTED,
                "Google signed in",
                youtubeAuthorized = false
            )
        } else {
            YoutubeAuthorizationStatus()
        }
    )
    val status: StateFlow<YoutubeAuthorizationStatus> = _status.asStateFlow()

    @Volatile private var youtubeAccessToken: String? = null
    @Volatile private var authorizedAndroidAccount: Account? = startupPlayServicesAccount?.account
    @Volatile private var pendingIdentity: SignedInIdentity? =
        startupPlayServicesAccount?.let(::identityFromGoogleSignIn) ?: rememberedIdentity

    fun effectiveWebClientId(): String {
        val buildClient = normalizeClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        if (buildClient.isNotBlank()) return buildClient
        return normalizeClientId(
            legacySettingsPrefs.getString(LEGACY_CLIENT_ID_KEY, "").orEmpty()
        )
    }

    fun isIdentityConfigured(): Boolean = effectiveWebClientId().isNotBlank()

    fun googlePlayServicesSignInIntent(activity: Activity): Intent =
        GoogleSignIn.getClient(activity, basicGoogleSignInOptions()).signInIntent

    fun googleAccountChooserIntent(): Intent =
        AccountManager.newChooseAccountIntent(
            null,
            null,
            arrayOf("com.google"),
            null,
            null,
            null,
            null
        )

    fun completeGoogleAccountChooser(data: Intent?): Result<GoogleAccountProfile> =
        runCatching {
            val intent = requireNotNull(data) { "Google account selection was cancelled" }
            val name = intent.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                ?.takeIf { it.isNotBlank() }
                ?: error("Google account name was not returned")
            val type = intent.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE)
                ?.takeIf { it.isNotBlank() }
                ?: "com.google"
            require(type == "com.google") { "Selected account is not a Google account" }

            val account = Account(name, type)
            authorizedAndroidAccount = account
            publishGoogleIdentity(
                SignedInIdentity(
                    uniqueId = name.lowercase(),
                    email = name,
                    displayName = name.substringBefore('@').ifBlank { "Google Account" },
                    photoUrl = null
                )
            )
        }.onFailure(::recordExplicitFailure)

    fun completeGooglePlayServicesSignIn(data: Intent?): Result<GoogleAccountProfile> =
        runCatching {
            val intent = requireNotNull(data) { "Google sign-in was cancelled" }
            val google = GoogleSignIn.getSignedInAccountFromIntent(intent)
                .getResult(ApiException::class.java)
                ?: error("Google did not return an account")

            authorizedAndroidAccount = google.account
            publishGoogleIdentity(identityFromGoogleSignIn(google))
        }.onFailure(::recordExplicitFailure)

    suspend fun restoreYoutubeAuthorization(): Result<Boolean> {
        val liveGoogle = GoogleSignIn.getLastSignedInAccount(appContext)
        if (liveGoogle != null) {
            authorizedAndroidAccount = liveGoogle.account
            pendingIdentity = identityFromGoogleSignIn(liveGoogle)
            publishGoogleIdentity(pendingIdentity!!)
        }

        val identity = pendingIdentity ?: return Result.success(false)

        return runCatching {
            val builder = AuthorizationRequest.builder()
                .setRequestedScopes(YOUTUBE_SCOPES)
            identity.email.takeIf { it.isNotBlank() }?.let {
                builder.setAccount(Account(it, "com.google"))
            }

            val result = Identity.getAuthorizationClient(appContext)
                .authorize(builder.build())
                .await()

            if (result.hasResolution()) {
                youtubeAccessToken = null
                if (_account.value == null) _status.value = YoutubeAuthorizationStatus()
                false
            } else {
                completeAuthorizationResult(result)
                true
            }
        }.recover {
            youtubeAccessToken = null
            authorizedAndroidAccount = liveGoogle?.account
            _status.value = if (_account.value != null) {
                YoutubeAuthorizationStatus(
                    YoutubeAuthorizationState.CONNECTED,
                    "Google connected"
                )
            } else {
                YoutubeAuthorizationStatus()
            }
            false
        }
    }

    suspend fun beginYoutubeAuthorization(
        activity: Activity
    ): Result<YoutubeAuthorizationOutcome> {
        _status.value = YoutubeAuthorizationStatus(YoutubeAuthorizationState.CONNECTING)

        return runCatching {
            val clientId = effectiveWebClientId()
            require(clientId.isNotBlank()) {
                "Credential Manager requires a Google Web OAuth client ID"
            }

            val identity = signInGoogleIdentity(activity, clientId)
            pendingIdentity = identity
            val profile = publishGoogleIdentity(identity)

            runCatching {
                val result = authorizeForIdentity(activity, identity)
                if (result.hasResolution()) {
                    val pendingIntent = requireNotNull(result.pendingIntent) {
                        "YouTube authorization unavailable"
                    }
                    YoutubeAuthorizationOutcome(
                        profile = profile,
                        resolution = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    )
                } else {
                    YoutubeAuthorizationOutcome(profile = completeAuthorizationResult(result))
                }
            }.getOrElse {
                authorizeWithGoogleAuthUtil(identity)
            }
        }.recoverCatching { error ->
            _account.value?.let {
                preserveGoogleIdentityAfterYoutubeFailure(error)
                return@recoverCatching YoutubeAuthorizationOutcome(profile = it)
            }
            recordExplicitFailure(error)
            throw decorateAuthorizationError(error)
        }
    }

    suspend fun authorizeYoutubeForCurrentIdentity(
        activity: Activity
    ): Result<YoutubeAuthorizationOutcome> {
        val identity = pendingIdentity
            ?: _account.value?.let(::identityFromProfile)
            ?: return Result.failure(IllegalStateException("Google account is not signed in"))

        _status.value = YoutubeAuthorizationStatus(
            YoutubeAuthorizationState.CONNECTING,
            "Requesting YouTube access"
        )

        return runCatching {
            // Independent Google Play services access-token path. GoogleAuthUtil is deprecated,
            // but remains a documented direct-token fallback when AuthorizationClient is not
            // completing on a device. UserRecoverableAuthException carries Google's consent UI.
            runCatching {
                authorizeWithGoogleAuthUtil(identity)
            }.getOrElse {
                val result = authorizeForIdentity(activity, identity)
                if (result.hasResolution()) {
                    val pendingIntent = requireNotNull(result.pendingIntent) {
                        "YouTube authorization unavailable"
                    }
                    YoutubeAuthorizationOutcome(
                        profile = _account.value,
                        resolution = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    )
                } else {
                    YoutubeAuthorizationOutcome(profile = completeAuthorizationResult(result))
                }
            }
        }.recoverCatching { error ->
            _account.value?.let {
                preserveGoogleIdentityAfterYoutubeFailure(error)
                return@recoverCatching YoutubeAuthorizationOutcome(profile = it)
            }
            recordExplicitFailure(error)
            throw decorateAuthorizationError(error)
        }
    }

    suspend fun completeYoutubeAuthorization(
        activity: Activity,
        data: Intent?
    ): Result<GoogleAccountProfile> {
        _status.value = YoutubeAuthorizationStatus(
            YoutubeAuthorizationState.CONNECTING,
            "Completing YouTube access"
        )
        return runCatching {
            val resultIntent = requireNotNull(data) { "Authorization cancelled" }
            val result = Identity.getAuthorizationClient(activity)
                .getAuthorizationResultFromIntent(resultIntent)
            completeAuthorizationResult(result)
        }.recoverCatching { error ->
            _account.value?.let {
                preserveGoogleIdentityAfterYoutubeFailure(error)
                return@recoverCatching it
            }
            recordExplicitFailure(error)
            throw decorateAuthorizationError(error)
        }
    }

    fun currentYoutubeAccessToken(): String? = youtubeAccessToken

    fun oauthRegistrationInfo(): String {
        val source = when {
            normalizeClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID).isNotBlank() -> "build"
            normalizeClientId(
                legacySettingsPrefs.getString(LEGACY_CLIENT_ID_KEY, "").orEmpty()
            ).isNotBlank() -> "saved"
            else -> "none"
        }
        return "Package ${appContext.packageName} • SHA-1 ${signingCertificateSha1()} • Web client $source"
    }

    suspend fun signOut() {
        youtubeAccessToken?.let { token ->
            runCatching {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    GoogleAuthUtil.clearToken(appContext, token)
                }
            }
        }

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
        runCatching {
            GoogleSignIn.getClient(appContext, basicGoogleSignInOptions())
                .signOut()
                .await()
        }

        prefs.edit().clear().apply()
        legacyIdentityPrefs.edit().clear().apply()
        pendingIdentity = null
        youtubeAccessToken = null
        authorizedAndroidAccount = null
        _account.value = null
        _status.value = YoutubeAuthorizationStatus()
    }

    private suspend fun authorizeForIdentity(
        context: Context,
        identity: SignedInIdentity
    ): AuthorizationResult {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(YOUTUBE_SCOPES)

        if (identity.email.isNotBlank()) {
            builder.setAccount(Account(identity.email, "com.google"))
        } else {
            builder.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
        }

        return Identity.getAuthorizationClient(context)
            .authorize(builder.build())
            .await()
    }

    private suspend fun signInGoogleIdentity(
        activity: Activity,
        clientId: String
    ): SignedInIdentity {
        val option = GetSignInWithGoogleOption.Builder(clientId)
            .setNonce(generateNonce())
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
        val claims = parseGoogleIdTokenClaims(google.idToken)
        @Suppress("DEPRECATION")
        val legacyId = google.id

        val email = claims.optString("email")
            .takeIf { it.isNotBlank() }
            ?: legacyId.takeIf { it.contains("@") }.orEmpty()

        val uniqueId = claims.optString("sub")
            .takeIf { it.isNotBlank() }
            ?: legacyId.takeIf { it.isNotBlank() }
            ?: email.lowercase().takeIf { it.isNotBlank() }
            ?: error("Google account identity unavailable")

        return SignedInIdentity(
            uniqueId = uniqueId,
            email = email,
            displayName = google.displayName.orEmpty(),
            photoUrl = google.profilePictureUri?.toString()
        )
    }

    private fun identityFromGoogleSignIn(account: GoogleSignInAccount): SignedInIdentity {
        val email = account.email.orEmpty()
        val uniqueId = account.id?.takeIf { it.isNotBlank() }
            ?: email.lowercase().takeIf { it.isNotBlank() }
            ?: error("Google account identity unavailable")
        return SignedInIdentity(
            uniqueId = uniqueId,
            email = email,
            displayName = account.displayName.orEmpty(),
            photoUrl = account.photoUrl?.toString()
        )
    }

    private fun identityFromProfile(profile: GoogleAccountProfile): SignedInIdentity =
        SignedInIdentity(
            uniqueId = profile.uniqueId,
            email = profile.email,
            displayName = profile.displayName,
            photoUrl = profile.photoUrl
        )

    private fun publishGoogleIdentity(identity: SignedInIdentity): GoogleAccountProfile {
        persistIdentity(identity)
        pendingIdentity = identity
        val profile = profileFromIdentity(identity)
        _account.value = profile
        _status.value = YoutubeAuthorizationStatus(
            YoutubeAuthorizationState.CONNECTED,
            "Google signed in",
            youtubeAuthorized = false
        )
        return profile
    }

    private fun profileFromIdentity(identity: SignedInIdentity): GoogleAccountProfile =
        GoogleAccountProfile(
            uniqueId = identity.uniqueId
                .ifBlank { identity.email.lowercase() }
                .ifBlank { "google-account" },
            email = identity.email,
            displayName = identity.displayName
                .ifBlank { identity.email.substringBefore('@') }
                .ifBlank { "Google Account" },
            photoUrl = identity.photoUrl
        )

    private suspend fun completeAuthorizationResult(
        result: AuthorizationResult
    ): GoogleAccountProfile {
        require(result.grantedScopes.contains(YOUTUBE_READONLY)) {
            "YouTube permission denied"
        }
        val token = result.accessToken?.takeIf { it.isNotBlank() }
            ?: error("YouTube access token unavailable")

        @Suppress("DEPRECATION")
        val legacy = result.toGoogleSignInAccount()
        val identity = pendingIdentity
            ?: legacy?.let(::identityFromGoogleSignIn)
            ?: loadPersistedIdentity()
            ?: error("Google identity unavailable")

        authorizedAndroidAccount =
            identity.email.takeIf { it.isNotBlank() }?.let { Account(it, "com.google") }
                ?: legacy?.account

        return completeAccessToken(token, identity)
    }

    private suspend fun authorizeWithGoogleAuthUtil(
        identity: SignedInIdentity
    ): YoutubeAuthorizationOutcome {
        val androidAccount = authorizedAndroidAccount
            ?: identity.email.takeIf { it.isNotBlank() }?.let { Account(it, "com.google") }
            ?: error("Google account email is unavailable for direct YouTube authorization")

        return try {
            val token = withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                GoogleAuthUtil.getToken(
                    appContext,
                    androidAccount,
                    "oauth2:$YOUTUBE_READONLY"
                )
            }.takeIf { it.isNotBlank() }
                ?: error("Google Play services returned an empty YouTube token")

            authorizedAndroidAccount = androidAccount
            YoutubeAuthorizationOutcome(profile = completeAccessToken(token, identity))
        } catch (recoverable: UserRecoverableAuthException) {
            YoutubeAuthorizationOutcome(
                profile = _account.value,
                recoveryIntent = recoverable.intent
            )
        }
    }

    private suspend fun completeAccessToken(
        token: String,
        identity: SignedInIdentity
    ): GoogleAccountProfile {
        val verified = fetchYoutubeIdentity(token)
        val email = identity.email
        val displayName = identity.displayName
            .ifBlank { verified.channelTitle }
            .ifBlank { email.substringBefore('@') }
            .ifBlank { "YouTube" }

        val profile = GoogleAccountProfile(
            uniqueId = identity.uniqueId
                .ifBlank { verified.channelId }
                .ifBlank { email.lowercase() }
                .ifBlank { error("Google identity unavailable") },
            email = email,
            displayName = displayName,
            photoUrl = identity.photoUrl ?: verified.photoUrl,
            youtubeChannelId = verified.channelId,
            subscriptionCount = verified.subscriptionCount
        )

        persistIdentity(identityFromProfile(profile))
        pendingIdentity = identityFromProfile(profile)
        youtubeAccessToken = token
        _account.value = profile
        _status.value = YoutubeAuthorizationStatus(
            YoutubeAuthorizationState.CONNECTED,
            "Google + YouTube connected",
            youtubeAuthorized = true
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

    private fun preserveGoogleIdentityAfterYoutubeFailure(error: Throwable) {
        youtubeAccessToken = null
        val profile = _account.value
        _status.value = if (profile != null) {
            YoutubeAuthorizationStatus(
                YoutubeAuthorizationState.CONNECTED,
                "Google signed in • YouTube authorization not active",
                youtubeAuthorized = false
            )
        } else {
            YoutubeAuthorizationStatus(
                YoutubeAuthorizationState.ERROR,
                decorateAuthorizationError(error).message.orEmpty()
            )
        }
    }

    private fun recordExplicitFailure(error: Throwable) {
        if (_account.value != null) {
            preserveGoogleIdentityAfterYoutubeFailure(error)
            return
        }

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
        youtubeAccessToken = null
        authorizedAndroidAccount = null
        _status.value = YoutubeAuthorizationStatus(state, message)
    }

    private fun decorateAuthorizationError(error: Throwable): Throwable {
        val registration = oauthRegistrationInfo()
        if (error is ApiException && error.statusCode == CommonStatusCodes.DEVELOPER_ERROR) {
            return IllegalStateException(
                "Google rejected this Android OAuth identity • $registration",
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

    /**
     * Migration-only. This value never directly becomes _account.
     */
    private fun loadPersistedIdentity(): SignedInIdentity? {
        val currentUniqueId = prefs.getString(KEY_UNIQUE_ID, null)?.takeIf { it.isNotBlank() }
        if (currentUniqueId != null) {
            return SignedInIdentity(
                uniqueId = currentUniqueId,
                email = prefs.getString(KEY_EMAIL, "").orEmpty(),
                displayName = prefs.getString(KEY_NAME, "").orEmpty(),
                photoUrl = prefs.getString(KEY_PHOTO, null)
            )
        }

        val legacyId =
            legacyIdentityPrefs.getString("unique_id", null)?.takeIf { it.isNotBlank() }
                ?: return null
        val legacy = SignedInIdentity(
            uniqueId = legacyId,
            email = legacyIdentityPrefs.getString("email", "").orEmpty(),
            displayName = legacyIdentityPrefs.getString("display_name", "Google Account")
                .orEmpty()
                .ifBlank { "Google Account" },
            photoUrl = legacyIdentityPrefs.getString("photo_url", null)
        )
        persistIdentity(legacy)
        return legacy
    }

    private fun normalizeClientId(value: String): String {
        val trimmed = value.trim()
        return trimmed.takeIf {
            it.endsWith(".apps.googleusercontent.com", ignoreCase = true)
        }.orEmpty()
    }

    private fun parseGoogleIdTokenClaims(idToken: String): JSONObject {
        val parts = idToken.split('.')
        require(parts.size >= 2) { "Google ID token is malformed" }
        val payload = Base64.decode(
            parts[1],
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return JSONObject(payload.toString(Charsets.UTF_8))
    }

    private fun basicGoogleSignInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()

    private fun generateNonce(byteLength: Int = 32): String {
        val randomBytes = ByteArray(byteLength)
        SecureRandom().nextBytes(randomBytes)
        return Base64.encodeToString(
            randomBytes,
            Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
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
}
