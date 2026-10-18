package com.browntowndev.enclave.core.data.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class DownloadSessionManagerTest {
    private lateinit var context: Context
    private lateinit var sessionManager: DownloadSessionManager

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        sessionManager = DownloadSessionManager(context)
    }

    private fun persistedSessionId(): String? =
        context
            .getSharedPreferences("model_download_prefs", Context.MODE_PRIVATE)
            .getString("download_session_id", null)

    @Test
    fun createNewSession_generatesUniqueId() {
        val session1 = sessionManager.createNewSession()
        val session2 = sessionManager.createNewSession()

        assertNotEquals(session1, session2)
    }

    @Test
    fun createNewSession_persistsWithApply() {
        val sessionId = sessionManager.createNewSession()

        // The new id is durably written to SharedPreferences under the session key.
        assertEquals(sessionId, persistedSessionId())
        assertEquals(sessionId, sessionManager.currentSessionId)
    }

    @Test
    fun isSessionStale_true_whenNoActiveSession() {
        val result = sessionManager.isSessionStale("any-id")

        assertTrue(result)
    }

    @Test
    fun isSessionStale_true_whenWorkSessionNull() {
        sessionManager.createNewSession()

        val result = sessionManager.isSessionStale(null)

        assertTrue(result)
    }

    @Test
    fun isSessionStale_true_whenIdsMismatch() {
        sessionManager.createNewSession()

        val result = sessionManager.isSessionStale("different-id")

        assertTrue(result)
    }

    @Test
    fun isSessionStale_false_whenIdsMatch() {
        val sessionId = sessionManager.createNewSession()

        val result = sessionManager.isSessionStale(sessionId)

        assertFalse(result)
    }

    @Test
    fun clearSession_clearsCachedSessionAndRemovesFromPrefs() {
        sessionManager.createNewSession()

        sessionManager.clearSession()

        assertNull(sessionManager.currentSessionId)
        // The persisted key is removed as well.
        assertNull(persistedSessionId())
    }
}
