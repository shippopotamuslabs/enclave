package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.settings.AppTheme
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import javax.inject.Inject

open class UpdateThemeUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        open suspend operator fun invoke(theme: AppTheme) {
            settingsRepository.updateTheme(theme)
        }
    }
