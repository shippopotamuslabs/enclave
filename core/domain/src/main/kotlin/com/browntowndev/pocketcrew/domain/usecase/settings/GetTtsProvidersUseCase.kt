package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.model.config.TtsProviderAsset
import com.browntowndev.pocketcrew.domain.port.repository.TtsProviderRepositoryPort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTtsProvidersUseCase @Inject constructor(
    private val repository: TtsProviderRepositoryPort,
) {
    operator fun invoke(): Flow<List<TtsProviderAsset>> = repository.getTtsProviders()
}
