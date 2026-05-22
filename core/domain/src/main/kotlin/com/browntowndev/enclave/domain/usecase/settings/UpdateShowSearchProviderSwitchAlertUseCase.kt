package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import javax.inject.Inject

class UpdateShowSearchProviderSwitchAlertUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        suspend operator fun invoke(show: Boolean) {
            settingsRepository.updateShowSearchProviderSwitchAlert(show)
        }
    }
