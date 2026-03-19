package com.browntowndev.pocketcrew.presentation.components.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for MarkdownTheme configuration.
 * 
 * Verifies the theme system provides customization options for:
 * - Light/dark mode
 * - Code block backgrounds
 * - Typography
 * - Colors
 * 
 * REF: CLARIFIED_REQUIREMENTS.md - Section 5 - "Fully Themeable" feature
 */
class MarkdownThemeTest {

    @Test
    fun `light theme provides valid colors`() {
        val theme = MarkdownTheme.light()

        assertNotNull(theme)
        assertNotNull(theme.codeBlockBackground)
        assertNotNull(theme.bodyStyle)
        assertNotNull(theme.headingStyles)
    }

    @Test
    fun `dark theme provides valid colors`() {
        val theme = MarkdownTheme.dark()

        assertNotNull(theme)
        assertNotNull(theme.codeBlockBackground)
        // Dark theme should have darker code background
        assertTrue(theme.codeBlockBackground != androidx.compose.ui.graphics.Color.White)
    }

    @Test
    fun `auto theme is available`() {
        val theme = MarkdownTheme.auto()

        assertNotNull(theme)
    }

    @Test
    fun `theme allows custom code block background`() {
        val customBackground = androidx.compose.ui.graphics.Color(0xFF2D2D2D)

        val theme = MarkdownTheme(
            codeBlockBackground = customBackground
        )

        assertEquals(customBackground, theme.codeBlockBackground)
    }

    @Test
    fun `theme allows custom heading styles`() {
        val customHeadingStyle = androidx.compose.ui.text.TextStyle(fontSize = 24.sp)

        val theme = MarkdownTheme(
            headingStyles = listOf(
                customHeadingStyle,
                androidx.compose.ui.text.TextStyle(fontSize = 20.sp),
                androidx.compose.ui.text.TextStyle(fontSize = 18.sp),
                androidx.compose.ui.text.TextStyle(fontSize = 16.sp),
                androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
            )
        )

        assertEquals(6, theme.headingStyles.size)
        assertEquals(24.sp, theme.headingStyles[0].fontSize)
    }

    @Test
    fun `theme allows custom body style`() {
        val customBodyStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp)

        val theme = MarkdownTheme(
            bodyStyle = customBodyStyle
        )

        assertEquals(customBodyStyle, theme.bodyStyle)
    }

    @Test
    fun `light and dark themes are visually distinct`() {
        val lightTheme = MarkdownTheme.light()
        val darkTheme = MarkdownTheme.dark()

        // Code block backgrounds should differ between themes
        assertTrue(lightTheme.codeBlockBackground != darkTheme.codeBlockBackground)
    }
}
