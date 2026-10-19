package com.browntowndev.enclave.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the Shippopotamus gospel token definitions (contracts/UI_DESIGN_SPEC.md §8,
 * ENC-87) against drift and typos: these values are canonical across Shippopotamus apps.
 */
class ShippopotamusTokensTest {
    @Test
    fun palette_backgroundsBordersAndTextMatchGospel() {
        assertEquals(Color(0xFF0D0E11), ShippopotamusPalette.BgBase)
        assertEquals(Color(0xFF111216), ShippopotamusPalette.BgSubtle)
        assertEquals(Color(0xFF181A20), ShippopotamusPalette.Surface1)
        assertEquals(Color(0xFF1F222A), ShippopotamusPalette.Surface2)
        assertEquals(Color(0xFF272A33), ShippopotamusPalette.Surface3)
        assertEquals(Color(0xFF2A2E39), ShippopotamusPalette.BorderDefault)
        assertEquals(Color(0xFF343A48), ShippopotamusPalette.BorderStrong)
        assertEquals(Color(0xFF20222E), ShippopotamusPalette.BorderSubtle)
        assertEquals(Color(0xFFEDEEF0), ShippopotamusPalette.TextPrimary)
        assertEquals(Color(0xFFC5C7DD), ShippopotamusPalette.TextHigh)
        assertEquals(Color(0xFFA1A5B0), ShippopotamusPalette.TextSecondary)
        assertEquals(Color(0xFF858994), ShippopotamusPalette.TextTertiary)
        assertEquals(Color(0xFF6E7280), ShippopotamusPalette.TextFaint)
    }

    @Test
    fun palette_accentFamilyAndSemanticsMatchGospel() {
        assertEquals(Color(0xFF7A7CFF), ShippopotamusPalette.Accent)
        assertEquals(Color(0xFF8D8FFF), ShippopotamusPalette.AccentHover)
        assertEquals(Color(0xFF5F62B9), ShippopotamusPalette.AccentActive)
        assertEquals(Color(0xFF4B4EBF), ShippopotamusPalette.AccentBorderSubtle)
        assertEquals(Color(0xFF3B3BAA), ShippopotamusPalette.AccentDeep)
        assertEquals(Color(0xFF7DBE8C), ShippopotamusPalette.Success)
        assertEquals(Color(0xFFD9B07A), ShippopotamusPalette.Warning)
        assertEquals(Color(0xFFE07A7A), ShippopotamusPalette.Danger)
        assertEquals(Color(0xFF7BB5D6), ShippopotamusPalette.Info)
    }

    @Test
    fun palette_alphaTokensCarryGospelAlphas() {
        assertEquals(0.15f, ShippopotamusPalette.AccentMutedBg.alpha, 0.01f)
        assertEquals(0.40f, ShippopotamusPalette.FocusRing.alpha, 0.01f)
        assertEquals(0.80f, ShippopotamusPalette.BgScrim.alpha, 0.01f)
        assertEquals(ShippopotamusPalette.Accent, ShippopotamusPalette.AccentMutedBg.copy(alpha = 1f))
        assertEquals(ShippopotamusPalette.BgBase, ShippopotamusPalette.BgScrim.copy(alpha = 1f))
        assertEquals(ShippopotamusPalette.Surface2, ShippopotamusPalette.SurfaceOverlay.copy(alpha = 1f))
    }

    @Test
    fun palette_neverPureBlackOrPureWhite() {
        val opaqueTokens =
            listOf(
                ShippopotamusPalette.BgBase,
                ShippopotamusPalette.BgSubtle,
                ShippopotamusPalette.Surface1,
                ShippopotamusPalette.Surface2,
                ShippopotamusPalette.Surface3,
                ShippopotamusPalette.TextPrimary,
                ShippopotamusPalette.Accent,
            )
        opaqueTokens.forEach { color ->
            assertTrue(color != Color.Black && color != Color.White, "gospel bans pure black/white: $color")
        }
    }

    @Test
    fun spacing_onlyGospelValues() {
        assertEquals(4.dp, ShippopotamusSpacing.s4)
        assertEquals(8.dp, ShippopotamusSpacing.s8)
        assertEquals(12.dp, ShippopotamusSpacing.s12)
        assertEquals(16.dp, ShippopotamusSpacing.s16)
        assertEquals(24.dp, ShippopotamusSpacing.s24)
        assertEquals(32.dp, ShippopotamusSpacing.s32)
        assertEquals(48.dp, ShippopotamusSpacing.s48)
        assertEquals(64.dp, ShippopotamusSpacing.s64)
        assertEquals(600.dp, ShippopotamusSpacing.contentMaxWidthTablet)
    }

    @Test
    fun shapes_scaleMatchesGospel() {
        assertEquals(4.dp, ShippopotamusShapeScale.extraSmall)
        assertEquals(8.dp, ShippopotamusShapeScale.small)
        assertEquals(12.dp, ShippopotamusShapeScale.medium)
        assertEquals(16.dp, ShippopotamusShapeScale.large)
        assertEquals(28.dp, ShippopotamusShapeScale.extraLarge)
        assertEquals(RoundedCornerShape(12.dp), ShippopotamusShapes.medium)
        assertEquals(RoundedCornerShape(28.dp), ShippopotamusShapes.extraLarge)
    }

    @Test
    fun typography_keyStylesMatchGospel() {
        val titleLarge = ShippopotamusTypography.titleLarge
        assertEquals(FontWeight.SemiBold, titleLarge.fontWeight)
        assertEquals(22.sp, titleLarge.fontSize)
        assertEquals(28.sp, titleLarge.lineHeight)

        val labelLarge = ShippopotamusTypography.labelLarge
        assertEquals(FontWeight.Medium, labelLarge.fontWeight)
        assertEquals(14.sp, labelLarge.fontSize)
        assertEquals(20.sp, labelLarge.lineHeight)

        val bodyLarge = ShippopotamusTypography.bodyLarge
        assertEquals(FontWeight.Normal, bodyLarge.fontWeight)
        assertEquals(16.sp, bodyLarge.fontSize)
        assertEquals(24.sp, bodyLarge.lineHeight)

        val bodySmall = ShippopotamusTypography.bodySmall
        assertEquals(12.sp, bodySmall.fontSize)
        assertEquals(0.15.sp, bodySmall.letterSpacing)

        val displayLarge = ShippopotamusTypography.displayLarge
        assertEquals(57.sp, displayLarge.fontSize)
        assertEquals(64.sp, displayLarge.lineHeight)
        assertEquals((-0.25).sp, displayLarge.letterSpacing)
    }

    @Test
    fun componentMetrics_matchGospelDefaults() {
        assertEquals(48.dp, ShippopotamusComponentMetrics.minTouchTarget)
        assertEquals(40.dp, ShippopotamusComponentMetrics.buttonHeight)
        assertEquals(56.dp, ShippopotamusComponentMetrics.buttonHeightLarge)
        assertEquals(56.dp, ShippopotamusComponentMetrics.textFieldHeight)
        assertEquals(32.dp, ShippopotamusComponentMetrics.chipHeight)
        assertEquals(52.dp, ShippopotamusComponentMetrics.switchTrackWidth)
        assertEquals(32.dp, ShippopotamusComponentMetrics.switchTrackHeight)
        assertEquals(24.dp, ShippopotamusComponentMetrics.switchThumb)
        assertEquals(80.dp, ShippopotamusComponentMetrics.navBarHeight)
        assertEquals(56.dp, ShippopotamusComponentMetrics.listItemOneLineHeight)
        assertEquals(72.dp, ShippopotamusComponentMetrics.listItemTwoLineHeight)
        assertEquals(88.dp, ShippopotamusComponentMetrics.listItemThreeLineHeight)
        assertEquals(0.05f, ShippopotamusComponentMetrics.elevationTintLevel1)
        assertEquals(0.08f, ShippopotamusComponentMetrics.elevationTintLevel3)
        assertEquals(0.11f, ShippopotamusComponentMetrics.elevationTintLevel6)
    }
}
