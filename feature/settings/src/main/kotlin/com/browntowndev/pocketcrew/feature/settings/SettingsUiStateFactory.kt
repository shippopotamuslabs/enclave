package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelAsset
import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset
import com.browntowndev.pocketcrew.domain.model.inference.ApiModelParameterSupport
import com.browntowndev.pocketcrew.domain.model.inference.DiscoveredApiModel
import com.browntowndev.pocketcrew.domain.model.inference.ModelSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.settings.SearchProvider
import com.browntowndev.pocketcrew.domain.port.auth.AuthState
import com.browntowndev.pocketcrew.domain.port.auth.UserProfile
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.usecase.settings.ApiModelDiscoveryScope
import com.browntowndev.pocketcrew.domain.usecase.settings.ApplyApiModelMetadataDefaultsUseCase
import javax.inject.Inject

internal data class SheetVisibilityState(
    val customization: Boolean = false,
    val dataControls: Boolean = false,
    val memories: Boolean = false,
    val feedback: Boolean = false,
    val visionSettings: Boolean = false,
    val searchSkill: Boolean = false,
    val tos: Boolean = false,
    val searchSkillTabIndex: Int = 0,
    val ossLicenses: Boolean = false,
)

internal data class LocalModelsTransientState(
    val isSheetOpen: Boolean = false,
    val availableToDownloadModels: List<LocalModelAsset> = emptyList(),
    val selectedAsset: LocalModelAssetUi? = null,
    val selectedConfig: LocalModelConfigUi? = null,
    val configDraft: LocalModelConfigUi? = null,
)

internal data class ApiProvidersTransientState(
    val isSheetOpen: Boolean = false,
    val selectedAsset: ApiModelAssetUi? = null,
    val assetDraft: ApiModelAssetUi? = null,
    val selectedReusableApiCredentialAlias: String? = null,
    val selectedReusableApiCredentialName: String? = null,
    val presetDraft: ApiModelConfigUi? = null,
    val discoveredApiModels: List<DiscoveredApiModel> = emptyList(),
    val discoveredApiModelScope: ApiModelDiscoveryScope? = null,
    val isDiscoveringApiModels: Boolean = false,
    val modelSearchQuery: String = "",
    val modelProviderFilters: Set<String> = emptySet(),
    val modelSortOption: ModelSortOption = ModelSortOption.A_TO_Z,
)

internal data class MemoriesTransientState(
    val memoryDraft: StoredMemory? = null,
    val isEditing: Boolean = false,
)

internal data class DataControlsTransientState(
    val pendingAction: DataControlsPendingAction? = null,
    val isDeleting: Boolean = false,
    val deleteError: String? = null,
)

internal data class SearchSkillTransientState(
    val isEditing: Boolean = false,
    val enabled: Boolean? = null,
    val selectedProvider: SearchProvider? = null,
    val showSwitchAlert: Boolean? = null,
)

internal data class XSearchTransientState(
    val enabled: Boolean? = null,
)

internal data class TtsProvidersTransientState(
    val isSheetOpen: Boolean = false,
    val selectedAsset: TtsProviderAssetUi? = null,
    val assetDraft: TtsProviderAssetUi? = null,
    val selectedReusableApiCredentialAlias: String? = null,
    val selectedReusableApiCredentialName: String? = null,
)

internal data class MediaProvidersTransientState(
    val isSheetOpen: Boolean = false,
    val selectedAsset: MediaProviderAssetUi? = null,
    val assetDraft: MediaProviderAssetUi? = null,
    val selectedReusableApiCredentialAlias: String? = null,
    val selectedReusableApiCredentialName: String? = null,
    val discoveredApiModels: List<DiscoveredApiModel> = emptyList(),
    val discoveredApiModelScope: ApiModelDiscoveryScope? = null,
    val isDiscoveringApiModels: Boolean = false,
    val modelSearchQuery: String = "",
    val modelProviderFilters: Set<String> = emptySet(),
    val modelSortOption: ModelSortOption = ModelSortOption.A_TO_Z,
)

internal data class AssignmentDialogTransientState(
    val isOpen: Boolean = false,
    val editingSlot: com.browntowndev.pocketcrew.domain.model.inference.ModelType? = null,
)

internal data class DeletionTransientState(
    val showLastModelAlert: Boolean = false,
    val pendingTarget: PendingDeletionTarget? = null,
    val modelTypesNeedingReassignment: List<com.browntowndev.pocketcrew.domain.model.inference.ModelType> = emptyList(),
    val reassignmentOptions: List<ReassignmentOptionUi> = emptyList(),
)

class SettingsUiStateFactory
    @Inject
    constructor(
        private val localModelAssetUiMapper: LocalModelAssetUiMapper,
        private val apiModelAssetUiMapper: ApiModelAssetUiMapper,
        private val ttsProviderAssetUiMapper: TtsProviderAssetUiMapper,
        private val mediaProviderAssetUiMapper: MediaProviderAssetUiMapper,
        private val apiDiscoveryUiFilter: ApiDiscoveryUiFilter,
        private val applyApiModelMetadataDefaultsUseCase: ApplyApiModelMetadataDefaultsUseCase,
    ) {
        @Suppress("LongParameterList", "CyclomaticComplexMethod")
        internal fun create(
            persistedSettings: SettingsData,
            localAssets: List<LocalModelAsset>,
            apiAssets: List<ApiModelAsset>,
            ttsAssets: List<com.browntowndev.pocketcrew.domain.model.config.TtsProviderAsset>,
            mediaAssets: List<MediaProviderAsset>,
            defaultModels: List<DefaultModelAssignment>,
            authState: AuthState,
            sheetVisibility: SheetVisibilityState,
            memories: List<StoredMemory>,
            feedbackText: String,
            localModelsState: LocalModelsTransientState,
            apiState: ApiProvidersTransientState,
            ttsState: TtsProvidersTransientState,
            mediaState: MediaProvidersTransientState,
            searchSkillState: SearchSkillTransientState,
            assignmentsState: AssignmentDialogTransientState,
            deletionState: DeletionTransientState,
            memoriesState: MemoriesTransientState,
            dataControlsState: DataControlsTransientState,
            currentTavilyApiKey: String,
            currentExaApiKey: String,
            currentXApiKey: String,
            xSearchState: XSearchTransientState,
            isReindexing: Boolean,
            reindexProgress: Float,
            vaultReauthSheet: VaultReauthSheetState,
        ): SettingsUiState {
            val localModels = localAssets.map(localModelAssetUiMapper::map)
            val refreshedSelectedLocalAsset =
                localModelsState.selectedAsset?.let { selected ->
                    if (selected.metadataId.value.isEmpty()) {
                        selected
                    } else {
                        localModels.find { it.metadataId == selected.metadataId }?.let { refreshed ->
                            selected.copy(configurations = refreshed.configurations)
                        } ?: selected
                    }
                }

            val apiModels = apiAssets.map(apiModelAssetUiMapper::map)
            val refreshedSelectedApiAsset =
                apiState.selectedAsset?.let { selected ->
                    if (selected.credentialsId.value.isEmpty()) {
                        selected
                    } else {
                        apiModels.find { it.credentialsId == selected.credentialsId }?.let { refreshed ->
                            selected.copy(configurations = refreshed.configurations)
                        } ?: selected
                    }
                }

            val defaultTtsProviderId = defaultModels.find { it.modelType == ModelType.TTS }?.ttsProviderId
            val ttsModels =
                ttsAssets.map { asset ->
                    ttsProviderAssetUiMapper.map(asset, isDefault = asset.id == defaultTtsProviderId)
                }
            val refreshedSelectedTtsAsset =
                ttsState.selectedAsset?.let { selected ->
                    if (selected.id.value.isEmpty()) {
                        selected
                    } else {
                        ttsModels.find { it.id == selected.id } ?: selected
                    }
                }

            val defaultImageProviderId = defaultModels.find { it.modelType == ModelType.IMAGE_GENERATION }?.mediaProviderId
            val defaultVideoProviderId = defaultModels.find { it.modelType == ModelType.VIDEO_GENERATION }?.mediaProviderId
            val defaultMusicProviderId = defaultModels.find { it.modelType == ModelType.MUSIC_GENERATION }?.mediaProviderId
            val mediaModels =
                mediaAssets.map { asset ->
                    val isDefault =
                        when (asset.capability) {
                            MediaCapability.IMAGE -> asset.id == defaultImageProviderId
                            MediaCapability.VIDEO -> asset.id == defaultVideoProviderId
                            MediaCapability.MUSIC -> asset.id == defaultMusicProviderId
                        }
                    mediaProviderAssetUiMapper.map(asset, isDefault = isDefault)
                }
            val refreshedSelectedMediaAsset =
                mediaState.selectedAsset?.let { selected ->
                    if (selected.id.value.isEmpty()) {
                        selected
                    } else {
                        mediaModels.find { it.id == selected.id } ?: selected
                    }
                }

            val activeApiAsset = apiState.assetDraft ?: refreshedSelectedApiAsset
            val parameterSupport =
                activeApiAsset?.let {
                    applyApiModelMetadataDefaultsUseCase.parameterSupport(
                        provider = it.provider,
                        modelId = it.modelId,
                    )
                } ?: ApiModelParameterSupport.DEFAULT
            val selectedReusableCredential =
                apiState.selectedReusableApiCredentialAlias?.let { alias ->
                    apiAssets
                        .find { it.credentials.credentialAlias == alias }
                        ?.let(apiModelAssetUiMapper::mapReusable)
                        ?: apiState.selectedReusableApiCredentialName?.let { displayName ->
                            ReusableApiCredentialUi(
                                credentialsId = ApiCredentialsId(""),
                                displayName = displayName,
                                modelId = "",
                                credentialAlias = alias,
                            )
                        }
                }

            val selectedTtsReusableCredential =
                ttsState.selectedReusableApiCredentialAlias?.let { alias ->
                    apiAssets
                        .find { it.credentials.credentialAlias == alias }
                        ?.let(apiModelAssetUiMapper::mapReusable)
                        ?: ttsState.selectedReusableApiCredentialName?.let { displayName ->
                            ReusableApiCredentialUi(
                                credentialsId = ApiCredentialsId(""),
                                displayName = displayName,
                                modelId = "",
                                credentialAlias = alias,
                            )
                        }
                }

            val selectedMediaReusableCredential =
                mediaState.selectedReusableApiCredentialAlias?.let { alias ->
                    apiAssets
                        .find { it.credentials.credentialAlias == alias }
                        ?.let(apiModelAssetUiMapper::mapReusable)
                        ?: mediaState.selectedReusableApiCredentialName?.let { displayName ->
                            ReusableApiCredentialUi(
                                credentialsId = ApiCredentialsId(""),
                                displayName = displayName,
                                modelId = "",
                                credentialAlias = alias,
                            )
                        }
                }

            val selectedDiscoveredModel = apiState.discoveredApiModels.find { it.id == activeApiAsset?.modelId }
            val normalizedPresetDraft =
                apiState.presetDraft?.let { draft ->
                    val parentCredentialsId =
                        when {
                            draft.credentialsId.value.isNotEmpty() -> draft.credentialsId
                            activeApiAsset != null -> activeApiAsset.credentialsId
                            else -> ApiCredentialsId("")
                        }
                    val normalized =
                        applyApiModelMetadataDefaultsUseCase(
                            provider = activeApiAsset?.provider,
                            modelId = activeApiAsset?.modelId.orEmpty(),
                            currentReasoningEffort = draft.reasoningEffort,
                            currentMaxTokens = draft.maxTokens.toIntOrNull(),
                            currentContextWindow = draft.contextWindow.toIntOrNull(),
                            discoveredModel = selectedDiscoveredModel,
                        )
                    draft.copy(
                        credentialsId = parentCredentialsId,
                        reasoningEffort = normalized.reasoningEffort,
                        maxTokens = normalized.maxTokens?.toString() ?: draft.maxTokens,
                        contextWindow = normalized.contextWindow?.toString() ?: draft.contextWindow,
                    )
                }

            val discoveredApiModels = apiState.discoveredApiModels.map(DiscoveredApiModel::toUi)
            val filteredDiscoveredApiModels =
                apiDiscoveryUiFilter.filter(
                    models = discoveredApiModels,
                    query = apiState.modelSearchQuery,
                    providerFilters = apiState.modelProviderFilters,
                    sortOption = apiState.modelSortOption,
                    requireChatCapability = true,
                )

            val discoveredMediaModels = mediaState.discoveredApiModels.map(DiscoveredApiModel::toUi)
            val filteredDiscoveredMediaModels =
                apiDiscoveryUiFilter.filter(
                    models = discoveredMediaModels,
                    query = mediaState.modelSearchQuery,
                    providerFilters = mediaState.modelProviderFilters,
                    sortOption = mediaState.modelSortOption,
                )

            val assignmentRows =
                buildVisibleAssignments(
                    defaultModels = defaultModels,
                    localModels = localModels,
                    apiModels = apiModels,
                )

            return SettingsUiState(
                home =
                    SettingsHomeUiState(
                        theme = persistedSettings.theme,
                        hapticResponse = persistedSettings.hapticResponse,
                        backgroundInferenceEnabled = persistedSettings.backgroundInferenceEnabled,
                        auth = authState.toUi(),
                        isLocalModelsSheetOpen = localModelsState.isSheetOpen,
                        isApiProvidersSheetOpen = apiState.isSheetOpen,
                        isDataControlsSheetOpen = sheetVisibility.dataControls,
                        isMemoriesSheetOpen = sheetVisibility.memories,
                        isFeedbackSheetOpen = sheetVisibility.feedback,
                        isVisionSettingsSheetOpen = sheetVisibility.visionSettings,
                        isTtsProvidersSheetOpen = ttsState.isSheetOpen,
                        isMediaProvidersSheetOpen = mediaState.isSheetOpen,
                        isSearchSkillSheetOpen = sheetVisibility.searchSkill,
                        isOssLicensesSheetOpen = sheetVisibility.ossLicenses,
                        isTosSheetOpen = sheetVisibility.tos,
                        searchSkillTabIndex = sheetVisibility.searchSkillTabIndex,
                        isReindexing = isReindexing,
                        reindexProgress = reindexProgress,
                    ),
                customization =
                    CustomizationUiState(
                        isSheetOpen = sheetVisibility.customization,
                        enabled = persistedSettings.customizationEnabled,
                        selectedPromptOption = persistedSettings.selectedPromptOption,
                        customPromptText = persistedSettings.customPromptText,
                    ),
                dataControls =
                    DataControlsUiState(
                        isSheetOpen = sheetVisibility.dataControls,
                        allowMemories = persistedSettings.allowMemories,
                        pendingAction = dataControlsState.pendingAction,
                        isDeleting = dataControlsState.isDeleting,
                        deleteError = dataControlsState.deleteError,
                    ),
                memories =
                    MemoriesUiState(
                        isSheetOpen = sheetVisibility.memories,
                        memories = memories,
                        memoryDraft = memoriesState.memoryDraft,
                        isEditing = memoriesState.isEditing,
                    ),
                feedback =
                    FeedbackUiState(
                        isSheetOpen = sheetVisibility.feedback,
                        feedbackText = feedbackText,
                    ),
                localModelsSheet =
                    LocalModelsSheetUiState(
                        isVisible = localModelsState.isSheetOpen,
                        models = localModels.filter { !it.isSoftDeleted },
                        availableDownloads = localModels.filter { it.isSoftDeleted },
                        selectedAsset = refreshedSelectedLocalAsset,
                    ),
                localModelEditor =
                    LocalModelEditorUiState(
                        selectedAsset =
                            refreshedSelectedLocalAsset
                                ?: localModelsState.configDraft
                                    ?.localModelId
                                    ?.takeIf { it.value.isNotEmpty() }
                                    ?.let { id -> localModels.find { it.metadataId == id } },
                        configDraft = localModelsState.configDraft,
                    ),
                apiProvidersSheet =
                    ApiProvidersSheetUiState(
                        isVisible = apiState.isSheetOpen,
                        assets = apiModels,
                        selectedAsset = refreshedSelectedApiAsset,
                    ),
                apiProviderEditor =
                    ApiProviderEditorUiState(
                        assetDraft = apiState.assetDraft,
                        selectedReusableCredential = selectedReusableCredential,
                        presetDraft = normalizedPresetDraft,
                        parameterSupport = parameterSupport,
                        discovery =
                            ApiModelDiscoveryUiState(
                                models = discoveredApiModels,
                                filteredModels = filteredDiscoveredApiModels,
                                isLoading = apiState.isDiscoveringApiModels,
                                searchQuery = apiState.modelSearchQuery,
                                providerFilters = apiState.modelProviderFilters,
                                sortOption = apiState.modelSortOption,
                            ),
                    ),
                ttsProvidersSheet =
                    TtsProvidersSheetUiState(
                        isVisible = ttsState.isSheetOpen,
                        assets = ttsModels,
                        selectedAsset = refreshedSelectedTtsAsset,
                    ),
                ttsProviderEditor =
                    TtsProviderEditorUiState(
                        isEditing = ttsState.selectedAsset != null || ttsState.assetDraft != null,
                        assetDraft = ttsState.assetDraft,
                        selectedReusableCredential = selectedTtsReusableCredential,
                    ),
                mediaProvidersSheet =
                    MediaProvidersSheetUiState(
                        isVisible = mediaState.isSheetOpen,
                        assets = mediaModels,
                        selectedAsset = refreshedSelectedMediaAsset,
                    ),
                mediaProviderEditor =
                    MediaProviderEditorUiState(
                        isEditing = mediaState.selectedAsset != null || mediaState.assetDraft != null,
                        assetDraft = mediaState.assetDraft,
                        selectedReusableCredential = selectedMediaReusableCredential,
                        discovery =
                            ApiModelDiscoveryUiState(
                                models = discoveredMediaModels,
                                filteredModels = filteredDiscoveredMediaModels,
                                isLoading = mediaState.isDiscoveringApiModels,
                                searchQuery = mediaState.modelSearchQuery,
                                providerFilters = mediaState.modelProviderFilters,
                                sortOption = mediaState.modelSortOption,
                            ),
                    ),
                searchSkillEditor =
                    SearchSkillEditorUiState(
                        isEditing = searchSkillState.isEditing,
                        enabled = searchSkillState.enabled ?: persistedSettings.searchEnabled,
                        selectedProvider = searchSkillState.selectedProvider ?: persistedSettings.searchProvider,
                        tavilyKeyPresent = persistedSettings.tavilyKeyPresent,
                        exaKeyPresent = persistedSettings.exaKeyPresent,
                        currentTavilyApiKey = currentTavilyApiKey,
                        currentExaApiKey = currentExaApiKey,
                        showSwitchAlert = persistedSettings.showSearchProviderSwitchAlert,
                    ),
                xSearchEditor =
                    XSearchEditorUiState(
                        enabled = xSearchState.enabled ?: persistedSettings.xSearchEnabled,
                        apiKeyPresent = persistedSettings.xApiKeyPresent,
                        currentApiKey = currentXApiKey,
                    ),
                assignments =
                    ModelAssignmentsUiState(
                        assignments = assignmentRows,
                        isDialogOpen = assignmentsState.isOpen,
                        editingSlot = assignmentsState.editingSlot,
                    ),
                deletion =
                    DeletionFlowUiState(
                        showLastModelAlert = deletionState.showLastModelAlert,
                        pendingTarget = deletionState.pendingTarget,
                        modelTypesNeedingReassignment = deletionState.modelTypesNeedingReassignment,
                        reassignmentOptions = deletionState.reassignmentOptions,
                    ),
                vaultReauthSheet = vaultReauthSheet,
            )
        }

        private fun buildVisibleAssignments(
            defaultModels: List<DefaultModelAssignment>,
            localModels: List<LocalModelAssetUi>,
            apiModels: List<ApiModelAssetUi>,
        ): List<DefaultModelAssignmentUi> {
            val byModelType = defaultModels.associateBy { it.modelType }
            return VISIBLE_MODEL_ASSIGNMENT_TYPES.map { modelType ->
                byModelType[modelType]
                    ?.let { assignment ->
                        assignment.toUi(
                            isMultimodal =
                                isMultimodalAssignment(
                                    assignment = assignment,
                                    localModels = localModels,
                                    apiModels = apiModels,
                                ),
                        )
                    }
                    ?: emptyAssignment(modelType)
            }
        }

        private fun isMultimodalAssignment(
            assignment: DefaultModelAssignment,
            localModels: List<LocalModelAssetUi>,
            apiModels: List<ApiModelAssetUi>,
        ): Boolean =
            when {
                assignment.apiConfigId != null -> {
                    apiModels.any { asset ->
                        asset.isMultimodal && asset.configurations.any { it.id == assignment.apiConfigId }
                    }
                }
                assignment.localConfigId != null -> {
                    localModels.any { asset ->
                        asset.isMultimodal && asset.configurations.any { it.id == assignment.localConfigId }
                    }
                }
                else -> false
            }

        private fun emptyAssignment(modelType: ModelType): DefaultModelAssignmentUi =
            DefaultModelAssignmentUi(
                modelType = modelType,
                source = ModelSource.API,
                currentModelName = "None Assigned",
                displayLabel = modelType.displayLabel,
            )

        private fun AuthState.toUi(): AuthStateUi =
            when (this) {
                is AuthState.Authenticated -> AuthStateUi.Authenticated(user.toUi())
                AuthState.Loading -> AuthStateUi.Loading
                AuthState.Unauthenticated -> AuthStateUi.Unauthenticated
                is AuthState.Error -> AuthStateUi.Error(message)
            }

        private fun UserProfile.toUi(): UserProfileUi =
            UserProfileUi(
                uid = uid,
                displayName = displayName,
                email = email,
                photoUrl = photoUrl,
            )

        private companion object {
            val VISIBLE_MODEL_ASSIGNMENT_TYPES =
                listOf(
                    ModelType.FAST,
                    ModelType.THINKING,
                    ModelType.DRAFT_ONE,
                    ModelType.DRAFT_TWO,
                    ModelType.MAIN,
                    ModelType.FINAL_SYNTHESIS,
                    ModelType.TTS,
                    ModelType.IMAGE_GENERATION,
                    ModelType.VIDEO_GENERATION,
                    ModelType.VISION,
                )
        }
    }
