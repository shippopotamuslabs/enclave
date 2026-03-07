package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.AppTheme
import com.browntowndev.pocketcrew.domain.model.SystemPromptOption
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
    val allowMemories: Boolean = true
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
}
