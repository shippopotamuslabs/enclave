package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import javax.inject.Inject

class SaveXApiKeyUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        suspend operator fun invoke(apiKey: String) {
            settingsRepository.saveXApiKey(apiKey)
        }
    }
