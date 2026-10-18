package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import javax.inject.Inject

open class UpdateCustomizationEnabledUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        open suspend operator fun invoke(enabled: Boolean) {
            settingsRepository.updateCustomizationEnabled(enabled)
        }
    }
