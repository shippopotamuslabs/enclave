package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfig
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Returns an observable stream of all configured API models.
 */
class GetApiModelsUseCase @Inject constructor(
    private val apiModelRepository: ApiModelRepositoryPort,
) {
    operator fun invoke(): Flow<List<ApiModelConfig>> {
        return apiModelRepository.observeAll()
    }
}
