package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.port.media.MusicGenerationPort
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import javax.inject.Inject

class GenerateMusicUseCase
    @Inject
    constructor(
        private val defaultModelRepository: DefaultModelRepositoryPort,
        private val mediaProviderRepository: MediaProviderRepositoryPort,
        private val musicGenerationPort: MusicGenerationPort,
    ) {
        suspend operator fun invoke(
            prompt: String,
            settings: GenerationSettings,
        ): Result<ByteArray> {
            val assignment =
                defaultModelRepository.getDefault(ModelType.MUSIC_GENERATION)
                    ?: return Result.failure(IllegalStateException("No default music model assigned"))

            val mediaProviderId =
                assignment.mediaProviderId
                    ?: return Result.failure(IllegalStateException("No media provider assigned"))

            val providerAsset =
                mediaProviderRepository.getMediaProvider(mediaProviderId)
                    ?: return Result.failure(IllegalStateException("Provider not found for ID: $mediaProviderId"))

            return musicGenerationPort.generateMusic(prompt, providerAsset, settings)
        }
    }
