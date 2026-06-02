package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import javax.inject.Inject

/**
 * Resolves the user-assigned media provider for a [ModelType] default slot.
 */
class ResolveAssignedMediaProviderUseCase
    @Inject
    constructor(
        private val defaultModelRepository: DefaultModelRepositoryPort,
        private val mediaProviderRepository: MediaProviderRepositoryPort,
    ) {
        suspend operator fun invoke(modelType: ModelType): Result<MediaProviderAsset> {
            val providerId =
                defaultModelRepository.getDefault(modelType)?.mediaProviderId
                    ?: return Result.failure(
                        IllegalStateException(modelType.missingDefaultMediaProviderMessage()),
                    )

            val provider =
                mediaProviderRepository.getMediaProvider(providerId)
                    ?: return Result.failure(IllegalStateException("Assigned media provider not found"))

            return Result.success(provider)
        }
    }

internal fun ModelType.missingDefaultMediaProviderMessage(): String =
    when (this) {
        ModelType.IMAGE_GENERATION -> "No image generation provider assigned"
        ModelType.IMAGE_EDIT -> "No image edit provider assigned"
        ModelType.VIDEO_GENERATION -> "No video generation provider assigned"
        ModelType.IMAGE_TO_VIDEO -> "No image-to-video provider assigned"
        ModelType.MUSIC_GENERATION -> "No default music model assigned"
        else -> "No media provider assigned for $name"
    }
