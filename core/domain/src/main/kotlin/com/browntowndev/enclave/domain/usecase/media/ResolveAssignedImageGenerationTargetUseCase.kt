package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import javax.inject.Inject

class ResolveAssignedImageGenerationTargetUseCase
    @Inject
    constructor(
        private val defaultModelRepository: DefaultModelRepositoryPort,
        private val mediaProviderRepository: MediaProviderRepositoryPort,
        private val localStableDiffusionTargetResolver: LocalStableDiffusionTargetResolver,
    ) {
        suspend operator fun invoke(
            modelType: ModelType = ModelType.IMAGE_GENERATION,
        ): Result<ImageGenerationTarget> =
            runCatching {
                require(modelType == ModelType.IMAGE_GENERATION || modelType == ModelType.IMAGE_EDIT) {
                    "Unsupported image model slot: $modelType"
                }
                val assignment =
                    defaultModelRepository.getDefault(modelType)
                        ?: throw IllegalStateException(modelType.noAssignmentMessage())

                when {
                    assignment.mediaProviderId != null ->
                        resolveRemote(modelType, assignment.mediaProviderId)
                    assignment.localConfigId != null ->
                        localStableDiffusionTargetResolver.resolve(assignment.localConfigId)
                    else -> throw IllegalStateException(modelType.noAssignmentMessage())
                }
            }

        private suspend fun resolveRemote(
            modelType: ModelType,
            mediaProviderId: MediaProviderId,
        ): ImageGenerationTarget {
            val provider =
                mediaProviderRepository.getMediaProvider(mediaProviderId)
                    ?: throw IllegalStateException(modelType.noAssignmentMessage())
            return ImageGenerationTarget.Remote(
                providerAssetId = provider.id,
                providerDisplayName = provider.displayName,
            )
        }

        private companion object {
            fun ModelType.noAssignmentMessage(): String =
                when (this) {
                    ModelType.IMAGE_EDIT -> "No image edit model assigned."
                    else -> "No image generation model assigned."
                }
        }
    }
