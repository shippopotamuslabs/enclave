package com.browntowndev.pocketcrew.domain.usecase.media

import com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import com.browntowndev.pocketcrew.domain.port.repository.MediaProviderRepositoryPort
import javax.inject.Inject

/**
 * Use case for saving a media provider configuration.
 */
class SaveMediaProviderUseCase @Inject constructor(
    private val mediaProviderRepository: MediaProviderRepositoryPort,
) {
    suspend operator fun invoke(
        asset: MediaProviderAsset,
        apiKey: String?,
    ): MediaProviderId {
        return mediaProviderRepository.saveMediaProvider(asset, apiKey)
    }
}
