package com.browntowndev.pocketcrew.core.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

import androidx.annotation.VisibleForTesting

/**
 * Manages API key storage using EncryptedSharedPreferences.
 * Keys are AES-256 GCM encrypted via Android Keystore MasterKey.
 * Room stores everything EXCEPT the raw key — this is the only place keys exist.
 */
class ApiKeyManager @VisibleForTesting internal constructor(
    private val prefsProvider: () -> SharedPreferences
) {
    @Inject constructor(
        @ApplicationContext context: Context
    ) : this({
        EncryptedSharedPreferences.create(
            context,
            "byok_api_keys",
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    })

    private val prefs: SharedPreferences by lazy { prefsProvider() }

    fun save(apiModelId: Long, apiKey: String) {
        prefs.edit().putString("api_key_$apiModelId", apiKey).apply()
    }

    fun get(apiModelId: Long): String? {
        return prefs.getString("api_key_$apiModelId", null)
    }

    fun delete(apiModelId: Long) {
        prefs.edit().remove("api_key_$apiModelId").apply()
    }
}
