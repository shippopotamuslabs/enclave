package com.browntowndev.pocketcrew.domain.usecase.settings

import javax.inject.Inject

class SettingsUseCasesImpl @Inject constructor(
    override val updateTheme: UpdateThemeUseCase,
    override val updateHapticPress: UpdateHapticPressUseCase,
    override val updateHapticResponse: UpdateHapticResponseUseCase,
    override val updateCustomizationEnabled: UpdateCustomizationEnabledUseCase,
    override val updateSelectedPromptOption: UpdateSelectedPromptOptionUseCase,
    override val updateCustomPromptText: UpdateCustomPromptTextUseCase,
    override val updateAllowMemories: UpdateAllowMemoriesUseCase,
    override val getSettings: GetSettingsUseCase
) : SettingsUseCases
