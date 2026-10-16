package com.browntowndev.enclave.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.browntowndev.enclave.domain.theme.ColorMode
import com.browntowndev.enclave.domain.theme.ThemeFamily
import com.browntowndev.enclave.domain.theme.ThemePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DataStoreThemePreferencesRepositoryTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun defaultsAreNeoSystem_andWritesPersist() = runTest {
        val repo = createRepository()
        assertEquals(ThemePreferences(), repo.observe().first())

        repo.setFamily(ThemeFamily.Tonal)
        repo.setColorMode(ColorMode.Dark)

        assertEquals(ThemePreferences(ThemeFamily.Tonal, ColorMode.Dark), repo.observe().first())
    }

    @Test
    fun legacySettingsThemeIsHonouredWhenNoColorModeStored() = runTest {
        val repo = createRepository()
        // Recreate over a store that only carries the legacy AppTheme key.
        val store = createStore("legacy")
        store.edit { it[stringPreferencesKey("settings_theme")] = "DARK" }
        val legacyRepo = DataStoreThemePreferencesRepository(store)

        assertEquals(ColorMode.Dark, legacyRepo.observe().first().colorMode)
        assertEquals(ThemeFamily.NeoBrutalist, repo.observe().first().family)
    }

    private fun createRepository() = DataStoreThemePreferencesRepository(createStore("theme"))

    private fun createStore(name: String) =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.Unconfined),
            produceFile = { tempDir.resolve("$name.preferences_pb").toFile() },
        )
}
