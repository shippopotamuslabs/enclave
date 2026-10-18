package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import javax.inject.Inject

open class UpdateAllowMemoriesUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        open suspend operator fun invoke(allowed: Boolean) {
            settingsRepository.updateAllowMemories(allowed)
        }
    }
