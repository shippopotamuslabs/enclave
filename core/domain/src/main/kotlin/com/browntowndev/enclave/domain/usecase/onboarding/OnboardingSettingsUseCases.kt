package com.browntowndev.enclave.domain.usecase.onboarding

import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import javax.inject.Inject

class ConfirmAgeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke() = settingsRepository.updateAgeConfirmed(true)
}

class SetAutomaticErrorTrackingUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(enabled: Boolean) =
        settingsRepository.updateAutomaticErrorTrackingEnabled(enabled)
}

class CompleteOnboardingUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke() = settingsRepository.updateOnboardingCompleted(true)
}
