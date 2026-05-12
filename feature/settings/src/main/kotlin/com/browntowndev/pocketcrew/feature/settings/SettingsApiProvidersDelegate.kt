package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.DiscoveredApiModel
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.usecase.settings.ApiModelDiscoveryRequest
import com.browntowndev.pocketcrew.domain.usecase.settings.ApiPresetDraft
import com.browntowndev.pocketcrew.domain.usecase.settings.ApiProviderDraft
import com.browntowndev.pocketcrew.domain.usecase.settings.MediaProviderDraft
import com.browntowndev.pocketcrew.domain.usecase.settings.ModelDeletionTarget
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsApiProviderUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsMediaUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsTtsUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.TtsProviderDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class SettingsApiProvidersDelegate @Inject constructor(
    private val apiProviderUseCases: SettingsApiProviderUseCases,
    private val ttsUseCases: SettingsTtsUseCases,
    private val mediaUseCases: SettingsMediaUseCases,
    private val deletionHelper: SettingsDeletionHelper,
    private val apiModelAssetUiMapper: ApiModelAssetUiMapper,
    private val ttsProviderAssetUiMapper: TtsProviderAssetUiMapper,
    private val errorHandler: ViewModelErrorHandler,
) {
    fun onShowByokSheet(apiState: MutableStateFlow<ApiProvidersTransientState>, show: Boolean) {
        apiState.update { it.copy(isSheetOpen = show) }
    }

    fun onShowTtsProvidersSheet(ttsState: MutableStateFlow<TtsProvidersTransientState>, show: Boolean) {
        ttsState.update { it.copy(isSheetOpen = show) }
    }

    fun onShowMediaProvidersSheet(mediaState: MutableStateFlow<MediaProvidersTransientState>, show: Boolean) {
        mediaState.update { it.copy(isSheetOpen = show) }
    }

    fun onSelectApiProviderAsset(
        scope: CoroutineScope,
        apiState: MutableStateFlow<ApiProvidersTransientState>,
        currentApiKey: MutableStateFlow<String>,
        asset: ApiModelAssetUi?
    ) {
        apiState.update {
            it.copy(
                selectedAsset = asset,
                assetDraft = asset,
                presetDraft = null,
                selectedReusableApiCredentialAlias = null,
                selectedReusableApiCredentialName = null,
                discoveredApiModels = emptyList<DiscoveredApiModel>().mergeSelectedModel(asset),
            )
        }
        currentApiKey.value = ""
        maybeFetchModelsOnReEntry(scope, apiState, currentApiKey, asset)
    }

    fun onSelectApiAsset(
        scope: CoroutineScope,
        apiState: MutableStateFlow<ApiProvidersTransientState>,
        currentApiKey: MutableStateFlow<String>,
        domainAsset: com.browntowndev.pocketcrew.domain.model.config.ApiModelAsset
    ) {
        onSelectApiProviderAsset(scope, apiState, currentApiKey, apiModelAssetUiMapper.map(domainAsset))
    }

    fun onApiProviderAssetDraftChange(
        scope: CoroutineScope,
        apiState: MutableStateFlow<ApiProvidersTransientState>,
        currentApiKey: MutableStateFlow<String>,
        asset: ApiModelAssetUi
    ) {
        val previousDraft = apiState.value.assetDraft
        val discoveryScopeChanged = !previousDraft.matchesDiscoveryScope(asset)
        
        apiState.update {
            val existingDraft = it.assetDraft
            val existingModels = if (discoveryScopeChanged) emptyList() else it.discoveredApiModels
            val normalizedAsset = it.normalizeVisionCapability(asset, existingModels)
            it.copy(
                assetDraft = normalizedAsset,
                selectedReusableApiCredentialAlias = if (existingDraft?.provider != asset.provider) null
                    else it.selectedReusableApiCredentialAlias,
                selectedReusableApiCredentialName = if (existingDraft?.provider != asset.provider) null
                    else it.selectedReusableApiCredentialName,
                discoveredApiModels = existingModels.mergeSelectedModel(normalizedAsset),
                discoveredApiModelScope = if (discoveryScopeChanged) null else it.discoveredApiModelScope,
            )
        }
        maybeFetchSelectedXaiModelMetadata(scope, apiState, currentApiKey, asset)
    }

    fun onApiModelConfigFieldChange(
        apiState: MutableStateFlow<ApiProvidersTransientState>,
        config: ApiModelConfigUi
    ) {
        apiState.update { it.copy(presetDraft = config) }
    }

    fun onSelectApiModelConfig(
        apiState: MutableStateFlow<ApiProvidersTransientState>,
        config: ApiModelConfigUi?
    ) {
        apiState.update { it.copy(presetDraft = config) }
    }

    fun onFetchApiModels(
        scope: CoroutineScope,
        tag: String,
        apiState: MutableStateFlow<ApiProvidersTransientState>,
        currentApiKey: MutableStateFlow<String>
    ) {
        val assetDraft = apiState.value.assetDraft ?: apiState.value.selectedAsset ?: return
        val credentialAlias = resolveCredentialAlias(apiState.value, assetDraft)
        if (currentApiKey.value.isBlank() && credentialAlias == null) return

        apiState.update { it.copy(isDiscoveringApiModels = true) }
        scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to fetch provider models", "Failed to fetch models")) {
            try {
                val result = apiProviderUseCases.discoverApiModels(
                    ApiModelDiscoveryRequest(
                        provider = assetDraft.provider,
                        currentApiKey = currentApiKey.value,
                        credentialAlias = credentialAlias,
                        baseUrl = assetDraft.baseUrl,
                        selectedModelId = assetDraft.modelId.takeIf(String::isNotBlank),
                    )
                )
                apiState.update { state ->
                    state.copy(
                        discoveredApiModels = result.models.filterChatModels(),
                        discoveredApiModelScope = result.scope,
                        assetDraft = state.normalizeVisionCapability(state.assetDraft, result.models),
                    )
                }
            } finally {
                apiState.update { it.copy(isDiscoveringApiModels = false) }
            }
        }
    }

    fun onSaveApiCredentials(
        scope: CoroutineScope,
        tag: String,
        apiState: MutableStateFlow<ApiProvidersTransientState>,
        currentApiKey: MutableStateFlow<String>,
        onSuccess: (ApiModelAssetUi, ApiModelConfigUi?) -> Unit,
        onShowSnackbar: (String) -> Unit
    ) {
        val assetDraft = apiState.value.assetDraft ?: return
        val sourceCredentialAlias = if (currentApiKey.value.isBlank()) {
            apiState.value.selectedReusableApiCredentialAlias
        } else {
            null
        }

        scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to save API credentials", "Failed to save credentials")) {
            val result = apiProviderUseCases.saveApiProviderDraft(
                ApiProviderDraft(
                    id = assetDraft.credentialsId,
                    displayName = assetDraft.displayName,
                    provider = assetDraft.provider,
                    modelId = assetDraft.modelId,
                    baseUrl = assetDraft.baseUrl,
                    isMultimodal = assetDraft.isMultimodal,
                    credentialAlias = assetDraft.credentialAlias,
                    apiKey = currentApiKey.value,
                    sourceCredentialAlias = sourceCredentialAlias,
                    defaultReasoningEffort = apiProviderUseCases.applyApiModelMetadataDefaults.defaultReasoningEffort(
                        provider = assetDraft.provider,
                        modelId = assetDraft.modelId,
                    ),
                )
            ).getOrNull() ?: throw IllegalStateException("Operation failed")

            val updatedAsset = apiModelAssetUiMapper.map(result.persistedAsset)
            val createdPreset = result.createdPreset?.let(apiModelAssetUiMapper::mapConfig)

            currentApiKey.value = ""
            apiState.update { state ->
                state.copy(
                    selectedAsset = updatedAsset,
                    assetDraft = updatedAsset,
                    presetDraft = createdPreset,
                    selectedReusableApiCredentialAlias = null,
                    selectedReusableApiCredentialName = null,
                    discoveredApiModels = state.discoveredApiModels.mergeSelectedModel(updatedAsset),
                    discoveredApiModelScope = updatedAsset.toDiscoveryScope(),
                )
            }
            result.linkedExistingAssetDisplayName?.let { displayName ->
                onShowSnackbar("Automatically linked to \"$displayName\" API Model")
            }
            onSuccess(updatedAsset, createdPreset)
        }
    }

    fun onFetchTtsModels(
        scope: CoroutineScope,
        tag: String,
        ttsState: MutableStateFlow<TtsProvidersTransientState>,
        apiState: MutableStateFlow<ApiProvidersTransientState>,
        currentApiKey: MutableStateFlow<String>
    ) {
        val draft = ttsState.value.assetDraft ?: ttsState.value.selectedAsset ?: return
        val credentialAlias = resolveTtsCredentialAlias(ttsState.value, draft)
        if (currentApiKey.value.isBlank() && credentialAlias == null) return

        apiState.update { it.copy(isDiscoveringApiModels = true) }
        scope.launch(
            errorHandler.coroutineExceptionHandler(
                tag,
                "Failed to fetch TTS provider models",
                "Failed to fetch models"
            )
        ) {
            try {
                val result = apiProviderUseCases.discoverApiModels(
                    ApiModelDiscoveryRequest(
                        provider = draft.provider,
                        currentApiKey = currentApiKey.value,
                        credentialAlias = credentialAlias,
                        baseUrl = draft.baseUrl,
                        selectedModelId = draft.modelName
                            ?.takeIf(String::isNotBlank)
                            ?.takeIf { it.isGoogleTtsModelCandidate() },
                    )
                )
                apiState.update { state ->
                    state.copy(
                        discoveredApiModels = result.models.filterGoogleTtsModels(),
                        discoveredApiModelScope = result.scope,
                    )
                }
            } finally {
                apiState.update { it.copy(isDiscoveringApiModels = false) }
            }
        }
    }

    fun onStartCreateTtsProviderAsset(ttsState: MutableStateFlow<TtsProvidersTransientState>) {
        ttsState.update {
            it.copy(
                selectedAsset = null,
                assetDraft = TtsProviderAssetUi(provider = ApiProvider.GOOGLE),
            )
        }
    }

    fun onTtsAssetFieldChange(
        scope: CoroutineScope,
        tag: String,
        ttsState: MutableStateFlow<TtsProvidersTransientState>,
        apiState: MutableStateFlow<ApiProvidersTransientState>,
        currentApiKey: MutableStateFlow<String>,
        assignmentsUseCases: com.browntowndev.pocketcrew.domain.usecase.settings.SettingsAssignmentUseCases,
        asset: TtsProviderAssetUi
    ) {
        val previousDraft = ttsState.value.assetDraft ?: ttsState.value.selectedAsset
        val discoveryScopeChanged = previousDraft?.provider != asset.provider ||
            previousDraft.baseUrl != asset.baseUrl ||
            previousDraft.credentialAlias != asset.credentialAlias

        ttsState.update {
            it.copy(
                assetDraft = asset,
                selectedReusableApiCredentialAlias = if (it.assetDraft?.provider != asset.provider) null
                else it.selectedReusableApiCredentialAlias,
                selectedReusableApiCredentialName = if (it.assetDraft?.provider != asset.provider) null
                else it.selectedReusableApiCredentialName,
            )
        }

        if (asset.id.value.isNotBlank()) {
            // Update default assignment immediately for existing provider
            scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to update default TTS provider", "Failed to update setting")) {
                if (asset.useAsDefault) {
                    assignmentsUseCases.setDefaultModel(
                        modelType = ModelType.TTS,
                        localConfigId = null,
                        apiConfigId = null,
                        ttsProviderId = asset.id
                    )
                } else if (previousDraft?.useAsDefault == true) {
                    assignmentsUseCases.clearDefaultModel(ModelType.TTS)
                }
            }
        }

        if (discoveryScopeChanged) {
            apiState.update { it.copy(discoveredApiModels = emptyList(), discoveredApiModelScope = null) }
            onFetchTtsModels(scope, tag, ttsState, apiState, currentApiKey)
        }
    }

    fun onSelectTtsProviderAsset(
        scope: CoroutineScope,
        tag: String,
        ttsState: MutableStateFlow<TtsProvidersTransientState>,
        apiState: MutableStateFlow<ApiProvidersTransientState>,
        currentApiKey: MutableStateFlow<String>,
        asset: TtsProviderAssetUi?
    ) {
        ttsState.update {
            it.copy(
                selectedAsset = asset,
                assetDraft = asset,
                selectedReusableApiCredentialAlias = null,
                selectedReusableApiCredentialName = null,
            )
        }
        apiState.update { it.copy(discoveredApiModels = emptyList(), discoveredApiModelScope = null) }
        currentApiKey.value = ""
        if (asset != null) {
            onFetchTtsModels(scope, tag, ttsState, apiState, currentApiKey)
        }
    }

    fun onSaveTtsProvider(
        scope: CoroutineScope,
        tag: String,
        ttsState: MutableStateFlow<TtsProvidersTransientState>,
        currentApiKey: MutableStateFlow<String>,
        assignmentsUseCases: com.browntowndev.pocketcrew.domain.usecase.settings.SettingsAssignmentUseCases,
        onSuccess: () -> Unit
    ) {
        val draft = ttsState.value.assetDraft ?: return
        val sourceCredentialAlias = if (currentApiKey.value.isBlank()) {
            ttsState.value.selectedReusableApiCredentialAlias
        } else {
            null
        }

        scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to save TTS provider", "Failed to save provider")) {
            val ttsId = ttsUseCases.saveTtsProvider(
                TtsProviderDraft(
                    id = draft.id,
                    displayName = draft.displayName,
                    provider = draft.provider,
                    modelName = draft.modelName,
                    voiceName = draft.voiceName,
                    baseUrl = draft.baseUrl,
                    credentialAlias = draft.credentialAlias,
                    apiKey = currentApiKey.value,
                    sourceCredentialAlias = sourceCredentialAlias,
                )
            ).getOrNull() ?: throw IllegalStateException("Operation failed")

            if (draft.useAsDefault) {
                assignmentsUseCases.setDefaultModel(
                    modelType = ModelType.TTS,
                    localConfigId = null,
                    apiConfigId = null,
                    ttsProviderId = ttsId
                )
            }

            currentApiKey.value = ""
            ttsState.update { TtsProvidersTransientState() }
            onSuccess()
        }
    }

    fun onStartCreateMediaProviderAsset(mediaState: MutableStateFlow<MediaProvidersTransientState>) {
        mediaState.update {
            it.copy(
                selectedAsset = null,
                assetDraft = MediaProviderAssetUi(provider = ApiProvider.OPENAI, capability = MediaCapability.IMAGE),
            )
        }
    }

    fun onMediaAssetFieldChange(
        scope: CoroutineScope,
        tag: String,
        mediaState: MutableStateFlow<MediaProvidersTransientState>,
        apiState: MutableStateFlow<ApiProvidersTransientState>,
        currentApiKey: MutableStateFlow<String>,
        assignmentsUseCases: com.browntowndev.pocketcrew.domain.usecase.settings.SettingsAssignmentUseCases,
        asset: MediaProviderAssetUi
    ) {
        val previousDraft = mediaState.value.assetDraft ?: mediaState.value.selectedAsset
        val discoveryScopeChanged = previousDraft?.provider != asset.provider ||
            previousDraft.baseUrl != asset.baseUrl ||
            previousDraft.credentialAlias != asset.credentialAlias ||
            previousDraft.capability != asset.capability

        mediaState.update {
            it.copy(
                assetDraft = asset,
                selectedReusableApiCredentialAlias = if (it.assetDraft?.provider != asset.provider) null
                else it.selectedReusableApiCredentialAlias,
                selectedReusableApiCredentialName = if (it.assetDraft?.provider != asset.provider) null
                else it.selectedReusableApiCredentialName,
            )
        }

        if (asset.id.value.isNotBlank()) {
            scope.launch(
                errorHandler.coroutineExceptionHandler(tag, "Failed to update default media provider", "Failed to update setting")
            ) {
                val modelType = when (asset.capability) {
                    MediaCapability.IMAGE -> ModelType.IMAGE_GENERATION
                    MediaCapability.VIDEO -> ModelType.VIDEO_GENERATION
                    MediaCapability.MUSIC -> ModelType.MUSIC_GENERATION
                }
                if (asset.useAsDefault) {
                    assignmentsUseCases.setDefaultModel(
                        modelType = modelType,
                        localConfigId = null,
                        apiConfigId = null,
                        mediaProviderId = asset.id
                    )
                } else if (previousDraft?.useAsDefault == true) {
                    assignmentsUseCases.clearDefaultModel(modelType)
                }
            }
        }

        if (discoveryScopeChanged) {
            apiState.update { it.copy(discoveredApiModels = emptyList(), discoveredApiModelScope = null) }
            onFetchMediaModels(scope, tag, mediaState, currentApiKey)
        }
    }

    fun onFetchMediaModels(
        scope: CoroutineScope,
        tag: String,
        mediaState: MutableStateFlow<MediaProvidersTransientState>,
        currentApiKey: MutableStateFlow<String>
    ) {
        val draft = mediaState.value.assetDraft ?: mediaState.value.selectedAsset ?: return
        val credentialAlias = resolveMediaCredentialAlias(mediaState.value, draft)
        if (currentApiKey.value.isBlank() && credentialAlias == null) return

        mediaState.update { it.copy(isDiscoveringApiModels = true) }
        scope.launch(
            errorHandler.coroutineExceptionHandler(
                tag,
                "Failed to fetch media provider models",
                "Failed to fetch models"
            )
        ) {
            try {
                val result = apiProviderUseCases.discoverApiModels(
                    ApiModelDiscoveryRequest(
                        provider = draft.provider,
                        currentApiKey = currentApiKey.value,
                        credentialAlias = credentialAlias,
                        baseUrl = draft.baseUrl,
                        selectedModelId = draft.modelName.takeIf(String::isNotBlank),
                    )
                )
                mediaState.update { state ->
                    state.copy(
                        discoveredApiModels = result.models.filterMediaModels(draft.capability),
                        discoveredApiModelScope = result.scope,
                    )
                }
            } finally {
                mediaState.update { it.copy(isDiscoveringApiModels = false) }
            }
        }
    }

    fun onSelectMediaProviderAsset(
        scope: CoroutineScope,
        tag: String,
        mediaState: MutableStateFlow<MediaProvidersTransientState>,
        apiState: MutableStateFlow<ApiProvidersTransientState>,
        currentApiKey: MutableStateFlow<String>,
        asset: MediaProviderAssetUi?
    ) {
        mediaState.update {
            it.copy(
                selectedAsset = asset,
                assetDraft = asset,
                selectedReusableApiCredentialAlias = null,
                selectedReusableApiCredentialName = null,
            )
        }
        apiState.update { it.copy(discoveredApiModels = emptyList(), discoveredApiModelScope = null) }
        currentApiKey.value = ""
        if (asset != null) {
            onFetchMediaModels(scope, tag, mediaState, currentApiKey)
        }
    }

    fun onSaveMediaProvider(
        scope: CoroutineScope,
        tag: String,
        mediaState: MutableStateFlow<MediaProvidersTransientState>,
        currentApiKey: MutableStateFlow<String>,
        assignmentsUseCases: com.browntowndev.pocketcrew.domain.usecase.settings.SettingsAssignmentUseCases,
        onSuccess: () -> Unit
    ) {
        val draft = mediaState.value.assetDraft ?: return
        val sourceCredentialAlias = if (currentApiKey.value.isBlank()) {
            mediaState.value.selectedReusableApiCredentialAlias
        } else {
            null
        }

        scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to save media provider", "Failed to save provider")) {
            val mediaId = mediaUseCases.saveMediaProvider(
                com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset(
                    id = draft.id,
                    displayName = draft.displayName,
                    provider = draft.provider,
                    capability = draft.capability,
                    modelName = draft.modelName,
                    baseUrl = draft.baseUrl,
                    credentialAlias = draft.credentialAlias,
                ),
                apiKey = currentApiKey.value.takeIf { it.isNotBlank() },
            )

            if (draft.useAsDefault) {
                val modelType = when (draft.capability) {
                    MediaCapability.IMAGE -> ModelType.IMAGE_GENERATION
                    MediaCapability.VIDEO -> ModelType.VIDEO_GENERATION
                    MediaCapability.MUSIC -> ModelType.MUSIC_GENERATION
                }
                assignmentsUseCases.setDefaultModel(
                    modelType = modelType,
                    localConfigId = null,
                    apiConfigId = null,
                    mediaProviderId = mediaId
                )
            }

            currentApiKey.value = ""
            mediaState.update { MediaProvidersTransientState() }
            onSuccess()
        }
    }

    fun onStartCreateApiModelAsset(apiState: MutableStateFlow<ApiProvidersTransientState>) {
        apiState.update {
            it.copy(
                selectedAsset = null,
                assetDraft = ApiModelAssetUi(
                    credentialsId = ApiCredentialsId(""),
                    displayName = "",
                    provider = ApiProvider.OPENAI,
                    modelId = "",
                    baseUrl = null,
                    isMultimodal = false,
                    credentialAlias = "",
                    configurations = emptyList()
                ),
                discoveredApiModels = emptyList(),
                discoveredApiModelScope = null,
                presetDraft = null,
            )
        }
    }


    fun onDeleteApiModelAsset(
        scope: CoroutineScope,
        deletionState: MutableStateFlow<DeletionTransientState>,
        tag: String,
        id: ApiCredentialsId
    ) {
        deletionHelper.prepareDeletion(
            scope = scope,
            deletionState = deletionState,
            target = ModelDeletionTarget.ApiProvider(id),
            tag = tag,
            failureMessage = "Failed to delete API provider",
            userMessage = "Failed to delete provider",
        )
    }

    fun onDeleteApiModelConfig(
        scope: CoroutineScope,
        deletionState: MutableStateFlow<DeletionTransientState>,
        tag: String,
        id: ApiModelConfigurationId,
        onSuccess: () -> Unit
    ) {
        deletionHelper.prepareDeletion(
            scope = scope,
            deletionState = deletionState,
            target = ModelDeletionTarget.ApiPreset(id),
            tag = tag,
            failureMessage = "Failed to delete API configuration",
            userMessage = "Failed to delete configuration",
            onImmediateSuccess = onSuccess,
        )
    }

    fun onDeleteTtsProviderAsset(
        scope: CoroutineScope,
        tag: String,
        id: com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
    ) {
        scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to delete TTS provider", "Failed to delete provider")) {
            ttsUseCases.deleteTtsProvider(id)
        }
    }

    fun onDeleteMediaProviderAsset(
        scope: CoroutineScope,
        tag: String,
        id: com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
    ) {
        scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to delete media provider", "Failed to delete provider")) {
            mediaUseCases.deleteMediaProvider(id)
        }
    }

    fun onSaveApiModelConfig(
        scope: CoroutineScope,
        tag: String,
        apiState: MutableStateFlow<ApiProvidersTransientState>,
        onSuccess: () -> Unit
    ) {
        val assetDraft = apiState.value.assetDraft ?: apiState.value.selectedAsset ?: return
        val presetDraft = apiState.value.presetDraft ?: return
        val parentCredentialsId = if (presetDraft.credentialsId.value.isNotEmpty()) presetDraft.credentialsId else assetDraft.credentialsId

        scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to save API configuration", "Failed to save configuration")) {
            apiProviderUseCases.saveApiPreset(
                provider = assetDraft.provider,
                parentCredentialsId = parentCredentialsId,
                defaultReasoningEffort = apiProviderUseCases.applyApiModelMetadataDefaults.defaultReasoningEffort(
                    provider = assetDraft.provider,
                    modelId = assetDraft.modelId,
                ),
                draft = presetDraft.toDomain(),
            ).getOrThrow()
            onSuccess()
        }
    }

    private fun ApiModelConfigUi.toDomain(): ApiPresetDraft = ApiPresetDraft(
        id = id,
        credentialsId = credentialsId,
        displayName = displayName,
        maxTokens = maxTokens,
        contextWindow = contextWindow,
        temperature = temperature,
        topP = topP,
        topK = topK,
        minP = minP,
        frequencyPenalty = frequencyPenalty,
        presencePenalty = presencePenalty,
        maxTokensDisabled = maxTokensDisabled,
        temperatureDisabled = temperatureDisabled,
        topPDisabled = topPDisabled,
        topKDisabled = topKDisabled,
        minPDisabled = minPDisabled,
        frequencyPenaltyDisabled = frequencyPenaltyDisabled,
        presencePenaltyDisabled = presencePenaltyDisabled,
        systemPrompt = systemPrompt,
        reasoningEffort = reasoningEffort,
        customHeaders = customHeaders.map { it.key to it.value },
        openRouterRouting = openRouterRouting,
    )

    private fun maybeFetchSelectedXaiModelMetadata(
        scope: CoroutineScope,
        apiState: MutableStateFlow<ApiProvidersTransientState>,
        currentApiKey: MutableStateFlow<String>,
        asset: ApiModelAssetUi?
    ) {
        if (asset?.provider != ApiProvider.XAI || asset.modelId.isBlank()) return
        if (apiState.value.discoveredApiModels.isEmpty()) return
        if (apiState.value.discoveredModelFor(asset)?.contextWindowTokens != null) return

        val credentialAlias = resolveCredentialAlias(apiState.value, asset)
        if (currentApiKey.value.isBlank() && credentialAlias == null) return

        scope.launch(
            errorHandler.coroutineExceptionHandler(
                "SettingsApiProvidersDelegate",
                "Failed to fetch xAI model details",
                "Failed to fetch model details"
            )
        ) {
            val result = apiProviderUseCases.discoverApiModels(
                ApiModelDiscoveryRequest(
                    provider = asset.provider,
                    currentApiKey = currentApiKey.value,
                    credentialAlias = credentialAlias,
                    baseUrl = asset.baseUrl,
                    selectedModelId = asset.modelId,
                )
            )
            apiState.update { state ->
                state.copy(
                    discoveredApiModels = result.models,
                    discoveredApiModelScope = result.scope,
                    assetDraft = state.normalizeVisionCapability(state.assetDraft, result.models),
                )
            }
        }
    }

    private fun maybeFetchModelsOnReEntry(
        scope: CoroutineScope,
        apiState: MutableStateFlow<ApiProvidersTransientState>,
        currentApiKey: MutableStateFlow<String>,
        asset: ApiModelAssetUi?
    ) {
        if (asset == null) return
        val state = apiState.value
        if (state.discoveredApiModels.isNotEmpty()) return
        if (state.isDiscoveringApiModels) return

        val credentialAlias = resolveCredentialAlias(state, asset)
        if (currentApiKey.value.isBlank() && credentialAlias == null) return

        apiState.update { it.copy(isDiscoveringApiModels = true) }
        scope.launch(
            errorHandler.coroutineExceptionHandler(
                "SettingsApiProvidersDelegate",
                "Failed to fetch provider models on re-entry",
                "Failed to fetch models"
            )
        ) {
            try {
                val result = apiProviderUseCases.discoverApiModels(
                    ApiModelDiscoveryRequest(
                        provider = asset.provider,
                        currentApiKey = currentApiKey.value,
                        credentialAlias = credentialAlias,
                        baseUrl = asset.baseUrl,
                        selectedModelId = asset.modelId.takeIf(String::isNotBlank),
                    )
                )
                apiState.update { s ->
                    s.copy(
                        discoveredApiModels = result.models,
                        discoveredApiModelScope = result.scope,
                        assetDraft = s.normalizeVisionCapability(s.assetDraft, result.models),
                    )
                }
            } finally {
                apiState.update { it.copy(isDiscoveringApiModels = false) }
            }
        }
    }

    private fun resolveCredentialAlias(state: ApiProvidersTransientState, asset: ApiModelAssetUi): String? = 
        state.selectedReusableApiCredentialAlias?.takeIf { it.isNotBlank() }
        ?: asset.credentialAlias.takeIf { it.isNotBlank() }

    private fun resolveTtsCredentialAlias(state: TtsProvidersTransientState, asset: TtsProviderAssetUi): String? = 
        state.selectedReusableApiCredentialAlias?.takeIf { it.isNotBlank() }
        ?: asset.credentialAlias.takeIf { it.isNotBlank() }

    private fun resolveMediaCredentialAlias(state: MediaProvidersTransientState, asset: MediaProviderAssetUi): String? = 
        state.selectedReusableApiCredentialAlias?.takeIf { it.isNotBlank() }
        ?: asset.credentialAlias.takeIf { it.isNotBlank() }

    private fun ApiProvidersTransientState.discoveredModelFor(asset: ApiModelAssetUi?): DiscoveredApiModel? = 
        discoveredApiModels.find { it.id == asset?.modelId }

    private fun ApiProvidersTransientState.normalizeVisionCapability(
        asset: ApiModelAssetUi?,
        models: List<DiscoveredApiModel> = discoveredApiModels,
    ): ApiModelAssetUi? {
        if (asset == null) return null
        val discoveredVisionCapability = models
            .find { it.id == asset.modelId }
            ?.isMultimodal
        return discoveredVisionCapability?.let { asset.copy(isMultimodal = it) } ?: asset
    }

    private fun ApiModelAssetUi?.matchesDiscoveryScope(other: ApiModelAssetUi?): Boolean {
        if (this == null || other == null) return false
        if (this.provider != other.provider) return false
        if (this.baseUrl.normalizedBaseUrl() != other.baseUrl.normalizedBaseUrl()) return false
        val currentAlias = this.credentialAlias.takeIf(String::isNotBlank)
        val nextAlias = other.credentialAlias.takeIf(String::isNotBlank)
        return currentAlias == null || nextAlias == null || currentAlias == nextAlias
    }

    private fun String?.normalizedBaseUrl(): String = this?.trim().orEmpty()

    private fun List<DiscoveredApiModel>.mergeSelectedModel(asset: ApiModelAssetUi?): List<DiscoveredApiModel> = when {
        asset == null || asset.modelId.isBlank() -> this
        any { it.id == asset.modelId } -> this
        else -> listOf(DiscoveredApiModel(id = asset.modelId)) + this
    }

    private fun List<DiscoveredApiModel>.filterGoogleTtsModels(): List<DiscoveredApiModel> =
        filter { it.id.isGoogleTtsModelCandidate() }

    private fun List<DiscoveredApiModel>.filterChatModels(): List<DiscoveredApiModel> =
        filter { it.isChatCapable }

    private fun List<DiscoveredApiModel>.filterMediaModels(capability: MediaCapability): List<DiscoveredApiModel> {
        return filter { model ->
            when (capability) {
                MediaCapability.IMAGE -> model.isImageGenerationCapable
                MediaCapability.VIDEO -> model.isVideoGenerationCapable
                MediaCapability.MUSIC -> model.isAudioGenerationCapable
            }
        }
    }

    private fun String.isGoogleTtsModelCandidate(): Boolean =
        endsWith("-tts") || endsWith("-tts-preview")

    private fun ApiModelAssetUi.toDiscoveryScope(): com.browntowndev.pocketcrew.domain.usecase.settings.ApiModelDiscoveryScope =
        com.browntowndev.pocketcrew.domain.usecase.settings.ApiModelDiscoveryScope(
            provider = provider,
            baseUrl = baseUrl.normalizedBaseUrl(),
            credentialAlias = credentialAlias.takeIf(String::isNotBlank),
        )
}
