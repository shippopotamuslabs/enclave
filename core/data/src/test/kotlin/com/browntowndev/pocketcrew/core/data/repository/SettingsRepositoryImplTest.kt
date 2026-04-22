package com.browntowndev.pocketcrew.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryImplTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `settingsFlow defaults background inference enabled when preference is absent`() = runTest {
        val repository = createRepository(backgroundScope)

        val settings = repository.settingsFlow.first()

        assertTrue(settings.backgroundInferenceEnabled)
    }

    @Test
    fun `updateBackgroundInferenceEnabled persists explicit false`() = runTest {
        val repository = createRepository(backgroundScope)

        repository.updateBackgroundInferenceEnabled(false)

        assertFalse(repository.settingsFlow.first().backgroundInferenceEnabled)
    }

    private fun createRepository(scope: CoroutineScope): SettingsRepositoryImpl {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempDir.resolve("settings.preferences_pb").toFile() },
        )
        val apiKeyManager = mockk<ApiKeyManager> {
            every { has(ApiKeyManager.TAVILY_SEARCH_ALIAS) } returns false
        }
        return SettingsRepositoryImpl(
            dataStore = dataStore,
            apiKeyManager = apiKeyManager,
        )
    }
}
