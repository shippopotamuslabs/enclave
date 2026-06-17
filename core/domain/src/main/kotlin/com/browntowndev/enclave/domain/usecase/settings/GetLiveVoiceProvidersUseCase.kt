package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.port.repository.LiveVoiceProviderRepositoryPort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetLiveVoiceProvidersUseCase
    @Inject
    constructor(
        private val repository: LiveVoiceProviderRepositoryPort,
    ) {
        operator fun invoke(): Flow<List<LiveVoiceProviderAsset>> = repository.getLiveVoiceProviders()
    }
