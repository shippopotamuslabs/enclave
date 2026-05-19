package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.usecase.settings.ApiModelDiscoveryRequest
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsApiProviderUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsMediaUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class MediaProvidersCoordinator
    @Inject
    constructor(
        private val apiProviderUseCases: SettingsApiProviderUseCases,
        private val mediaUseCases: SettingsMediaUseCases,
        private val errorHandler: ViewModelErrorHandler,
    ) {
        fun onShowMediaProvidersSheet(
            mediaState: MutableStateFlow<MediaProvidersTransientState>,
            show: Boolean,
        ) {
            mediaState.update { it.copy(isSheetOpen = show) }
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
            assignmentsUseCases: SettingsAssignmentUseCases,
            asset: MediaProviderAssetUi,
        ) {
            val previousDraft = mediaState.value.assetDraft ?: mediaState.value.selectedAsset
            val discoveryScopeChanged =
                previousDraft?.provider != asset.provider ||
                    previousDraft.baseUrl != asset.baseUrl ||
                    previousDraft.credentialAlias != asset.credentialAlias ||
                    previousDraft.capability != asset.capability

            mediaState.update {
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
                    errorHandler.coroutineExceptionHandler(tag, "Failed to update default media provider", "Failed to update setting"),
                ) {
                    val modelType =
                        when (asset.capability) {
                            MediaCapability.IMAGE -> ModelType.IMAGE_GENERATION
                            MediaCapability.VIDEO -> ModelType.VIDEO_GENERATION
                            MediaCapability.MUSIC -> ModelType.MUSIC_GENERATION
                        }
                    if (asset.useAsDefault) {
                        assignmentsUseCases.setDefaultModel(
                            modelType = modelType,
                            localConfigId = null,
                            apiConfigId = null,
                            mediaProviderId = asset.id,
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
            currentApiKey: MutableStateFlow<String>,
        ) {
            val draft = mediaState.value.assetDraft ?: mediaState.value.selectedAsset ?: return
            val credentialAlias = resolveMediaCredentialAlias(mediaState.value, draft)
            if (currentApiKey.value.isBlank() && credentialAlias == null) return

            mediaState.update { it.copy(isDiscoveringApiModels = true) }
            scope.launch(
                errorHandler.coroutineExceptionHandler(
                    tag,
                    "Failed to fetch media provider models",
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
                                selectedModelId = draft.modelName.takeIf(String::isNotBlank),
                            ),
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
            asset: MediaProviderAssetUi?,
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
            assignmentsUseCases: SettingsAssignmentUseCases,
            onSuccess: () -> Unit,
        ) {
            val draft = mediaState.value.assetDraft ?: return

            scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to save media provider", "Failed to save provider")) {
                val mediaId =
                    mediaUseCases.saveMediaProvider(
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
                    val modelType =
                        when (draft.capability) {
                            MediaCapability.IMAGE -> ModelType.IMAGE_GENERATION
                            MediaCapability.VIDEO -> ModelType.VIDEO_GENERATION
                            MediaCapability.MUSIC -> ModelType.MUSIC_GENERATION
                        }
                    assignmentsUseCases.setDefaultModel(
                        modelType = modelType,
                        localConfigId = null,
                        apiConfigId = null,
                        mediaProviderId = mediaId,
                    )
                }

                currentApiKey.value = ""
                mediaState.update { MediaProvidersTransientState() }
                onSuccess()
            }
        }

        fun onDeleteMediaProviderAsset(
            scope: CoroutineScope,
            tag: String,
            id: com.browntowndev.pocketcrew.domain.model.config.MediaProviderId,
        ) {
            scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to delete media provider", "Failed to delete provider")) {
                mediaUseCases.deleteMediaProvider(id)
            }
        }
    }
