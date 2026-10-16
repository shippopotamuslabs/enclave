package com.browntowndev.enclave.core.ui.theme

/** Theme family selectable by the user. See contracts/UI_DESIGN_SPEC.md §1.4 (ENC-1). */
enum class EnclaveThemeId {
    Tonal,
    NeoBrutalist,
}

/** Appearance / dark-mode policy, resolved against the system flag by the root theme. */
enum class EnclaveColorMode {
    System,
    Light,
    Dark,
}

fun EnclaveColorMode.resolveDark(isSystemDark: Boolean): Boolean =
    when (this) {
        EnclaveColorMode.System -> isSystemDark
        EnclaveColorMode.Light -> false
        EnclaveColorMode.Dark -> true
    }
