package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.toModelType
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.byok.FetchApiProviderModelDetailUseCase
import com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryRequest
import com.browntowndev.enclave.domain.usecase.settings.SettingsApiProviderUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsMediaUseCases
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
        private val fetchApiProviderModelDetailUseCase: FetchApiProviderModelDetailUseCase,
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
                    val modelTypes =
                        if (asset.tasks.isNotEmpty()) {
                            asset.tasks.map { it.toModelType() }
                        } else {
                            val legacyType =
                                when (asset.capability) {
                                    MediaCapability.IMAGE -> ModelType.IMAGE_GENERATION
                                    MediaCapability.VIDEO -> ModelType.VIDEO_GENERATION
                                    MediaCapability.MUSIC -> ModelType.MUSIC_GENERATION
                                }
                            listOf(legacyType)
                        }
                    if (asset.useAsDefault) {
                        modelTypes.forEach { modelType ->
                            assignmentsUseCases.setDefaultModel(
                                modelType = modelType,
                                localConfigId = null,
                                apiConfigId = null,
                                mediaProviderId = asset.id,
                            )
                        }
                    } else if (previousDraft?.useAsDefault == true) {
                        val previousModelTypes =
                            if (previousDraft.tasks.isNotEmpty()) {
                                previousDraft.tasks.map { it.toModelType() }
                            } else {
                                val legacyType =
                                    when (previousDraft.capability) {
                                        MediaCapability.IMAGE -> ModelType.IMAGE_GENERATION
                                        MediaCapability.VIDEO -> ModelType.VIDEO_GENERATION
                                        MediaCapability.MUSIC -> ModelType.MUSIC_GENERATION
                                    }
                                listOf(legacyType)
                            }
                        previousModelTypes.forEach { modelType ->
                            assignmentsUseCases.clearDefaultModel(modelType)
                        }
                    }
                }
            }

            if (discoveryScopeChanged) {
                apiState.update { it.copy(discoveredApiModels = emptyList(), discoveredApiModelScope = null) }
                onFetchMediaModels(scope, tag, mediaState, currentApiKey)
            } else if (
                previousDraft?.modelName != asset.modelName &&
                asset.provider == ApiProvider.FAL &&
                asset.modelName.isNotBlank()
            ) {
                enrichFalModelTasksIfNeeded(scope, tag, mediaState, currentApiKey, asset)
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
                            discoveredApiModels =
                                result.models.filterMediaModels(
                                    capability = draft.capability,
                                    provider = draft.provider,
                                ),
                            discoveredApiModelScope = result.scope,
                            isDiscoveryTruncated = result.isTruncated,
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
            val credentialAlias = resolveMediaCredentialAlias(mediaState.value, draft).orEmpty()

            scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to save media provider", "Failed to save provider")) {
                val mediaId =
                    mediaUseCases.saveMediaProvider(
                        com.browntowndev.enclave.domain.model.config.MediaProviderAsset(
                            id = draft.id,
                            displayName = draft.displayName,
                            provider = draft.provider,
                            capability = draft.capability,
                            tasks = draft.tasks,
                            modelName = draft.modelName,
                            baseUrl = draft.baseUrl,
                            credentialAlias = credentialAlias,
                        ),
                        apiKey = currentApiKey.value.takeIf { it.isNotBlank() },
                    )

                if (draft.useAsDefault) {
                    val modelTypes =
                        if (draft.tasks.isNotEmpty()) {
                            draft.tasks.map { it.toModelType() }
                        } else {
                            val legacyType =
                                when (draft.capability) {
                                    MediaCapability.IMAGE -> ModelType.IMAGE_GENERATION
                                    MediaCapability.VIDEO -> ModelType.VIDEO_GENERATION
                                    MediaCapability.MUSIC -> ModelType.MUSIC_GENERATION
                                }
                            listOf(legacyType)
                        }
                    modelTypes.forEach { modelType ->
                        assignmentsUseCases.setDefaultModel(
                            modelType = modelType,
                            localConfigId = null,
                            apiConfigId = null,
                            mediaProviderId = mediaId,
                        )
                    }
                }

                currentApiKey.value = ""
                mediaState.update { MediaProvidersTransientState() }
                onSuccess()
            }
        }

        private fun enrichFalModelTasksIfNeeded(
            scope: CoroutineScope,
            tag: String,
            mediaState: MutableStateFlow<MediaProvidersTransientState>,
            currentApiKey: MutableStateFlow<String>,
            draft: MediaProviderAssetUi,
        ) {
            val catalogModel = mediaState.value.discoveredApiModels.find { it.id == draft.modelName }
            if (catalogModel?.tasks?.isNotEmpty() == true) return

            val credentialAlias = resolveMediaCredentialAlias(mediaState.value, draft)
            if (currentApiKey.value.isBlank() && credentialAlias == null) return

            scope.launch(
                errorHandler.coroutineExceptionHandler(
                    tag,
                    "Failed to fetch fal.ai model details",
                    "Failed to fetch model details",
                ),
            ) {
                val detail =
                    fetchApiProviderModelDetailUseCase(
                        provider = ApiProvider.FAL,
                        modelId = draft.modelName,
                        currentApiKey = currentApiKey.value,
                        credentialAlias = credentialAlias,
                        baseUrl = draft.baseUrl,
                    ) ?: return@launch
                if (detail.tasks.isEmpty()) return@launch

                mediaState.update { state ->
                    val updatedCatalog = mergeDiscoveredModel(state.discoveredApiModels, detail)
                    val updatedDraft =
                        state.assetDraft?.takeIf { it.modelName == draft.modelName }?.copy(tasks = detail.tasks)
                            ?: state.assetDraft
                    state.copy(
                        discoveredApiModels = updatedCatalog,
                        assetDraft = updatedDraft,
                    )
                }
            }
        }

        private fun mergeDiscoveredModel(
            models: List<DiscoveredApiModel>,
            detail: DiscoveredApiModel,
        ): List<DiscoveredApiModel> =
            if (models.any { it.id == detail.id }) {
                models.map { if (it.id == detail.id) detail else it }
            } else {
                models + detail
            }

        fun onDeleteMediaProviderAsset(
            scope: CoroutineScope,
            tag: String,
            id: com.browntowndev.enclave.domain.model.config.MediaProviderId,
        ) {
            scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to delete media provider", "Failed to delete provider")) {
                mediaUseCases.deleteMediaProvider(id)
            }
        }
    }
