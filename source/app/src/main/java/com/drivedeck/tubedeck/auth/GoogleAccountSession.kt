package com.drivedeck.tubedeck.auth

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GoogleAccountProfile(
    val uniqueId: String,
    val email: String,
    val displayName: String,
    val photoUrl: String?
)

/**
 * App-wide selected Google account.
 *
 * MyTube only needs a simple account identity for a YouTube-like account chooser across Home,
 * Browser and ExoPlayer. Android's account chooser is used instead of asking the user for an
 * OAuth client id in the app UI. No password or Google auth token is read or stored.
 */
class GoogleAccountSession private constructor(context: Context) {
    companion object {
        @Volatile private var instance: GoogleAccountSession? = null

        fun get(context: Context): GoogleAccountSession =
            instance ?: synchronized(this) {
                instance ?: GoogleAccountSession(context.applicationContext).also { instance = it }
            }

        fun chooserIntent(currentEmail: String? = null): Intent =
            AccountManager.newChooseAccountIntent(
                currentEmail?.takeIf { it.isNotBlank() }?.let {
                    android.accounts.Account(it, "com.google")
                },
                null,
                arrayOf("com.google"),
                "Choose the Google Account to use with MyTube",
                null,
                null,
                null
            )
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("google_account_session", Context.MODE_PRIVATE)
    private val _account = MutableStateFlow(loadProfile())
    val account: StateFlow<GoogleAccountProfile?> = _account.asStateFlow()

    fun selectDeviceAccount(accountName: String): GoogleAccountProfile {
        val email = accountName.trim()
        require(email.isNotBlank()) { "Google account selection was empty" }
        val profile = GoogleAccountProfile(
            uniqueId = email.lowercase(),
            email = email,
            displayName = email.substringBefore('@').ifBlank { "Google Account" },
            photoUrl = null
        )
        saveProfile(profile)
        _account.value = profile
        return profile
    }

    suspend fun signOut() {
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
}
