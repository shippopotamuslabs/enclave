package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryRequest
import com.browntowndev.enclave.domain.usecase.settings.LiveVoiceProviderDraft
import com.browntowndev.enclave.domain.usecase.settings.SettingsApiProviderUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsLiveVoiceUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class LiveVoiceProvidersCoordinator
    @Inject
    constructor(
        private val apiProviderUseCases: SettingsApiProviderUseCases,
        private val liveVoiceUseCases: SettingsLiveVoiceUseCases,
        private val errorHandler: ViewModelErrorHandler,
    ) {
        fun onShowLiveVoiceProvidersSheet(
            liveVoiceState: MutableStateFlow<LiveVoiceProvidersTransientState>,
            show: Boolean,
        ) {
            liveVoiceState.update { it.copy(isSheetOpen = show) }
        }

        fun onFetchLiveVoiceModels(
            scope: CoroutineScope,
            tag: String,
            liveVoiceState: MutableStateFlow<LiveVoiceProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
        ) {
            val draft = liveVoiceState.value.assetDraft ?: liveVoiceState.value.selectedAsset ?: return
            val credentialAlias = resolveLiveVoiceCredentialAlias(liveVoiceState.value, draft)
            if (currentApiKey.value.isBlank() && credentialAlias == null) return

            liveVoiceState.update { it.copy(isDiscoveringApiModels = true) }
            scope.launch(
                errorHandler.coroutineExceptionHandler(
                    tag,
                    "Failed to fetch Live Voice provider models",
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
                                selectedModelId = draft.modelName?.takeIf(String::isNotBlank),
                            ),
                        )
                    liveVoiceState.update { state ->
                        state.copy(
                            discoveredApiModels = result.models.filterLiveVoiceModels(draft.provider),
                            discoveredApiModelScope = result.scope,
                            isDiscoveryTruncated = result.isTruncated,
                        )
                    }
                } finally {
                    liveVoiceState.update { it.copy(isDiscoveringApiModels = false) }
                }
            }
        }

        fun onStartCreateLiveVoiceProviderAsset(
            liveVoiceState: MutableStateFlow<LiveVoiceProvidersTransientState>,
        ) {
            liveVoiceState.update {
                it.copy(
                    selectedAsset = null,
                    assetDraft = LiveVoiceProviderAssetUi(provider = ApiProvider.GOOGLE),
                )
            }
        }

        fun onLiveVoiceAssetFieldChange(
            scope: CoroutineScope,
            tag: String,
            liveVoiceState: MutableStateFlow<LiveVoiceProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
            assignmentsUseCases: SettingsAssignmentUseCases,
            asset: LiveVoiceProviderAssetUi,
        ) {
            val previousDraft = liveVoiceState.value.assetDraft ?: liveVoiceState.value.selectedAsset
            val discoveryScopeChanged =
                previousDraft?.provider != asset.provider ||
                    previousDraft.baseUrl != asset.baseUrl ||
                    previousDraft.credentialAlias != asset.credentialAlias

            liveVoiceState.update {
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
                    errorHandler.coroutineExceptionHandler(tag, "Failed to update default Live Voice provider", "Failed to update setting"),
                ) {
                    if (asset.useAsDefault) {
                        assignmentsUseCases.setDefaultModel(
                            modelType = ModelType.LIVE_VOICE,
                            localConfigId = null,
                            apiConfigId = null,
                            liveVoiceProviderId = asset.id,
                        )
                    } else if (previousDraft?.useAsDefault == true) {
                        assignmentsUseCases.clearDefaultModel(ModelType.LIVE_VOICE)
                    }
                }
            }

            if (discoveryScopeChanged) {
                liveVoiceState.update { it.copy(discoveredApiModels = emptyList(), discoveredApiModelScope = null) }
                onFetchLiveVoiceModels(scope, tag, liveVoiceState, currentApiKey)
            }
        }

        fun onSelectLiveVoiceProviderAsset(
            scope: CoroutineScope,
            tag: String,
            liveVoiceState: MutableStateFlow<LiveVoiceProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
            asset: LiveVoiceProviderAssetUi?,
        ) {
            liveVoiceState.update {
                it.copy(
                    selectedAsset = asset,
                    assetDraft = asset,
                    selectedReusableApiCredentialAlias = null,
                    selectedReusableApiCredentialName = null,
                    discoveredApiModels = emptyList(),
                    discoveredApiModelScope = null,
                )
            }
            currentApiKey.value = ""
            if (asset != null) {
                onFetchLiveVoiceModels(scope, tag, liveVoiceState, currentApiKey)
            }
        }

        fun onSaveLiveVoiceProvider(
            scope: CoroutineScope,
            tag: String,
            liveVoiceState: MutableStateFlow<LiveVoiceProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
            assignmentsUseCases: SettingsAssignmentUseCases,
            onSuccess: () -> Unit,
        ) {
            val draft = liveVoiceState.value.assetDraft ?: return
            val sourceCredentialAlias =
                if (currentApiKey.value.isBlank()) {
                    liveVoiceState.value.selectedReusableApiCredentialAlias
                } else {
                    null
                }

            scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to save Live Voice provider", "Failed to save provider")) {
                val liveVoiceId =
                    liveVoiceUseCases
                        .saveLiveVoiceProvider(
                            LiveVoiceProviderDraft(
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
                        modelType = ModelType.LIVE_VOICE,
                        localConfigId = null,
                        apiConfigId = null,
                        liveVoiceProviderId = liveVoiceId,
                    )
                }

                currentApiKey.value = ""
                liveVoiceState.update { LiveVoiceProvidersTransientState() }
                onSuccess()
            }
        }

        fun onDeleteLiveVoiceProviderAsset(
            scope: CoroutineScope,
            tag: String,
            id: com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId,
            assignmentsUseCases: SettingsAssignmentUseCases,
        ) {
            scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to delete Live Voice provider", "Failed to delete provider")) {
                val defaults = assignmentsUseCases.getDefaultModels().first()
                val liveVoiceDefault = defaults.find { it.modelType == ModelType.LIVE_VOICE }
                if (liveVoiceDefault?.liveVoiceProviderId == id) {
                    // Clear the slot assignment before removing the provider so Firestore never
                    // retains a default_models FK pointing at a deleted live_voice_providers doc.
                    assignmentsUseCases.clearDefaultModel(ModelType.LIVE_VOICE)
                }
                liveVoiceUseCases.deleteLiveVoiceProvider(id)
            }
        }
    }
