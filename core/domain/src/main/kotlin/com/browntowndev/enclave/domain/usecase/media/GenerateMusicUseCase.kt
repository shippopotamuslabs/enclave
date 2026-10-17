package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.StudioModelOverride
import com.browntowndev.enclave.domain.port.media.MusicGenerationPort
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import javax.inject.Inject

open class GenerateMusicUseCase
    @Inject
    constructor(
        private val defaultModelRepository: DefaultModelRepositoryPort,
        private val mediaProviderRepository: MediaProviderRepositoryPort,
        private val musicGenerationPort: MusicGenerationPort,
    ) {
        open suspend operator fun invoke(
            prompt: String,
            settings: GenerationSettings,
            override: StudioModelOverride? = null,
        ): Result<ByteArray> {
            // Short-circuit default-slot resolution so getDefault(MUSIC_GENERATION) is
            // never consulted when an override is present (MD01).
            val providerAsset =
                resolveProvider(override)
                    .getOrElse { error -> return Result.failure(error) }
            return musicGenerationPort.generateMusic(prompt, providerAsset, settings)
        }

        private suspend fun resolveProvider(override: StudioModelOverride?): Result<MediaProviderAsset> {
            val provider =
                if (override is StudioModelOverride.MediaProvider) {
                    mediaProviderRepository.getMediaProvider(override.providerId)
                        ?: return Result.failure(
                            IllegalStateException("Override media provider not found: ${override.providerId.value}"),
                        )
                } else {
                    val assignment =
                        defaultModelRepository.getDefault(ModelType.MUSIC_GENERATION)
                            ?: return Result.failure(IllegalStateException("No default music model assigned"))
                    val mediaProviderId =
                        assignment.mediaProviderId
                            ?: return Result.failure(IllegalStateException("No media provider assigned"))
                    mediaProviderRepository.getMediaProvider(mediaProviderId)
                        ?: return Result.failure(IllegalStateException("Provider not found for ID: $mediaProviderId"))
                }
            return Result.success(provider)
        }
    }
