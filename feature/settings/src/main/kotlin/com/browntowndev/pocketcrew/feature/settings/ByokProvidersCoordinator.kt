package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.DiscoveredApiModel
import com.browntowndev.pocketcrew.domain.usecase.settings.ApiModelDiscoveryRequest
import com.browntowndev.pocketcrew.domain.usecase.settings.ApiPresetDraft
import com.browntowndev.pocketcrew.domain.usecase.settings.ApiProviderDraft
import com.browntowndev.pocketcrew.domain.usecase.settings.ModelDeletionTarget
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsApiProviderUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class ByokProvidersCoordinator
    @Inject
    constructor(
        private val apiProviderUseCases: SettingsApiProviderUseCases,
        private val deletionHelper: SettingsDeletionHelper,
        private val apiModelAssetUiMapper: ApiModelAssetUiMapper,
        private val errorHandler: ViewModelErrorHandler,
    ) {
        fun onShowByokSheet(
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            show: Boolean,
        ) {
            apiState.update { it.copy(isSheetOpen = show) }
        }

        fun onSelectApiProviderAsset(
            scope: CoroutineScope,
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
            asset: ApiModelAssetUi?,
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
            domainAsset: com.browntowndev.pocketcrew.domain.model.config.ApiModelAsset,
        ) {
            onSelectApiProviderAsset(scope, apiState, currentApiKey, apiModelAssetUiMapper.map(domainAsset))
        }

        fun onApiProviderAssetDraftChange(
            scope: CoroutineScope,
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
            asset: ApiModelAssetUi,
        ) {
            val previousDraft = apiState.value.assetDraft
            val discoveryScopeChanged = !previousDraft.matchesDiscoveryScope(asset)

            apiState.update {
                val existingDraft = it.assetDraft
                val existingModels = if (discoveryScopeChanged) emptyList() else it.discoveredApiModels
                val normalizedAsset = it.normalizeVisionCapability(asset, existingModels)
                it.copy(
                    assetDraft = normalizedAsset,
                    selectedReusableApiCredentialAlias =
                        if (existingDraft?.provider != asset.provider) {
                            null
                        } else {
                            it.selectedReusableApiCredentialAlias
                        },
                    selectedReusableApiCredentialName =
                        if (existingDraft?.provider != asset.provider) {
                            null
                        } else {
                            it.selectedReusableApiCredentialName
                        },
                    discoveredApiModels = existingModels.mergeSelectedModel(normalizedAsset),
                    discoveredApiModelScope = if (discoveryScopeChanged) null else it.discoveredApiModelScope,
                )
            }
            maybeFetchSelectedXaiModelMetadata(scope, apiState, currentApiKey, asset)
        }

        fun onApiModelConfigFieldChange(
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            config: ApiModelConfigUi,
        ) {
            apiState.update { it.copy(presetDraft = config) }
        }

        fun onSelectApiModelConfig(
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            config: ApiModelConfigUi?,
        ) {
            apiState.update { it.copy(presetDraft = config) }
        }

        fun onFetchApiModels(
            scope: CoroutineScope,
            tag: String,
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
        ) {
            val assetDraft = apiState.value.assetDraft ?: apiState.value.selectedAsset ?: return
            val credentialAlias = resolveApiCredentialAlias(apiState.value, assetDraft)
            if (currentApiKey.value.isBlank() && credentialAlias == null) return

            apiState.update { it.copy(isDiscoveringApiModels = true) }
            scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to fetch provider models", "Failed to fetch models")) {
                try {
                    val result =
                        apiProviderUseCases.discoverApiModels(
                            ApiModelDiscoveryRequest(
                                provider = assetDraft.provider,
                                currentApiKey = currentApiKey.value,
                                credentialAlias = credentialAlias,
                                baseUrl = assetDraft.baseUrl,
                                selectedModelId = assetDraft.modelId.takeIf(String::isNotBlank),
                            ),
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
            onShowSnackbar: (String) -> Unit,
        ) {
            val assetDraft = apiState.value.assetDraft ?: return
            val sourceCredentialAlias =
                if (currentApiKey.value.isBlank()) {
                    apiState.value.selectedReusableApiCredentialAlias
                } else {
                    null
                }

            scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to save API credentials", "Failed to save credentials")) {
                val result =
                    apiProviderUseCases
                        .saveApiProviderDraft(
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
                                defaultReasoningEffort =
                                    apiProviderUseCases.applyApiModelMetadataDefaults.defaultReasoningEffort(
                                        provider = assetDraft.provider,
                                        modelId = assetDraft.modelId,
                                    ),
                            ),
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

        fun onStartCreateApiModelAsset(apiState: MutableStateFlow<ApiProvidersTransientState>) {
            apiState.update {
                it.copy(
                    selectedAsset = null,
                    assetDraft =
                        ApiModelAssetUi(
                            credentialsId = ApiCredentialsId(""),
                            displayName = "",
                            provider = ApiProvider.OPENAI,
                            modelId = "",
                            baseUrl = null,
                            isMultimodal = false,
                            credentialAlias = "",
                            configurations = emptyList(),
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
            id: ApiCredentialsId,
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
            onSuccess: () -> Unit,
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

        fun onSaveApiModelConfig(
            scope: CoroutineScope,
            tag: String,
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            onSuccess: () -> Unit,
        ) {
            val assetDraft = apiState.value.assetDraft ?: apiState.value.selectedAsset ?: return
            val presetDraft = apiState.value.presetDraft ?: return
            val parentCredentialsId =
                if (presetDraft.credentialsId.value.isNotEmpty()) {
                    presetDraft.credentialsId
                } else {
                    assetDraft.credentialsId
                }

            scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to save API configuration", "Failed to save configuration")) {
                apiProviderUseCases
                    .saveApiPreset(
                        provider = assetDraft.provider,
                        parentCredentialsId = parentCredentialsId,
                        defaultReasoningEffort =
                            apiProviderUseCases.applyApiModelMetadataDefaults.defaultReasoningEffort(
                                provider = assetDraft.provider,
                                modelId = assetDraft.modelId,
                            ),
                        draft = presetDraft.toDomain(),
                    ).getOrThrow()
                onSuccess()
            }
        }

        private fun maybeFetchSelectedXaiModelMetadata(
            scope: CoroutineScope,
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
            asset: ApiModelAssetUi?,
        ) {
            if (asset?.provider != ApiProvider.XAI || asset.modelId.isBlank()) return
            if (apiState.value.discoveredApiModels.isEmpty()) return
            if (apiState.value.discoveredModelFor(asset)?.contextWindowTokens != null) return

            val credentialAlias = resolveApiCredentialAlias(apiState.value, asset)
            if (currentApiKey.value.isBlank() && credentialAlias == null) return

            scope.launch(
                errorHandler.coroutineExceptionHandler(
                    COORDINATOR_TAG,
                    "Failed to fetch xAI model details",
                    "Failed to fetch model details",
                ),
            ) {
                val result =
                    apiProviderUseCases.discoverApiModels(
                        ApiModelDiscoveryRequest(
                            provider = asset.provider,
                            currentApiKey = currentApiKey.value,
                            credentialAlias = credentialAlias,
                            baseUrl = asset.baseUrl,
                            selectedModelId = asset.modelId,
                        ),
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
            asset: ApiModelAssetUi?,
        ) {
            if (asset == null) return
            val state = apiState.value
            if (state.discoveredApiModels.isNotEmpty()) return
            if (state.isDiscoveringApiModels) return

            val credentialAlias = resolveApiCredentialAlias(state, asset)
            if (currentApiKey.value.isBlank() && credentialAlias == null) return

            apiState.update { it.copy(isDiscoveringApiModels = true) }
            scope.launch(
                errorHandler.coroutineExceptionHandler(
                    COORDINATOR_TAG,
                    "Failed to fetch provider models on re-entry",
                    "Failed to fetch models",
                ),
            ) {
                try {
                    val result =
                        apiProviderUseCases.discoverApiModels(
                            ApiModelDiscoveryRequest(
                                provider = asset.provider,
                                currentApiKey = currentApiKey.value,
                                credentialAlias = credentialAlias,
                                baseUrl = asset.baseUrl,
                                selectedModelId = asset.modelId.takeIf(String::isNotBlank),
                            ),
                        )
                    apiState.update { current ->
                        current.copy(
                            discoveredApiModels = result.models,
                            discoveredApiModelScope = result.scope,
                            assetDraft = current.normalizeVisionCapability(current.assetDraft, result.models),
                        )
                    }
                } finally {
                    apiState.update { it.copy(isDiscoveringApiModels = false) }
                }
            }
        }

        private fun ApiModelConfigUi.toDomain(): ApiPresetDraft =
            ApiPresetDraft(
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

        private companion object {
            const val COORDINATOR_TAG = "ByokProvidersCoordinator"
        }
    }
