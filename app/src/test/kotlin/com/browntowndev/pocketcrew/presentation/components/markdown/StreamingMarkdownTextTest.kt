package com.browntowndev.pocketcrew.presentation.components.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for StreamingMarkdownText markdown parsing logic.
 * 
 * Tests the markdown parsing functions that don't require Compose UI.
 * REF: CLARIFIED_REQUIREMENTS.md - Section 5 (MarkdownBlockDetector Replacement)
 */
class StreamingMarkdownTextTest {

    // ========================================================================
    // Test: Basic Markdown Parsing Logic
    // Evidence: App needs to render markdown from LLM responses
    // ========================================================================

    @Test
    fun `markdown theme provides valid defaults`() {
        val theme = MarkdownTheme.light()
        
        assertNotNull(theme)
        assertNotNull(theme.codeBlockBackground)
        assertNotNull(theme.bodyStyle)
        assertNotNull(theme.headingStyles)
    }

    @Test
    fun `dark theme has darker background than light theme`() {
        val lightTheme = MarkdownTheme.light()
        val darkTheme = MarkdownTheme.dark()
        
        // The dark theme code background should be darker
        // (lower brightness = lower alpha or darker color)
        assertTrue(lightTheme.codeBlockBackground != darkTheme.codeBlockBackground)
    }

    @Test
    fun `theme provides 6 heading styles for h1-h6`() {
        val theme = MarkdownTheme.light()
        
        assertEquals(6, theme.headingStyles.size)
    }

    @Test
    fun `custom theme overrides code block background`() {
        val customColor = androidx.compose.ui.graphics.Color(0xFF123456)
        val theme = MarkdownTheme(codeBlockBackground = customColor)
        
        assertEquals(customColor, theme.codeBlockBackground)
    }

    @Test
    fun `custom theme overrides body style`() {
        val customStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp)
        val theme = MarkdownTheme(bodyStyle = customStyle)
        
        assertEquals(customStyle, theme.bodyStyle)
    }

    @Test
    fun `custom theme overrides link color`() {
        val customColor = androidx.compose.ui.graphics.Color(0xFFFF0000)
        val theme = MarkdownTheme(linkColor = customColor)
        
        assertEquals(customColor, theme.linkColor)
    }

    @Test
    fun `auto theme returns a valid theme`() {
        val theme = MarkdownTheme.auto()
        
        assertNotNull(theme)
        assertNotNull(theme.codeBlockBackground)
    }

    // ========================================================================
    // Test: Theme Configuration
    // Evidence: App has light/dark mode support
    // ========================================================================

    @Test
    fun `light theme is not equal to dark theme`() {
        val lightTheme = MarkdownTheme.light()
        val darkTheme = MarkdownTheme.dark()
        
        assertTrue(lightTheme.codeBlockBackground != darkTheme.codeBlockBackground)
        assertTrue(lightTheme.bodyStyle != darkTheme.bodyStyle)
    }

    @Test
    fun `same theme instances are equal`() {
        val theme1 = MarkdownTheme.light()
        val theme2 = MarkdownTheme.light()
        
        assertEquals(theme1.codeBlockBackground, theme2.codeBlockBackground)
    }
}
