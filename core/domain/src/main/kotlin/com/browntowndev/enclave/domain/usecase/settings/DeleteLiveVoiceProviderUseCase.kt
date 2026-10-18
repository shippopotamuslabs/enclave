package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.port.repository.LiveVoiceProviderRepositoryPort
import javax.inject.Inject

open class DeleteLiveVoiceProviderUseCase
    @Inject
    constructor(
        private val repository: LiveVoiceProviderRepositoryPort,
    ) {
        open suspend operator fun invoke(id: LiveVoiceProviderId) {
            repository.deleteLiveVoiceProvider(id)
        }
    }
