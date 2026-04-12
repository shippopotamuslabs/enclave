package com.browntowndev.pocketcrew.core.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import androidx.annotation.VisibleForTesting

class ApiKeyManager @VisibleForTesting internal constructor(
    private val prefsProvider: () -> SharedPreferences
) {
    companion object {
        const val TAVILY_SEARCH_ALIAS = "tavily_web_search"
    }

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

    fun save(credentialAlias: String, apiKey: String) {
        val normalizedKey = apiKey.trim()
        if (normalizedKey.isEmpty()) {
            delete(credentialAlias)
            return
        }
        prefs.edit().putString(credentialAlias, normalizedKey).commit()
    }

    fun get(credentialAlias: String): String? {
        return prefs.getString(credentialAlias, null)
    }

    fun has(credentialAlias: String): Boolean = !get(credentialAlias).isNullOrBlank()

    fun delete(credentialAlias: String) {
        prefs.edit().remove(credentialAlias).commit()
    }
}
