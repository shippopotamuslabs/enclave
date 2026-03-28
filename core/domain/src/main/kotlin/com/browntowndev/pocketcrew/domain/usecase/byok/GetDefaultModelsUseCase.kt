package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Returns an observable stream of all default model assignments.
 */
class GetDefaultModelsUseCase @Inject constructor(
    private val defaultModelRepository: DefaultModelRepositoryPort,
) {
    operator fun invoke(): Flow<List<DefaultModelAssignment>> {
        return defaultModelRepository.observeDefaults()
    }
}
