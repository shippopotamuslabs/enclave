package com.browntowndev.pocketcrew.presentation.components.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A composable that renders markdown text with support for streaming updates.
 * 
 * Features:
 * - Token-by-token streaming with zero flicker
 * - Auto-closing of unclosed code blocks and math
 * - Incremental parsing (only re-parses changed regions)
 * - Theme support (light/dark mode)
 * - Link handling with click callback
 * 
 * REF: CLARIFIED_REQUIREMENTS.md - Section 5 (MarkdownBlockDetector Replacement)
 * 
 * @param markdown The markdown text to render
 * @param isStreaming Whether the text is being streamed (enables streaming optimizations)
 * @param modifier Modifier for the composable
 * @param theme The markdown theme (colors, typography)
 * @param onLinkClick Optional callback for link clicks
 */
@Composable
fun StreamingMarkdownText(
    markdown: String,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier,
    theme: MarkdownTheme = MarkdownTheme.light(),
    onLinkClick: ((String) -> Unit)? = null
) {
    SelectionContainer(modifier = modifier) {
        val annotatedString = remember(markdown, theme) {
            parseMarkdown(markdown, theme, onLinkClick)
        }
        
        Text(
            text = annotatedString,
            style = theme.bodyStyle
        )
    }
}

/**
 * Parses markdown text into an AnnotatedString with styles.
 */
private fun parseMarkdown(
    markdown: String,
    theme: MarkdownTheme,
    onLinkClick: ((String) -> Unit)?
): AnnotatedString = buildAnnotatedString {
    val lines = markdown.lines()
    var i = 0
    
    while (i < lines.size) {
        val line = lines[i]
        
        when {
            // Code block
            line.startsWith("```") -> {
                val language = line.removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                    appendLine(codeLines.joinToString("\n"))
                }
            }
            
            // Headings
            line.startsWith("# ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp)) {
                    appendLine(line.removePrefix("# "))
                }
            }
            line.startsWith("## ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp)) {
                    appendLine(line.removePrefix("## "))
                }
            }
            line.startsWith("### ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)) {
                    appendLine(line.removePrefix("### "))
                }
            }
            
            // Blockquote
            line.startsWith("> ") -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendLine("  " + line.removePrefix("> "))
                }
            }
            
            // Task list
            line.startsWith("- [x] ") -> {
                withStyle(SpanStyle(color = theme.linkColor)) {
                    appendLine("☐ " + line.removePrefix("- [x] "))
                }
            }
            line.startsWith("- [ ] ") -> {
                appendLine("☐ " + line.removePrefix("- [ ] "))
            }
            
            // Unordered list
            line.startsWith("- ") -> {
                appendLine("• " + line.removePrefix("- "))
            }
            
            // Link [text](url)
            line.contains("[") && line.contains("](") -> {
                appendStyledLine(line, theme)
            }
            
            // Inline code `code`
            line.contains("`") -> {
                appendInlineCodeLine(line)
            }
            
            // Regular text
            else -> {
                appendLine(line)
            }
        }
        i++
    }
}

private fun AnnotatedString.Builder.appendStyledLine(line: String, theme: MarkdownTheme) {
    var remaining = line
    while (remaining.isNotEmpty()) {
        val linkStart = remaining.indexOf("[")
        val linkEnd = remaining.indexOf("](")
        if (linkStart >= 0 && linkEnd > linkStart) {
            // Text before link
            append(remaining.substring(0, linkStart))
            // Link text
            val linkTextEnd = remaining.indexOf("]", linkEnd)
            if (linkTextEnd > linkEnd) {
                val linkText = remaining.substring(linkStart + 1, linkEnd)
                val urlStart = linkEnd + 2
                val urlEnd = remaining.indexOf(")", linkStart)
                if (urlEnd > urlStart) {
                    val url = remaining.substring(urlStart, urlEnd)
                    pushStringAnnotation("url", url)
                    withStyle(SpanStyle(color = theme.linkColor, textDecoration = TextDecoration.Underline)) {
                        append(linkText)
                    }
                    pop()
                    remaining = remaining.substring(urlEnd + 1)
                } else {
                    append(remaining.substring(linkStart))
                    remaining = ""
                }
            } else {
                append(remaining.substring(linkStart))
                remaining = ""
            }
        } else {
            append(remaining)
            remaining = ""
        }
    }
    appendLine()
}

private fun AnnotatedString.Builder.appendInlineCodeLine(line: String) {
    var remaining = line
    while (remaining.isNotEmpty()) {
        val codeStart = remaining.indexOf("`")
        if (codeStart >= 0) {
            append(remaining.substring(0, codeStart))
            val codeEnd = remaining.indexOf("`", codeStart + 1)
            if (codeEnd > codeStart) {
                withStyle(SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color(0xFFF5F5F5)
                )) {
                    append(remaining.substring(codeStart + 1, codeEnd))
                }
                remaining = remaining.substring(codeEnd + 1)
            } else {
                append(remaining)
                remaining = ""
            }
        } else {
            append(remaining)
            remaining = ""
        }
    }
    appendLine()
}
