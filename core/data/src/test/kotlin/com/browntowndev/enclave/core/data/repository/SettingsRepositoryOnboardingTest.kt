package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.security.LocalDataEncryptionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.nio.file.Path
import org.robolectric.annotation.Config as RoboConfig

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(RobolectricExtension::class)
@RoboConfig(sdk = [34])
class SettingsRepositoryOnboardingTest {
    @TempDir
    lateinit var tempDir: Path

    // -------- OB-12 --------

    @Test
    @DisplayName("OB-12 — updateAgeConfirmed(true) persists true to settings flow")
    fun `OB-12 updateAgeConfirmed true is read back as true from settingsFlow`() =
        runTest {
            val repository = createRepository(backgroundScope)

            // Precondition: default is false
            assertFalse(repository.settingsFlow.first().ageConfirmed)

            repository.updateAgeConfirmed(true)

            assertTrue(
                repository.settingsFlow.first().ageConfirmed,
                "settingsFlow.first().ageConfirmed should be true after updateAgeConfirmed(true)",
            )
        }

    // -------- OB-13 --------

    @Test
    @DisplayName("OB-13 — updateOnboardingCompleted(true) round-trips correctly")
    fun `OB-13 updateOnboardingCompleted true is read back as true from settingsFlow`() =
        runTest {
            val repository = createRepository(backgroundScope)

            // Precondition: default is false
            assertFalse(
                repository.settingsFlow.first().onboardingCompleted,
                "onboardingCompleted should default to false",
            )

            repository.updateOnboardingCompleted(true)

            assertTrue(
                repository.settingsFlow.first().onboardingCompleted,
                "settingsFlow.first().onboardingCompleted should be true after updateOnboardingCompleted(true)",
            )
        }

    // -------- helpers --------

    private fun createRepository(scope: CoroutineScope): SettingsRepositoryImpl {
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { tempDir.resolve("settings-${System.nanoTime()}.preferences_pb").toFile() },
            )
        // Real ApiKeyManager over cleared Robolectric prefs: has() is false for every alias.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val apiKeyManager = testApiKeyManager(context, scope, "settings_onboarding_test_prefs")
        return SettingsRepositoryImpl(
            dataStore = dataStore,
            apiKeyManager = apiKeyManager,
            localEncryption = LocalDataEncryptionService.NoOp,
        )
    }
}
