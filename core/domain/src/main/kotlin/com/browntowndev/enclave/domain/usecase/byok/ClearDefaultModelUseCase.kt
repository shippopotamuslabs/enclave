package com.browntowndev.enclave.domain.usecase.byok

import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import javax.inject.Inject

class ClearDefaultModelUseCase
    @Inject
    constructor(
        private val repository: DefaultModelRepositoryPort,
    ) {
        suspend operator fun invoke(modelType: ModelType) {
            repository.clearDefault(modelType)
        }
    }
