package com.browntowndev.pocketcrew.data.download

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

@Singleton
class DownloadSessionManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "model_download_prefs"
        private const val KEY_PERSISTENT_SESSION_ID = "download_session_id"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Volatile
    private var cachedSessionId: String? = null

    val currentSessionId: String?
        get() {
            if (cachedSessionId == null) {
                cachedSessionId = prefs.getString(KEY_PERSISTENT_SESSION_ID, null)
            }
            return cachedSessionId
        }

    fun createNewSession(): String {
        val newId = UUID.randomUUID().toString()
        cachedSessionId = newId
        prefs.edit { putString(KEY_PERSISTENT_SESSION_ID, newId) }
        return newId
    }

    fun clearSession() {
        cachedSessionId = null
        prefs.edit { remove(KEY_PERSISTENT_SESSION_ID) }
    }

    fun isSessionStale(workSessionId: String?): Boolean {
        // If no active session, nothing is stale
        val current = currentSessionId ?: return false
        // If work has no session ID, treat as stale (from old app run)
        if (workSessionId == null) return true
        // Only stale if IDs don't match
        return workSessionId != current
    }
}
