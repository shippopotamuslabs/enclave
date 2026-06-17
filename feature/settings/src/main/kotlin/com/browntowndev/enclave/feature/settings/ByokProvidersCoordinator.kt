package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.ApiModelPricingFormat
import com.browntowndev.enclave.domain.model.config.ApiModelSavedPricing
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryRequest
import com.browntowndev.enclave.domain.usecase.settings.ApiPresetDraft
import com.browntowndev.enclave.domain.usecase.settings.ApiProviderDraft
import com.browntowndev.enclave.domain.usecase.settings.ModelDeletionTarget
import com.browntowndev.enclave.domain.usecase.settings.SettingsApiProviderUseCases
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
                    discoveredApiModels = emptyList<DiscoveredApiModel>().mergeSelectedModel(it.presetDraft),
                    connectionTestStatus = ApiConnectionTestStatus.IDLE,
                    connectionTestFingerprint = null,
                )
            }
            currentApiKey.value = ""
            maybeFetchModelsOnReEntry(scope, apiState, currentApiKey, asset)
        }

        fun onSelectApiAsset(
            scope: CoroutineScope,
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
            domainAsset: com.browntowndev.enclave.domain.model.config.ApiModelAsset,
        ) {
            onSelectApiProviderAsset(scope, apiState, currentApiKey, apiModelAssetUiMapper.map(domainAsset))
        }

        fun onApiKeyChanged(
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
            apiKey: String,
        ) {
            currentApiKey.value = apiKey
            val assetDraft = apiState.value.assetDraft ?: return
            val fingerprint =
                connectionFingerprint(
                    asset = assetDraft,
                    apiKey = apiKey,
                    credentialAlias = resolveApiCredentialAlias(apiState.value, assetDraft),
                )
            apiState.update { state ->
                val nextStatus = state.connectionTestStatusForFingerprint(fingerprint)
                state.copy(
                    connectionTestStatus = nextStatus,
                    connectionTestFingerprint =
                        if (nextStatus == ApiConnectionTestStatus.SUCCESS) fingerprint else null,
                )
            }
        }

        fun onApiProviderAssetDraftChange(
            scope: CoroutineScope,
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
            asset: ApiModelAssetUi,
        ) {
            val previousDraft = apiState.value.assetDraft
            val discoveryScopeChanged = !previousDraft.matchesDiscoveryScope(asset)

            apiState.update { state ->
                val existingModels = if (discoveryScopeChanged) emptyList() else state.discoveredApiModels
                val fingerprint =
                    connectionFingerprint(
                        asset = asset,
                        apiKey = currentApiKey.value,
                        credentialAlias = resolveApiCredentialAlias(state, asset),
                    )
                state.copy(
                    assetDraft = asset,
                    selectedReusableApiCredentialAlias =
                        if (previousDraft?.provider != asset.provider) {
                            null
                        } else {
                            state.selectedReusableApiCredentialAlias
                        },
                    selectedReusableApiCredentialName =
                        if (previousDraft?.provider != asset.provider) {
                            null
                        } else {
                            state.selectedReusableApiCredentialName
                        },
                    discoveredApiModels = existingModels.mergeSelectedModel(state.presetDraft),
                    discoveredApiModelScope = if (discoveryScopeChanged) null else state.discoveredApiModelScope,
                    connectionTestStatus = state.connectionTestStatusForFingerprint(fingerprint),
                    connectionTestFingerprint =
                        if (state.connectionTestStatusForFingerprint(fingerprint) == ApiConnectionTestStatus.SUCCESS) {
                            fingerprint
                        } else {
                            null
                        },
                )
            }
            maybeFetchSelectedXaiModelMetadata(scope, apiState, currentApiKey, asset)
        }

        fun onApiModelConfigFieldChange(
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            config: ApiModelConfigUi,
        ) {
            apiState.update { state ->
                val normalized = state.normalizeVisionCapability(config.copy(isFreshPreset = false))
                state.copy(
                    presetDraft = normalized,
                    discoveredApiModels = state.discoveredApiModels.mergeSelectedModel(normalized),
                )
            }
        }

        fun onSelectApiModelConfig(
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            config: ApiModelConfigUi?,
        ) {
            apiState.update {
                it.copy(
                    presetDraft = config,
                    discoveredApiModels = it.discoveredApiModels.mergeSelectedModel(config),
                )
            }
        }

        fun onFetchApiModels(
            scope: CoroutineScope,
            tag: String,
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
        ) {
            val assetDraft = apiState.value.assetDraft ?: apiState.value.selectedAsset ?: return
            val presetDraft = apiState.value.presetDraft ?: return
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
                                selectedModelId = presetDraft.modelId.takeIf(String::isNotBlank),
                            ),
                        )
                    apiState.update { state ->
                        state.copy(
                            discoveredApiModels = result.models.filterChatModels(),
                            discoveredApiModelScope = result.scope,
                            isDiscoveryTruncated = result.isTruncated,
                            presetDraft = state.normalizeVisionCapability(state.presetDraft, result.models),
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
                                baseUrl = assetDraft.baseUrl,
                                credentialAlias = assetDraft.credentialAlias,
                                apiKey = currentApiKey.value,
                                sourceCredentialAlias = sourceCredentialAlias,
                            ),
                        ).getOrElse { throw it }

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
                        discoveredApiModels = state.discoveredApiModels.mergeSelectedModel(createdPreset),
                        discoveredApiModelScope = updatedAsset.toDiscoveryScope(),
                        connectionTestStatus = ApiConnectionTestStatus.IDLE,
                        connectionTestFingerprint = null,
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
                            baseUrl = null,
                            credentialAlias = "",
                            configurations = emptyList(),
                        ),
                    discoveredApiModels = emptyList(),
                    discoveredApiModelScope = null,
                    presetDraft = null,
                    connectionTestStatus = ApiConnectionTestStatus.IDLE,
                    connectionTestFingerprint = null,
                )
            }
        }

        fun onDeleteApiModelAsset(
            scope: CoroutineScope,
            deletionState: MutableStateFlow<DeletionTransientState>,
            tag: String,
            id: ApiCredentialsId,
            onSuccess: () -> Unit = {},
        ) {
            deletionHelper.prepareDeletion(
                scope = scope,
                deletionState = deletionState,
                target = ModelDeletionTarget.ApiProvider(id),
                tag = tag,
                failureMessage = "Failed to delete API provider",
                userMessage = "Failed to delete provider",
                onImmediateSuccess = onSuccess,
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
                                modelId = presetDraft.modelId,
                            ),
                        draft = presetDraft.toDomain(),
                    ).getOrThrow()
                onSuccess()
            }
        }

        fun onTestApiProviderConnection(
            scope: CoroutineScope,
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
            onShowSnackbar: (String) -> Unit,
        ) {
            val assetDraft = apiState.value.assetDraft ?: return
            val credentialAlias = resolveApiCredentialAlias(apiState.value, assetDraft)
            val canTest =
                currentApiKey.value.isNotBlank() ||
                    credentialAlias != null ||
                    assetDraft.credentialsId.value.isNotEmpty()
            if (!canTest) return

            apiState.update { it.copy(connectionTestStatus = ApiConnectionTestStatus.TESTING) }
            scope.launch(
                errorHandler.coroutineExceptionHandler(
                    COORDINATOR_TAG,
                    "Failed to test API provider connection",
                    "Connection test failed",
                ),
            ) {
                val result =
                    apiProviderUseCases.testApiProviderConnection(
                        provider = assetDraft.provider,
                        currentApiKey = currentApiKey.value,
                        credentialAlias = credentialAlias,
                        baseUrl = assetDraft.baseUrl,
                    )
                val fingerprint =
                    connectionFingerprint(
                        asset = assetDraft,
                        apiKey = currentApiKey.value,
                        credentialAlias = credentialAlias,
                    )
                apiState.update { state ->
                    if (result.isSuccess) {
                        state.copy(
                            connectionTestStatus = ApiConnectionTestStatus.SUCCESS,
                            connectionTestFingerprint = fingerprint,
                        )
                    } else {
                        state.copy(
                            connectionTestStatus = ApiConnectionTestStatus.IDLE,
                            connectionTestFingerprint = null,
                        )
                    }
                }
                result.onFailure { error ->
                    onShowSnackbar(error.message ?: "Connection test failed")
                }
            }
        }

        private fun maybeFetchSelectedXaiModelMetadata(
            scope: CoroutineScope,
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
            asset: ApiModelAssetUi?,
        ) {
            val preset = apiState.value.presetDraft
            if (asset?.provider != ApiProvider.XAI || preset == null || preset.modelId.isBlank()) return
            if (apiState.value.discoveredApiModels.isEmpty()) return
            if (apiState.value.discoveredModelFor(preset)?.contextWindowTokens != null) return

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
                            selectedModelId = preset.modelId,
                        ),
                    )
                apiState.update { state ->
                    state.copy(
                        discoveredApiModels = result.models,
                        discoveredApiModelScope = result.scope,
                        isDiscoveryTruncated = result.isTruncated,
                        presetDraft = state.normalizeVisionCapability(state.presetDraft, result.models),
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
            val preset = apiState.value.presetDraft ?: return
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
                                selectedModelId = preset.modelId.takeIf(String::isNotBlank),
                            ),
                        )
                    apiState.update { current ->
                        current.copy(
                            discoveredApiModels = result.models,
                            discoveredApiModelScope = result.scope,
                            isDiscoveryTruncated = result.isTruncated,
                            presetDraft = current.normalizeVisionCapability(current.presetDraft, result.models),
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
                modelId = modelId,
                isMultimodal = isMultimodal,
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
                pricing =
                    ApiModelSavedPricing(
                        promptUsdPerMillion = ApiModelPricingFormat.parseUsdPerMillion(promptPriceUsdPerMillion),
                        completionUsdPerMillion = ApiModelPricingFormat.parseUsdPerMillion(completionPriceUsdPerMillion),
                        cachedPromptUsdPerMillion = ApiModelPricingFormat.parseUsdPerMillion(cachedPromptPriceUsdPerMillion),
                    ),
            )

        private fun connectionFingerprint(
            asset: ApiModelAssetUi,
            apiKey: String,
            credentialAlias: String?,
        ): String =
            apiConnectionFingerprint(
                provider = asset.provider,
                baseUrl = asset.baseUrl,
                apiKey = apiKey,
                credentialAlias = credentialAlias,
            )

        private fun ApiProvidersTransientState.connectionTestStatusForFingerprint(fingerprint: String): ApiConnectionTestStatus =
            if (connectionTestStatus == ApiConnectionTestStatus.SUCCESS && connectionTestFingerprint != fingerprint) {
                ApiConnectionTestStatus.IDLE
            } else {
                connectionTestStatus
            }

        private companion object {
            const val COORDINATOR_TAG = "ByokProvidersCoordinator"
        }
    }
