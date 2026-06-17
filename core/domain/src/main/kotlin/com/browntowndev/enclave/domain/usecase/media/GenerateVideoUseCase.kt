package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.StudioModelOverride
import com.browntowndev.enclave.domain.port.media.VideoGenerationPort
import javax.inject.Inject

/**
 * Use case for generating videos using the assigned media provider.
 */
class GenerateVideoUseCase
    @Inject
    constructor(
        private val resolveMediaModelTypeUseCase: ResolveMediaModelTypeUseCase,
        private val resolveAssignedMediaProviderUseCase: ResolveAssignedMediaProviderUseCase,
        private val videoGenerationPort: VideoGenerationPort,
    ) {
        suspend operator fun invoke(
            prompt: String,
            settings: GenerationSettings,
            override: StudioModelOverride? = null,
        ): Result<ByteArray> {
            val modelType = resolveMediaModelTypeUseCase(MediaCapability.VIDEO, settings)
            val provider =
                resolveAssignedMediaProviderUseCase(modelType, override).getOrElse { error ->
                    return Result.failure(error)
                }

            return videoGenerationPort.generateVideo(prompt, provider, settings)
        }
    }
