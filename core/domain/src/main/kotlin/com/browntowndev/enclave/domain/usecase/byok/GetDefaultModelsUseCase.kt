package com.browntowndev.enclave.domain.usecase.byok

import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface GetDefaultModelsUseCase {
    open operator fun invoke(): Flow<List<DefaultModelAssignment>>
}

class GetDefaultModelsUseCaseImpl
    @Inject
    constructor(
        private val defaultModelRepository: DefaultModelRepositoryPort,
    ) : GetDefaultModelsUseCase {
        override fun invoke(): Flow<List<DefaultModelAssignment>> = defaultModelRepository.observeDefaults()
    }
