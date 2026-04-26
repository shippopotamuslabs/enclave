package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import javax.inject.Inject

class ClearDefaultModelUseCase @Inject constructor(
    private val repository: DefaultModelRepositoryPort
) {
    suspend operator fun invoke(modelType: ModelType) {
        repository.clearDefault(modelType)
    }
}
