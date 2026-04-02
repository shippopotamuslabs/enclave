package com.browntowndev.pocketcrew.core.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiKeyManagerTest {
    private lateinit var apiKeyManager: ApiKeyManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        apiKeyManager = ApiKeyManager { prefs }
    }

    @Test
    fun `save and retrieve key by credentialAlias`() {
        assertNull(apiKeyManager.get("my_openai_key"))
        apiKeyManager.save("my_openai_key", "sk-abc123")
        assertEquals("sk-abc123", apiKeyManager.get("my_openai_key"))
    }

    @Test
    fun `delete key by credentialAlias`() {
        apiKeyManager.save("my_openai_key", "sk-abc123")
        apiKeyManager.delete("my_openai_key")
        assertNull(apiKeyManager.get("my_openai_key"))
    }

    @Test
    fun `retrieve key for non-existent alias returns null`() {
        assertNull(apiKeyManager.get("nonexistent_alias"))
    }

    @Test
    fun `overwrite existing key for same alias`() {
        apiKeyManager.save("my_key", "old_key")
        apiKeyManager.save("my_key", "new_key")
        assertEquals("new_key", apiKeyManager.get("my_key"))
    }

    @Test
    fun `ApiKeyManager must use credentialAlias string not numeric ID`() {
        apiKeyManager.save("my_key", "sk-secret")
        assertEquals("sk-secret", apiKeyManager.get("my_key"))
        assertNull(apiKeyManager.get("42"))
    }
}
