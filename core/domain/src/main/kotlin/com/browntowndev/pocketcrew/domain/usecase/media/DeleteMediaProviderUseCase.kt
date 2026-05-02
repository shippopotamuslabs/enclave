package com.browntowndev.pocketcrew.domain.usecase.media

import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import com.browntowndev.pocketcrew.domain.port.repository.MediaProviderRepositoryPort
import javax.inject.Inject

/**
 * Use case for deleting a media provider configuration.
 */
class DeleteMediaProviderUseCase @Inject constructor(
    private val mediaProviderRepository: MediaProviderRepositoryPort,
) {
    suspend operator fun invoke(id: MediaProviderId) {
        mediaProviderRepository.deleteMediaProvider(id)
    }
}
