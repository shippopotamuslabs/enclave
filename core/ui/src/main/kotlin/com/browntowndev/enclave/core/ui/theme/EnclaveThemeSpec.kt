package com.browntowndev.enclave.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class EnclaveColors(
    val accent: Color,
    val onAccent: Color,
    val accentMuted: Color,
    val surfaceBase: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val borderStrong: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val error: Color,
    val onError: Color,
)

@Immutable
data class EnclaveShapes(
    val panel: Shape,
    val panelCorner: Dp,
    val button: Shape,
    val chip: Shape,
    val chipCorner: Dp,
    val borderWidth: Dp,
)

@Immutable
data class EnclaveElevations(
    val cardShadowOffset: Dp,
    val buttonShadowOffset: Dp,
    val useHardShadow: Boolean,
    val hardShadowColor: Color,
)

/**
 * Translucent blur-over-content treatment for bars floating over scrolling media.
 * Disabled specs render those bars solid/opaque instead.
 */
@Immutable
data class EnclaveGlass(
    val enabled: Boolean,
    val tint: Color,
    val blurRadius: Dp,
    val noiseFactor: Float,
)

@Immutable
data class EnclaveThemeSpec(
    val id: EnclaveThemeId,
    val dark: Boolean,
    val colors: EnclaveColors,
    val shapes: EnclaveShapes,
    val elevations: EnclaveElevations,
    val glass: EnclaveGlass,
)

internal fun tonalGlass(dark: Boolean): EnclaveGlass =
    EnclaveGlass(
        enabled = true,
        tint = if (dark) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.55f),
        blurRadius = 24.dp,
        noiseFactor = 0.15f,
    )

internal fun opaqueGlass(): EnclaveGlass =
    EnclaveGlass(
        enabled = false,
        tint = Color.Transparent,
        blurRadius = 0.dp,
        noiseFactor = 0f,
    )

internal fun tonalShapes(): EnclaveShapes =
    EnclaveShapes(
        panel = RoundedCornerShape(12.dp),
        panelCorner = 12.dp,
        button = RoundedCornerShape(12.dp),
        chip = RoundedCornerShape(8.dp),
        chipCorner = 8.dp,
        borderWidth = 1.dp,
    )

internal fun neoBrutalistShapes(): EnclaveShapes =
    EnclaveShapes(
        panel = RoundedCornerShape(EnclaveNeoTokens.cornerRadius),
        panelCorner = EnclaveNeoTokens.cornerRadius,
        button = RoundedCornerShape(EnclaveNeoTokens.cornerRadius),
        chip = RoundedCornerShape(EnclaveNeoTokens.cornerRadius),
        chipCorner = EnclaveNeoTokens.cornerRadius,
        borderWidth = EnclaveNeoTokens.borderWidth,
    )

object EnclaveThemeSpecFactory {
    fun resolve(family: EnclaveThemeId, dark: Boolean): EnclaveThemeSpec =
        when (family) {
            EnclaveThemeId.Tonal -> TonalSpec.resolve(dark)
            EnclaveThemeId.NeoBrutalist -> NeoBrutalistSpec.resolve(dark)
        }
}
