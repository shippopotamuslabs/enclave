package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.domain.model.settings.AppTheme
import com.browntowndev.pocketcrew.domain.model.config.ModelConfigurationUi
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.settings.SystemPromptOption
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelSource

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
    val availableHuggingFaceModels: List<String> = emptyList(),

    // BYOK Sheet
    val showByokSheet: Boolean = false,
    val apiModels: List<ApiModelConfigUi> = emptyList(),
    val selectedApiModel: ApiModelConfigUi? = null,

    // Default model assignments (for Model Config sheet)
    val defaultAssignments: List<DefaultModelAssignmentUi> = emptyList(),
)

data class ApiModelConfigUi(
    val id: Long = 0,
    val displayName: String = "",
    val provider: ApiProvider = ApiProvider.ANTHROPIC,
    val modelId: String = "",
    val baseUrl: String = "",
    val isVision: Boolean = false,
    val maxTokens: Int = 4096,
    val contextWindow: Int = 4096,
    val temperature: Double = 0.7,
    val topP: Double = 0.95,
)

data class DefaultModelAssignmentUi(
    val modelType: ModelType,
    val source: ModelSource,
    val currentModelName: String,     // Whichever model is currently assigned
    val providerName: String? = null, // Non-null when source = API
)
