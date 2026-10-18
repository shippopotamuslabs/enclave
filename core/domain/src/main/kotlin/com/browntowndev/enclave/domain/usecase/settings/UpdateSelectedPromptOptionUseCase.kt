package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.settings.SystemPromptOption
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import javax.inject.Inject

open class UpdateSelectedPromptOptionUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        open suspend operator fun invoke(option: SystemPromptOption) {
            settingsRepository.updateSelectedPromptOption(option)
        }
    }
