package com.browntowndev.pocketcrew.presentation.screen.settings

import com.browntowndev.pocketcrew.domain.model.AppTheme
import com.browntowndev.pocketcrew.domain.model.ModelConfigurationUi
import com.browntowndev.pocketcrew.domain.model.ModelType
import com.browntowndev.pocketcrew.domain.model.SystemPromptOption

data class StoredMemory(
    val id: String,
    val text: String
)

data class SettingsUiState(
    val theme: AppTheme = AppTheme.SYSTEM,
    val hapticPress: Boolean = true,
    val hapticResponse: Boolean = true,

    // Customization Bottom Sheet
    val showCustomizationSheet: Boolean = false,
    val customizationEnabled: Boolean = true,
    val selectedPromptOption: SystemPromptOption = SystemPromptOption.CONCISE,
    val customPromptText: String = "",

    // Data Controls Bottom Sheet
    val showDataControlsSheet: Boolean = false,
    val allowMemories: Boolean = true,

    // Memories Bottom Sheet
    val showMemoriesSheet: Boolean = false,
    val memories: List<StoredMemory> = emptyList(),

    // Feedback Bottom Sheet
    val showFeedbackSheet: Boolean = false,
    val feedbackText: String = "",

    // Model Configuration Bottom Sheet
    val showModelConfigSheet: Boolean = false,
    val modelConfigurations: List<ModelConfigurationUi> = emptyList(),
    val selectedModelType: ModelType? = null,
    val selectedModelConfig: ModelConfigurationUi? = null,
    val availableHuggingFaceModels: List<String> = emptyList()
)
