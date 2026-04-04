package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.runtime.Immutable
import com.browntowndev.pocketcrew.domain.model.settings.AppTheme
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ModelSource
import com.browntowndev.pocketcrew.domain.model.settings.SystemPromptOption
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider

@Immutable
data class StoredMemory(
    val id: String,
    val text: String
)

@Immutable
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
    val localModels: List<LocalModelAssetUi> = emptyList(),
    val availableToDownloadModels: List<LocalModelAssetUi> = emptyList(),
    val selectedLocalModelAsset: LocalModelAssetUi? = null,
    val selectedLocalModelConfig: LocalModelConfigUi? = null,
    val availableHuggingFaceModels: List<LocalModelMetadataUi> = emptyList(),

    val showByokSheet: Boolean = false,
    val apiModels: List<ApiModelAssetUi> = emptyList(),
    val selectedApiModelAsset: ApiModelAssetUi? = null,
    val selectedApiModelConfig: ApiModelConfigUi? = null,

    val defaultAssignments: List<DefaultModelAssignmentUi> = emptyList(),

    // Deletion Flow
    val showCannotDeleteLastModelAlert: Boolean = false,
    val pendingDeletionModelId: Long? = null,
    val pendingDeletionConfigId: Long? = null,
    val modelTypesNeedingReassignment: List<ModelType> = emptyList(),
    val reassignmentOptions: List<ReassignmentOptionUi> = emptyList(),

    // Assignment Selection Dialog
    val showAssignmentDialog: Boolean = false,
    val editingAssignmentSlot: ModelType? = null
)

@Immutable
data class ReassignmentOptionUi(
    val configId: Long,
    val displayName: String,
    val source: ModelSource,
    val providerName: String? = null,
    val apiCredentialsId: Long? = null,
    val localModelId: Long? = null
)

@Immutable
data class ApiModelAssetUi(
    val credentialsId: Long,
    val displayName: String,
    val provider: ApiProvider,
    val modelId: String,
    val baseUrl: String?,
    val isVision: Boolean,
    val credentialAlias: String,
    val configurations: List<ApiModelConfigUi>
)

@Immutable
data class CustomHeaderUi(
    val key: String = "",
    val value: String = ""
)

@Immutable
data class ApiModelConfigUi(
    val id: Long = 0,
    val credentialsId: Long = 0,
    val displayName: String = "",
    val maxTokens: String = "4096",
    val contextWindow: String = "4096",
    val temperature: Double = 0.7,
    val topP: Double = 0.95,
    val topK: String = "40",
    val minP: Double = 0.05,
    val frequencyPenalty: Double = 0.0,
    val presencePenalty: Double = 0.0,
    val customHeaders: List<CustomHeaderUi> = emptyList(),
    val thinkingEnabled: Boolean = false,
    val systemPrompt: String = ""
)

@Immutable
data class LocalModelAssetUi(
    val metadataId: Long,
    val huggingFaceModelName: String,
    val remoteFileName: String,
    val sizeInBytes: Long,
    val configurations: List<LocalModelConfigUi>,
    val isExpanded: Boolean = false,
    val visionCapable: Boolean = false
)

@Immutable
data class LocalModelConfigUi(
    val id: Long = 0,
    val localModelId: Long = 0,
    val displayName: String = "",
    val maxTokens: String = "4096",
    val contextWindow: String = "4096",
    val temperature: Double = 0.7,
    val topP: Double = 0.95,
    val topK: String = "40",
    val minP: Double = 0.0,
    val repetitionPenalty: Double = 1.1,
    val systemPrompt: String = "",
    val thinkingEnabled: Boolean = false,
    val isSystemPreset: Boolean = false
)

@Immutable
data class LocalModelMetadataUi(
    val id: Long = 0,
    val huggingFaceModelName: String
)

@Immutable
data class DefaultModelAssignmentUi(
    val modelType: ModelType,
    val source: ModelSource,
    val currentModelName: String,
    val displayLabel: String,
    val providerName: String? = null,
)

internal val ModelType.displayLabel: String
    get() = when (this) {
        ModelType.MAIN -> "Synthesis"
        ModelType.FAST -> "Fast"
        ModelType.THINKING -> "Thinking"
        ModelType.VISION -> "Vision"
        ModelType.DRAFT_ONE -> "Draft 1"
        ModelType.DRAFT_TWO -> "Draft 2"
        ModelType.FINAL_SYNTHESIS -> "Final Refinement"
    }

internal val ModelType.description: String
    get() = when (this) {
        ModelType.MAIN -> "The primary model responsible for synthesizing the draft content into a cohesive response."
        ModelType.FAST -> "A lightweight, efficient model for quick, non-reasoning responses."
        ModelType.THINKING -> "A reasoning model with extended context for complex tasks."
        ModelType.VISION -> "A specialized model for image understanding and visual analysis."
        ModelType.DRAFT_ONE -> "Generates the initial analytical draft for the Crew pipeline."
        ModelType.DRAFT_TWO -> "Produces a secondary creative draft for the Crew pipeline."
        ModelType.FINAL_SYNTHESIS -> "Polishes and refines the synthesized content for a professional final output."
    }

/**
 * Robust mock data provider for Settings UI Previews.
 */
object MockSettingsData {
    val localModels = listOf(
        LocalModelAssetUi(
            metadataId = 1,
            huggingFaceModelName = "meta-llama/Meta-Llama-3-8B-Instruct",
            remoteFileName = "Meta-Llama-3-8B-Instruct-Q4_K_M.gguf",
            sizeInBytes = 4_920_000_000L,
            configurations = listOf(
                LocalModelConfigUi(id = 1, localModelId = 1, displayName = "Default", temperature = 0.7),
                LocalModelConfigUi(id = 2, localModelId = 1, displayName = "Creative", temperature = 1.2),
                LocalModelConfigUi(id = 3, localModelId = 1, displayName = "Precise", temperature = 0.1)
            )
        ),
        LocalModelAssetUi(
            metadataId = 2,
            huggingFaceModelName = "google/gemma-2-9b-it",
            remoteFileName = "gemma-2-9b-it-Q4_K_M.gguf",
            sizeInBytes = 5_400_000_000L,
            configurations = listOf(
                LocalModelConfigUi(id = 4, localModelId = 2, displayName = "Standard", temperature = 0.8)
            )
        )
    )

    val apiModels = listOf(
        ApiModelAssetUi(
            credentialsId = 1,
            displayName = "GPT-4o",
            provider = ApiProvider.OPENAI,
            modelId = "gpt-4o",
            baseUrl = null,
            isVision = true,
            credentialAlias = "OpenAI Primary",
            configurations = listOf(
                ApiModelConfigUi(id = 1, credentialsId = 1, displayName = "Balanced", temperature = 0.7),
                ApiModelConfigUi(id = 2, credentialsId = 1, displayName = "Creative", temperature = 1.0)
            )
        ),
        ApiModelAssetUi(
            credentialsId = 2,
            displayName = "Claude 3.5 Sonnet",
            provider = ApiProvider.ANTHROPIC,
            modelId = "claude-3-5-sonnet-20240620",
            baseUrl = null,
            isVision = true,
            credentialAlias = "Anthropic Work",
            configurations = listOf(
                ApiModelConfigUi(id = 3, credentialsId = 2, displayName = "Standard", temperature = 0.7)
            )
        )
    )

    val defaultAssignments = listOf(
        DefaultModelAssignmentUi(ModelType.MAIN, ModelSource.API, "GPT-4o (Balanced)", "Main", "OpenAI"),
        DefaultModelAssignmentUi(ModelType.FAST, ModelSource.ON_DEVICE, "Llama 3 8B (Default)", "Fast"),
        DefaultModelAssignmentUi(ModelType.VISION, ModelSource.API, "Claude 3.5 Sonnet (Standard)", "Vision", "Anthropic"),
        DefaultModelAssignmentUi(ModelType.THINKING, ModelSource.ON_DEVICE, "Llama 3 8B (Precise)", "Thinking")
    )

    val baseUiState = SettingsUiState(
        theme = AppTheme.SYSTEM,
        localModels = localModels,
        apiModels = apiModels,
        defaultAssignments = defaultAssignments,
        memories = listOf(
            StoredMemory("1", "User prefers concise answers."),
            StoredMemory("2", "User is a senior software engineer.")
        )
    )
}
