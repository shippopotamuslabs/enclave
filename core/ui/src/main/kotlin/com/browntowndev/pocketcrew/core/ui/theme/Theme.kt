package com.browntowndev.pocketcrew.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import com.hrm.markdown.renderer.AdmonitionStyle
import com.hrm.markdown.renderer.MarkdownTheme
import com.hrm.markdown.renderer.highlight.SyntaxColorScheme

private val DarkColorScheme = darkColorScheme(
    primary = PeachAccent,
    onPrimary = Color.Black,
    primaryContainer = DarkSurfaceVariant,
    onPrimaryContainer = PeachAccent,

    secondary = DarkOnSurfaceVariant,
    onSecondary = DarkOnBackground,

    tertiary = PeachVariant,
    onTertiary = DarkOnBackground,

    background = DarkBackground,
    onBackground = DarkOnBackground,

    surface = DarkSurface,
    onSurface = DarkOnBackground,

    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,

    outline = DarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = PurpleLightPrimary,
    secondary = Color(0xFF64748B),
    tertiary = GoldVariant,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onBackground = LightOnBackground,
    onSurface = LightOnBackground,
)

@Composable
fun PocketCrewTheme(
    darkTheme: Boolean? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDarkTheme = darkTheme ?: isSystemInDarkTheme()

    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

private fun darkHeadingStyles() = listOf(
    Typography.headlineLarge.copy(color = DarkOnBackground, textDecoration = TextDecoration.None),
    Typography.headlineMedium.copy(color = DarkOnBackground, textDecoration = TextDecoration.None),
    Typography.titleLarge.copy(color = DarkOnBackground, textDecoration = TextDecoration.None),
    Typography.titleMedium.copy(color = DarkOnBackground, textDecoration = TextDecoration.None),
    Typography.titleMedium.copy(color = DarkOnSurfaceVariant, textDecoration = TextDecoration.None),
    Typography.titleMedium.copy(color = DarkOnSurfaceVariant, textDecoration = TextDecoration.None),
)

private fun darkAdmonitionStyles(): Map<String, AdmonitionStyle> = mapOf(
    "tip" to AdmonitionStyle(
        borderColor = PeachAccent,
        backgroundColor = DarkSurface,
        iconText = "\u2728",
        titleColor = DarkOnBackground
    ),
    "warning" to AdmonitionStyle(
        borderColor = PeachVariant,
        backgroundColor = DarkSurface,
        iconText = "\u26A0\uFE0F",
        titleColor = DarkOnBackground
    ),
    "danger" to AdmonitionStyle(
        borderColor = Color(0xFFE53E3E),
        backgroundColor = DarkSurface,
        iconText = "\u274C",
        titleColor = DarkOnBackground
    ),
    "info" to AdmonitionStyle(
        borderColor = Color(0xFF3182CE),
        backgroundColor = DarkSurface,
        iconText = "\u2139\uFE0F",
        titleColor = DarkOnBackground
    ),
    "note" to AdmonitionStyle(
        borderColor = Color(0xFF805AD5),
        backgroundColor = DarkSurface,
        iconText = "\uD83D\uDCDD",
        titleColor = DarkOnBackground
    ),
)

fun darkMarkdownTheme(): MarkdownTheme = MarkdownTheme(
    headingStyles = darkHeadingStyles(),
    bodyStyle = Typography.bodyLarge.copy(color = DarkOnBackground),
    inlineCodeStyle = SpanStyle(color = DarkOnBackground),
    inlineCodeBackground = DarkSurfaceVariant,
    codeBlockStyle = Typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        color = DarkOnBackground,
    ),
    codeBlockBackground = DarkSurface,
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
    syntaxColorScheme = SyntaxColorScheme.GitHubDark,
    codeBlockTitleBackground = Color(0xFF21262D),
    codeBlockLineNumberColor = Color(0xFF484F58),
    codeBlockHighlightLineColor = Color(0xFF3B2E00),
)
