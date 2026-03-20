package com.browntowndev.pocketcrew.core.data.download

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DownloadSessionManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var sessionManager: DownloadSessionManager

    @BeforeEach
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockSharedPreferences = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        every { mockContext.getSharedPreferences(any(), any()) } returns mockSharedPreferences
        every { mockSharedPreferences.edit() } returns mockEditor
        every { mockSharedPreferences.getString(any(), any()) } returns null
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.remove(any()) } returns mockEditor
        every { mockEditor.apply() } returns Unit

        sessionManager = DownloadSessionManager(mockContext)
    }

    @Test
    fun createNewSession_generatesUniqueId() {
        val session1 = sessionManager.createNewSession()
        val session2 = sessionManager.createNewSession()

        assertNotEquals(session1, session2)
    }

    @Test
    fun isSessionStale_false_whenNoActiveSession() {
        val result = sessionManager.isSessionStale("any-id")

        assertFalse(result)
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
}
