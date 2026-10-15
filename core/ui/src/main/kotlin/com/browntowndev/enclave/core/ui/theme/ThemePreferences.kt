package com.browntowndev.enclave.core.ui.theme

/**
 * Composition-side theme selection (ENC-7). Mirrors the domain
 * ThemePreferences; the app shell maps domain -> this type so domain
 * models never enter composables.
 */
data class ThemePreferences(
    val family: EnclaveThemeId = EnclaveThemeId.NeoBrutalist,
    val colorMode: EnclaveColorMode = EnclaveColorMode.System,
)
