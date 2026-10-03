package com.drivedeck.tubedeck.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class GoogleDeviceCodePrompt(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresAtEpochMs: Long,
    val intervalSeconds: Int
)

data class GoogleDeviceTokenSet(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochMs: Long,
    val scope: String,
    val idToken: String?
)

internal class GoogleDeviceCodeOAuth(
    context: Context,
    private val clientId: String,
    private val clientSecret: String
) {
    companion object {
        private const val DEVICE_CODE_ENDPOINT = "https://oauth2.googleapis.com/device/code"
        private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        private const val REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke"
        private const val SCOPE =
            "openid email profile https://www.googleapis.com/auth/youtube.readonly"

        private const val PREFS = "mytube_google_device_oauth_v1"
        private const val KEY_REFRESH_TOKEN = "refresh_token_ciphertext"
        private const val KEYSTORE_ALIAS = "mytube_google_device_refresh_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isConfigured(): Boolean =
        clientId.isNotBlank() && clientSecret.isNotBlank()

    suspend fun requestDeviceCode(): GoogleDeviceCodePrompt = withContext(Dispatchers.IO) {
        check(isConfigured()) { "MyTube device-code OAuth is not configured" }

        val json = postForm(
            DEVICE_CODE_ENDPOINT,
            linkedMapOf(
                "client_id" to clientId,
                "scope" to SCOPE
            )
        )

        val deviceCode = json.optString("device_code").takeIf { it.isNotBlank() }
            ?: error("Google did not return a device code")
        val userCode = json.optString("user_code").takeIf { it.isNotBlank() }
            ?: error("Google did not return a user code")
        val verificationUrl = json.optString("verification_url")
            .ifBlank { json.optString("verification_uri") }
            .takeIf { it.isNotBlank() }
            ?: error("Google did not return a verification URL")
        val expiresIn = json.optLong("expires_in", 0L).coerceAtLeast(60L)
        val interval = json.optInt("interval", 5).coerceAtLeast(1)

        GoogleDeviceCodePrompt(
            deviceCode = deviceCode,
            userCode = userCode,
            verificationUrl = verificationUrl,
            expiresAtEpochMs = System.currentTimeMillis() + expiresIn * 1_000L,
            intervalSeconds = interval
        )
    }

    suspend fun pollForToken(
        prompt: GoogleDeviceCodePrompt,
        onStatus: (String) -> Unit = {}
    ): GoogleDeviceTokenSet {
        var intervalSeconds = prompt.intervalSeconds.coerceAtLeast(1)

        while (System.currentTimeMillis() < prompt.expiresAtEpochMs) {
            delay(intervalSeconds * 1_000L)

            val response = withContext(Dispatchers.IO) {
                postFormAllowError(
                    TOKEN_ENDPOINT,
                    linkedMapOf(
                        "client_id" to clientId,
                        "client_secret" to clientSecret,
                        "device_code" to prompt.deviceCode,
                        "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
                    )
                )
            }

            val errorCode = response.optString("error")
            when {
                errorCode.isBlank() && response.optString("access_token").isNotBlank() -> {
                    val token = tokenSetFromJson(response)
                    token.refreshToken?.takeIf { it.isNotBlank() }?.let(::storeRefreshToken)
                    onStatus("Google + YouTube connected")
                    return token
                }
                errorCode == "authorization_pending" -> onStatus("Waiting for Google approval…")
                errorCode == "slow_down" -> {
                    intervalSeconds += 5
                    onStatus("Waiting for Google approval…")
                }
                errorCode == "access_denied" -> error("Google sign-in was denied")
                errorCode == "expired_token" -> error("Google sign-in code expired")
                errorCode.isNotBlank() -> {
                    val description = response.optString("error_description")
                    error(description.ifBlank { "Google device authorization failed: $errorCode" })
                }
            }
        }

        error("Google sign-in code expired")
    }

    suspend fun refreshStoredToken(): GoogleDeviceTokenSet? = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext null
        val refreshToken = readRefreshToken() ?: return@withContext null

        val json = postForm(
            TOKEN_ENDPOINT,
            linkedMapOf(
                "client_id" to clientId,
                "client_secret" to clientSecret,
                "refresh_token" to refreshToken,
                "grant_type" to "refresh_token"
            )
        )

        val refreshed = tokenSetFromJson(json, existingRefreshToken = refreshToken)
        refreshed.refreshToken?.takeIf { it.isNotBlank() }?.let(::storeRefreshToken)
        refreshed
    }

    suspend fun revokeAndClear() = withContext(Dispatchers.IO) {
        val refreshToken = readRefreshToken()
        if (!refreshToken.isNullOrBlank()) {
            runCatching {
                postFormAllowError(
                    REVOKE_ENDPOINT,
                    linkedMapOf("token" to refreshToken)
                )
            }
        }
        clearRefreshToken()
    }

    fun hasStoredRefreshToken(): Boolean =
        prefs.contains(KEY_REFRESH_TOKEN)

    private fun tokenSetFromJson(
        json: JSONObject,
        existingRefreshToken: String? = null
    ): GoogleDeviceTokenSet {
        val accessToken = json.optString("access_token").takeIf { it.isNotBlank() }
            ?: error("Google token response did not include an access token")
        val expiresIn = json.optLong("expires_in", 3600L).coerceAtLeast(60L)
        val refreshToken = json.optString("refresh_token")
            .takeIf { it.isNotBlank() }
            ?: existingRefreshToken
        return GoogleDeviceTokenSet(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtEpochMs = System.currentTimeMillis() + expiresIn * 1_000L,
            scope = json.optString("scope"),
            idToken = json.optString("id_token").takeIf { it.isNotBlank() }
        )
    }

    private fun postForm(url: String, fields: Map<String, String>): JSONObject {
        val json = postFormAllowError(url, fields)
        val errorCode = json.optString("error")
        if (errorCode.isNotBlank()) {
            val description = json.optString("error_description")
            error(description.ifBlank { "Google OAuth error: $errorCode" })
        }
        return json
    }

    private fun postFormAllowError(url: String, fields: Map<String, String>): JSONObject {
        val body = fields.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }.toByteArray(StandardCharsets.UTF_8)

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
            fixedLengthStreamingMode(body.size)
        }

        try {
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return if (response.isBlank()) JSONObject() else JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun storeRefreshToken(refreshToken: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(refreshToken.toByteArray(StandardCharsets.UTF_8))
        val packed = ByteArray(cipher.iv.size + encrypted.size)
        System.arraycopy(cipher.iv, 0, packed, 0, cipher.iv.size)
        System.arraycopy(encrypted, 0, packed, cipher.iv.size, encrypted.size)
        prefs.edit()
            .putString(KEY_REFRESH_TOKEN, Base64.encodeToString(packed, Base64.NO_WRAP))
            .apply()
    }

    private fun readRefreshToken(): String? {
        val encoded = prefs.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }
            ?: return null
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > 12)
            val iv = packed.copyOfRange(0, 12)
            val encrypted = packed.copyOfRange(12, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun clearRefreshToken() {
        prefs.edit().remove(KEY_REFRESH_TOKEN).apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return keyGenerator.generateKey()
    }
}
