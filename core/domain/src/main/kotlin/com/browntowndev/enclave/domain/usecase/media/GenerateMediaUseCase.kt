package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.GeneratedMediaAsset
import com.browntowndev.enclave.domain.model.media.ImageGenerationProgress
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget
import com.browntowndev.enclave.domain.model.media.MediaGenerationOrigin
import com.browntowndev.enclave.domain.model.media.MediaGenerationRequest
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.domain.model.media.withClampedGenerationCount
import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.util.Clock
import java.util.UUID
import javax.inject.Inject

class GenerateMediaUseCase
    @Inject
    constructor(
        private val generateImageUseCase: GenerateImageUseCase,
        private val generateVideoUseCase: GenerateVideoUseCase,
        private val generateMusicUseCase: GenerateMusicUseCase,
        private val resolveMediaModelTypeUseCase: ResolveMediaModelTypeUseCase,
        private val resolveAssignedImageGenerationTargetUseCase: ResolveAssignedImageGenerationTargetUseCase,
        private val studioRepository: StudioRepositoryPort,
        private val clock: Clock,
    ) {
        suspend operator fun invoke(
            request: MediaGenerationRequest,
            onImageProgress: (ImageGenerationProgress) -> Unit = {},
        ): Result<List<GeneratedMediaAsset>> {
            val mediaType = request.capability.name

            val bytesResult =
                when (request.capability) {
                    MediaCapability.IMAGE -> {
                        val settings = request.settings
                        if (settings !is ImageGenerationSettings) {
                            return Result.failure(
                                IllegalArgumentException(
                                    "Settings type does not match capability ${request.capability}",
                                ),
                            )
                        }
                        val normalizedSettings = settings.withClampedGenerationCount()
                        if (shouldGenerateStudioImagesIncrementally(request, normalizedSettings)) {
                            return generateStudioImagesIncrementally(
                                request = request,
                                settings = normalizedSettings,
                                mediaType = mediaType,
                                onImageProgress = onImageProgress,
                            )
                        }
                        generateImageUseCase(request.prompt, normalizedSettings, onImageProgress, request.override)
                    }
                    MediaCapability.VIDEO -> {
                        val settings = request.settings
                        if (settings !is VideoGenerationSettings) {
                            return Result.failure(
                                IllegalArgumentException(
                                    "Settings type does not match capability ${request.capability}",
                                ),
                            )
                        }
                        generateVideoUseCase(request.prompt, settings, request.override).map { listOf(it) }
                    }
                    MediaCapability.MUSIC -> {
                        val settings = request.settings
                        if (settings !is MusicGenerationSettings) {
                            return Result.failure(
                                IllegalArgumentException(
                                    "Settings type does not match capability ${request.capability}",
                                ),
                            )
                        }
                        generateMusicUseCase(request.prompt, settings, request.override).map { listOf(it) }
                    }
                }

            return bytesResult.fold(
                onFailure = { Result.failure(it) },
                onSuccess = { bytesList ->
                    Result.success(
                        persistGeneratedAssets(
                            request = request,
                            mediaType = mediaType,
                            bytesList = bytesList,
                        ),
                    )
                },
            )
        }

        private suspend fun shouldGenerateStudioImagesIncrementally(
            request: MediaGenerationRequest,
            settings: ImageGenerationSettings,
        ): Boolean {
            if (request.origin != MediaGenerationOrigin.STUDIO_FOREGROUND || settings.generationCount <= 1) {
                return false
            }
            val modelType = resolveMediaModelTypeUseCase(MediaCapability.IMAGE, settings)
            val target =
                resolveAssignedImageGenerationTargetUseCase(modelType, request.override).getOrElse {
                    return false
                }
            return target is ImageGenerationTarget.LocalStableDiffusion
        }

        private suspend fun generateStudioImagesIncrementally(
            request: MediaGenerationRequest,
            settings: ImageGenerationSettings,
            mediaType: String,
            onImageProgress: (ImageGenerationProgress) -> Unit,
        ): Result<List<GeneratedMediaAsset>> {
            val baseTime = clock.currentTimeMillis()
            val assets = mutableListOf<GeneratedMediaAsset>()

            repeat(settings.generationCount) { index ->
                val iterationSettings = imageSettingsForSequentialIteration(settings, index)
                val bytesList =
                    generateImageUseCase(request.prompt, iterationSettings, onImageProgress, request.override)
                        .getOrElse { return Result.failure(it) }
                val bytes =
                    bytesList.singleOrNull()
                        ?: return Result.failure(
                            IllegalStateException(
                                "Expected exactly one image per sequential generation iteration.",
                            ),
                        )
                val asset =
                    buildGeneratedMediaAsset(
                        request = request,
                        mediaType = mediaType,
                        bytes = bytes,
                        baseTime = baseTime,
                        index = index,
                    )
                studioRepository.addEphemeralMedia(asset.toStudioMediaAsset())
                assets += asset
            }

            return Result.success(assets)
        }

        private suspend fun persistGeneratedAssets(
            request: MediaGenerationRequest,
            mediaType: String,
            bytesList: List<ByteArray>,
        ): List<GeneratedMediaAsset> {
            val baseTime =
                if (request.origin == MediaGenerationOrigin.STUDIO_FOREGROUND) {
                    clock.currentTimeMillis()
                } else {
                    null
                }

            val assets =
                bytesList.mapIndexed { index, bytes ->
                    buildGeneratedMediaAsset(
                        request = request,
                        mediaType = mediaType,
                        bytes = bytes,
                        baseTime = baseTime,
                        index = index,
                    )
                }

            assets.forEach { asset ->
                studioRepository.addEphemeralMedia(asset.toStudioMediaAsset())
            }

            return assets
        }

        private suspend fun buildGeneratedMediaAsset(
            request: MediaGenerationRequest,
            mediaType: String,
            bytes: ByteArray,
            baseTime: Long?,
            index: Int,
        ): GeneratedMediaAsset {
            val localUri = studioRepository.cacheEphemeralMedia(bytes, mediaType)
            val id =
                when (request.origin) {
                    MediaGenerationOrigin.CHAT_TOOL -> "chat_media_${UUID.randomUUID()}"
                    MediaGenerationOrigin.STUDIO_FOREGROUND -> UUID.randomUUID().toString()
                }
            val createdAt =
                when (request.origin) {
                    MediaGenerationOrigin.CHAT_TOOL -> clock.currentTimeMillis()
                    MediaGenerationOrigin.STUDIO_FOREGROUND -> baseTime!! + index
                }
            return GeneratedMediaAsset(
                id = id,
                localUri = localUri,
                prompt = request.prompt,
                mediaType = mediaType,
                createdAt = createdAt,
            )
        }

        private fun GeneratedMediaAsset.toStudioMediaAsset(): StudioMediaAsset =
            StudioMediaAsset(
                id = id,
                localUri = localUri,
                prompt = prompt,
                mediaType = mediaType,
                createdAt = createdAt,
            )

        private fun imageSettingsForSequentialIteration(
            settings: ImageGenerationSettings,
            index: Int,
        ): ImageGenerationSettings {
            val explicitSeed = settings.seed.toLongOrNull()
            return settings.copy(
                generationCount = 1,
                seed = if (explicitSeed != null) (explicitSeed + index).toString() else settings.seed,
            )
        }
    }
