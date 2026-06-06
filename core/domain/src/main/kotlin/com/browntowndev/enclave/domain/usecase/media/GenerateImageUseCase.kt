package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationProgress
import com.browntowndev.enclave.domain.port.media.ImageGenerationPort
import javax.inject.Inject

/**
 * Generates images against the assigned default-model slot for the current studio request.
 *
 * Text-to-image resolves [com.browntowndev.enclave.domain.model.inference.ModelType.IMAGE_GENERATION];
 * image-edit (reference attached) resolves [com.browntowndev.enclave.domain.model.inference.ModelType.IMAGE_EDIT].
 * Each slot may be backed by either a remote media provider or an on-device diffusion pack.
 */
class GenerateImageUseCase
    @Inject
    constructor(
        private val resolveMediaModelTypeUseCase: ResolveMediaModelTypeUseCase,
        private val resolveAssignedImageGenerationTargetUseCase: ResolveAssignedImageGenerationTargetUseCase,
        private val imageGenerationPort: ImageGenerationPort,
    ) {
        suspend operator fun invoke(
            prompt: String,
            settings: GenerationSettings,
            onProgress: (ImageGenerationProgress) -> Unit = {},
        ): Result<List<ByteArray>> {
            val modelType = resolveMediaModelTypeUseCase(MediaCapability.IMAGE, settings)
            val target =
                resolveAssignedImageGenerationTargetUseCase(modelType).getOrElse { error ->
                    return Result.failure(error)
                }
            return imageGenerationPort.generateImage(prompt, target, settings, onProgress)
        }
    }
