package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.config.TtsProviderAsset
import com.browntowndev.enclave.domain.port.repository.TtsProviderRepositoryPort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

open class GetTtsProvidersUseCase
    @Inject
    constructor(
        private val repository: TtsProviderRepositoryPort,
    ) {
        open operator fun invoke(): Flow<List<TtsProviderAsset>> = repository.getTtsProviders()
    }
