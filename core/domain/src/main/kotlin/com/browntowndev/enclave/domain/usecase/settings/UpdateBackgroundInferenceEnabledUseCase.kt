package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import javax.inject.Inject

open class UpdateBackgroundInferenceEnabledUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        open suspend operator fun invoke(enabled: Boolean) {
            settingsRepository.updateBackgroundInferenceEnabled(enabled)
        }
    }
