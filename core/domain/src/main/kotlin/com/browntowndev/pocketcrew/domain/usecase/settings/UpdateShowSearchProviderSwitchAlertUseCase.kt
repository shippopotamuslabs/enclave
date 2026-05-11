package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import javax.inject.Inject

class UpdateShowSearchProviderSwitchAlertUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(show: Boolean) {
        settingsRepository.updateShowSearchProviderSwitchAlert(show)
    }
}
