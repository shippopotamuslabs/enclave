package com.browntowndev.enclave.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.browntowndev.enclave.domain.theme.ColorMode
import com.browntowndev.enclave.domain.theme.ThemeFamily
import com.browntowndev.enclave.domain.theme.ThemePreferences
import com.browntowndev.enclave.domain.theme.ThemePreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * DataStore-backed [ThemePreferencesRepository] over the shared settings store (ENC-7).
 * When no explicit color mode is stored yet, the legacy `settings_theme` value
 * (AppTheme) is honoured as a migration fallback so existing users keep their mode.
 */
class DataStoreThemePreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ThemePreferencesRepository {
    override fun observe(): Flow<ThemePreferences> =
        dataStore.data.map { prefs ->
            ThemePreferences(
                family = prefs[FAMILY_KEY]?.let { runCatching { ThemeFamily.valueOf(it) }.getOrNull() }
                    ?: ThemeFamily.NeoBrutalist,
                colorMode = prefs[COLOR_MODE_KEY]?.let { runCatching { ColorMode.valueOf(it) }.getOrNull() }
                    ?: legacyColorMode(prefs[LEGACY_THEME_KEY]),
            )
        }

    override suspend fun setFamily(family: ThemeFamily) {
        dataStore.edit { it[FAMILY_KEY] = family.name }
    }

    override suspend fun setColorMode(colorMode: ColorMode) {
        dataStore.edit { it[COLOR_MODE_KEY] = colorMode.name }
    }

    private fun legacyColorMode(legacy: String?): ColorMode =
        when (legacy) {
            "DARK" -> ColorMode.Dark
            "LIGHT" -> ColorMode.Light
            else -> ColorMode.System
        }

    private companion object {
        val FAMILY_KEY = stringPreferencesKey("theme_family")
        val COLOR_MODE_KEY = stringPreferencesKey("theme_color_mode")
        val LEGACY_THEME_KEY = stringPreferencesKey("settings_theme")
    }
}
