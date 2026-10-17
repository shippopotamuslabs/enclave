package com.browntowndev.enclave.core.ui.theme

import androidx.compose.ui.graphics.Color

/** Enclave neo-brutalist family: peach-on-charcoal dark, slate light, hard shadows. */
object NeoBrutalistSpec {
    fun resolve(dark: Boolean): EnclaveThemeSpec = if (dark) darkSpec() else lightSpec()

    private fun darkSpec(): EnclaveThemeSpec =
        EnclaveThemeSpec(
            id = EnclaveThemeId.NeoBrutalist,
            dark = true,
            colors = EnclaveColors(
                accent = PeachAccent,
                onAccent = Color.Black,
                accentMuted = PeachVariant,
                surfaceBase = DarkBackground,
                surfaceRaised = DarkSurface,
                surfaceSunken = DarkSurfaceVariant,
                borderStrong = DarkNeoBorder,
                borderSubtle = DarkOutline,
                textPrimary = DarkOnBackground,
                textMuted = DarkOnSurfaceVariant,
                error = ErrorRed,
                onError = OnErrorWhite,
            ),
            shapes = neoBrutalistShapes(),
            elevations = EnclaveElevations(
                cardShadowOffset = EnclaveNeoTokens.buttonShadowOffsetDark,
                buttonShadowOffset = EnclaveNeoTokens.buttonShadowOffsetDark,
                useHardShadow = true,
                hardShadowColor = DarkNeoShadow,
            ),
            glass = opaqueGlass(),
        )

    private fun lightSpec(): EnclaveThemeSpec =
        EnclaveThemeSpec(
            id = EnclaveThemeId.NeoBrutalist,
            dark = false,
            colors = EnclaveColors(
                accent = PurpleLightPrimary,
                onAccent = Color.White,
                accentMuted = GoldVariant,
                surfaceBase = LightBackground,
                surfaceRaised = LightSurface,
                surfaceSunken = LightSurface,
                borderStrong = LightNeoBorder,
                borderSubtle = LightOutline,
                textPrimary = LightOnBackground,
                textMuted = LightOnBackground,
                error = ErrorRed,
                onError = OnErrorWhite,
            ),
            shapes = neoBrutalistShapes(),
            elevations = EnclaveElevations(
                cardShadowOffset = EnclaveNeoTokens.buttonShadowOffsetLight,
                buttonShadowOffset = EnclaveNeoTokens.buttonShadowOffsetLight,
                useHardShadow = true,
                hardShadowColor = LightNeoShadow,
            ),
            glass = opaqueGlass(),
        )
}
