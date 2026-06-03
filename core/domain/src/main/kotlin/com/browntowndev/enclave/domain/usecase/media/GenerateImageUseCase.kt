package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.port.media.ImageGenerationPort
import javax.inject.Inject

/**
 * Use case for generating images using the assigned media provider.
 */
class GenerateImageUseCase
    @Inject
    constructor(
        private val resolveMediaModelTypeUseCase: ResolveMediaModelTypeUseCase,
        private val resolveAssignedMediaProviderUseCase: ResolveAssignedMediaProviderUseCase,
        private val imageGenerationPort: ImageGenerationPort,
    ) {
        suspend operator fun invoke(
            prompt: String,
            settings: GenerationSettings,
        ): Result<List<ByteArray>> {
            val modelType = resolveMediaModelTypeUseCase(MediaCapability.IMAGE, settings)
            val provider =
                resolveAssignedMediaProviderUseCase(modelType).getOrElse { error ->
                    return Result.failure(error)
                }

            return imageGenerationPort.generateImage(prompt, provider, settings)
        }
    }
