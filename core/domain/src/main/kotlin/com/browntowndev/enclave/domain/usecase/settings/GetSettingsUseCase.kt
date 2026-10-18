package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import javax.inject.Inject

open class GetSettingsUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        open operator fun invoke() = settingsRepository.settingsFlow
    }
