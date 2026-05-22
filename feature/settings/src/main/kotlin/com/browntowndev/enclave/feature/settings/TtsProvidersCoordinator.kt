package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryRequest
import com.browntowndev.enclave.domain.usecase.settings.SettingsApiProviderUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsTtsUseCases
import com.browntowndev.enclave.domain.usecase.settings.TtsProviderDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class TtsProvidersCoordinator
    @Inject
    constructor(
        private val apiProviderUseCases: SettingsApiProviderUseCases,
        private val ttsUseCases: SettingsTtsUseCases,
        private val errorHandler: ViewModelErrorHandler,
    ) {
        fun onShowTtsProvidersSheet(
            ttsState: MutableStateFlow<TtsProvidersTransientState>,
            show: Boolean,
        ) {
            ttsState.update { it.copy(isSheetOpen = show) }
        }

        fun onFetchTtsModels(
            scope: CoroutineScope,
            tag: String,
            ttsState: MutableStateFlow<TtsProvidersTransientState>,
            apiState: MutableStateFlow<ApiProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
        ) {
            val draft = ttsState.value.assetDraft ?: ttsState.value.selectedAsset ?: return
            val credentialAlias = resolveTtsCredentialAlias(ttsState.value, draft)
            if (currentApiKey.value.isBlank() && credentialAlias == null) return

            apiState.update { it.copy(isDiscoveringApiModels = true) }
            scope.launch(
                errorHandler.coroutineExceptionHandler(
                    tag,
                    "Failed to fetch TTS provider models",
                    "Failed to fetch models",
                ),
            ) {
                try {
                    val result =
                        apiProviderUseCases.discoverApiModels(
                            ApiModelDiscoveryRequest(
                                provider = draft.provider,
                                currentApiKey = currentApiKey.value,
                                credentialAlias = credentialAlias,
                                baseUrl = draft.baseUrl,
                                selectedModelId =
                                    draft.modelName
                                        ?.takeIf(String::isNotBlank)
                                        ?.takeIf { it.isGoogleTtsModelCandidate() },
                            ),
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
            assignmentsUseCases: SettingsAssignmentUseCases,
            asset: TtsProviderAssetUi,
        ) {
            val previousDraft = ttsState.value.assetDraft ?: ttsState.value.selectedAsset
            val discoveryScopeChanged =
                previousDraft?.provider != asset.provider ||
                    previousDraft.baseUrl != asset.baseUrl ||
                    previousDraft.credentialAlias != asset.credentialAlias

            ttsState.update {
                it.copy(
                    assetDraft = asset,
                    selectedReusableApiCredentialAlias =
                        if (it.assetDraft?.provider != asset.provider) {
                            null
                        } else {
                            it.selectedReusableApiCredentialAlias
                        },
                    selectedReusableApiCredentialName =
                        if (it.assetDraft?.provider != asset.provider) {
                            null
                        } else {
                            it.selectedReusableApiCredentialName
                        },
                )
            }

            if (asset.id.value.isNotBlank()) {
                scope.launch(
                    errorHandler.coroutineExceptionHandler(tag, "Failed to update default TTS provider", "Failed to update setting"),
                ) {
                    if (asset.useAsDefault) {
                        assignmentsUseCases.setDefaultModel(
                            modelType = ModelType.TTS,
                            localConfigId = null,
                            apiConfigId = null,
                            ttsProviderId = asset.id,
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
            asset: TtsProviderAssetUi?,
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
            assignmentsUseCases: SettingsAssignmentUseCases,
            onSuccess: () -> Unit,
        ) {
            val draft = ttsState.value.assetDraft ?: return
            val sourceCredentialAlias =
                if (currentApiKey.value.isBlank()) {
                    ttsState.value.selectedReusableApiCredentialAlias
                } else {
                    null
                }

            scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to save TTS provider", "Failed to save provider")) {
                val ttsId =
                    ttsUseCases
                        .saveTtsProvider(
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
                            ),
                        ).getOrNull() ?: throw IllegalStateException("Operation failed")

                if (draft.useAsDefault) {
                    assignmentsUseCases.setDefaultModel(
                        modelType = ModelType.TTS,
                        localConfigId = null,
                        apiConfigId = null,
                        ttsProviderId = ttsId,
                    )
                }

                currentApiKey.value = ""
                ttsState.update { TtsProvidersTransientState() }
                onSuccess()
            }
        }

        fun onDeleteTtsProviderAsset(
            scope: CoroutineScope,
            tag: String,
            id: com.browntowndev.enclave.domain.model.config.TtsProviderId,
        ) {
            scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to delete TTS provider", "Failed to delete provider")) {
                ttsUseCases.deleteTtsProvider(id)
            }
        }
    }
