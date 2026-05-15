package com.browntowndev.pocketcrew.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import com.browntowndev.pocketcrew.core.data.security.LocalDataEncryptionService
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryImplTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `settingsFlow defaults background inference enabled when preference is absent`() =
        runTest {
            val repository = createRepository(backgroundScope)

            val settings = repository.settingsFlow.first()

            assertTrue(settings.backgroundInferenceEnabled)
        }

    @Test
    fun `updateBackgroundInferenceEnabled persists explicit false`() =
        runTest {
            val repository = createRepository(backgroundScope)

            repository.updateBackgroundInferenceEnabled(false)

            assertFalse(repository.settingsFlow.first().backgroundInferenceEnabled)
        }

    @Test
    fun `updateCustomPromptText stores encrypted value and emits plaintext`() =
        runTest {
            val dataStore = createDataStore(backgroundScope)
            val repository = createRepository(dataStore = dataStore, localEncryption = FakeLocalDataEncryptionService())

            repository.updateCustomPromptText("answer like me")

            assertEquals("PCENC:v1:aes-gcm:answer like me", dataStore.data.first()[customPromptTextKey])
            assertEquals("answer like me", repository.settingsFlow.first().customPromptText)
        }

    @Test
    fun `settingsFlow self-heals plaintext custom prompt text`() =
        runTest {
            val dataStore = createDataStore(backgroundScope)
            dataStore.edit { preferences ->
                preferences[customPromptTextKey] = "plaintext prompt"
            }
            val repository = createRepository(dataStore = dataStore, localEncryption = FakeLocalDataEncryptionService())

            assertEquals("plaintext prompt", repository.settingsFlow.first().customPromptText)

            assertEquals("PCENC:v1:aes-gcm:plaintext prompt", dataStore.data.first()[customPromptTextKey])
        }

    private fun createRepository(scope: CoroutineScope): SettingsRepositoryImpl {
        val dataStore = createDataStore(scope)
        return createRepository(dataStore = dataStore)
    }

    private fun createDataStore(scope: CoroutineScope) =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempDir.resolve("settings-${System.nanoTime()}.preferences_pb").toFile() },
        )

    private fun createRepository(
        dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
        localEncryption: LocalDataEncryptionService = LocalDataEncryptionService.NoOp,
    ): SettingsRepositoryImpl {
        val apiKeyManager =
            mockk<ApiKeyManager> {
                every { has(ApiKeyManager.TAVILY_SEARCH_ALIAS) } returns false
                every { has(ApiKeyManager.EXA_SEARCH_ALIAS) } returns false
                every { has(ApiKeyManager.X_SEARCH_ALIAS) } returns false
            }
        return SettingsRepositoryImpl(
            dataStore = dataStore,
            apiKeyManager = apiKeyManager,
            localEncryption = localEncryption,
        )
    }

    private class FakeLocalDataEncryptionService : LocalDataEncryptionService {
        override fun encryptString(plaintext: String): String = "PCENC:v1:aes-gcm:$plaintext"

        override fun decryptString(stored: String): String = stored.removePrefix("PCENC:v1:aes-gcm:")

        override fun isEncrypted(stored: String): Boolean = stored.startsWith("PCENC:v1:aes-gcm:")

        override fun encryptString(plaintext: String, aad: ByteArray): String = "PCENC:v1:aes-gcm:$plaintext"
        override fun decryptString(stored: String, aad: ByteArray): String = stored.removePrefix("PCENC:v1:aes-gcm:")
    }

    private companion object {
        private val customPromptTextKey = stringPreferencesKey("settings_custom_prompt_text")
    }
}
