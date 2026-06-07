package com.browntowndev.enclave.feature.settings

import androidx.compose.runtime.Immutable
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.MediaTask
import com.browntowndev.enclave.domain.model.config.ModelConfigurationId
import com.browntowndev.enclave.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ApiModelParameterSupport
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ApiReasoningEffort
import com.browntowndev.enclave.domain.model.inference.ModelSource
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import com.browntowndev.enclave.domain.model.settings.AppTheme
import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.browntowndev.enclave.domain.model.settings.SystemPromptOption
import com.browntowndev.enclave.domain.usecase.settings.ModelDeletionTarget

@Immutable
data class StoredMemory(
    val id: String = "",
    val text: String = "",
    val category: MemoryCategory = MemoryCategory.PREFERENCES,
)

@Immutable
data class UserProfileUi(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
)

@Immutable
sealed interface AuthStateUi {
    data object Unauthenticated : AuthStateUi

    data class Authenticated(
        val user: UserProfileUi,
    ) : AuthStateUi

    data class Error(
        val message: String,
    ) : AuthStateUi

    data object Loading : AuthStateUi
}

@Immutable
data class SettingsUiState(
    val home: SettingsHomeUiState = SettingsHomeUiState(),
    val customization: CustomizationUiState = CustomizationUiState(),
    val dataControls: DataControlsUiState = DataControlsUiState(),
    val memories: MemoriesUiState = MemoriesUiState(),
    val feedback: FeedbackUiState = FeedbackUiState(),
    val usageSpend: UsageSpendSheetUiState = UsageSpendSheetUiState(),
    val localModelsSheet: LocalModelsSheetUiState = LocalModelsSheetUiState(),
    val localModelEditor: LocalModelEditorUiState = LocalModelEditorUiState(),
    val apiProvidersSheet: ApiProvidersSheetUiState = ApiProvidersSheetUiState(),
    val apiProviderEditor: ApiProviderEditorUiState = ApiProviderEditorUiState(),
    val searchSkillEditor: SearchSkillEditorUiState = SearchSkillEditorUiState(),
    val xSearchEditor: XSearchEditorUiState = XSearchEditorUiState(),
    val ttsProvidersSheet: TtsProvidersSheetUiState = TtsProvidersSheetUiState(),
    val ttsProviderEditor: TtsProviderEditorUiState = TtsProviderEditorUiState(),
    val mediaProvidersSheet: MediaProvidersSheetUiState = MediaProvidersSheetUiState(),
    val mediaProviderEditor: MediaProviderEditorUiState = MediaProviderEditorUiState(),
    val assignments: ModelAssignmentsUiState = ModelAssignmentsUiState(),
    val deletion: DeletionFlowUiState = DeletionFlowUiState(),
    val vaultReauthSheet: VaultReauthSheetState = VaultReauthSheetState.Hidden,
)

/**
 * Drives the post-Settings-sign-in re-auth bottom sheet. Sign-in alone is not enough to start
 * cloud sync — the user must also re-enter their vault password so we can reconcile local and
 * cloud DEKs before the Firestore listener spins up.
 *
 *  - [Hidden] — no sheet shown; default.
 *  - [EnterPassword] — sheet visible, awaiting user password. Carries any inline error
 *    message (e.g. "Incorrect Vault Password") and an `isUnlocking` flag for the spinner.
 *  - [ConfirmClobber] — local and cloud DEKs differ; the user must explicitly accept the
 *    destructive clobber via the dialog overlaid on top of the sheet.
 */
sealed interface VaultReauthSheetState {
    data object Hidden : VaultReauthSheetState

    @Immutable
    data class EnterPassword(
        val errorMessage: String? = null,
        val isUnlocking: Boolean = false,
    ) : VaultReauthSheetState

    @Immutable
    data class ConfirmClobber(
        val isReconciling: Boolean = false,
    ) : VaultReauthSheetState
}

@Immutable
data class SettingsHomeUiState(
    val theme: AppTheme = AppTheme.SYSTEM,
    val hapticResponse: Boolean = true,
    val backgroundInferenceEnabled: Boolean = true,
    val auth: AuthStateUi = AuthStateUi.Loading,
    val isLocalModelsSheetOpen: Boolean = false,
    val isApiProvidersSheetOpen: Boolean = false,
    val isDataControlsSheetOpen: Boolean = false,
    val isMemoriesSheetOpen: Boolean = false,
    val isFeedbackSheetOpen: Boolean = false,
    val isUsageSpendSheetOpen: Boolean = false,
    val isVisionSettingsSheetOpen: Boolean = false,
    val isTtsProvidersSheetOpen: Boolean = false,
    val isMediaProvidersSheetOpen: Boolean = false,
    val isSearchSkillSheetOpen: Boolean = false,
    val isOssLicensesSheetOpen: Boolean = false,
    val isTosSheetOpen: Boolean = false,
    val searchSkillTabIndex: Int = 0,
    val isReindexing: Boolean = false,
    val reindexProgress: Float = 0f,
)

@Immutable
data class CustomizationUiState(
    val isSheetOpen: Boolean = false,
    val enabled: Boolean = true,
    val selectedPromptOption: SystemPromptOption = SystemPromptOption.CONCISE,
    val customPromptText: String = "",
)

@Immutable
sealed interface DataControlsPendingAction {
    data object DeleteChats : DataControlsPendingAction

    data object DeleteMedia : DataControlsPendingAction

    data object DeleteMemories : DataControlsPendingAction

    data object DeleteArtifacts : DataControlsPendingAction

    data object DeleteAccount : DataControlsPendingAction
}

@Immutable
data class DataControlsUiState(
    val isSheetOpen: Boolean = false,
    val allowMemories: Boolean = true,
    val automaticErrorTrackingEnabled: Boolean = false,
    val pendingAction: DataControlsPendingAction? = null,
    val isDeleting: Boolean = false,
    val deleteError: String? = null,
)

@Immutable
data class MemoriesUiState(
    val isSheetOpen: Boolean = false,
    val memories: List<StoredMemory> = emptyList(),
    val memoryDraft: StoredMemory? = null,
    val isEditing: Boolean = false,
)

@Immutable
data class FeedbackUiState(
    val isSheetOpen: Boolean = false,
    val feedbackText: String = "",
)

@Immutable
data class ModelPackUi(
    val packId: String,
    val displayName: String,
    val completenessLabel: String,
    val installedCount: Int,
    val totalCount: Int,
    val showDownloadAction: Boolean,
    val isDiffusionSoftDeleted: Boolean,
    val disabledReason: String? = null,
    val isEligible: Boolean = true,
)

@Immutable
data class LocalModelsSheetUiState(
    val isVisible: Boolean = false,
    val modelPacks: List<ModelPackUi> = emptyList(),
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
    val discovery: ApiModelDiscoveryUiState = ApiModelDiscoveryUiState(),
)

@Immutable
data class MediaProvidersSheetUiState(
    val isVisible: Boolean = false,
    val assets: List<MediaProviderAssetUi> = emptyList(),
    val selectedAsset: MediaProviderAssetUi? = null,
)

@Immutable
data class MediaProviderEditorUiState(
    val isEditing: Boolean = false,
    val assetDraft: MediaProviderAssetUi? = null,
    val selectedReusableCredential: ReusableApiCredentialUi? = null,
    val discovery: ApiModelDiscoveryUiState = ApiModelDiscoveryUiState(),
)

@Immutable
data class ApiModelDiscoveryUiState(
    val models: List<DiscoveredApiModelUi> = emptyList(),
    val filteredModels: List<DiscoveredApiModelUi> = emptyList(),
    val isLoading: Boolean = false,
    val isTruncated: Boolean = false,
    val searchQuery: String = "",
    val providerFilters: Set<String> = emptySet(),
    val sortOption: ModelSortOption = ModelSortOption.A_TO_Z,
)

@Immutable
data class ModelDiscoveryFieldCallbacks(
    val onFetchModels: () -> Unit,
    val onUpdateSearchQuery: (String) -> Unit,
    val onToggleProviderFilter: (String) -> Unit,
    val onClearProviderFilters: () -> Unit,
    val onUpdateSortOption: (ModelSortOption) -> Unit,
)

@Immutable
data class ApiProviderEditorUiState(
    val assetDraft: ApiModelAssetUi? = null,
    val selectedReusableCredential: ReusableApiCredentialUi? = null,
    val presetDraft: ApiModelConfigUi? = null,
    val parameterSupport: ApiModelParameterSupport = ApiModelParameterSupport.DEFAULT,
    val discovery: ApiModelDiscoveryUiState = ApiModelDiscoveryUiState(),
    val connectionTestStatus: ApiConnectionTestStatus = ApiConnectionTestStatus.IDLE,
)

@Immutable
data class SearchSkillEditorUiState(
    val isEditing: Boolean = false,
    val enabled: Boolean = false,
    val selectedProvider: SearchProvider = SearchProvider.TAVILY,
    val tavilyKeyPresent: Boolean = false,
    val exaKeyPresent: Boolean = false,
    val currentTavilyApiKey: String = "",
    val currentExaApiKey: String = "",
    val showSwitchAlert: Boolean = true,
)

@Immutable
data class XSearchEditorUiState(
    val enabled: Boolean = false,
    val apiKeyPresent: Boolean = false,
    val currentApiKey: String = "",
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
    data class LocalModelAsset(
        val id: LocalModelId,
    ) : PendingDeletionTarget

    data class LocalModelPreset(
        val id: LocalModelConfigurationId,
    ) : PendingDeletionTarget

    data class ApiProvider(
        val id: ApiCredentialsId,
    ) : PendingDeletionTarget

    data class ApiPreset(
        val id: ApiModelConfigurationId,
    ) : PendingDeletionTarget

    data class ModelPack(
        val packId: String,
    ) : PendingDeletionTarget
}

internal fun ModelDeletionTarget.toUi(): PendingDeletionTarget =
    when (this) {
        is ModelDeletionTarget.LocalModelAsset -> PendingDeletionTarget.LocalModelAsset(id)
        is ModelDeletionTarget.LocalModelPreset -> PendingDeletionTarget.LocalModelPreset(id)
        is ModelDeletionTarget.ApiProvider -> PendingDeletionTarget.ApiProvider(id)
        is ModelDeletionTarget.ApiPreset -> PendingDeletionTarget.ApiPreset(id)
        is ModelDeletionTarget.ModelPack -> PendingDeletionTarget.ModelPack(packId)
    }

@Immutable
data class ReassignmentOptionUi(
    val configId: ModelConfigurationId? = null,
    val mediaProviderId: MediaProviderId? = null,
    val displayName: String,
    val source: ModelSource,
    val providerName: String? = null,
    val apiCredentialsId: ApiCredentialsId? = null,
    val localModelId: LocalModelId? = null,
)

@Immutable
data class TtsProviderAssetUi(
    val id: com.browntowndev.enclave.domain.model.config.TtsProviderId =
        com.browntowndev.enclave.domain.model.config
            .TtsProviderId(""),
    val displayName: String = "",
    val provider: ApiProvider = ApiProvider.OPENAI,
    val voiceName: String = "",
    val modelName: String? = null,
    val baseUrl: String? = null,
    val credentialAlias: String = "",
    val useAsDefault: Boolean = false,
)

@Immutable
data class MediaProviderAssetUi(
    val id: MediaProviderId = MediaProviderId(""),
    val displayName: String = "",
    val provider: ApiProvider = ApiProvider.OPENAI,
    val capability: MediaCapability = MediaCapability.IMAGE,
    val tasks: Set<MediaTask> = emptySet(),
    val modelName: String = "",
    val baseUrl: String? = null,
    val credentialAlias: String = "",
    val useAsDefault: Boolean = false,
)

@Immutable
data class ApiModelAssetUi(
    val credentialsId: ApiCredentialsId,
    val displayName: String,
    val provider: ApiProvider,
    val baseUrl: String?,
    val credentialAlias: String,
    val configurations: List<ApiModelConfigUi>,
)

@Immutable
data class ReusableApiCredentialUi(
    val credentialsId: ApiCredentialsId,
    val displayName: String,
    val providerLabel: String,
    val credentialAlias: String,
)

enum class ApiConnectionTestStatus {
    IDLE,
    TESTING,
    SUCCESS,
}

@Immutable
data class CustomHeaderUi(
    val key: String = "",
    val value: String = "",
)

enum class ModelSortOption {
    A_TO_Z,
    NEWEST,
    PRICE_LOW_TO_HIGH,
}

@Immutable
data class ApiModelConfigUi(
    val id: ApiModelConfigurationId = ApiModelConfigurationId(""),
    val credentialsId: ApiCredentialsId = ApiCredentialsId(""),
    val modelId: String = "",
    val isMultimodal: Boolean = false,
    val displayName: String = "",
    val maxTokens: String = "4096",
    val contextWindow: String = "4096",
    val temperature: Double = 0.7,
    val topP: Double = 0.95,
    val topK: String = "40",
    val minP: Double = 0.05,
    val frequencyPenalty: Double = 0.0,
    val presencePenalty: Double = 0.0,
    val maxTokensDisabled: Boolean = false,
    val temperatureDisabled: Boolean = false,
    val topPDisabled: Boolean = false,
    val topKDisabled: Boolean = false,
    val minPDisabled: Boolean = false,
    val frequencyPenaltyDisabled: Boolean = false,
    val presencePenaltyDisabled: Boolean = false,
    val customHeaders: List<CustomHeaderUi> = emptyList(),
    val openRouterRouting: OpenRouterRoutingConfiguration = OpenRouterRoutingConfiguration(),
    val thinkingEnabled: Boolean = false,
    val systemPrompt: String = "",
    val reasoningEffort: ApiReasoningEffort? = null,
    val promptPriceUsdPerMillion: String = "",
    val completionPriceUsdPerMillion: String = "",
    val cachedPromptPriceUsdPerMillion: String = "",
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
    val cachedPromptPrice: Double? = null,
    val providerName: String? = null,
    val isMultimodal: Boolean? = null,
    val isChatCapable: Boolean = true,
    val isImageGenerationCapable: Boolean = false,
    val isVideoGenerationCapable: Boolean = false,
    val isAudioGenerationCapable: Boolean = false,
    val tasks: Set<MediaTask> = emptySet(),
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
    val isImageGenerationCapable: Boolean = false,
    val isSoftDeleted: Boolean = false,
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
    val isSystemPreset: Boolean = false,
    val defaultAssignments: List<ModelType> = emptyList(),
)

@Immutable
data class DefaultModelAssignmentUi(
    val modelType: ModelType,
    val source: ModelSource,
    val currentModelName: String,
    val displayLabel: String,
    val providerName: String? = null,
    val presetName: String? = null,
    val modelId: String? = null,
    val isMultimodal: Boolean = false,
)

@Immutable
internal data class AssignmentRowDisplay(
    val presetName: String,
    val providerName: String? = null,
    val modelName: String? = null,
)

private fun String.isReadableAssignmentLabel(): Boolean = isNotBlank() && this != "None Assigned"

private fun String?.toReadableAssignmentLabel(): String? = this?.takeIf { it.isReadableAssignmentLabel() }

private val MEDIA_ASSIGNMENT_TYPES =
    setOf(
        ModelType.IMAGE_GENERATION,
        ModelType.IMAGE_EDIT,
        ModelType.VIDEO_GENERATION,
        ModelType.IMAGE_TO_VIDEO,
        ModelType.MUSIC_GENERATION,
    )

/**
 * Model Role Assignment card body: preset name, provider, then catalog/base model id.
 * Mapping differs by source (API preset vs local config vs media/TTS provider).
 */
internal val DefaultModelAssignmentUi.assignmentRowDisplay: AssignmentRowDisplay
    get() {
        if (currentModelName == "None Assigned") {
            return AssignmentRowDisplay(presetName = currentModelName)
        }
        return when (source) {
            ModelSource.ON_DEVICE ->
                AssignmentRowDisplay(
                    presetName = presetName.toReadableAssignmentLabel() ?: currentModelName,
                    providerName = providerName ?: "Local",
                    modelName = currentModelName.toReadableAssignmentLabel(),
                )
            ModelSource.API ->
                when (modelType) {
                    in MEDIA_ASSIGNMENT_TYPES ->
                        AssignmentRowDisplay(
                            presetName = currentModelName.toReadableAssignmentLabel() ?: currentModelName,
                            providerName = providerName,
                            modelName = presetName.toReadableAssignmentLabel(),
                        )
                    ModelType.TTS ->
                        AssignmentRowDisplay(
                            presetName = currentModelName.toReadableAssignmentLabel() ?: currentModelName,
                            providerName = providerName,
                            modelName = presetName.toReadableAssignmentLabel(),
                        )
                    else ->
                        AssignmentRowDisplay(
                            presetName = presetName.toReadableAssignmentLabel() ?: currentModelName,
                            providerName = providerName,
                            modelName = modelId.toReadableAssignmentLabel(),
                        )
                }
        }
    }

internal val ModelType.displayLabel: String
    get() =
        when (this) {
            ModelType.MAIN -> "Synthesis"
            ModelType.FAST -> "Fast"
            ModelType.THINKING -> "Thinking"
            ModelType.VISION -> "Vision (API)"
            ModelType.TTS -> "Text-to-Speech"
            ModelType.IMAGE_GENERATION -> "Image Generation"
            ModelType.IMAGE_EDIT -> "Image Edit"
            ModelType.VIDEO_GENERATION -> "Video Generation"
            ModelType.IMAGE_TO_VIDEO -> "Image to Video"
            ModelType.MUSIC_GENERATION -> "Music Generation"
            ModelType.DRAFT_ONE -> "Draft 1"
            ModelType.DRAFT_TWO -> "Draft 2"
            ModelType.FINAL_SYNTHESIS -> "Final Refinement"
            ModelType.UNASSIGNED -> "Unassigned"
        }

internal val ModelType.description: String
    get() =
        when (this) {
            ModelType.MAIN -> "The primary model responsible for synthesizing the draft content into a cohesive response."
            ModelType.FAST -> "A lightweight, efficient model for quick, non-reasoning responses."
            ModelType.THINKING -> "A reasoning model with extended context for complex tasks."
            ModelType.VISION -> "A dedicated API vision model that acts as the chat's eyes for image inspection."
            ModelType.TTS -> "Synthesize text responses into natural-sounding speech."
            ModelType.IMAGE_GENERATION -> "Generate images from text prompts."
            ModelType.IMAGE_EDIT -> "Edit or transform images using a reference image."
            ModelType.VIDEO_GENERATION -> "Generate videos from text prompts."
            ModelType.IMAGE_TO_VIDEO -> "Animate a reference image into video."
            ModelType.MUSIC_GENERATION -> "Generate music from text prompts."
            ModelType.DRAFT_ONE -> "Generates the initial analytical draft for the Council pipeline."
            ModelType.DRAFT_TWO -> "Produces a secondary creative draft for the Council pipeline."
            ModelType.FINAL_SYNTHESIS -> "Polishes and refines the synthesized content for a professional final output."
            ModelType.UNASSIGNED -> "A model that is downloaded but not currently assigned to a specific engine role."
        }

/**
 * Robust mock data provider for Settings UI Previews.
 */
object MockSettingsData {
    val localModels =
        listOf(
            LocalModelAssetUi(
                metadataId = LocalModelId("1"),
                huggingFaceModelName = "meta-llama/Meta-Llama-3-8B-Instruct",
                friendlyName = "Meta Llama 3 8B Instruct",
                providerName = "meta-llama",
                format = "GGUF",
                remoteFileName = "Meta-Llama-3-8B-Instruct-Q4_K_M.gguf",
                sizeInBytes = 4_920_000_000L,
                configurations =
                    listOf(
                        LocalModelConfigUi(
                            id = LocalModelConfigurationId("cfg-1"),
                            localModelId = LocalModelId("1"),
                            displayName = "Default",
                            temperature = 0.7,
                        ),
                        LocalModelConfigUi(
                            id = LocalModelConfigurationId("cfg-2"),
                            localModelId = LocalModelId("1"),
                            displayName = "Creative",
                            temperature = 1.2,
                        ),
                        LocalModelConfigUi(
                            id = LocalModelConfigurationId("cfg-3"),
                            localModelId = LocalModelId("1"),
                            displayName = "Precise",
                            temperature = 0.1,
                        ),
                    ),
                isMultimodal = true,
            ),
            LocalModelAssetUi(
                metadataId = LocalModelId("2"),
                huggingFaceModelName = "google/gemma-2-9b-it",
                friendlyName = "gemma 2 9b it",
                providerName = "google",
                format = "GGUF",
                remoteFileName = "gemma-2-9b-it-Q4_K_M.gguf",
                sizeInBytes = 5_400_000_000L,
                configurations =
                    listOf(
                        LocalModelConfigUi(
                            id = LocalModelConfigurationId("cfg-4"),
                            localModelId = LocalModelId("2"),
                            displayName = "Standard",
                            temperature = 0.8,
                        ),
                    ),
            ),
        )

    val apiModels =
        listOf(
            ApiModelAssetUi(
                credentialsId = ApiCredentialsId("1"),
                displayName = "GPT-4o",
                provider = ApiProvider.OPENAI,
                baseUrl = null,
                credentialAlias = "OpenAI Primary",
                configurations =
                    listOf(
                        ApiModelConfigUi(
                            id = ApiModelConfigurationId("cfg-1"),
                            credentialsId = ApiCredentialsId("1"),
                            modelId = "gpt-4o",
                            isMultimodal = true,
                            displayName = "Balanced",
                            temperature = 0.7,
                        ),
                        ApiModelConfigUi(
                            id = ApiModelConfigurationId("cfg-2"),
                            credentialsId = ApiCredentialsId("1"),
                            modelId = "gpt-4o",
                            isMultimodal = true,
                            displayName = "Creative",
                            temperature = 1.0,
                        ),
                    ),
            ),
            ApiModelAssetUi(
                credentialsId = ApiCredentialsId("2"),
                displayName = "Claude 3.5 Sonnet",
                provider = ApiProvider.ANTHROPIC,
                baseUrl = null,
                credentialAlias = "Anthropic Work",
                configurations =
                    listOf(
                        ApiModelConfigUi(
                            id = ApiModelConfigurationId("cfg-3"),
                            credentialsId = ApiCredentialsId("2"),
                            modelId = "claude-3-5-sonnet-20240620",
                            isMultimodal = true,
                            displayName = "Standard",
                            temperature = 0.7,
                        ),
                    ),
            ),
            ApiModelAssetUi(
                credentialsId = ApiCredentialsId("3"),
                displayName = "OpenRouter GPT-5.2",
                provider = ApiProvider.OPENROUTER,
                baseUrl = ApiProvider.OPENROUTER.defaultBaseUrl(),
                credentialAlias = "OpenRouter",
                configurations =
                    listOf(
                        ApiModelConfigUi(
                            id = ApiModelConfigurationId("cfg-4"),
                            credentialsId = ApiCredentialsId("3"),
                            modelId = "openai/gpt-5.2",
                            isMultimodal = true,
                            displayName = "Reliability",
                            temperature = 0.3,
                            openRouterRouting = OpenRouterRoutingConfiguration(),
                        ),
                    ),
            ),
        )

    val defaultAssignments =
        listOf(
            DefaultModelAssignmentUi(ModelType.MAIN, ModelSource.API, "GPT-4o (Balanced)", "Synthesis", "OpenAI", isMultimodal = true),
            DefaultModelAssignmentUi(ModelType.FAST, ModelSource.ON_DEVICE, "Llama 3 8B (Default)", "Fast", isMultimodal = true),
            DefaultModelAssignmentUi(
                ModelType.VISION,
                ModelSource.API,
                "Claude 3.5 Sonnet (Standard)",
                "Vision (API)",
                "Anthropic",
                isMultimodal = true,
            ),
            DefaultModelAssignmentUi(ModelType.THINKING, ModelSource.ON_DEVICE, "Llama 3 8B (Precise)", "Thinking"),
        )

    val baseUiState =
        SettingsUiState(
            home = SettingsHomeUiState(theme = AppTheme.SYSTEM),
            memories =
                MemoriesUiState(
                    memories =
                        listOf(
                            StoredMemory("1", "User prefers concise answers."),
                            StoredMemory("2", "User is a senior software engineer."),
                        ),
                ),
            localModelsSheet = LocalModelsSheetUiState(models = localModels),
            apiProvidersSheet = ApiProvidersSheetUiState(assets = apiModels),
            ttsProvidersSheet =
                TtsProvidersSheetUiState(
                    assets =
                        listOf(
                            TtsProviderAssetUi(
                                id =
                                    com.browntowndev.enclave.domain.model.config
                                        .TtsProviderId("tts-1"),
                                displayName = "Default OpenAI TTS",
                                provider = ApiProvider.OPENAI,
                                voiceName = "alloy",
                            ),
                        ),
                ),
            searchSkillEditor =
                SearchSkillEditorUiState(
                    isEditing = false,
                    enabled = false,
                    selectedProvider = SearchProvider.TAVILY,
                    tavilyKeyPresent = false,
                    exaKeyPresent = false,
                    currentTavilyApiKey = "",
                    currentExaApiKey = "",
                    showSwitchAlert = true,
                ),
            xSearchEditor =
                XSearchEditorUiState(
                    enabled = false,
                    apiKeyPresent = false,
                    currentApiKey = "",
                ),
            assignments = ModelAssignmentsUiState(assignments = defaultAssignments),
        )
}

@Immutable
enum class UsagePeriodUi {
    LIFETIME,
    CURRENT_MONTH,
}

@Immutable
data class UsageSpendProviderRowUi(
    val providerLabel: String,
    val costLabel: String,
    val turnCount: Int,
)

@Immutable
data class UsageSpendSheetUiState(
    val isVisible: Boolean = false,
    val selectedPeriod: UsagePeriodUi = UsagePeriodUi.LIFETIME,
    val totalCostLabel: String = "",
    val providerRows: List<UsageSpendProviderRowUi> = emptyList(),
    val emptyMessage: String? = null,
    val missingCostTurnsLabel: String? = null,
)
