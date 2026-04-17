package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.model.chat.CompactionProviderType
import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import javax.inject.Inject

class UpdateCompactionProviderTypeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(type: CompactionProviderType) {
        settingsRepository.updateCompactionProviderType(type)
    }
}
