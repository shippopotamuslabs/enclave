package com.browntowndev.pocketcrew.domain.usecase.media

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings
import com.browntowndev.pocketcrew.domain.port.media.ImageGenerationPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.MediaProviderRepositoryPort
import javax.inject.Inject

/**
 * Use case for generating images using the assigned media provider.
 */
class GenerateImageUseCase @Inject constructor(
    private val defaultModelRepository: DefaultModelRepositoryPort,
    private val mediaProviderRepository: MediaProviderRepositoryPort,
    private val imageGenerationPort: ImageGenerationPort,
) {
    suspend operator fun invoke(
        prompt: String,
        settings: GenerationSettings
    ): Result<List<ByteArray>> {
        val assignment = defaultModelRepository.getDefault(ModelType.IMAGE_GENERATION)
        val providerId = assignment?.mediaProviderId 
            ?: return Result.failure(IllegalStateException("No image generation provider assigned"))

        val provider = mediaProviderRepository.getMediaProvider(providerId)
            ?: return Result.failure(IllegalStateException("Assigned media provider not found"))

        return imageGenerationPort.generateImage(prompt, provider, settings)
    }
}
