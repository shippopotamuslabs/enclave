package com.browntowndev.enclave.feature.studio.coordinators

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.AspectRatio
import com.browntowndev.enclave.domain.model.media.GenerationQuality
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.domain.port.media.ShareMediaPort
import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.feature.studio.STUDIO_ANIMATE_I2V_UNAVAILABLE
import com.browntowndev.enclave.feature.studio.StudioMediaUi
import com.browntowndev.enclave.feature.studio.buildRehydratedAssets
import com.browntowndev.enclave.feature.studio.effectiveLocalUri
import com.browntowndev.enclave.feature.studio.toAsset
import com.browntowndev.enclave.feature.studio.toMediaCapability
import com.browntowndev.enclave.feature.studio.toUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

class StudioMediaActionsCoordinator
    @Inject
    constructor(
        private val shareMediaPort: ShareMediaPort,
        private val studioRepository: StudioRepositoryPort,
        private val generationCoordinator: StudioGenerationCoordinator,
    ) {
        private lateinit var scope: CoroutineScope
        private lateinit var sessionMedia: StateFlow<List<StudioMediaUi>>
        private lateinit var hasImageToVideoModel: Flow<Boolean>

        fun attach(
            scope: CoroutineScope,
            sessionMedia: StateFlow<List<StudioMediaUi>>,
            hasImageToVideoModel: Flow<Boolean>,
        ) {
            this.scope = scope
            this.sessionMedia = sessionMedia
            this.hasImageToVideoModel = hasImageToVideoModel
        }

        fun shareMedia(assetIds: Set<String>) {
            scope.launch {
                val uris = assetIds.mapNotNull { id -> resolveStudioMediaUi(id)?.effectiveLocalUri() }
                if (uris.isNotEmpty()) {
                    shareMediaPort.shareMedia(uris, "*/*")
                }
            }
        }

        fun shareSingleMedia(assetId: String) {
            scope.launch {
                val asset = resolveStudioMediaUi(assetId) ?: return@launch
                val mimeType =
                    when (asset.mediaType) {
                        MediaCapability.IMAGE -> "image/*"
                        MediaCapability.VIDEO -> "video/*"
                        MediaCapability.MUSIC -> "audio/*"
                    }
                shareMediaPort.shareMedia(listOf(asset.effectiveLocalUri()), mimeType)
            }
        }

        fun onEditMedia(
            prompt: StudioPromptCoordinator,
            assetId: String?,
            rehydrateIds: List<String> = emptyList(),
            rehydrateUris: List<String> = emptyList(),
            rehydrateTypes: List<String> = emptyList(),
            rehydratePrompts: List<String> = emptyList(),
            fromUserAction: Boolean = false,
        ) {
            val cacheKey = assetId ?: rehydrateUris.firstOrNull()
            if (cacheKey == null) return
            if (!fromUserAction && cacheKey == generationCoordinator.lastProcessedEditAssetId) return
            generationCoordinator.lastProcessedEditAssetId = cacheKey

            scope.launch {
                val finalAsset =
                    resolveStudioMediaAsset(
                        assetId = assetId,
                        rehydrateIds = rehydrateIds,
                        rehydrateUris = rehydrateUris,
                        rehydrateTypes = rehydrateTypes,
                        rehydratePrompts = rehydratePrompts,
                    ) ?: return@launch

                prompt.mediaType.value = finalAsset.toMediaCapability()
                prompt.referenceMediaType.value = finalAsset.toMediaCapability()
                prompt.selectedTemplateId.value = null
                generationCoordinator.stopGeneration()
                generationCoordinator.clearScrollAnchors()

                if (assetId != null) {
                    when (finalAsset.toMediaCapability()) {
                        MediaCapability.IMAGE,
                        MediaCapability.VIDEO,
                        -> prompt.onUpdateReferenceImage(finalAsset.localUri)
                        MediaCapability.MUSIC -> Unit
                    }
                }

                if (assetId == null && rehydrateUris.isNotEmpty()) {
                    buildRehydratedAssets(rehydrateIds, rehydrateUris, rehydrateTypes, rehydratePrompts)
                        .forEach { studioRepository.addEphemeralMedia(it) }
                }
            }
        }

        fun onAnimateMedia(
            prompt: StudioPromptCoordinator,
            assetId: String,
            autoAnimate: Boolean = false,
            customPrompt: String? = null,
        ) {
            if (assetId == generationCoordinator.lastProcessedAnimateAssetId && customPrompt == null && !autoAnimate) {
                return
            }

            if (!autoAnimate) {
                generationCoordinator.lastProcessedAnimateAssetId = assetId
            }

            scope.launch {
                val sessionItem = sessionMedia.value.find { it.id == assetId }
                val asset =
                    if (sessionItem != null) {
                        sessionItem.toAsset()
                    } else {
                        studioRepository.getMediaById(assetId)
                    } ?: return@launch

                if (!autoAnimate) {
                    prompt.mediaType.value = MediaCapability.VIDEO
                    prompt.referenceMediaType.value = MediaCapability.VIDEO
                    prompt.settings.value =
                        VideoGenerationSettings(
                            aspectRatio = AspectRatio.ONE_ONE,
                            quality = GenerationQuality.SPEED,
                            videoDuration = DEFAULT_VIDEO_DURATION_SECONDS,
                            videoResolution = DEFAULT_VIDEO_RESOLUTION,
                        )
                } else {
                    if (!hasImageToVideoModel.first()) {
                        generationCoordinator.setVideoGenerationError(
                            sourceAssetId = assetId,
                            message = STUDIO_ANIMATE_I2V_UNAVAILABLE,
                        )
                    } else {
                        generationCoordinator.startDetailVideoGeneration(asset, customPrompt, true)
                    }
                }
            }
        }

        fun deleteMedia(id: String) {
            generationCoordinator.clearEditAssetId(id)
            generationCoordinator.clearAnimateAssetId(id)
            scope.launch {
                studioRepository.deleteMedia(id)
                studioRepository.removeEphemeralMedia(id)
            }
        }

        private suspend fun resolveStudioMediaUi(assetId: String): StudioMediaUi? {
            sessionMedia.value.find { it.id == assetId }?.let { return it }
            return studioRepository.getMediaById(assetId)?.toUi()
        }

        private suspend fun resolveStudioMediaAsset(
            assetId: String?,
            rehydrateIds: List<String>,
            rehydrateUris: List<String>,
            rehydrateTypes: List<String>,
            rehydratePrompts: List<String>,
        ): StudioMediaAsset? {
            val sessionItem = assetId?.let { id -> sessionMedia.value.find { it.id == id } }
            val asset =
                when {
                    sessionItem != null -> sessionItem.toAsset()
                    assetId != null -> studioRepository.getMediaById(assetId)
                    else -> null
                }
            if (asset != null) return asset
            if (rehydrateUris.isEmpty()) return null
            return buildRehydratedAssets(rehydrateIds, rehydrateUris, rehydrateTypes, rehydratePrompts).firstOrNull()
        }

        private companion object {
            private const val DEFAULT_VIDEO_DURATION_SECONDS = 5
            private const val DEFAULT_VIDEO_RESOLUTION = "480p"
        }
    }
