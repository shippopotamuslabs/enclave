package com.browntowndev.enclave.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Material 3 tonal family: rounded geometry, thin borders, no hard shadow. */
object TonalSpec {
    fun resolve(dark: Boolean): EnclaveThemeSpec = if (dark) darkSpec() else lightSpec()

    private fun darkSpec(): EnclaveThemeSpec =
        EnclaveThemeSpec(
            id = EnclaveThemeId.Tonal,
            dark = true,
            colors = EnclaveColors(
                accent = PeachAccent,
                onAccent = Color.Black,
                accentMuted = PeachVariant,
                surfaceBase = DarkBackground,
                surfaceRaised = DarkSurface,
                surfaceSunken = DarkSurfaceVariant,
                borderStrong = DarkOutline,
                borderSubtle = DarkOutline,
                textPrimary = DarkOnBackground,
                textMuted = DarkOnSurfaceVariant,
                error = ErrorRed,
                onError = OnErrorWhite,
            ),
            shapes = tonalShapes(),
            elevations = EnclaveElevations(
                cardShadowOffset = 0.dp,
                buttonShadowOffset = 0.dp,
                useHardShadow = false,
                hardShadowColor = Color.Transparent,
            ),
            glass = tonalGlass(dark = true),
        )

    private fun lightSpec(): EnclaveThemeSpec =
        EnclaveThemeSpec(
            id = EnclaveThemeId.Tonal,
            dark = false,
            colors = EnclaveColors(
                accent = PurpleLightPrimary,
                onAccent = Color.White,
                accentMuted = GoldVariant,
                surfaceBase = LightBackground,
                surfaceRaised = LightSurface,
                surfaceSunken = LightSurface,
                borderStrong = LightOutline,
                borderSubtle = LightOutline,
                textPrimary = LightOnBackground,
                textMuted = LightOnBackground,
                error = ErrorRed,
                onError = OnErrorWhite,
            ),
            shapes = tonalShapes(),
            elevations = EnclaveElevations(
                cardShadowOffset = 0.dp,
                buttonShadowOffset = 0.dp,
                useHardShadow = false,
                hardShadowColor = Color.Transparent,
            ),
            glass = tonalGlass(dark = false),
        )
}
