package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.GeneratedMediaAsset
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.MediaGenerationOrigin
import com.browntowndev.enclave.domain.model.media.MediaGenerationRequest
import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
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
        private val studioRepository: StudioRepositoryPort,
        private val clock: Clock,
    ) {
        suspend operator fun invoke(request: MediaGenerationRequest): Result<List<GeneratedMediaAsset>> {
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
                        generateImageUseCase(request.prompt, settings)
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
                        generateVideoUseCase(request.prompt, settings).map { listOf(it) }
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
                        generateMusicUseCase(request.prompt, settings).map { listOf(it) }
                    }
                }

            return bytesResult.fold(
                onFailure = { Result.failure(it) },
                onSuccess = { bytesList ->
                    val baseTime =
                        if (request.origin == MediaGenerationOrigin.STUDIO_FOREGROUND) {
                            clock.currentTimeMillis()
                        } else {
                            null
                        }

                    val assets =
                        bytesList.mapIndexed { index, bytes ->
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
                            GeneratedMediaAsset(
                                id = id,
                                localUri = localUri,
                                prompt = request.prompt,
                                mediaType = mediaType,
                                createdAt = createdAt,
                            )
                        }

                    assets.forEach { asset ->
                        studioRepository.addEphemeralMedia(
                            StudioMediaAsset(
                                id = asset.id,
                                localUri = asset.localUri,
                                prompt = asset.prompt,
                                mediaType = asset.mediaType,
                                createdAt = asset.createdAt,
                            ),
                        )
                    }

                    Result.success(assets)
                },
            )
        }
    }
