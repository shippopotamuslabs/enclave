package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.usecase.settings.SettingsAssignmentUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

/**
 * Facade over BYOK, TTS, and media provider coordinators for [SettingsViewModel].
 */
internal class SettingsApiProvidersDelegate
    @Inject
    constructor(
        private val byokCoordinator: ByokProvidersCoordinator,
        private val ttsCoordinator: TtsProvidersCoordinator,
        private val mediaCoordinator: MediaProvidersCoordinator,
    ) {
        fun onShowByokSheet(
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            show: Boolean,
        ) = byokCoordinator.onShowByokSheet(apiState, show)

        fun onShowTtsProvidersSheet(
            ttsState: MutableStateFlow<TtsProvidersTransientState>,
            show: Boolean,
        ) = ttsCoordinator.onShowTtsProvidersSheet(ttsState, show)

        fun onShowMediaProvidersSheet(
            mediaState: MutableStateFlow<MediaProvidersTransientState>,
            show: Boolean,
        ) = mediaCoordinator.onShowMediaProvidersSheet(mediaState, show)

        fun onSelectApiProviderAsset(
            scope: CoroutineScope,
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
            asset: ApiModelAssetUi?,
        ) = byokCoordinator.onSelectApiProviderAsset(scope, apiState, currentApiKey, asset)

        fun onSelectApiAsset(
            scope: CoroutineScope,
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
            domainAsset: com.browntowndev.enclave.domain.model.config.ApiModelAsset,
        ) = byokCoordinator.onSelectApiAsset(scope, apiState, currentApiKey, domainAsset)

        fun onApiProviderAssetDraftChange(
            scope: CoroutineScope,
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
            asset: ApiModelAssetUi,
        ) = byokCoordinator.onApiProviderAssetDraftChange(scope, apiState, currentApiKey, asset)

        fun onApiModelConfigFieldChange(
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            config: ApiModelConfigUi,
        ) = byokCoordinator.onApiModelConfigFieldChange(apiState, config)

        fun onSelectApiModelConfig(
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            config: ApiModelConfigUi?,
        ) = byokCoordinator.onSelectApiModelConfig(apiState, config)

        fun onFetchApiModels(
            scope: CoroutineScope,
            tag: String,
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
        ) = byokCoordinator.onFetchApiModels(scope, tag, apiState, currentApiKey)

        fun onSaveApiCredentials(
            scope: CoroutineScope,
            tag: String,
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
            onSuccess: (ApiModelAssetUi, ApiModelConfigUi?) -> Unit,
            onShowSnackbar: (String) -> Unit,
        ) = byokCoordinator.onSaveApiCredentials(scope, tag, apiState, currentApiKey, onSuccess, onShowSnackbar)

        fun onFetchTtsModels(
            scope: CoroutineScope,
            tag: String,
            ttsState: MutableStateFlow<TtsProvidersTransientState>,
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
        ) = ttsCoordinator.onFetchTtsModels(scope, tag, ttsState, apiState, currentApiKey)

        fun onStartCreateTtsProviderAsset(ttsState: MutableStateFlow<TtsProvidersTransientState>) =
            ttsCoordinator.onStartCreateTtsProviderAsset(ttsState)

        fun onTtsAssetFieldChange(
            scope: CoroutineScope,
            tag: String,
            ttsState: MutableStateFlow<TtsProvidersTransientState>,
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
            assignmentsUseCases: SettingsAssignmentUseCases,
            asset: TtsProviderAssetUi,
        ) = ttsCoordinator.onTtsAssetFieldChange(scope, tag, ttsState, apiState, currentApiKey, assignmentsUseCases, asset)

        fun onSelectTtsProviderAsset(
            scope: CoroutineScope,
            tag: String,
            ttsState: MutableStateFlow<TtsProvidersTransientState>,
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
            asset: TtsProviderAssetUi?,
        ) = ttsCoordinator.onSelectTtsProviderAsset(scope, tag, ttsState, apiState, currentApiKey, asset)

        fun onSaveTtsProvider(
            scope: CoroutineScope,
            tag: String,
            ttsState: MutableStateFlow<TtsProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
            assignmentsUseCases: SettingsAssignmentUseCases,
            onSuccess: () -> Unit,
        ) = ttsCoordinator.onSaveTtsProvider(scope, tag, ttsState, currentApiKey, assignmentsUseCases, onSuccess)

        fun onStartCreateMediaProviderAsset(mediaState: MutableStateFlow<MediaProvidersTransientState>) =
            mediaCoordinator.onStartCreateMediaProviderAsset(mediaState)

        fun onMediaAssetFieldChange(
            scope: CoroutineScope,
            tag: String,
            mediaState: MutableStateFlow<MediaProvidersTransientState>,
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
            assignmentsUseCases: SettingsAssignmentUseCases,
            asset: MediaProviderAssetUi,
        ) = mediaCoordinator.onMediaAssetFieldChange(scope, tag, mediaState, apiState, currentApiKey, assignmentsUseCases, asset)

        fun onFetchMediaModels(
            scope: CoroutineScope,
            tag: String,
            mediaState: MutableStateFlow<MediaProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
        ) = mediaCoordinator.onFetchMediaModels(scope, tag, mediaState, currentApiKey)

        fun onSelectMediaProviderAsset(
            scope: CoroutineScope,
            tag: String,
            mediaState: MutableStateFlow<MediaProvidersTransientState>,
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
            asset: MediaProviderAssetUi?,
        ) = mediaCoordinator.onSelectMediaProviderAsset(scope, tag, mediaState, apiState, currentApiKey, asset)

        fun onSaveMediaProvider(
            scope: CoroutineScope,
            tag: String,
            mediaState: MutableStateFlow<MediaProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
            assignmentsUseCases: SettingsAssignmentUseCases,
            onSuccess: () -> Unit,
        ) = mediaCoordinator.onSaveMediaProvider(scope, tag, mediaState, currentApiKey, assignmentsUseCases, onSuccess)

        fun onStartCreateApiModelAsset(apiState: MutableStateFlow<ApiProvidersTransientState>) =
            byokCoordinator.onStartCreateApiModelAsset(apiState)

        fun onDeleteApiModelAsset(
            scope: CoroutineScope,
            deletionState: MutableStateFlow<DeletionTransientState>,
            tag: String,
            id: ApiCredentialsId,
        ) = byokCoordinator.onDeleteApiModelAsset(scope, deletionState, tag, id)

        fun onDeleteApiModelConfig(
            scope: CoroutineScope,
            deletionState: MutableStateFlow<DeletionTransientState>,
            tag: String,
            id: ApiModelConfigurationId,
            onSuccess: () -> Unit,
        ) = byokCoordinator.onDeleteApiModelConfig(scope, deletionState, tag, id, onSuccess)

        fun onDeleteTtsProviderAsset(
            scope: CoroutineScope,
            tag: String,
            id: com.browntowndev.enclave.domain.model.config.TtsProviderId,
        ) = ttsCoordinator.onDeleteTtsProviderAsset(scope, tag, id)

        fun onDeleteMediaProviderAsset(
            scope: CoroutineScope,
            tag: String,
            id: com.browntowndev.enclave.domain.model.config.MediaProviderId,
        ) = mediaCoordinator.onDeleteMediaProviderAsset(scope, tag, id)

        fun onSaveApiModelConfig(
            scope: CoroutineScope,
            tag: String,
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            onSuccess: () -> Unit,
        ) = byokCoordinator.onSaveApiModelConfig(scope, tag, apiState, onSuccess)

        fun onTestApiProviderConnection(
            scope: CoroutineScope,
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
            onShowSnackbar: (String) -> Unit,
        ) = byokCoordinator.onTestApiProviderConnection(scope, apiState, currentApiKey, onShowSnackbar)

        fun onApiKeyChange(
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
            apiKey: String,
        ) = byokCoordinator.onApiKeyChanged(apiState, currentApiKey, apiKey)
    }
