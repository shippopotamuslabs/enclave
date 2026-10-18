package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.security.LocalDataEncryptionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import org.robolectric.annotation.Config as RoboConfig

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(RobolectricExtension::class)
@RoboConfig(sdk = [34])
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
            val fakeEnc = FakeLocalDataEncryptionService()
            val repository = createRepository(dataStore = dataStore, localEncryption = fakeEnc, scope = backgroundScope)

            repository.updateCustomPromptText("answer like me")

            assertEquals("PCENC:v1:aes-gcm:answer like me", dataStore.data.first()[customPromptTextKey])
            assertEquals("answer like me", repository.settingsFlow.first().customPromptText)
            assertEquals(
                "SettingsRepositoryImpl|settings_custom_prompt_text|singleton",
                fakeEnc.lastEncryptAad?.toString(StandardCharsets.UTF_8),
            )
        }

    @Test
    fun `settingsFlow self-heals plaintext custom prompt text`() =
        runTest {
            val dataStore = createDataStore(backgroundScope)
            dataStore.edit { preferences ->
                preferences[customPromptTextKey] = "plaintext prompt"
            }
            val repository =
                createRepository(
                    dataStore = dataStore,
                    localEncryption = FakeLocalDataEncryptionService(),
                    scope = backgroundScope,
                )

            assertEquals("plaintext prompt", repository.settingsFlow.first().customPromptText)

            assertEquals("PCENC:v1:aes-gcm:plaintext prompt", dataStore.data.first()[customPromptTextKey])
        }

    private fun createRepository(scope: CoroutineScope): SettingsRepositoryImpl {
        val dataStore = createDataStore(scope)
        return createRepository(dataStore = dataStore, scope = scope)
    }

    private fun createDataStore(scope: CoroutineScope) =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempDir.resolve("settings-${System.nanoTime()}.preferences_pb").toFile() },
        )

    private fun createRepository(
        dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
        localEncryption: LocalDataEncryptionService = LocalDataEncryptionService.NoOp,
        scope: CoroutineScope,
    ): SettingsRepositoryImpl {
        // Real ApiKeyManager over cleared Robolectric prefs: has() is false for every alias.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val apiKeyManager = testApiKeyManager(context, scope, "settings_impl_test_prefs")
        return SettingsRepositoryImpl(
            dataStore = dataStore,
            apiKeyManager = apiKeyManager,
            localEncryption = localEncryption,
        )
    }

    private companion object {
        private val customPromptTextKey = stringPreferencesKey("settings_custom_prompt_text")
    }
}
