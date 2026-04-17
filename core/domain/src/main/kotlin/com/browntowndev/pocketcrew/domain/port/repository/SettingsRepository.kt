package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.chat.CompactionProviderType
import com.browntowndev.pocketcrew.domain.model.settings.AppTheme
import com.browntowndev.pocketcrew.domain.model.settings.SystemPromptOption
import kotlinx.coroutines.flow.Flow

/**
 * Domain model representing persisted settings.
 */
data class SettingsData(
    val theme: AppTheme = AppTheme.SYSTEM,
    val hapticPress: Boolean = true,
    val hapticResponse: Boolean = true,
    val customizationEnabled: Boolean = true,
    val selectedPromptOption: SystemPromptOption = SystemPromptOption.CONCISE,
    val customPromptText: String = "",
    val allowMemories: Boolean = true,
    val searchEnabled: Boolean = false,
    val alwaysUseVisionModel: Boolean = false,
    val tavilyKeyPresent: Boolean = false,
    val compactionProviderType: CompactionProviderType = CompactionProviderType.DISABLED,
    val compactionApiModelId: String? = null,
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
    suspend fun updateHapticPress(value: Boolean)
    suspend fun updateHapticResponse(value: Boolean)
    suspend fun updateCustomizationEnabled(enabled: Boolean)
    suspend fun updateSelectedPromptOption(option: SystemPromptOption)
    suspend fun updateCustomPromptText(text: String)
    suspend fun updateAllowMemories(allowed: Boolean)
    suspend fun updateSearchEnabled(enabled: Boolean)
    suspend fun updateAlwaysUseVisionModel(enabled: Boolean)
    suspend fun updateCompactionProviderType(type: CompactionProviderType)
    suspend fun updateCompactionApiModelId(modelId: String?)
    suspend fun saveTavilyApiKey(apiKey: String)
    suspend fun clearTavilyApiKey()
}
