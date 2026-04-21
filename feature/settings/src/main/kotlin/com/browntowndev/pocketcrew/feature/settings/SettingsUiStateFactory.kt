package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelAsset
import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.inference.ApiModelParameterSupport
import com.browntowndev.pocketcrew.domain.model.inference.DiscoveredApiModel
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
)

internal data class LocalModelsTransientState(
    val isSheetOpen: Boolean = false,
    val availableToDownloadModels: List<LocalModelAsset> = emptyList(),
    val selectedAsset: LocalModelAssetUi? = null,
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

internal data class SearchSkillTransientState(
    val isEditing: Boolean = false,
    val enabled: Boolean? = null,
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

class SettingsUiStateFactory @Inject constructor(
    private val localModelAssetUiMapper: LocalModelAssetUiMapper,
    private val apiModelAssetUiMapper: ApiModelAssetUiMapper,
    private val apiDiscoveryUiFilter: ApiDiscoveryUiFilter,
    private val applyApiModelMetadataDefaultsUseCase: ApplyApiModelMetadataDefaultsUseCase,
) {
    internal fun create(
        persistedSettings: SettingsData,
        localAssets: List<LocalModelAsset>,
        apiAssets: List<ApiModelAsset>,
        defaultModels: List<DefaultModelAssignment>,
        sheetVisibility: SheetVisibilityState,
        memories: List<StoredMemory>,
        feedbackText: String,
        localModelsState: LocalModelsTransientState,
        apiState: ApiProvidersTransientState,
        searchSkillState: SearchSkillTransientState,
        assignmentsState: AssignmentDialogTransientState,
        deletionState: DeletionTransientState,
    ): SettingsUiState {
        val localModels = localAssets.map(localModelAssetUiMapper::map)
        val refreshedSelectedLocalAsset = localModelsState.selectedAsset?.let { selected ->
            if (selected.metadataId.value.isEmpty()) {
                selected
            } else {
                localModels.find { it.metadataId == selected.metadataId }?.let { refreshed ->
                    selected.copy(configurations = refreshed.configurations)
                } ?: selected
            }
        }

        val apiModels = apiAssets.map(apiModelAssetUiMapper::map)
        val refreshedSelectedApiAsset = apiState.selectedAsset?.let { selected ->
            if (selected.credentialsId.value.isEmpty()) {
                selected
            } else {
                apiModels.find { it.credentialsId == selected.credentialsId }?.let { refreshed ->
                    selected.copy(configurations = refreshed.configurations)
                } ?: selected
            }
        }

        val activeApiAsset = apiState.assetDraft ?: refreshedSelectedApiAsset
        val parameterSupport = activeApiAsset?.let {
            applyApiModelMetadataDefaultsUseCase.parameterSupport(
                provider = it.provider,
                modelId = it.modelId,
            )
        } ?: ApiModelParameterSupport.DEFAULT
        val selectedReusableCredential = apiState.selectedReusableApiCredentialAlias?.let { alias ->
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
        val selectedDiscoveredModel = apiState.discoveredApiModels.find { it.id == activeApiAsset?.modelId }
        val normalizedPresetDraft = apiState.presetDraft?.let { draft ->
            val parentCredentialsId = when {
                draft.credentialsId.value.isNotEmpty() -> draft.credentialsId
                activeApiAsset != null -> activeApiAsset.credentialsId
                else -> ApiCredentialsId("")
            }
            val normalized = applyApiModelMetadataDefaultsUseCase(
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
        val filteredDiscoveredApiModels = apiDiscoveryUiFilter.filter(
            models = discoveredApiModels,
            query = apiState.modelSearchQuery,
            providerFilters = apiState.modelProviderFilters,
            sortOption = apiState.modelSortOption,
        )

        return SettingsUiState(
            home = SettingsHomeUiState(
                theme = persistedSettings.theme,
                hapticPress = persistedSettings.hapticPress,
                hapticResponse = persistedSettings.hapticResponse,
                backgroundInferenceEnabled = persistedSettings.backgroundInferenceEnabled,
                isLocalModelsSheetOpen = localModelsState.isSheetOpen,
                isApiProvidersSheetOpen = apiState.isSheetOpen,
                isDataControlsSheetOpen = sheetVisibility.dataControls,
                isMemoriesSheetOpen = sheetVisibility.memories,
                isFeedbackSheetOpen = sheetVisibility.feedback,
                isVisionSettingsSheetOpen = sheetVisibility.visionSettings,
            ),
            customization = CustomizationUiState(
                isSheetOpen = sheetVisibility.customization,
                enabled = persistedSettings.customizationEnabled,
                selectedPromptOption = persistedSettings.selectedPromptOption,
                customPromptText = persistedSettings.customPromptText,
            ),
            dataControls = DataControlsUiState(
                isSheetOpen = sheetVisibility.dataControls,
                allowMemories = persistedSettings.allowMemories,
            ),
            memories = MemoriesUiState(
                isSheetOpen = sheetVisibility.memories,
                memories = memories,
            ),
            feedback = FeedbackUiState(
                isSheetOpen = sheetVisibility.feedback,
                feedbackText = feedbackText,
            ),
            localModelsSheet = LocalModelsSheetUiState(
                isVisible = localModelsState.isSheetOpen,
                models = localModels,
                availableDownloads = localModelsState.availableToDownloadModels.map(localModelAssetUiMapper::map),
                selectedAsset = refreshedSelectedLocalAsset,
            ),
            localModelEditor = LocalModelEditorUiState(
                selectedAsset = refreshedSelectedLocalAsset,
                configDraft = localModelsState.configDraft,
            ),
            apiProvidersSheet = ApiProvidersSheetUiState(
                isVisible = apiState.isSheetOpen,
                assets = apiModels,
                selectedAsset = refreshedSelectedApiAsset,
            ),
            apiProviderEditor = ApiProviderEditorUiState(
                assetDraft = apiState.assetDraft,
                selectedReusableCredential = selectedReusableCredential,
                presetDraft = normalizedPresetDraft,
                parameterSupport = parameterSupport,
                discovery = ApiModelDiscoveryUiState(
                    models = discoveredApiModels,
                    filteredModels = filteredDiscoveredApiModels,
                    isLoading = apiState.isDiscoveringApiModels,
                    searchQuery = apiState.modelSearchQuery,
                    providerFilters = apiState.modelProviderFilters,
                    sortOption = apiState.modelSortOption,
                ),
            ),
            searchSkillEditor = SearchSkillEditorUiState(
                isEditing = searchSkillState.isEditing,
                enabled = searchSkillState.enabled ?: persistedSettings.searchEnabled,
                tavilyKeyPresent = persistedSettings.tavilyKeyPresent,
            ),
            assignments = ModelAssignmentsUiState(
                assignments = defaultModels.map { assignment ->
                    val isMultimodal = when {
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
                    assignment.toUi(isMultimodal)
                },
                isDialogOpen = assignmentsState.isOpen,
                editingSlot = assignmentsState.editingSlot,
            ),
            deletion = DeletionFlowUiState(
                showLastModelAlert = deletionState.showLastModelAlert,
                pendingTarget = deletionState.pendingTarget,
                modelTypesNeedingReassignment = deletionState.modelTypesNeedingReassignment,
                reassignmentOptions = deletionState.reassignmentOptions,
            ),
        )
    }
}
