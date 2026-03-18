package com.browntowndev.pocketcrew.domain.usecase.settings

interface SettingsUseCases {
    val updateTheme: UpdateThemeUseCase
    val updateHapticPress: UpdateHapticPressUseCase
    val updateHapticResponse: UpdateHapticResponseUseCase
    val updateCustomizationEnabled: UpdateCustomizationEnabledUseCase
    val updateSelectedPromptOption: UpdateSelectedPromptOptionUseCase
    val updateCustomPromptText: UpdateCustomPromptTextUseCase
    val updateAllowMemories: UpdateAllowMemoriesUseCase
    val getSettings: GetSettingsUseCase
}
