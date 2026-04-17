package ca.kloosterman.bccmediatv.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class StoredTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val idToken: String = ""
)

@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "bccmedia_tokens",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(tokens: StoredTokens, userId: String) {
        prefs.edit()
            .putString("profile_${userId}_access_token", tokens.accessToken)
            .putString("profile_${userId}_refresh_token", tokens.refreshToken)
            .putLong("profile_${userId}_expires_at", tokens.expiresAt)
            .putString("profile_${userId}_id_token", tokens.idToken)
            .apply()
    }

    fun load(userId: String): StoredTokens? {
        val accessToken = prefs.getString("profile_${userId}_access_token", null) ?: return null
        val refreshToken = prefs.getString("profile_${userId}_refresh_token", null) ?: return null
        val expiresAt = prefs.getLong("profile_${userId}_expires_at", 0L)
        val idToken = prefs.getString("profile_${userId}_id_token", "") ?: ""
        return StoredTokens(accessToken, refreshToken, expiresAt, idToken)
    }

    fun clear(userId: String) {
        prefs.edit()
            .remove("profile_${userId}_access_token")
            .remove("profile_${userId}_refresh_token")
            .remove("profile_${userId}_expires_at")
            .remove("profile_${userId}_id_token")
            .apply()
    }

    // Legacy: reads old un-prefixed keys written before multi-profile support
    fun loadLegacy(): StoredTokens? {
        val accessToken = prefs.getString("access_token", null) ?: return null
        val refreshToken = prefs.getString("refresh_token", null) ?: return null
        val expiresAt = prefs.getLong("expires_at", 0L)
        val idToken = prefs.getString("id_token", "") ?: ""
        return StoredTokens(accessToken, refreshToken, expiresAt, idToken)
    }

    fun clearLegacy() {
        prefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("expires_at")
            .remove("id_token")
            .apply()
    }
}
