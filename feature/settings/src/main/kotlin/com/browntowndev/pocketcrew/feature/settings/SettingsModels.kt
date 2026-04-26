package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.runtime.Immutable
import com.browntowndev.pocketcrew.domain.model.settings.AppTheme
import com.browntowndev.pocketcrew.domain.usecase.settings.ModelDeletionTarget
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ModelSource
import com.browntowndev.pocketcrew.domain.model.settings.SystemPromptOption
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningEffort
import com.browntowndev.pocketcrew.domain.model.inference.ApiModelParameterSupport
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.ModelConfigurationId

@Immutable
data class StoredMemory(
    val id: String,
    val text: String
)

@Immutable
data class SettingsUiState(
    val home: SettingsHomeUiState = SettingsHomeUiState(),
    val customization: CustomizationUiState = CustomizationUiState(),
    val dataControls: DataControlsUiState = DataControlsUiState(),
    val memories: MemoriesUiState = MemoriesUiState(),
    val feedback: FeedbackUiState = FeedbackUiState(),
    val localModelsSheet: LocalModelsSheetUiState = LocalModelsSheetUiState(),
    val localModelEditor: LocalModelEditorUiState = LocalModelEditorUiState(),
    val apiProvidersSheet: ApiProvidersSheetUiState = ApiProvidersSheetUiState(),
    val apiProviderEditor: ApiProviderEditorUiState = ApiProviderEditorUiState(),
    val searchSkillEditor: SearchSkillEditorUiState = SearchSkillEditorUiState(),
    val ttsProvidersSheet: TtsProvidersSheetUiState = TtsProvidersSheetUiState(),
    val ttsProviderEditor: TtsProviderEditorUiState = TtsProviderEditorUiState(),
    val assignments: ModelAssignmentsUiState = ModelAssignmentsUiState(),
    val deletion: DeletionFlowUiState = DeletionFlowUiState(),
)

@Immutable
data class SettingsHomeUiState(
    val theme: AppTheme = AppTheme.SYSTEM,
    val hapticPress: Boolean = true,
    val hapticResponse: Boolean = true,
    val backgroundInferenceEnabled: Boolean = true,
    val isLocalModelsSheetOpen: Boolean = false,
    val isApiProvidersSheetOpen: Boolean = false,
    val isDataControlsSheetOpen: Boolean = false,
    val isMemoriesSheetOpen: Boolean = false,
    val isFeedbackSheetOpen: Boolean = false,
    val isVisionSettingsSheetOpen: Boolean = false,
    val isTtsProvidersSheetOpen: Boolean = false,
)

@Immutable
data class CustomizationUiState(
    val isSheetOpen: Boolean = false,
    val enabled: Boolean = true,
    val selectedPromptOption: SystemPromptOption = SystemPromptOption.CONCISE,
    val customPromptText: String = "",
)

@Immutable
data class DataControlsUiState(
    val isSheetOpen: Boolean = false,
    val allowMemories: Boolean = true,
)

@Immutable
data class MemoriesUiState(
    val isSheetOpen: Boolean = false,
    val memories: List<StoredMemory> = emptyList(),
)

@Immutable
data class FeedbackUiState(
    val isSheetOpen: Boolean = false,
    val feedbackText: String = "",
)

@Immutable
data class LocalModelsSheetUiState(
    val isVisible: Boolean = false,
    val models: List<LocalModelAssetUi> = emptyList(),
    val availableDownloads: List<LocalModelAssetUi> = emptyList(),
    val selectedAsset: LocalModelAssetUi? = null,
)

@Immutable
data class LocalModelEditorUiState(
    val selectedAsset: LocalModelAssetUi? = null,
    val configDraft: LocalModelConfigUi? = null,
)

@Immutable
data class ApiProvidersSheetUiState(
    val isVisible: Boolean = false,
    val assets: List<ApiModelAssetUi> = emptyList(),
    val selectedAsset: ApiModelAssetUi? = null,
)

@Immutable
data class TtsProvidersSheetUiState(
    val isVisible: Boolean = false,
    val assets: List<TtsProviderAssetUi> = emptyList(),
    val selectedAsset: TtsProviderAssetUi? = null,
)

@Immutable
data class TtsProviderEditorUiState(
    val isEditing: Boolean = false,
    val assetDraft: TtsProviderAssetUi? = null,
    val selectedReusableCredential: ReusableApiCredentialUi? = null,
)

@Immutable
data class ApiModelDiscoveryUiState(
    val models: List<DiscoveredApiModelUi> = emptyList(),
    val filteredModels: List<DiscoveredApiModelUi> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val providerFilters: Set<String> = emptySet(),
    val sortOption: ModelSortOption = ModelSortOption.A_TO_Z,
)

@Immutable
data class ApiProviderEditorUiState(
    val assetDraft: ApiModelAssetUi? = null,
    val selectedReusableCredential: ReusableApiCredentialUi? = null,
    val presetDraft: ApiModelConfigUi? = null,
    val parameterSupport: ApiModelParameterSupport = ApiModelParameterSupport.DEFAULT,
    val discovery: ApiModelDiscoveryUiState = ApiModelDiscoveryUiState(),
)

@Immutable
data class SearchSkillEditorUiState(
    val isEditing: Boolean = false,
    val enabled: Boolean = false,
    val tavilyKeyPresent: Boolean = false,
)

@Immutable
data class ModelAssignmentsUiState(
    val assignments: List<DefaultModelAssignmentUi> = emptyList(),
    val isDialogOpen: Boolean = false,
    val editingSlot: ModelType? = null,
)

@Immutable
data class DeletionFlowUiState(
    val showLastModelAlert: Boolean = false,
    val pendingTarget: PendingDeletionTarget? = null,
    val modelTypesNeedingReassignment: List<ModelType> = emptyList(),
    val reassignmentOptions: List<ReassignmentOptionUi> = emptyList(),
)

sealed interface PendingDeletionTarget {
    data class LocalModelAsset(val id: LocalModelId) : PendingDeletionTarget
    data class LocalModelPreset(val id: LocalModelConfigurationId) : PendingDeletionTarget
    data class ApiProvider(val id: ApiCredentialsId) : PendingDeletionTarget
    data class ApiPreset(val id: ApiModelConfigurationId) : PendingDeletionTarget
}

internal fun ModelDeletionTarget.toUi(): PendingDeletionTarget = when (this) {
    is ModelDeletionTarget.LocalModelAsset -> PendingDeletionTarget.LocalModelAsset(id)
    is ModelDeletionTarget.LocalModelPreset -> PendingDeletionTarget.LocalModelPreset(id)
    is ModelDeletionTarget.ApiProvider -> PendingDeletionTarget.ApiProvider(id)
    is ModelDeletionTarget.ApiPreset -> PendingDeletionTarget.ApiPreset(id)
}

@Immutable
data class ReassignmentOptionUi(
    val configId: ModelConfigurationId,
    val displayName: String,
    val source: ModelSource,
    val providerName: String? = null,
    val apiCredentialsId: ApiCredentialsId? = null,
    val localModelId: LocalModelId? = null
)

@Immutable
data class TtsProviderAssetUi(
    val id: com.browntowndev.pocketcrew.domain.model.config.TtsProviderId = com.browntowndev.pocketcrew.domain.model.config.TtsProviderId(""),
    val displayName: String = "",
    val provider: ApiProvider = ApiProvider.OPENAI,
    val voiceName: String = "",
    val modelName: String? = null,
    val baseUrl: String? = null,
    val credentialAlias: String = "",
    val useAsDefault: Boolean = false
)

@Immutable
data class ApiModelAssetUi(
    val credentialsId: ApiCredentialsId,
    val displayName: String,
    val provider: ApiProvider,
    val modelId: String,
    val baseUrl: String?,
    val isMultimodal: Boolean,
    val credentialAlias: String,
    val configurations: List<ApiModelConfigUi>
)

@Immutable
data class ReusableApiCredentialUi(
    val credentialsId: ApiCredentialsId,
    val displayName: String,
    val modelId: String,
    val credentialAlias: String,
)

@Immutable
data class CustomHeaderUi(
    val key: String = "",
    val value: String = ""
)

enum class ModelSortOption {
    A_TO_Z,
    NEWEST,
    PRICE_LOW_TO_HIGH
}

@Immutable
data class ApiModelConfigUi(
    val id: ApiModelConfigurationId = ApiModelConfigurationId(""),
    val credentialsId: ApiCredentialsId = ApiCredentialsId(""),
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
    val openRouterRouting: OpenRouterRoutingConfiguration = OpenRouterRoutingConfiguration(),
    val thinkingEnabled: Boolean = false,
    val systemPrompt: String = "",
    val reasoningEffort: ApiReasoningEffort? = null
)

@Immutable
data class DiscoveredApiModelUi(
    val modelId: String,
    val name: String? = null,
    val contextWindowTokens: Int? = null,
    val maxOutputTokens: Int? = null,
    val created: Long? = null,
    val promptPrice: Double? = null,
    val completionPrice: Double? = null,
    val providerName: String? = null,
    val isMultimodal: Boolean? = null,
)

@Immutable
data class LocalModelAssetUi(
    val metadataId: LocalModelId,
    val huggingFaceModelName: String,
    val friendlyName: String,
    val providerName: String,
    val format: String,
    val remoteFileName: String,
    val sizeInBytes: Long,
    val configurations: List<LocalModelConfigUi>,
    val isExpanded: Boolean = false,
    val isMultimodal: Boolean = false,
    val isSoftDeleted: Boolean = false
)

@Immutable
data class LocalModelConfigUi(
    val id: LocalModelConfigurationId = LocalModelConfigurationId(""),
    val localModelId: LocalModelId = LocalModelId(""),
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
data class DefaultModelAssignmentUi(
    val modelType: ModelType,
    val source: ModelSource,
    val currentModelName: String,
    val displayLabel: String,
    val providerName: String? = null,
    val presetName: String? = null,
    val isMultimodal: Boolean = false,
)

internal val ModelType.displayLabel: String
    get() = when (this) {
        ModelType.MAIN -> "Synthesis"
        ModelType.FAST -> "Fast"
        ModelType.THINKING -> "Thinking"
        ModelType.VISION -> "Vision (API)"
        ModelType.TTS -> "Text-to-Speech"
        ModelType.DRAFT_ONE -> "Draft 1"
        ModelType.DRAFT_TWO -> "Draft 2"
        ModelType.FINAL_SYNTHESIS -> "Final Refinement"
        ModelType.UNASSIGNED -> "Unassigned"
    }

internal val ModelType.description: String
    get() = when (this) {
        ModelType.MAIN -> "The primary model responsible for synthesizing the draft content into a cohesive response."
        ModelType.FAST -> "A lightweight, efficient model for quick, non-reasoning responses."
        ModelType.THINKING -> "A reasoning model with extended context for complex tasks."
        ModelType.VISION -> "A dedicated API vision model that acts as the chat's eyes for image inspection."
        ModelType.TTS -> "Synthesize text responses into natural-sounding speech."
        ModelType.DRAFT_ONE -> "Generates the initial analytical draft for the Crew pipeline."
        ModelType.DRAFT_TWO -> "Produces a secondary creative draft for the Crew pipeline."
        ModelType.FINAL_SYNTHESIS -> "Polishes and refines the synthesized content for a professional final output."
        ModelType.UNASSIGNED -> "A model that is downloaded but not currently assigned to a specific engine role."
    }

/**
 * Robust mock data provider for Settings UI Previews.
 */
object MockSettingsData {
    val localModels = listOf(
        LocalModelAssetUi(
            metadataId = LocalModelId("1"),
            huggingFaceModelName = "meta-llama/Meta-Llama-3-8B-Instruct",
            friendlyName = "Meta Llama 3 8B Instruct",
            providerName = "meta-llama",
            format = "GGUF",
            remoteFileName = "Meta-Llama-3-8B-Instruct-Q4_K_M.gguf",
            sizeInBytes = 4_920_000_000L,
            configurations = listOf(
                LocalModelConfigUi(id = LocalModelConfigurationId("cfg-1"), localModelId = LocalModelId("1"), displayName = "Default", temperature = 0.7),
                LocalModelConfigUi(id = LocalModelConfigurationId("cfg-2"), localModelId = LocalModelId("1"), displayName = "Creative", temperature = 1.2),
                LocalModelConfigUi(id = LocalModelConfigurationId("cfg-3"), localModelId = LocalModelId("1"), displayName = "Precise", temperature = 0.1)
            ),
            isMultimodal = true
        ),
        LocalModelAssetUi(
            metadataId = LocalModelId("2"),
            huggingFaceModelName = "google/gemma-2-9b-it",
            friendlyName = "gemma 2 9b it",
            providerName = "google",
            format = "GGUF",
            remoteFileName = "gemma-2-9b-it-Q4_K_M.gguf",
            sizeInBytes = 5_400_000_000L,
            configurations = listOf(
                LocalModelConfigUi(id = LocalModelConfigurationId("cfg-4"), localModelId = LocalModelId("2"), displayName = "Standard", temperature = 0.8)
            )
        )
    )

    val apiModels = listOf(
        ApiModelAssetUi(
            credentialsId = ApiCredentialsId("1"),
            displayName = "GPT-4o",
            provider = ApiProvider.OPENAI,
            modelId = "gpt-4o",
            baseUrl = null,
            isMultimodal = true,
            credentialAlias = "OpenAI Primary",
            configurations = listOf(
                ApiModelConfigUi(id = ApiModelConfigurationId("cfg-1"), credentialsId = ApiCredentialsId("1"), displayName = "Balanced", temperature = 0.7),
                ApiModelConfigUi(id = ApiModelConfigurationId("cfg-2"), credentialsId = ApiCredentialsId("1"), displayName = "Creative", temperature = 1.0)
            )
        ),
        ApiModelAssetUi(
            credentialsId = ApiCredentialsId("2"),
            displayName = "Claude 3.5 Sonnet",
            provider = ApiProvider.ANTHROPIC,
            modelId = "claude-3-5-sonnet-20240620",
            baseUrl = null,
            isMultimodal = true,
            credentialAlias = "Anthropic Work",
            configurations = listOf(
                ApiModelConfigUi(id = ApiModelConfigurationId("cfg-3"), credentialsId = ApiCredentialsId("2"), displayName = "Standard", temperature = 0.7)
            )
        ),
        ApiModelAssetUi(
            credentialsId = ApiCredentialsId("3"),
            displayName = "OpenRouter GPT-5.2",
            provider = ApiProvider.OPENROUTER,
            modelId = "openai/gpt-5.2",
            baseUrl = ApiProvider.OPENROUTER.defaultBaseUrl(),
            isMultimodal = true,
            credentialAlias = "OpenRouter",
            configurations = listOf(
                ApiModelConfigUi(
                    id = ApiModelConfigurationId("cfg-4"),
                    credentialsId = ApiCredentialsId("3"),
                    displayName = "Reliability",
                    temperature = 0.3,
                    openRouterRouting = OpenRouterRoutingConfiguration()
                )
            )
        )
    )

    val defaultAssignments = listOf(
        DefaultModelAssignmentUi(ModelType.MAIN, ModelSource.API, "GPT-4o (Balanced)", "Synthesis", "OpenAI", isMultimodal = true),
        DefaultModelAssignmentUi(ModelType.FAST, ModelSource.ON_DEVICE, "Llama 3 8B (Default)", "Fast", isMultimodal = true),
        DefaultModelAssignmentUi(ModelType.VISION, ModelSource.API, "Claude 3.5 Sonnet (Standard)", "Vision (API)", "Anthropic", isMultimodal = true),
        DefaultModelAssignmentUi(ModelType.THINKING, ModelSource.ON_DEVICE, "Llama 3 8B (Precise)", "Thinking")
    )

    val baseUiState = SettingsUiState(
        home = SettingsHomeUiState(theme = AppTheme.SYSTEM),
        memories = MemoriesUiState(
            memories = listOf(
                StoredMemory("1", "User prefers concise answers."),
                StoredMemory("2", "User is a senior software engineer.")
            )
        ),
        localModelsSheet = LocalModelsSheetUiState(models = localModels),
        apiProvidersSheet = ApiProvidersSheetUiState(assets = apiModels),
        ttsProvidersSheet = TtsProvidersSheetUiState(
            assets = listOf(
                TtsProviderAssetUi(
                    id = com.browntowndev.pocketcrew.domain.model.config.TtsProviderId("tts-1"),
                    displayName = "Default OpenAI TTS",
                    provider = ApiProvider.OPENAI,
                    voiceName = "alloy"
                )
            )
        ),
        assignments = ModelAssignmentsUiState(assignments = defaultAssignments),
    )
}
