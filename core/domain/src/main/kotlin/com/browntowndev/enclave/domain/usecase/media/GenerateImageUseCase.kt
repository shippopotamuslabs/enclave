package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationProgress
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget
import com.browntowndev.enclave.domain.port.media.ImageGenerationPort
import javax.inject.Inject

/**
 * Use case for generating images against the assigned image-generation target.
 *
 * Text-to-image requests resolve through [ResolveAssignedImageGenerationTargetUseCase], which can
 * return either a remote BYOK provider or the on-device stable-diffusion (Bonsai) target and owns
 * the locked artifact-completeness error strings. Image-edit requests (a reference image is
 * attached) stay on the remote provider path, which has no on-device equivalent.
 */
class GenerateImageUseCase
    @Inject
    constructor(
        private val resolveMediaModelTypeUseCase: ResolveMediaModelTypeUseCase,
        private val resolveAssignedMediaProviderUseCase: ResolveAssignedMediaProviderUseCase,
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
                if (modelType == ModelType.IMAGE_GENERATION) {
                    resolveAssignedImageGenerationTargetUseCase().getOrElse { error ->
                        return Result.failure(error)
                    }
                } else {
                    val provider =
                        resolveAssignedMediaProviderUseCase(modelType).getOrElse { error ->
                            return Result.failure(error)
                        }
                    provider.toRemoteTarget()
                }

            return imageGenerationPort.generateImage(prompt, target, settings, onProgress)
        }

        private fun MediaProviderAsset.toRemoteTarget(): ImageGenerationTarget.Remote =
            ImageGenerationTarget.Remote(
                providerAssetId = id,
                providerDisplayName = displayName,
            )
    }
