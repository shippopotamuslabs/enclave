package com.browntowndev.enclave.domain.usecase.media

import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for fetching all configured media providers.
 */
open class GetMediaProvidersUseCase
    @Inject
    constructor(
        private val repository: MediaProviderRepositoryPort,
    ) {
        open operator fun invoke(): Flow<List<MediaProviderAsset>> = repository.getMediaProviders()
    }
