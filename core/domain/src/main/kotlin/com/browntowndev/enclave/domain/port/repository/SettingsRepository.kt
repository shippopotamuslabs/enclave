package com.browntowndev.enclave.domain.port.repository

import com.browntowndev.enclave.domain.model.settings.AppTheme
import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.browntowndev.enclave.domain.model.settings.SystemPromptOption
import kotlinx.coroutines.flow.Flow

/**
 * Domain model representing persisted settings.
 */
data class SettingsData(
    val theme: AppTheme = AppTheme.SYSTEM,
    val hapticResponse: Boolean = true,
    val customizationEnabled: Boolean = true,
    val selectedPromptOption: SystemPromptOption = SystemPromptOption.CONCISE,
    val customPromptText: String = "",
    val allowMemories: Boolean = true,
    val searchEnabled: Boolean = false,
    val searchProvider: SearchProvider = SearchProvider.TAVILY,
    val tavilyKeyPresent: Boolean = false,
    val exaKeyPresent: Boolean = false,
    val xSearchEnabled: Boolean = false,
    val xApiKeyPresent: Boolean = false,
    val showSearchProviderSwitchAlert: Boolean = true,
    val backgroundInferenceEnabled: Boolean = true,
)

/**
 * Port (interface) for settings persistence.
 * Implemented by the data layer.
 */
interface SettingsRepository {
    /**
     * Flow of settings data that emits whenever preferences change.
     */
    val settingsFlow: Flow<SettingsData>

    suspend fun updateTheme(theme: AppTheme)

    suspend fun updateHapticResponse(value: Boolean)

    suspend fun updateCustomizationEnabled(enabled: Boolean)

    suspend fun updateSelectedPromptOption(option: SystemPromptOption)

    suspend fun updateCustomPromptText(text: String)

    suspend fun updateAllowMemories(allowed: Boolean)

    suspend fun updateSearchEnabled(enabled: Boolean)

    suspend fun updateSearchProvider(provider: SearchProvider)

    suspend fun saveTavilyApiKey(apiKey: String)

    suspend fun clearTavilyApiKey()

    suspend fun saveExaApiKey(apiKey: String)

    suspend fun clearExaApiKey()

    suspend fun updateXSearchEnabled(enabled: Boolean)

    suspend fun saveXApiKey(apiKey: String)

    suspend fun clearXApiKey()

    suspend fun updateShowSearchProviderSwitchAlert(show: Boolean)

    suspend fun updateBackgroundInferenceEnabled(enabled: Boolean)
}
