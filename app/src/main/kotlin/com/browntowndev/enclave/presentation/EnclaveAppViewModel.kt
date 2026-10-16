package com.browntowndev.enclave.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.enclave.app.theme.toUi
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.core.ui.theme.EnclaveThemeId
import com.browntowndev.enclave.domain.theme.ObserveThemePreferencesUseCase
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
    val themeFamily: EnclaveThemeId = EnclaveThemeId.NeoBrutalist,
)

/**
 * ViewModel that observes persisted theme preferences via a use case (ENC-7)
 * and provides theme configuration to the EnclaveApp composable.
 */
@HiltViewModel
class EnclaveAppViewModel
    @Inject
    constructor(
        observeThemePreferences: ObserveThemePreferencesUseCase,
        val errorHandler: ViewModelErrorHandler,
    ) : ViewModel() {
        companion object {
            private const val SUBSCRIBE_TIMEOUT_MS = 5000L
        }

        val themeUiState: StateFlow<ThemeUiState> =
            observeThemePreferences()
                .map { prefs ->
                    val ui = prefs.toUi()
                    ThemeUiState(
                        darkTheme =
                            when (ui.colorMode) {
                                com.browntowndev.enclave.core.ui.theme.EnclaveColorMode.System -> null
                                com.browntowndev.enclave.core.ui.theme.EnclaveColorMode.Light -> false
                                com.browntowndev.enclave.core.ui.theme.EnclaveColorMode.Dark -> true
                            },
                        dynamicColor = false,
                        themeFamily = ui.family,
                    )
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
                    initialValue = ThemeUiState(),
                )
    }
