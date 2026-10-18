package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.port.repository.TtsProviderRepositoryPort
import javax.inject.Inject

open class DeleteTtsProviderUseCase
    @Inject
    constructor(
        private val repository: TtsProviderRepositoryPort,
    ) {
        open suspend operator fun invoke(id: TtsProviderId) {
            repository.deleteTtsProvider(id)
        }
    }
