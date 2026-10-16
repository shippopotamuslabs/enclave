package com.browntowndev.enclave.app.theme

import com.browntowndev.enclave.core.ui.theme.EnclaveColorMode
import com.browntowndev.enclave.core.ui.theme.EnclaveThemeId
import com.browntowndev.enclave.core.ui.theme.ThemePreferences as UiThemePreferences
import com.browntowndev.enclave.domain.theme.ColorMode
import com.browntowndev.enclave.domain.theme.ThemeFamily
import com.browntowndev.enclave.domain.theme.ThemePreferences as DomainThemePreferences

/** Maps persisted domain preferences to the composition mirror (ENC-7). */
fun DomainThemePreferences.toUi(): UiThemePreferences =
    UiThemePreferences(family = family.toEnclaveThemeId(), colorMode = colorMode.toEnclaveColorMode())

fun ThemeFamily.toEnclaveThemeId(): EnclaveThemeId =
    when (this) {
        ThemeFamily.Tonal -> EnclaveThemeId.Tonal
        ThemeFamily.NeoBrutalist -> EnclaveThemeId.NeoBrutalist
    }

fun ColorMode.toEnclaveColorMode(): EnclaveColorMode =
    when (this) {
        ColorMode.System -> EnclaveColorMode.System
        ColorMode.Light -> EnclaveColorMode.Light
        ColorMode.Dark -> EnclaveColorMode.Dark
    }
