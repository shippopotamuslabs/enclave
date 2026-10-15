package com.browntowndev.enclave.domain.theme

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveThemePreferencesUseCase @Inject constructor(
    private val repository: ThemePreferencesRepository,
) {
    operator fun invoke(): Flow<ThemePreferences> = repository.observe()
}

class SetThemeFamilyUseCase @Inject constructor(
    private val repository: ThemePreferencesRepository,
) {
    suspend operator fun invoke(family: ThemeFamily) = repository.setFamily(family)
}

class SetThemeColorModeUseCase @Inject constructor(
    private val repository: ThemePreferencesRepository,
) {
    suspend operator fun invoke(colorMode: ColorMode) = repository.setColorMode(colorMode)
}
