package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import javax.inject.Inject

/**
 * Use case for saving a media provider configuration.
 */
class SaveMediaProviderUseCase
    @Inject
    constructor(
        private val mediaProviderRepository: MediaProviderRepositoryPort,
    ) {
        suspend operator fun invoke(
            asset: MediaProviderAsset,
            apiKey: String?,
        ): MediaProviderId = mediaProviderRepository.saveMediaProvider(asset, apiKey)
    }
