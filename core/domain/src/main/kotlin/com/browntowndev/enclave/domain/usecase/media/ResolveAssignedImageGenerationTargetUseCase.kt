package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget
import com.browntowndev.enclave.domain.model.media.StudioModelOverride
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
            override: StudioModelOverride? = null,
        ): Result<ImageGenerationTarget> {
            val resolved = override?.let { resolveOverride(it) }
            if (resolved != null) {
                return resolved
            }
            return runCatching {
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
        }

        /**
         * Short-circuits slot resolution when an ephemeral override is present so the default
         * slot is never consulted (MD01). Local → on-device diffusion; MediaProvider → remote;
         * Api → fails fast (no API-config execution path for image generation).
         */
        private suspend fun resolveOverride(override: StudioModelOverride): Result<ImageGenerationTarget>? =
            when (override) {
                is StudioModelOverride.Local ->
                    runCatching {
                        localStableDiffusionTargetResolver.resolve(override.configId)
                    }
                is StudioModelOverride.MediaProvider ->
                    Result.success(
                        ImageGenerationTarget.Remote(
                            providerAssetId = override.providerId,
                            providerDisplayName = override.displayName,
                        ),
                    )
                is StudioModelOverride.Api ->
                    Result.failure(
                        IllegalArgumentException(
                            "API configuration overrides are not supported for image generation.",
                        ),
                    )
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
