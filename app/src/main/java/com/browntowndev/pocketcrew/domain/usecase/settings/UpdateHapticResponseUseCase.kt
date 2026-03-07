package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import javax.inject.Inject

class UpdateHapticResponseUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(enabled: Boolean) {
        settingsRepository.updateHapticResponse(enabled)
    }
}
