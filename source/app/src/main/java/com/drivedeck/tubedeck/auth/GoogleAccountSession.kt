package com.drivedeck.tubedeck.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import android.util.Base64

data class GoogleAccountProfile(
    val uniqueId: String,
    val email: String,
    val displayName: String,
    val photoUrl: String?
)

class GoogleAccountSession private constructor(context: Context) {
    companion object {
        @Volatile private var instance: GoogleAccountSession? = null

        fun get(context: Context): GoogleAccountSession =
            instance ?: synchronized(this) {
                instance ?: GoogleAccountSession(context.applicationContext).also { instance = it }
            }
    }

    private val appContext = context.applicationContext
    private val credentialManager = CredentialManager.create(appContext)
    private val prefs = appContext.getSharedPreferences("google_account_session", Context.MODE_PRIVATE)
    private val _account = MutableStateFlow(loadProfile())
    val account: StateFlow<GoogleAccountProfile?> = _account.asStateFlow()

    suspend fun signIn(activity: Activity, webClientId: String): Result<GoogleAccountProfile> = runCatching {
        require(webClientId.isNotBlank()) {
            "Google sign-in needs a Web OAuth client ID. Add it in MyTube Settings or the GOOGLE_WEB_CLIENT_ID build secret."
        }

        val option = GetSignInWithGoogleOption.Builder(webClientId.trim())
            .setNonce(generateNonce())
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val result = CredentialManager.create(activity).getCredential(
            context = activity,
            request = request
        )
        val custom = result.credential as? CustomCredential
            ?: error("Google did not return a supported credential")
        require(custom.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected Google credential type"
        }

        val google = GoogleIdTokenCredential.createFrom(custom.data)
        @Suppress("DEPRECATION")
        val accountId = google.id
        val profile = GoogleAccountProfile(
            uniqueId = accountId,
            email = accountId,
            displayName = google.displayName?.takeIf { it.isNotBlank() }
                ?: accountId.substringBefore('@').ifBlank { "Google Account" },
            photoUrl = google.profilePictureUri?.toString()
        )
        saveProfile(profile)
        _account.value = profile
        profile
    }

    suspend fun signOut() {
        runCatching {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        }
        prefs.edit().clear().apply()
        _account.value = null
    }

    private fun saveProfile(profile: GoogleAccountProfile) {
        prefs.edit()
            .putString("unique_id", profile.uniqueId)
            .putString("email", profile.email)
            .putString("display_name", profile.displayName)
            .putString("photo_url", profile.photoUrl)
            .apply()
    }

    private fun loadProfile(): GoogleAccountProfile? {
        val uniqueId = prefs.getString("unique_id", null)?.takeIf { it.isNotBlank() } ?: return null
        return GoogleAccountProfile(
            uniqueId = uniqueId,
            email = prefs.getString("email", "").orEmpty(),
            displayName = prefs.getString("display_name", "Google Account").orEmpty().ifBlank { "Google Account" },
            photoUrl = prefs.getString("photo_url", null)
        )
    }

    private fun generateNonce(byteLength: Int = 32): String {
        val randomBytes = ByteArray(byteLength)
        SecureRandom().nextBytes(randomBytes)
        return Base64.encodeToString(
            randomBytes,
            Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
        )
    }
}
