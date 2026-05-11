package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.settings.AppTheme
import com.browntowndev.pocketcrew.domain.model.settings.SystemPromptOption
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsAuthUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsPreferencesUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class SettingsGeneralDelegate @Inject constructor(
    private val preferencesUseCases: SettingsPreferencesUseCases,
    private val authUseCases: SettingsAuthUseCases,
    private val errorHandler: ViewModelErrorHandler,
) {
    fun onThemeChange(scope: CoroutineScope, tag: String, theme: AppTheme) {
        scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to update theme", "Failed to update theme")) {
            preferencesUseCases.updateTheme(theme)
        }
    }

    fun onHapticPressChange(scope: CoroutineScope, tag: String, enabled: Boolean) {
        scope.launch(
            errorHandler.coroutineExceptionHandler(
                tag,
                "Failed to update haptic press setting",
                "Failed to update setting"
            )
        ) {
            preferencesUseCases.updateHapticPress(enabled)
        }
    }

    fun onHapticResponseChange(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesUseCases.updateHapticResponse(enabled)
        }
    }

    fun onBackgroundInferenceChange(scope: CoroutineScope, tag: String, enabled: Boolean) {
        scope.launch(
            errorHandler.coroutineExceptionHandler(
                tag,
                "Failed to update background inference setting",
                "Failed to update setting"
            )
        ) {
            preferencesUseCases.updateBackgroundInferenceEnabled(enabled)
        }
    }

    fun onLoginWithGoogle(scope: CoroutineScope, tag: String) {
        scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to sign in with Google", "Failed to sign in")) {
            authUseCases.loginWithGoogle().getOrThrow()
        }
    }

    fun onLogout(scope: CoroutineScope, tag: String) {
        scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to sign out", "Failed to sign out")) {
            authUseCases.logout().getOrThrow()
        }
    }

    fun onShowCustomizationSheet(sheetVisibility: MutableStateFlow<SheetVisibilityState>, show: Boolean) {
        sheetVisibility.update { it.copy(customization = show) }
    }

    fun onShowVisionSettingsSheet(sheetVisibility: MutableStateFlow<SheetVisibilityState>, show: Boolean) {
        sheetVisibility.update { it.copy(visionSettings = show) }
    }

    fun onShowSearchSkillSheet(sheetVisibility: MutableStateFlow<SheetVisibilityState>, show: Boolean) {
        sheetVisibility.update { it.copy(searchSkill = show) }
    }

    fun onSearchSkillTabChange(sheetVisibility: MutableStateFlow<SheetVisibilityState>, index: Int) {
        sheetVisibility.update { it.copy(searchSkillTabIndex = index) }
    }

    fun onCustomizationEnabledChange(scope: CoroutineScope, tag: String, enabled: Boolean) {
        scope.launch(
            errorHandler.coroutineExceptionHandler(
                tag,
                "Failed to update customization setting",
                "Failed to update setting"
            )
        ) {
            preferencesUseCases.updateCustomizationEnabled(enabled)
        }
    }

    fun onPromptOptionChange(scope: CoroutineScope, tag: String, option: SystemPromptOption) {
        scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to update prompt option", "Failed to update setting")) {
            preferencesUseCases.updateSelectedPromptOption(option)
        }
    }

    fun onCustomPromptTextChange(scope: CoroutineScope, tag: String, text: String) {
        scope.launch(errorHandler.coroutineExceptionHandler(tag, "Failed to update custom prompt", "Failed to update setting")) {
            preferencesUseCases.updateCustomPromptText(text)
        }
    }

    fun onSaveCustomization(sheetVisibility: MutableStateFlow<SheetVisibilityState>) {
        sheetVisibility.update { it.copy(customization = false) }
    }

    fun onShowFeedbackSheet(sheetVisibility: MutableStateFlow<SheetVisibilityState>, show: Boolean) {
        sheetVisibility.update { it.copy(feedback = show) }
    }

    fun onFeedbackTextChange(feedbackText: MutableStateFlow<String>, text: String) {
        feedbackText.value = text
    }

    fun onSubmitFeedback(feedbackText: MutableStateFlow<String>, sheetVisibility: MutableStateFlow<SheetVisibilityState>) {
        feedbackText.value = ""
        sheetVisibility.update { it.copy(feedback = false) }
    }
}
