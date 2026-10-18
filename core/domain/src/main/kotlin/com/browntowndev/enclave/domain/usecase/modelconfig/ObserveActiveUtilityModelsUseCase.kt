package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.domain.model.config.UtilityModelMetadata
import com.browntowndev.enclave.domain.port.repository.UtilityModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

open class ObserveActiveUtilityModelsUseCase
    @Inject
    constructor(
        private val utilityModelRepository: UtilityModelRepositoryPort,
    ) {
        open operator fun invoke(): Flow<List<UtilityModelMetadata>> = utilityModelRepository.observeAllActive()
    }
