package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.domain.model.config.ApiModelAsset
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.TtsProviderAsset
import com.browntowndev.enclave.domain.port.auth.AuthState
import com.browntowndev.enclave.domain.port.repository.SettingsData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal object SettingsUiStateFlowCombiner {
    internal fun observeRepositorySlice(sources: SettingsUiStateSources): Flow<SettingsRepositorySlice> =
            with(sources) {
                combine(
                    combine(persistedSettings, localAssets, apiAssets, ttsAssets, mediaAssets) {
                        persistedSettings,
                        localAssets,
                        apiAssets,
                        ttsAssets,
                        mediaAssets,
                        ->
                        SettingsRepositoryAssetsSlice(
                            persistedSettings = persistedSettings,
                            localAssets = localAssets,
                            apiAssets = apiAssets,
                            ttsAssets = ttsAssets,
                            mediaAssets = mediaAssets,
                        )
                    },
                    combine(defaultModels, authState) { defaultModels, authState ->
                        SettingsRepositoryMetaSlice(
                            defaultModels = defaultModels,
                            authState = authState,
                        )
                    },
                ) { assets, meta ->
                    SettingsRepositorySlice(
                        persistedSettings = assets.persistedSettings,
                        localAssets = assets.localAssets,
                        apiAssets = assets.apiAssets,
                        ttsAssets = assets.ttsAssets,
                        mediaAssets = assets.mediaAssets,
                        defaultModels = meta.defaultModels,
                        authState = meta.authState,
                    )
                }
            }

    internal fun observeEditorSlice(sources: SettingsUiStateSources): Flow<SettingsEditorSlice> =
            with(sources) {
                combine(
                    combine(sheetVisibility, memories, feedbackText, localModelsState, apiState) {
                        sheetVisibility,
                        memories,
                        feedbackText,
                        localModelsState,
                        apiState,
                        ->
                        SettingsEditorPrimarySlice(
                            sheetVisibility = sheetVisibility,
                            memories = memories,
                            feedbackText = feedbackText,
                            localModelsState = localModelsState,
                            apiState = apiState,
                        )
                    },
                    combine(ttsState, mediaState, searchSkillState, assignmentsState, deletionState) {
                        ttsState,
                        mediaState,
                        searchSkillState,
                        assignmentsState,
                        deletionState,
                        ->
                        SettingsEditorSecondarySlice(
                            ttsState = ttsState,
                            mediaState = mediaState,
                            searchSkillState = searchSkillState,
                            assignmentsState = assignmentsState,
                            deletionState = deletionState,
                        )
                    },
                    combine(memoriesState, dataControlsState) { memoriesState, dataControlsState ->
                        SettingsEditorTertiarySlice(
                            memoriesState = memoriesState,
                            dataControlsState = dataControlsState,
                        )
                    },
                ) { primary, secondary, tertiary ->
                    SettingsEditorSlice(
                        sheetVisibility = primary.sheetVisibility,
                        memories = primary.memories,
                        feedbackText = primary.feedbackText,
                        localModelsState = primary.localModelsState,
                        apiState = primary.apiState,
                        ttsState = secondary.ttsState,
                        mediaState = secondary.mediaState,
                        searchSkillState = secondary.searchSkillState,
                        assignmentsState = secondary.assignmentsState,
                        deletionState = secondary.deletionState,
                        memoriesState = tertiary.memoriesState,
                        dataControlsState = tertiary.dataControlsState,
                    )
                }
            }

    internal fun observeSearchAndVaultSlice(sources: SettingsUiStateSources): Flow<SettingsSearchAndVaultSlice> =
            with(sources) {
                combine(
                    combine(currentTavilyApiKey, currentExaApiKey, currentXApiKey, xSearchState) {
                        currentTavilyApiKey,
                        currentExaApiKey,
                        currentXApiKey,
                        xSearchState,
                        ->
                        SettingsSearchKeysSlice(
                            currentTavilyApiKey = currentTavilyApiKey,
                            currentExaApiKey = currentExaApiKey,
                            currentXApiKey = currentXApiKey,
                            xSearchState = xSearchState,
                        )
                    },
                    combine(isReindexing, reindexProgress, vaultReauthSheet) { isReindexing, reindexProgress, vaultReauthSheet ->
                        SettingsVaultSlice(
                            isReindexing = isReindexing,
                            reindexProgress = reindexProgress,
                            vaultReauthSheet = vaultReauthSheet,
                        )
                    },
                ) { searchKeys, vault ->
                    SettingsSearchAndVaultSlice(
                        currentTavilyApiKey = searchKeys.currentTavilyApiKey,
                        currentExaApiKey = searchKeys.currentExaApiKey,
                        currentXApiKey = searchKeys.currentXApiKey,
                        xSearchState = searchKeys.xSearchState,
                        isReindexing = vault.isReindexing,
                        reindexProgress = vault.reindexProgress,
                        vaultReauthSheet = vault.vaultReauthSheet,
                    )
                }
            }
}

internal data class SettingsRepositoryAssetsSlice(
            val persistedSettings: SettingsData,
            val localAssets: List<LocalModelAsset>,
            val apiAssets: List<ApiModelAsset>,
            val ttsAssets: List<TtsProviderAsset>,
            val mediaAssets: List<MediaProviderAsset>,
        )

internal data class SettingsRepositoryMetaSlice(
            val defaultModels: List<DefaultModelAssignment>,
            val authState: AuthState,
        )

internal data class SettingsRepositorySlice(
            val persistedSettings: SettingsData,
            val localAssets: List<LocalModelAsset>,
            val apiAssets: List<ApiModelAsset>,
            val ttsAssets: List<TtsProviderAsset>,
            val mediaAssets: List<MediaProviderAsset>,
            val defaultModels: List<DefaultModelAssignment>,
            val authState: AuthState,
        )

internal data class SettingsEditorPrimarySlice(
            val sheetVisibility: SheetVisibilityState,
            val memories: List<StoredMemory>,
            val feedbackText: String,
            val localModelsState: LocalModelsTransientState,
            val apiState: ApiProvidersTransientState,
        )

internal data class SettingsEditorSecondarySlice(
            val ttsState: TtsProvidersTransientState,
            val mediaState: MediaProvidersTransientState,
            val searchSkillState: SearchSkillTransientState,
            val assignmentsState: AssignmentDialogTransientState,
            val deletionState: DeletionTransientState,
        )

internal data class SettingsEditorTertiarySlice(
            val memoriesState: MemoriesTransientState,
            val dataControlsState: DataControlsTransientState,
        )

internal data class SettingsEditorSlice(
            val sheetVisibility: SheetVisibilityState,
            val memories: List<StoredMemory>,
            val feedbackText: String,
            val localModelsState: LocalModelsTransientState,
            val apiState: ApiProvidersTransientState,
            val ttsState: TtsProvidersTransientState,
            val mediaState: MediaProvidersTransientState,
            val searchSkillState: SearchSkillTransientState,
            val assignmentsState: AssignmentDialogTransientState,
            val deletionState: DeletionTransientState,
            val memoriesState: MemoriesTransientState,
            val dataControlsState: DataControlsTransientState,
        )

internal data class SettingsSearchKeysSlice(
            val currentTavilyApiKey: String,
            val currentExaApiKey: String,
            val currentXApiKey: String,
            val xSearchState: XSearchTransientState,
        )

internal data class SettingsVaultSlice(
            val isReindexing: Boolean,
            val reindexProgress: Float,
            val vaultReauthSheet: VaultReauthSheetState,
        )

internal data class SettingsSearchAndVaultSlice(
            val currentTavilyApiKey: String,
            val currentExaApiKey: String,
            val currentXApiKey: String,
            val xSearchState: XSearchTransientState,
            val isReindexing: Boolean,
            val reindexProgress: Float,
            val vaultReauthSheet: VaultReauthSheetState,
        )
