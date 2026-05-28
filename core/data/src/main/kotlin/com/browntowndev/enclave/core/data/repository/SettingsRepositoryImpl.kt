package com.browntowndev.enclave.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.browntowndev.enclave.core.data.security.AadContext
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.data.security.LocalDataEncryptionService
import com.browntowndev.enclave.domain.model.settings.AppTheme
import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.browntowndev.enclave.domain.model.settings.SystemPromptOption
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * Implementation of SettingsRepository using DataStore.
 */
class SettingsRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        private val apiKeyManager: ApiKeyManager,
        private val localEncryption: LocalDataEncryptionService = LocalDataEncryptionService.NoOp,
        private val loggingPort: LoggingPort = NoOpLoggingPort,
    ) : SettingsRepository {
        companion object {
            private val THEME_KEY = stringPreferencesKey("settings_theme")
            private val HAPTIC_RESPONSE_KEY = booleanPreferencesKey("settings_haptic_response")
            private val CUSTOMIZATION_ENABLED_KEY = booleanPreferencesKey("settings_customization_enabled")
            private val SELECTED_PROMPT_OPTION_KEY = stringPreferencesKey("settings_selected_prompt_option")
            private val CUSTOM_PROMPT_TEXT_KEY = stringPreferencesKey("settings_custom_prompt_text")
            private val ALLOW_MEMORIES_KEY = booleanPreferencesKey("settings_allow_memories")
            private val SEARCH_ENABLED_KEY = booleanPreferencesKey("settings_search_enabled")
            private val SEARCH_PROVIDER_KEY = stringPreferencesKey("settings_search_provider")
            private val TAVILY_KEY_PRESENT_KEY = booleanPreferencesKey("settings_tavily_key_present")
            private val EXA_KEY_PRESENT_KEY = booleanPreferencesKey("settings_exa_key_present")
            private val X_SEARCH_ENABLED_KEY = booleanPreferencesKey("settings_x_search_enabled")
            private val X_API_KEY_PRESENT_KEY = booleanPreferencesKey("settings_x_api_key_present")
            private val SHOW_SEARCH_PROVIDER_SWITCH_ALERT_KEY = booleanPreferencesKey("settings_show_search_provider_switch_alert")
            private val BACKGROUND_INFERENCE_ENABLED_KEY = booleanPreferencesKey("settings_background_inference_enabled")
            private const val TAG = "SettingsRepository"

            private fun customPromptTextAad(): ByteArray =
                AadContext(
                    owner = "SettingsRepositoryImpl",
                    field = "settings_custom_prompt_text",
                    entityId = "singleton",
                ).toBytes()
        }

        override val settingsFlow: Flow<SettingsData> =
            dataStore.data
                .onEach { preferences ->
                    repairCustomPromptText(preferences[CUSTOM_PROMPT_TEXT_KEY])
                }.map { preferences ->
                    SettingsData(
                        theme =
                            preferences[THEME_KEY]?.let { name ->
                                try {
                                    AppTheme.valueOf(name)
                                } catch (_: IllegalArgumentException) {
                                    AppTheme.SYSTEM
                                }
                            } ?: AppTheme.SYSTEM,
                        hapticResponse = preferences[HAPTIC_RESPONSE_KEY] ?: true,
                        customizationEnabled = preferences[CUSTOMIZATION_ENABLED_KEY] ?: true,
                        selectedPromptOption =
                            preferences[SELECTED_PROMPT_OPTION_KEY]?.let { name ->
                                try {
                                    SystemPromptOption.valueOf(name)
                                } catch (_: IllegalArgumentException) {
                                    SystemPromptOption.CONCISE
                                }
                            } ?: SystemPromptOption.CONCISE,
                        customPromptText =
                            preferences[CUSTOM_PROMPT_TEXT_KEY]?.let { stored ->
                                localEncryption.decryptString(stored, customPromptTextAad())
                            } ?: "",
                        allowMemories = preferences[ALLOW_MEMORIES_KEY] ?: true,
                        searchEnabled = preferences[SEARCH_ENABLED_KEY] ?: false,
                        searchProvider =
                            preferences[SEARCH_PROVIDER_KEY]?.let { name ->
                                try {
                                    SearchProvider.valueOf(name)
                                } catch (_: IllegalArgumentException) {
                                    SearchProvider.TAVILY
                                }
                            } ?: SearchProvider.TAVILY,
                        tavilyKeyPresent =
                            preferences[TAVILY_KEY_PRESENT_KEY]
                                ?: apiKeyManager.has(ApiKeyManager.TAVILY_SEARCH_ALIAS),
                        exaKeyPresent =
                            preferences[EXA_KEY_PRESENT_KEY]
                                ?: apiKeyManager.has(ApiKeyManager.EXA_SEARCH_ALIAS),
                        xSearchEnabled = preferences[X_SEARCH_ENABLED_KEY] ?: false,
                        xApiKeyPresent =
                            preferences[X_API_KEY_PRESENT_KEY]
                                ?: apiKeyManager.has(ApiKeyManager.X_SEARCH_ALIAS),
                        showSearchProviderSwitchAlert = preferences[SHOW_SEARCH_PROVIDER_SWITCH_ALERT_KEY] ?: true,
                        backgroundInferenceEnabled = preferences[BACKGROUND_INFERENCE_ENABLED_KEY] ?: true,
                    )
                }

        override suspend fun updateTheme(theme: AppTheme) {
            dataStore.edit { preferences ->
                preferences[THEME_KEY] = theme.name
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
                preferences[CUSTOM_PROMPT_TEXT_KEY] = localEncryption.encryptString(text, customPromptTextAad())
            }
        }

        private suspend fun repairCustomPromptText(stored: String?) {
            if (stored.isNullOrEmpty() || localEncryption.isEncrypted(stored)) return
            if (!localEncryption.isUnlocked()) {
                loggingPort.warning(
                    TAG,
                    "Sensitive persistence self-heal owner=SettingsRepositoryImpl field=customPromptText action=DEFERRED_LOCKED",
                )
                return
            }
            val encrypted = localEncryption.encryptString(stored, customPromptTextAad())
            if (encrypted == stored) return
            dataStore.edit { preferences ->
                preferences[CUSTOM_PROMPT_TEXT_KEY] = encrypted
            }
            loggingPort.warning(
                TAG,
                "Sensitive persistence self-heal owner=SettingsRepositoryImpl field=customPromptText action=REPAIRED",
            )
        }

        private object NoOpLoggingPort : LoggingPort {
            override fun debug(
                tag: String,
                message: String,
            ) = Unit

            override fun info(
                tag: String,
                message: String,
            ) = Unit

            override fun warning(
                tag: String,
                message: String,
            ) = Unit

            override fun error(
                tag: String,
                message: String,
                throwable: Throwable?,
            ) = Unit
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

        override suspend fun updateSearchProvider(provider: SearchProvider) {
            dataStore.edit { preferences ->
                preferences[SEARCH_PROVIDER_KEY] = provider.name
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

        override suspend fun saveExaApiKey(apiKey: String) {
            val normalizedKey = apiKey.trim()
            apiKeyManager.save(ApiKeyManager.EXA_SEARCH_ALIAS, normalizedKey)
            dataStore.edit { preferences ->
                preferences[EXA_KEY_PRESENT_KEY] = normalizedKey.isNotBlank()
            }
        }

        override suspend fun clearExaApiKey() {
            apiKeyManager.delete(ApiKeyManager.EXA_SEARCH_ALIAS)
            dataStore.edit { preferences ->
                preferences[EXA_KEY_PRESENT_KEY] = false
            }
        }

        override suspend fun updateXSearchEnabled(enabled: Boolean) {
            dataStore.edit { preferences ->
                preferences[X_SEARCH_ENABLED_KEY] = enabled
            }
        }

        override suspend fun saveXApiKey(apiKey: String) {
            val normalizedKey = apiKey.trim()
            apiKeyManager.save(ApiKeyManager.X_SEARCH_ALIAS, normalizedKey)
            dataStore.edit { preferences ->
                preferences[X_API_KEY_PRESENT_KEY] = normalizedKey.isNotBlank()
            }
        }

        override suspend fun clearXApiKey() {
            apiKeyManager.delete(ApiKeyManager.X_SEARCH_ALIAS)
            dataStore.edit { preferences ->
                preferences[X_API_KEY_PRESENT_KEY] = false
            }
        }

        override suspend fun updateShowSearchProviderSwitchAlert(show: Boolean) {
            dataStore.edit { preferences ->
                preferences[SHOW_SEARCH_PROVIDER_SWITCH_ALERT_KEY] = show
            }
        }

        override suspend fun updateBackgroundInferenceEnabled(enabled: Boolean) {
            dataStore.edit { preferences ->
                preferences[BACKGROUND_INFERENCE_ENABLED_KEY] = enabled
            }
        }
    }
