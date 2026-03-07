package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.model.SystemPromptOption
import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import javax.inject.Inject

class UpdateSelectedPromptOptionUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(option: SystemPromptOption) {
        settingsRepository.updateSelectedPromptOption(option)
    }
}
