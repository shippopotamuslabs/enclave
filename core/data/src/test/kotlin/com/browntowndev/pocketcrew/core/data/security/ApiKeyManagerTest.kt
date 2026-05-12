package com.browntowndev.pocketcrew.core.data.security

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiKeyManagerTest {
    private lateinit var apiKeyManager: ApiKeyManager
    private lateinit var testScope: CoroutineScope

    @Before
    fun setup() {
        testScope = CoroutineScope(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = testScope,
                produceFile = { File(context.cacheDir, "test_datastore.preferences_pb") },
            )
        apiKeyManager = ApiKeyManager({ prefs }, dataStore, testScope)
    }

    @After
    fun tearDown() {
        testScope.cancel()
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

    @Test
    fun `has returns true only when alias is stored`() {
        assertFalse(apiKeyManager.has(ApiKeyManager.TAVILY_SEARCH_ALIAS))
        apiKeyManager.save(ApiKeyManager.TAVILY_SEARCH_ALIAS, "tavily-secret")
        assertTrue(apiKeyManager.has(ApiKeyManager.TAVILY_SEARCH_ALIAS))
    }

    @Test
    fun `save blank key clears stored alias`() {
        apiKeyManager.save(ApiKeyManager.TAVILY_SEARCH_ALIAS, "tavily-secret")
        apiKeyManager.save(ApiKeyManager.TAVILY_SEARCH_ALIAS, "   ")

        assertFalse(apiKeyManager.has(ApiKeyManager.TAVILY_SEARCH_ALIAS))
        assertNull(apiKeyManager.get(ApiKeyManager.TAVILY_SEARCH_ALIAS))
    }

    @Test
    fun `save tracks pending sync`() =
        runTest {
            apiKeyManager.save("new_key", "secret")
            apiKeyManager.claimPending()
            assertTrue(apiKeyManager.getClaimedSyncAliases().contains("new_key"))
            assertFalse(apiKeyManager.getClaimedDeleteAliases().contains("new_key"))
        }

    @Test
    fun `delete tracks pending delete`() =
        runTest {
            apiKeyManager.save("key_to_delete", "secret")
            apiKeyManager.claimPending()
            apiKeyManager.delete("key_to_delete")
            apiKeyManager.claimPending()

            assertFalse(apiKeyManager.getClaimedSyncAliases().contains("key_to_delete"))
            assertTrue(apiKeyManager.getClaimedDeleteAliases().contains("key_to_delete"))
        }

    @Test
    fun `clearClaimedSync removes alias from set`() =
        runTest {
            apiKeyManager.save("key1", "secret")
            apiKeyManager.save("key2", "secret")
            apiKeyManager.claimPending()

            apiKeyManager.clearClaimedSync(listOf("key1"))

            val claimed = apiKeyManager.getClaimedSyncAliases()
            assertFalse(claimed.contains("key1"))
            assertTrue(claimed.contains("key2"))
        }
}
