package com.browntowndev.enclave.domain.theme

/**
 * Persisted user theme selection (ENC-7). Domain stays free of Compose types;
 * the app shell maps this to the `:core:ui` mirror before the root theme.
 * Defaults preserve the historical Enclave rendering (NeoBrutalist + System).
 */
data class ThemePreferences(
    val family: ThemeFamily = ThemeFamily.NeoBrutalist,
    val colorMode: ColorMode = ColorMode.System,
)

enum class ThemeFamily {
    Tonal,
    NeoBrutalist,
}

enum class ColorMode {
    System,
    Light,
    Dark,
}
