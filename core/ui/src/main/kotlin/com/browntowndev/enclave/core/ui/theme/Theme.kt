package com.browntowndev.enclave.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.hrm.markdown.renderer.AdmonitionStyle
import com.hrm.markdown.renderer.MarkdownTheme

val LocalEnclaveTheme =
    staticCompositionLocalOf {
        NeoBrutalistSpec.resolve(dark = true)
    }

object EnclaveThemeAccessor {
    val colors: EnclaveColors
        @Composable get() = LocalEnclaveTheme.current.colors

    val shapes: EnclaveShapes
        @Composable get() = LocalEnclaveTheme.current.shapes

    val elevations: EnclaveElevations
        @Composable get() = LocalEnclaveTheme.current.elevations

    val spec: EnclaveThemeSpec
        @Composable get() = LocalEnclaveTheme.current
}

/**
 * Root Enclave theme. [themeFamily] selects the spec (ENC-1, UI_DESIGN_SPEC §1.4);
 * the default preserves the historical neo-brutalist rendering until ENC-7/ENC-9
 * wire persisted preferences. Dynamic color overrides the spec palette for
 * Material slots but the spec still drives shapes/elevations.
 */
@Composable
fun EnclaveTheme(
    darkTheme: Boolean? = null,
    dynamicColor: Boolean = false,
    themeFamily: EnclaveThemeId = EnclaveThemeId.NeoBrutalist,
    content: @Composable () -> Unit,
) {
    val isDarkTheme = darkTheme ?: isSystemInDarkTheme()
    val spec = EnclaveThemeSpecFactory.resolve(themeFamily, isDarkTheme)

    val specScheme =
        if (spec.dark) {
            darkColorScheme(
                primary = spec.colors.accent,
                onPrimary = spec.colors.onAccent,
                primaryContainer = spec.colors.surfaceSunken,
                onPrimaryContainer = spec.colors.accent,
                secondary = spec.colors.textMuted,
                onSecondary = spec.colors.textPrimary,
                tertiary = spec.colors.accentMuted,
                onTertiary = spec.colors.textPrimary,
                background = spec.colors.surfaceBase,
                onBackground = spec.colors.textPrimary,
                surface = spec.colors.surfaceRaised,
                onSurface = spec.colors.textPrimary,
                surfaceVariant = spec.colors.surfaceSunken,
                onSurfaceVariant = spec.colors.textMuted,
                outline = spec.colors.borderSubtle,
                outlineVariant = spec.colors.borderStrong,
                error = spec.colors.error,
                onError = spec.colors.onError,
            )
        } else {
            lightColorScheme(
                primary = spec.colors.accent,
                onPrimary = spec.colors.onAccent,
                primaryContainer = spec.colors.surfaceSunken,
                onPrimaryContainer = spec.colors.accent,
                secondary = spec.colors.textMuted,
                onSecondary = spec.colors.textPrimary,
                tertiary = spec.colors.accentMuted,
                onTertiary = spec.colors.textPrimary,
                background = spec.colors.surfaceBase,
                onBackground = spec.colors.textPrimary,
                surface = spec.colors.surfaceRaised,
                onSurface = spec.colors.textPrimary,
                surfaceVariant = spec.colors.surfaceSunken,
                onSurfaceVariant = spec.colors.textMuted,
                outline = spec.colors.borderSubtle,
                outlineVariant = spec.colors.borderStrong,
                error = spec.colors.error,
                onError = spec.colors.onError,
            )
        }

    val colorScheme =
        when {
            dynamicColor -> {
                val context = LocalContext.current
                if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            else -> specScheme
        }

    CompositionLocalProvider(LocalEnclaveTheme provides spec) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

private fun darkHeadingStyles() =
    listOf(
        LlmBodyTextStyle.copy(
            color = DarkOnBackground,
            fontWeight = FontWeight.ExtraBold,
            fontSize = Typography.headlineLarge.fontSize,
            lineHeight = Typography.headlineLarge.lineHeight,
            textDecoration = TextDecoration.None,
        ),
        LlmBodyTextStyle.copy(
            color = DarkOnBackground,
            fontWeight = FontWeight.ExtraBold,
            fontSize = Typography.headlineMedium.fontSize,
            lineHeight = Typography.headlineMedium.lineHeight,
            textDecoration = TextDecoration.None,
        ),
        LlmBodyTextStyle.copy(
            color = DarkOnBackground,
            fontWeight = FontWeight.Bold,
            fontSize = Typography.titleLarge.fontSize,
            lineHeight = Typography.titleLarge.lineHeight,
            textDecoration = TextDecoration.None,
        ),
        LlmBodyTextStyle.copy(
            color = DarkOnBackground,
            fontWeight = FontWeight.Bold,
            fontSize = Typography.titleMedium.fontSize,
            lineHeight = Typography.titleMedium.lineHeight,
            textDecoration = TextDecoration.None,
        ),
        LlmBodyTextStyle.copy(
            color = DarkOnSurfaceVariant,
            fontWeight = FontWeight.Bold,
            fontSize = Typography.titleMedium.fontSize,
            lineHeight = Typography.titleMedium.lineHeight,
            textDecoration = TextDecoration.None,
        ),
        LlmBodyTextStyle.copy(
            color = DarkOnSurfaceVariant,
            fontWeight = FontWeight.Bold,
            fontSize = Typography.titleMedium.fontSize,
            lineHeight = Typography.titleMedium.lineHeight,
            textDecoration = TextDecoration.None,
        ),
    )

private fun darkAdmonitionStyles(): Map<String, AdmonitionStyle> =
    mapOf(
        "tip" to
            AdmonitionStyle(
                borderColor = PeachAccent,
                backgroundColor = DarkSurface,
                iconText = "\u2728",
                titleColor = DarkOnBackground,
            ),
        "warning" to
            AdmonitionStyle(
                borderColor = PeachVariant,
                backgroundColor = DarkSurface,
                iconText = "\u26A0\uFE0F",
                titleColor = DarkOnBackground,
            ),
        "danger" to
            AdmonitionStyle(
                borderColor = ErrorRed,
                backgroundColor = DarkSurface,
                iconText = "\u274C",
                titleColor = DarkOnBackground,
            ),
        "info" to
            AdmonitionStyle(
                borderColor = Color(0xFF3182CE),
                backgroundColor = DarkSurface,
                iconText = "\u2139\uFE0F",
                titleColor = DarkOnBackground,
            ),
        "note" to
            AdmonitionStyle(
                borderColor = Color(0xFF805AD5),
                backgroundColor = DarkSurface,
                iconText = "\uD83D\uDCDD",
                titleColor = DarkOnBackground,
            ),
    )

fun darkMarkdownTheme(bodyStyle: TextStyle = LlmBodyTextStyle.copy(color = DarkOnBackground)): MarkdownTheme =
    MarkdownTheme(
        headingStyles = darkHeadingStyles(),
        bodyStyle = bodyStyle,
        inlineCodeStyle = SpanStyle(color = DarkOnBackground),
        inlineCodeBackground = DarkSurfaceVariant,
        codeBlockStyle =
            Typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = DarkOnBackground,
            ),
        codeBlockBackground = DarkSurface,
        blockSpacing = 18.dp,
        blockQuoteBorderColor = DarkOutline,
        blockQuoteTextColor = DarkOnSurfaceVariant,
        dividerColor = DarkOutline,
        linkColor = PeachAccent,
        listBulletColor = DarkOnBackground,
        tableBorderColor = DarkOutline,
        tableHeaderBackground = DarkSurface,
        highlightColor = Color(0xFF5C4B00),
        taskCheckedColor = Color(0xFF3FB950),
        taskUncheckedColor = DarkOutline,
        mathBlockBackground = DarkSurface,
        mathColor = DarkOnBackground,
        admonitionStyles = darkAdmonitionStyles(),
        kbdBackground = DarkSurfaceVariant,
        codeBlockTitleBackground = Color(0xFF21262D),
    )

private fun lightHeadingStyles() =
    listOf(
        LlmBodyTextStyle.copy(
            color = LightOnBackground,
            fontWeight = FontWeight.ExtraBold,
            fontSize = Typography.headlineLarge.fontSize,
            lineHeight = Typography.headlineLarge.lineHeight,
            textDecoration = TextDecoration.None,
        ),
        LlmBodyTextStyle.copy(
            color = LightOnBackground,
            fontWeight = FontWeight.ExtraBold,
            fontSize = Typography.headlineMedium.fontSize,
            lineHeight = Typography.headlineMedium.lineHeight,
            textDecoration = TextDecoration.None,
        ),
        LlmBodyTextStyle.copy(
            color = LightOnBackground,
            fontWeight = FontWeight.Bold,
            fontSize = Typography.titleLarge.fontSize,
            lineHeight = Typography.titleLarge.lineHeight,
            textDecoration = TextDecoration.None,
        ),
        LlmBodyTextStyle.copy(
            color = LightOnBackground,
            fontWeight = FontWeight.Bold,
            fontSize = Typography.titleMedium.fontSize,
            lineHeight = Typography.titleMedium.lineHeight,
            textDecoration = TextDecoration.None,
        ),
        LlmBodyTextStyle.copy(
            color = Color(0xFF475569), // Slate 600
            fontWeight = FontWeight.Bold,
            fontSize = Typography.titleMedium.fontSize,
            lineHeight = Typography.titleMedium.lineHeight,
            textDecoration = TextDecoration.None,
        ),
        LlmBodyTextStyle.copy(
            color = Color(0xFF475569), // Slate 600
            fontWeight = FontWeight.Bold,
            fontSize = Typography.titleMedium.fontSize,
            lineHeight = Typography.titleMedium.lineHeight,
            textDecoration = TextDecoration.None,
        ),
    )

private fun lightAdmonitionStyles(): Map<String, AdmonitionStyle> =
    mapOf(
        "tip" to
            AdmonitionStyle(
                borderColor = PurpleLightPrimary,
                backgroundColor = LightSurface,
                iconText = "\u2728",
                titleColor = LightOnBackground,
            ),
        "warning" to
            AdmonitionStyle(
                borderColor = GoldVariant,
                backgroundColor = LightSurface,
                iconText = "\u26A0\uFE0F",
                titleColor = LightOnBackground,
            ),
        "danger" to
            AdmonitionStyle(
                borderColor = ErrorRed,
                backgroundColor = LightSurface,
                iconText = "\u274C",
                titleColor = LightOnBackground,
            ),
        "info" to
            AdmonitionStyle(
                borderColor = Color(0xFF3182CE),
                backgroundColor = LightSurface,
                iconText = "\u2139\uFE0F",
                titleColor = LightOnBackground,
            ),
        "note" to
            AdmonitionStyle(
                borderColor = Color(0xFF805AD5),
                backgroundColor = LightSurface,
                iconText = "\uD83D\uDCDD",
                titleColor = LightOnBackground,
            ),
    )

fun lightMarkdownTheme(bodyStyle: TextStyle = LlmBodyTextStyle.copy(color = LightOnBackground)): MarkdownTheme =
    MarkdownTheme(
        headingStyles = lightHeadingStyles(),
        bodyStyle = bodyStyle,
        inlineCodeStyle = SpanStyle(color = LightOnBackground),
        inlineCodeBackground = LightSurface,
        codeBlockStyle =
            Typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = LightOnBackground,
            ),
        codeBlockBackground = LightSurface,
        blockSpacing = 18.dp,
        blockQuoteBorderColor = LightOutline,
        blockQuoteTextColor = Color(0xFF475569),
        dividerColor = LightOutline,
        linkColor = PurpleLightPrimary,
        listBulletColor = LightOnBackground,
        tableBorderColor = LightOutline,
        tableHeaderBackground = LightSurface,
        highlightColor = Color(0xFFFEF08A), // Light yellow highlight
        taskCheckedColor = Color(0xFF16A34A),
        taskUncheckedColor = LightOutline,
        mathBlockBackground = LightSurface,
        mathColor = LightOnBackground,
        admonitionStyles = lightAdmonitionStyles(),
        kbdBackground = LightSurface,
        codeBlockTitleBackground = Color(0xFFE2E8F0),
    )
