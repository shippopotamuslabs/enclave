package com.browntowndev.pocketcrew.core.data.security

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for ApiKeyManager.
 * Uses a mocked SharedPreferences (since EncryptedSharedPreferences
 * requires Android Keystore which is not available in unit tests).
 */
class ApiKeyManagerTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private val storage = mutableMapOf<String, String?>()

    @BeforeEach
    fun setUp() {
        storage.clear()
        editor = mockk(relaxed = true)
        prefs = mockk(relaxed = true)

        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } answers {
            storage[firstArg()] = secondArg()
            editor
        }
        every { editor.remove(any()) } answers {
            storage.remove(firstArg<String>())
            editor
        }
        every { prefs.getString(any(), any()) } answers {
            storage[firstArg()]
        }
    }

    @Test
    fun `save and retrieve a key`() {
        val keyManager = createApiKeyManagerWithMockedPrefs()

        keyManager.save(1L, "sk-ant-secret")
        val result = keyManager.get(1L)

        assertEquals("sk-ant-secret", result)
    }

    @Test
    fun `delete removes the key`() {
        val keyManager = createApiKeyManagerWithMockedPrefs()
        keyManager.save(1L, "sk-ant-secret")

        keyManager.delete(1L)
        val result = keyManager.get(1L)

        assertNull(result)
    }

    @Test
    fun `get returns null for non-existent key`() {
        val keyManager = createApiKeyManagerWithMockedPrefs()

        val result = keyManager.get(999L)

        assertNull(result)
    }

    private fun createApiKeyManagerWithMockedPrefs(): ApiKeyManager {
        return ApiKeyManager({ prefs })
    }
}
