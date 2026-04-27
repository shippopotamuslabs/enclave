package com.browntowndev.pocketcrew.domain.usecase.media

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings
import com.browntowndev.pocketcrew.domain.port.media.VideoGenerationPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.MediaProviderRepositoryPort
import javax.inject.Inject

/**
 * Use case for generating videos using the assigned media provider.
 */
class GenerateVideoUseCase @Inject constructor(
    private val defaultModelRepository: DefaultModelRepositoryPort,
    private val mediaProviderRepository: MediaProviderRepositoryPort,
    private val videoGenerationPort: VideoGenerationPort,
) {
    suspend operator fun invoke(
        prompt: String,
        settings: GenerationSettings
    ): Result<ByteArray> {
        val assignment = defaultModelRepository.getDefault(ModelType.VIDEO_GENERATION)
        val providerId = assignment?.mediaProviderId
            ?: return Result.failure(IllegalStateException("No video generation provider assigned"))

        val provider = mediaProviderRepository.getMediaProvider(providerId)
            ?: return Result.failure(IllegalStateException("Assigned media provider not found"))

        return videoGenerationPort.generateVideo(prompt, provider, settings)
    }
}
