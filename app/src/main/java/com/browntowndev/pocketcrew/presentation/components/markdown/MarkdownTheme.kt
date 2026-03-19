package com.browntowndev.pocketcrew.presentation.components.markdown

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * Theme configuration for markdown rendering.
 * 
 * Provides customizable colors, typography, and styles for different markdown elements.
 * Supports light/dark mode and custom theming.
 * 
 * REF: CLARIFIED_REQUIREMENTS.md - Section 5 - "Fully Themeable" feature
 */
@Immutable
data class MarkdownTheme(
    val codeBlockBackground: Color = Color(0xFFF5F5F5),
    val bodyStyle: TextStyle = TextStyle(
        fontSize = 16.sp,
        color = Color.Unspecified
    ),
    val headingStyles: List<TextStyle> = listOf(
        TextStyle(fontSize = 28.sp), // H1
        TextStyle(fontSize = 24.sp), // H2
        TextStyle(fontSize = 20.sp), // H3
        TextStyle(fontSize = 18.sp), // H4
        TextStyle(fontSize = 16.sp), // H5
        TextStyle(fontSize = 14.sp)  // H6
    ),
    val codeStyle: TextStyle = TextStyle(
        fontSize = 14.sp,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
    ),
    val linkColor: Color = Color(0xFF1E88E5)
) {
    companion object {
        /**
         * Light theme with default light colors.
         */
        fun light(): MarkdownTheme = MarkdownTheme(
            codeBlockBackground = Color(0xFFF5F5F5),
            bodyStyle = TextStyle(
                fontSize = 16.sp,
                color = Color(0xFF212121)
            ),
            headingStyles = listOf(
                TextStyle(fontSize = 28.sp, color = Color(0xFF212121)),
                TextStyle(fontSize = 24.sp, color = Color(0xFF212121)),
                TextStyle(fontSize = 20.sp, color = Color(0xFF212121)),
                TextStyle(fontSize = 18.sp, color = Color(0xFF212121)),
                TextStyle(fontSize = 16.sp, color = Color(0xFF212121)),
                TextStyle(fontSize = 14.sp, color = Color(0xFF212121))
            ),
            codeStyle = TextStyle(
                fontSize = 14.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = Color(0xFF212121)
            ),
            linkColor = Color(0xFF1E88E5)
        )

        /**
         * Dark theme with default dark colors.
         */
        fun dark(): MarkdownTheme = MarkdownTheme(
            codeBlockBackground = Color(0xFF2D2D2D),
            bodyStyle = TextStyle(
                fontSize = 16.sp,
                color = Color(0xFFE0E0E0)
            ),
            headingStyles = listOf(
                TextStyle(fontSize = 28.sp, color = Color(0xFFE0E0E0)),
                TextStyle(fontSize = 24.sp, color = Color(0xFFE0E0E0)),
                TextStyle(fontSize = 20.sp, color = Color(0xFFE0E0E0)),
                TextStyle(fontSize = 18.sp, color = Color(0xFFE0E0E0)),
                TextStyle(fontSize = 16.sp, color = Color(0xFFE0E0E0)),
                TextStyle(fontSize = 14.sp, color = Color(0xFFE0E0E0))
            ),
            codeStyle = TextStyle(
                fontSize = 14.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = Color(0xFFE0E0E0)
            ),
            linkColor = Color(0xFF64B5F6)
        )

        /**
         * Auto theme that adapts to system appearance.
         * Note: The actual theme switching should be handled at the call site.
         */
        fun auto(): MarkdownTheme = light()
    }
}
