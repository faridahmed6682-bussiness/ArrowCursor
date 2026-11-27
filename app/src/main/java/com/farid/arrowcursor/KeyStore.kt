package com.farid.arrowcursor

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object KeyStore {

    private const val PREFS_NAME = "gemini_keys"
    private const val KEY_API_KEY = "gemini_api_key"

    private fun encryptedPrefs(context: Context): SharedPreferences {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            throw IllegalStateException("Could not access the secure key store", e)
        }
    }

    fun saveApiKey(context: Context, key: String) {
        encryptedPrefs(context).edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    fun getApiKey(context: Context): String? {
        return encryptedPrefs(context).getString(KEY_API_KEY, null)
    }

    fun hasApiKey(context: Context): Boolean = !getApiKey(context).isNullOrBlank()
}
