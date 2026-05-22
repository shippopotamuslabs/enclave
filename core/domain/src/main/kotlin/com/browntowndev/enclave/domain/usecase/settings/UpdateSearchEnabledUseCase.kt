package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import javax.inject.Inject

class UpdateSearchEnabledUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        suspend operator fun invoke(enabled: Boolean) {
            settingsRepository.updateSearchEnabled(enabled)
        }
    }
