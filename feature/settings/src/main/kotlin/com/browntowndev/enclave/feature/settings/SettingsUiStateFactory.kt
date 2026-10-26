package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.domain.model.config.ApiModelAsset
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.config.TtsProviderAsset
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.browntowndev.enclave.domain.model.telemetry.UsageSpendSummary
import com.browntowndev.enclave.domain.model.entitlement.EntitlementState
import com.browntowndev.enclave.domain.port.auth.AuthState
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.usecase.modelconfig.ModelPackSettingsRow
import com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

internal data class SheetVisibilityState(
    val customization: Boolean = false,
    val dataControls: Boolean = false,
    val memories: Boolean = false,
    val feedback: Boolean = false,
    val usageSpend: Boolean = false,
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
    val isDiscoveryTruncated: Boolean = false,
    val modelSearchQuery: String = "",
    val modelProviderFilters: Set<String> = emptySet(),
    val modelSortOption: ModelSortOption = ModelSortOption.A_TO_Z,
    val connectionTestStatus: ApiConnectionTestStatus = ApiConnectionTestStatus.IDLE,
    val connectionTestFingerprint: String? = null,
    val isOpenRouterProvidersSheetOpen: Boolean = false,
    val isLoadingEndpoints: Boolean = false,
    val endpointFetchError: Boolean = false,
    val openRouterEndpoints: List<com.browntowndev.enclave.domain.model.config.OpenRouterModelEndpoint> = emptyList(),
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

internal data class LiveVoiceProvidersTransientState(
    val isSheetOpen: Boolean = false,
    val selectedAsset: LiveVoiceProviderAssetUi? = null,
    val assetDraft: LiveVoiceProviderAssetUi? = null,
    val selectedReusableApiCredentialAlias: String? = null,
    val selectedReusableApiCredentialName: String? = null,
    val discoveredApiModels: List<DiscoveredApiModel> = emptyList(),
    val discoveredApiModelScope: ApiModelDiscoveryScope? = null,
    val isDiscoveringApiModels: Boolean = false,
    val isDiscoveryTruncated: Boolean = false,
    val modelSearchQuery: String = "",
    val modelProviderFilters: Set<String> = emptySet(),
    val modelSortOption: ModelSortOption = ModelSortOption.A_TO_Z,
)

internal data class TtsProvidersTransientState(
    val isSheetOpen: Boolean = false,
    val selectedAsset: TtsProviderAssetUi? = null,
    val assetDraft: TtsProviderAssetUi? = null,
    val selectedReusableApiCredentialAlias: String? = null,
    val selectedReusableApiCredentialName: String? = null,
    val discoveredApiModels: List<DiscoveredApiModel> = emptyList(),
    val discoveredApiModelScope: ApiModelDiscoveryScope? = null,
    val isDiscoveringApiModels: Boolean = false,
    val isDiscoveryTruncated: Boolean = false,
    val modelSearchQuery: String = "",
    val modelProviderFilters: Set<String> = emptySet(),
    val modelSortOption: ModelSortOption = ModelSortOption.A_TO_Z,
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
    val isDiscoveryTruncated: Boolean = false,
    val modelSearchQuery: String = "",
    val modelProviderFilters: Set<String> = emptySet(),
    val modelSortOption: ModelSortOption = ModelSortOption.A_TO_Z,
)

internal data class AssignmentDialogTransientState(
    val isOpen: Boolean = false,
    val editingSlot: com.browntowndev.enclave.domain.model.inference.ModelType? = null,
)

internal data class DeletionTransientState(
    val showLastModelAlert: Boolean = false,
    val pendingTarget: PendingDeletionTarget? = null,
    val modelTypesNeedingReassignment: List<com.browntowndev.enclave.domain.model.inference.ModelType> = emptyList(),
    val reassignmentOptions: List<ReassignmentOptionUi> = emptyList(),
)

internal data class SettingsUiStateSources(
    val persistedSettings: Flow<SettingsData>,
    val localAssets: Flow<List<LocalModelAsset>>,
    val apiAssets: Flow<List<ApiModelAsset>>,
    val ttsAssets: Flow<List<TtsProviderAsset>>,
    val liveVoiceAssets: Flow<List<LiveVoiceProviderAsset>>,
    val mediaAssets: Flow<List<MediaProviderAsset>>,
    val defaultModels: Flow<List<DefaultModelAssignment>>,
    val authState: Flow<AuthState>,
    val entitlementState: Flow<EntitlementState> = flowOf(EntitlementState.SignedOut),
    val sheetVisibility: StateFlow<SheetVisibilityState>,
    val memories: Flow<List<StoredMemory>>,
    val feedbackText: StateFlow<String>,
    val localModelsState: StateFlow<LocalModelsTransientState>,
    val apiState: StateFlow<ApiProvidersTransientState>,
    val ttsState: StateFlow<TtsProvidersTransientState>,
    val liveVoiceState: StateFlow<LiveVoiceProvidersTransientState>,
    val mediaState: StateFlow<MediaProvidersTransientState>,
    val searchSkillState: StateFlow<SearchSkillTransientState>,
    val assignmentsState: StateFlow<AssignmentDialogTransientState>,
    val deletionState: StateFlow<DeletionTransientState>,
    val memoriesState: StateFlow<MemoriesTransientState>,
    val dataControlsState: StateFlow<DataControlsTransientState>,
    val currentTavilyApiKey: StateFlow<String>,
    val currentExaApiKey: StateFlow<String>,
    val currentXApiKey: StateFlow<String>,
    val xSearchState: StateFlow<XSearchTransientState>,
    val isReindexing: Flow<Boolean>,
    val reindexProgress: Flow<Float>,
    val vaultReauthSheet: StateFlow<VaultReauthSheetState>,
    val usageSpendSummary: Flow<UsageSpendSummary>,
    val modelPackRows: Flow<List<ModelPackSettingsRow>> = kotlinx.coroutines.flow.flowOf(emptyList()),
)

class SettingsUiStateFactory
    @Inject
    constructor(
        private val assembler: SettingsUiStateAssembler,
    ) {
        internal fun observeUiState(sources: SettingsUiStateSources): Flow<SettingsUiState> =
            combine(
                SettingsUiStateFlowCombiner.observeRepositorySlice(sources),
                SettingsUiStateFlowCombiner.observeEditorSlice(sources),
                SettingsUiStateFlowCombiner.observeSearchAndVaultSlice(sources),
                sources.usageSpendSummary,
                sources.modelPackRows,
            ) { repository, editor, searchAndVault, usageSpendSummary, modelPackRows ->
                assembler.create(
                    persistedSettings = repository.persistedSettings,
                    localAssets = repository.localAssets,
                    apiAssets = repository.apiAssets,
                    ttsAssets = repository.ttsAssets,
                    liveVoiceAssets = repository.liveVoiceAssets,
                    mediaAssets = repository.mediaAssets,
                    defaultModels = repository.defaultModels,
                    authState = repository.authState,
                    entitlementState = repository.entitlementState,
                    sheetVisibility = editor.sheetVisibility,
                    memories = editor.memories,
                    feedbackText = editor.feedbackText,
                    localModelsState = editor.localModelsState,
                    apiState = editor.apiState,
                    ttsState = editor.ttsState,
                    liveVoiceState = editor.liveVoiceState,
                    mediaState = editor.mediaState,
                    searchSkillState = editor.searchSkillState,
                    assignmentsState = editor.assignmentsState,
                    deletionState = editor.deletionState,
                    memoriesState = editor.memoriesState,
                    dataControlsState = editor.dataControlsState,
                    currentTavilyApiKey = searchAndVault.currentTavilyApiKey,
                    currentExaApiKey = searchAndVault.currentExaApiKey,
                    currentXApiKey = searchAndVault.currentXApiKey,
                    xSearchState = searchAndVault.xSearchState,
                    isReindexing = searchAndVault.isReindexing,
                    reindexProgress = searchAndVault.reindexProgress,
                    vaultReauthSheet = searchAndVault.vaultReauthSheet,
                    usageSpendSummary = usageSpendSummary,
                    modelPackRows = modelPackRows,
                )
            }

        @Suppress("LongParameterList")
        internal fun create(
            persistedSettings: SettingsData,
            localAssets: List<LocalModelAsset>,
            apiAssets: List<ApiModelAsset>,
            ttsAssets: List<TtsProviderAsset>,
            liveVoiceAssets: List<LiveVoiceProviderAsset> = emptyList(),
            mediaAssets: List<MediaProviderAsset>,
            defaultModels: List<DefaultModelAssignment>,
            authState: AuthState,
            entitlementState: EntitlementState = EntitlementState.SignedOut,
            sheetVisibility: SheetVisibilityState,
            memories: List<StoredMemory>,
            feedbackText: String,
            localModelsState: LocalModelsTransientState,
            apiState: ApiProvidersTransientState,
            ttsState: TtsProvidersTransientState,
            liveVoiceState: LiveVoiceProvidersTransientState = LiveVoiceProvidersTransientState(),
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
            usageSpendSummary: UsageSpendSummary,
            modelPackRows: List<ModelPackSettingsRow> = emptyList(),
        ): SettingsUiState =
            assembler.create(
                persistedSettings,
                localAssets,
                apiAssets,
                ttsAssets,
                liveVoiceAssets,
                mediaAssets,
                defaultModels,
                authState,
                entitlementState,
                sheetVisibility,
                memories,
                feedbackText,
                localModelsState,
                apiState,
                ttsState,
                liveVoiceState,
                mediaState,
                searchSkillState,
                assignmentsState,
                deletionState,
                memoriesState,
                dataControlsState,
                currentTavilyApiKey,
                currentExaApiKey,
                currentXApiKey,
                xSearchState,
                isReindexing,
                reindexProgress,
                vaultReauthSheet,
                usageSpendSummary,
                modelPackRows,
            )
    }
