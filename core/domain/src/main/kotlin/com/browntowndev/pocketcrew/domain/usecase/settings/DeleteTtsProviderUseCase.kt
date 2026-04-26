package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import com.browntowndev.pocketcrew.domain.port.repository.TtsProviderRepositoryPort
import javax.inject.Inject

class DeleteTtsProviderUseCase @Inject constructor(
    private val repository: TtsProviderRepositoryPort,
) {
    suspend operator fun invoke(id: TtsProviderId) {
        repository.deleteTtsProvider(id)
    }
}
