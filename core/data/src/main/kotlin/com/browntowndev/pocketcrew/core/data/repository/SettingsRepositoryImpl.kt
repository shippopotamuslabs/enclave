package com.browntowndev.pocketcrew.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import com.browntowndev.pocketcrew.domain.model.chat.CompactionProviderType
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
    private val dataStore: DataStore<Preferences>,
    private val apiKeyManager: ApiKeyManager,
) : SettingsRepository {

    companion object {
        private val THEME_KEY = stringPreferencesKey("settings_theme")
        private val HAPTIC_PRESS_KEY = booleanPreferencesKey("settings_haptic_press")
        private val HAPTIC_RESPONSE_KEY = booleanPreferencesKey("settings_haptic_response")
        private val CUSTOMIZATION_ENABLED_KEY = booleanPreferencesKey("settings_customization_enabled")
        private val SELECTED_PROMPT_OPTION_KEY = stringPreferencesKey("settings_selected_prompt_option")
        private val CUSTOM_PROMPT_TEXT_KEY = stringPreferencesKey("settings_custom_prompt_text")
        private val ALLOW_MEMORIES_KEY = booleanPreferencesKey("settings_allow_memories")
        private val SEARCH_ENABLED_KEY = booleanPreferencesKey("settings_search_enabled")
        private val ALWAYS_USE_VISION_MODEL_KEY = booleanPreferencesKey("settings_always_use_vision_model")
        private val TAVILY_KEY_PRESENT_KEY = booleanPreferencesKey("settings_tavily_key_present")
        private val COMPACTION_PROVIDER_TYPE_KEY = stringPreferencesKey("settings_compaction_provider_type")
        private val COMPACTION_API_MODEL_ID_KEY = stringPreferencesKey("settings_compaction_api_model_id")
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
            allowMemories = preferences[ALLOW_MEMORIES_KEY] ?: true,
            searchEnabled = preferences[SEARCH_ENABLED_KEY] ?: false,
            alwaysUseVisionModel = preferences[ALWAYS_USE_VISION_MODEL_KEY] ?: false,
            tavilyKeyPresent = preferences[TAVILY_KEY_PRESENT_KEY]
                ?: apiKeyManager.has(ApiKeyManager.TAVILY_SEARCH_ALIAS),
            compactionProviderType = preferences[COMPACTION_PROVIDER_TYPE_KEY]?.let { name ->
                try {
                    CompactionProviderType.valueOf(name)
                } catch (e: IllegalArgumentException) {
                    CompactionProviderType.DISABLED
                }
            } ?: CompactionProviderType.DISABLED,
            compactionApiModelId = preferences[COMPACTION_API_MODEL_ID_KEY],
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

    override suspend fun updateSearchEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SEARCH_ENABLED_KEY] = enabled
        }
    }

    override suspend fun updateAlwaysUseVisionModel(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[ALWAYS_USE_VISION_MODEL_KEY] = enabled
        }
    }

    override suspend fun updateCompactionProviderType(type: CompactionProviderType) {
        dataStore.edit { preferences ->
            preferences[COMPACTION_PROVIDER_TYPE_KEY] = type.name
        }
    }

    override suspend fun updateCompactionApiModelId(modelId: String?) {
        dataStore.edit { preferences ->
            if (modelId == null) {
                preferences.remove(COMPACTION_API_MODEL_ID_KEY)
            } else {
                preferences[COMPACTION_API_MODEL_ID_KEY] = modelId
            }
        }
    }

    override suspend fun saveTavilyApiKey(apiKey: String) {
        val normalizedKey = apiKey.trim()
        apiKeyManager.save(ApiKeyManager.TAVILY_SEARCH_ALIAS, normalizedKey)
        dataStore.edit { preferences ->
            preferences[TAVILY_KEY_PRESENT_KEY] = normalizedKey.isNotBlank()
        }
    }

    override suspend fun clearTavilyApiKey() {
        apiKeyManager.delete(ApiKeyManager.TAVILY_SEARCH_ALIAS)
        dataStore.edit { preferences ->
            preferences[TAVILY_KEY_PRESENT_KEY] = false
        }
    }
}
