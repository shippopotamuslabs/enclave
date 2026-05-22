package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import javax.inject.Inject

class UpdateSearchProviderUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        suspend operator fun invoke(provider: SearchProvider) {
            settingsRepository.updateSearchProvider(provider)
        }
    }
