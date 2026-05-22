package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import javax.inject.Inject

class UpdateAllowMemoriesUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        suspend operator fun invoke(allowed: Boolean) {
            settingsRepository.updateAllowMemories(allowed)
        }
    }
