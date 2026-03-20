package com.browntowndev.pocketcrew.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.browntowndev.pocketcrew.domain.model.settings.AppTheme
import com.browntowndev.pocketcrew.domain.model.settings.SystemPromptOption
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementation of SettingsRepository using DataStore.
 */
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    companion object {
        private val THEME_KEY = stringPreferencesKey("settings_theme")
        private val HAPTIC_PRESS_KEY = booleanPreferencesKey("settings_haptic_press")
        private val HAPTIC_RESPONSE_KEY = booleanPreferencesKey("settings_haptic_response")
        private val CUSTOMIZATION_ENABLED_KEY = booleanPreferencesKey("settings_customization_enabled")
        private val SELECTED_PROMPT_OPTION_KEY = stringPreferencesKey("settings_selected_prompt_option")
        private val CUSTOM_PROMPT_TEXT_KEY = stringPreferencesKey("settings_custom_prompt_text")
        private val ALLOW_MEMORIES_KEY = booleanPreferencesKey("settings_allow_memories")
    }

    override val settingsFlow: Flow<SettingsData> = dataStore.data.map { preferences ->
        SettingsData(
            theme = preferences[THEME_KEY]?.let { name ->
                try {
                    AppTheme.valueOf(name)
                } catch (e: IllegalArgumentException) {
                    AppTheme.SYSTEM
                }
            } ?: AppTheme.SYSTEM,
            hapticPress = preferences[HAPTIC_PRESS_KEY] ?: true,
            hapticResponse = preferences[HAPTIC_RESPONSE_KEY] ?: true,
            customizationEnabled = preferences[CUSTOMIZATION_ENABLED_KEY] ?: true,
            selectedPromptOption = preferences[SELECTED_PROMPT_OPTION_KEY]?.let { name ->
                try {
                    SystemPromptOption.valueOf(name)
                } catch (e: IllegalArgumentException) {
                    SystemPromptOption.CONCISE
                }
            } ?: SystemPromptOption.CONCISE,
            customPromptText = preferences[CUSTOM_PROMPT_TEXT_KEY] ?: "",
            allowMemories = preferences[ALLOW_MEMORIES_KEY] ?: true
        )
    }

    override suspend fun updateTheme(theme: AppTheme) {
        dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme.name
        }
    }

    override suspend fun updateHapticPress(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[HAPTIC_PRESS_KEY] = value
        }
    }

    override suspend fun updateHapticResponse(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[HAPTIC_RESPONSE_KEY] = value
        }
    }

    override suspend fun updateCustomizationEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[CUSTOMIZATION_ENABLED_KEY] = enabled
        }
    }

    override suspend fun updateSelectedPromptOption(option: SystemPromptOption) {
        dataStore.edit { preferences ->
            preferences[SELECTED_PROMPT_OPTION_KEY] = option.name
        }
    }

    override suspend fun updateCustomPromptText(text: String) {
        dataStore.edit { preferences ->
            preferences[CUSTOM_PROMPT_TEXT_KEY] = text
        }
    }

    override suspend fun updateAllowMemories(allowed: Boolean) {
        dataStore.edit { preferences ->
            preferences[ALLOW_MEMORIES_KEY] = allowed
        }
    }
}
