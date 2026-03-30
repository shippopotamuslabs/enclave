package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface GetDefaultModelsUseCase {
    operator fun invoke(): Flow<List<DefaultModelAssignment>>
}

class GetDefaultModelsUseCaseImpl @Inject constructor(
    private val defaultModelRepository: DefaultModelRepositoryPort,
) : GetDefaultModelsUseCase {
    override fun invoke(): Flow<List<DefaultModelAssignment>> {
        return defaultModelRepository.observeDefaults()
    }
}