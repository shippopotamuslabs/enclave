package com.browntowndev.enclave.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shippopotamus gospel tokens — the canonical cross-app UI system declared by Sean
 * (2026-10-19) and embedded in `contracts/UI_DESIGN_SPEC.md` §8 (ENC-87).
 *
 * These definitions are the source of truth for ALL new UI work. They deliberately do
 * NOT re-point the shipped dual-family specs ([TonalSpec], [NeoBrutalistSpec],
 * [Color.kt], [Type.kt], [EnclaveNeoTokens]): those remain preserve-mode under the
 * §8.4 conflict register until Sean ratifies migration sub-issues.
 */

/** Palette Gospel colors (UI_DESIGN_SPEC §8.2). Never pure black or pure white. */
object ShippopotamusPalette {
    // Background layers — elevation by lightness steps, not shadows.
    val BgBase: Color = Color(0xFF0D0E11)
    val BgSubtle: Color = Color(0xFF111216)
    val Surface1: Color = Color(0xFF181A20)
    val Surface2: Color = Color(0xFF1F222A)
    val Surface3: Color = Color(0xFF272A33)

    // Borders / dividers.
    val BorderDefault: Color = Color(0xFF2A2E39)
    val BorderStrong: Color = Color(0xFF343A48)
    val BorderSubtle: Color = Color(0xFF20222E)

    // Text layers — warm off-whites; text-primary on bg-base is 15.8:1.
    val TextPrimary: Color = Color(0xFFEDEEF0)
    val TextHigh: Color = Color(0xFFC5C7DD)
    val TextSecondary: Color = Color(0xFFA1A5B0)
    val TextTertiary: Color = Color(0xFF858994)
    val TextFaint: Color = Color(0xFF6E7280)

    // Indigo accent family (primary action) — desaturated to prevent vibration on dark.
    val Accent: Color = Color(0xFF7A7CFF)
    val AccentHover: Color = Color(0xFF8D8FFF)
    val AccentActive: Color = Color(0xFF5F62B9)
    val AccentBorderSubtle: Color = Color(0xFF4B4EBF)
    val AccentDeep: Color = Color(0xFF3B3BAA)

    /** rgba(122,124,255,0.15) — tonal containers, selected segmented buttons. */
    val AccentMutedBg: Color = Color(0x267A7CFF)

    /** rgba(122,124,255,0.40). */
    val FocusRing: Color = Color(0x667A7CFF)

    // Semantic colors — all desaturated to <= 60% saturation.
    val Success: Color = Color(0xFF7DBE8C)
    val Warning: Color = Color(0xFFD9B07A)
    val Danger: Color = Color(0xFFE07A7A)
    val Info: Color = Color(0xFF7BB5D6)

    // Mobile extras.
    /** rgba(13,14,17,0.80) — bottom-sheet scrim. */
    val BgScrim: Color = Color(0xCC0D0E11)

    /** #1F222AF2 — translucent elevated overlay surface. */
    val SurfaceOverlay: Color = Color(0xF21F222A)
}

/** Compose Gospel spacing (UI_DESIGN_SPEC §8.3). The ONLY spacing values for new work. */
object ShippopotamusSpacing {
    val s4: Dp = 4.dp
    val s8: Dp = 8.dp
    val s12: Dp = 12.dp
    val s16: Dp = 16.dp
    val s24: Dp = 24.dp
    val s32: Dp = 32.dp
    val s48: Dp = 48.dp
    val s64: Dp = 64.dp

    /** Screen margin: mobile 16dp, tablet 24dp. Content max-width on tablet: 600dp. */
    val screenMarginMobile: Dp = s16
    val screenMarginTablet: Dp = s24
    val contentMaxWidthTablet: Dp = 600.dp
}

/**
 * Compose Gospel shape scale (UI_DESIGN_SPEC §8.3).
 * Nested rule: child radius = parent radius - padding.
 */
object ShippopotamusShapeScale {
    val extraSmall: Dp = 4.dp
    val small: Dp = 8.dp
    val medium: Dp = 12.dp
    val large: Dp = 16.dp
    val extraLarge: Dp = 28.dp
}

/** Material 3 [Shapes] built from [ShippopotamusShapeScale]; `full` is [RoundedCornerShape] 50%. */
val ShippopotamusShapes: Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(ShippopotamusShapeScale.extraSmall),
        small = RoundedCornerShape(ShippopotamusShapeScale.small),
        medium = RoundedCornerShape(ShippopotamusShapeScale.medium),
        large = RoundedCornerShape(ShippopotamusShapeScale.large),
        extraLarge = RoundedCornerShape(ShippopotamusShapeScale.extraLarge),
    )

/**
 * Compose Gospel type scale (UI_DESIGN_SPEC §8.3). Font family is intentionally unset:
 * the gospel specifies Shipopotamus Sans with Roboto Flex / Google Sans Flex variable as
 * fallback; until a gospel font asset ships, the platform default family renders these
 * styles. Never weight 300 on dark; bodySmall carries +0.15 letter spacing.
 */
val ShippopotamusTypography: Typography =
    Typography(
        displayLarge =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 57.sp,
                lineHeight = 64.sp,
                letterSpacing = (-0.25).sp,
            ),
        displayMedium =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 45.sp,
                lineHeight = 52.sp,
                letterSpacing = 0.sp,
            ),
        displaySmall =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = 0.sp,
            ),
        headlineLarge =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                letterSpacing = 0.sp,
            ),
        headlineMedium =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = 0.sp,
            ),
        headlineSmall =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.sp,
            ),
        titleLarge =
            TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        titleMedium =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp,
            ),
        titleSmall =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.sp,
            ),
        bodyLarge =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp,
            ),
        bodyMedium =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.sp,
            ),
        bodySmall =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.15.sp,
            ),
        labelLarge =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.sp,
            ),
        labelMedium =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.sp,
            ),
        labelSmall =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.sp,
            ),
    )

/** Compose Gospel component metrics + tonal elevation steps (UI_DESIGN_SPEC §8.3). */
object ShippopotamusComponentMetrics {
    /** 48dp touch targets are law — enforce via minimumInteractiveComponentSize(). */
    val minTouchTarget: Dp = 48.dp

    // Buttons (full pill; label = labelLarge).
    val buttonHeight: Dp = 40.dp
    val buttonHeightLarge: Dp = 56.dp
    val buttonContentPaddingTextOnly: Dp = 24.dp
    val buttonContentPaddingWithIcon: Dp = 16.dp
    val buttonIconSize: Dp = 18.dp
    val buttonIconGap: Dp = 8.dp
    val iconButtonSize: Dp = 40.dp
    val iconButtonIconSize: Dp = 24.dp

    // FAB.
    val fabSmall: Dp = 40.dp
    val fabMedium: Dp = 56.dp
    val fabLarge: Dp = 96.dp

    // Text fields.
    val textFieldHeight: Dp = 56.dp

    // Chips.
    val chipHeight: Dp = 32.dp

    // Switch.
    val switchTrackWidth: Dp = 52.dp
    val switchTrackHeight: Dp = 32.dp
    val switchThumb: Dp = 24.dp

    // Checkbox / radio (visible box; touch target expands to minTouchTarget).
    val checkboxBox: Dp = 20.dp

    // Slider.
    val sliderTrackHeight: Dp = 4.dp
    val sliderThumb: Dp = 20.dp

    // Cards.
    val cardContentPadding: Dp = 16.dp

    // Bottom sheet.
    val sheetDragHandleWidth: Dp = 32.dp
    val sheetDragHandleHeight: Dp = 4.dp

    // Navigation.
    val navBarHeight: Dp = 80.dp
    val navActiveIndicatorHeight: Dp = 32.dp
    val navRailWidth: Dp = 80.dp
    val topBarSmallHeight: Dp = 64.dp
    val topBarMediumHeight: Dp = 112.dp
    val topBarLargeHeight: Dp = 152.dp

    // List items.
    val listItemOneLineHeight: Dp = 56.dp
    val listItemTwoLineHeight: Dp = 72.dp
    val listItemThreeLineHeight: Dp = 88.dp

    // Tonal elevation — primary-tint overlay alpha per level (no drop shadows in dark).
    const val elevationTintLevel1: Float = 0.05f
    const val elevationTintLevel3: Float = 0.08f
    const val elevationTintLevel6: Float = 0.11f
}
