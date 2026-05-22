package com.browntowndev.enclave.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.settings.AppTheme
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * UI state for theme configuration.
 * darkTheme is nullable - null means use system default.
 */
data class ThemeUiState(
    val darkTheme: Boolean? = null,
    val dynamicColor: Boolean = false,
)

/**
 * ViewModel that observes settingsFlow and provides theme configuration
 * to the EnclaveApp composable.
 */
@HiltViewModel
class EnclaveAppViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        val errorHandler: ViewModelErrorHandler,
    ) : ViewModel() {
        companion object {
            private const val SUBSCRIBE_TIMEOUT_MS = 5000L
        }

        val themeUiState: StateFlow<ThemeUiState> =
            settingsRepository.settingsFlow
                .map { settings ->
                    mapAppThemeToUiState(settings.theme)
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
                    initialValue = ThemeUiState(),
                )

        private fun mapAppThemeToUiState(theme: AppTheme): ThemeUiState =
            when (theme) {
                AppTheme.SYSTEM ->
                    ThemeUiState(
                        darkTheme = null, // Use system default
                        dynamicColor = false,
                    )
                AppTheme.DYNAMIC ->
                    ThemeUiState(
                        darkTheme = null, // Use system default with dynamic colors
                        dynamicColor = true,
                    )
                AppTheme.DARK ->
                    ThemeUiState(
                        darkTheme = true,
                        dynamicColor = false,
                    )
                AppTheme.LIGHT ->
                    ThemeUiState(
                        darkTheme = false,
                        dynamicColor = false,
                    )
            }
    }
