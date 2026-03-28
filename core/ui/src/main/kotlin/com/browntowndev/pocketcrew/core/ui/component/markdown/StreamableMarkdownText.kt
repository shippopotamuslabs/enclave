package com.browntowndev.pocketcrew.core.ui.component.markdown
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.browntowndev.pocketcrew.core.ui.theme.darkMarkdownTheme
import com.hrm.markdown.renderer.Markdown


/**
 * A composable that renders markdown text with support for streaming updates.
 * Uses the huarangmeng Markdown library for full markdown parsing and rendering.
 * 
 * Features:
 * - Token-by-token streaming with zero flicker
 * - Auto-closing of unclosed code blocks and math
 * - Incremental parsing (only re-parses changed regions)
 * - Theme support (light/dark mode)
 * - Link handling with click callback
 * 
 *
 * @param markdown The markdown text to render
 * @param isStreaming Whether the text is being streamed (enables streaming optimizations)
 * @param modifier Modifier for the composable
 * @param enableScroll Whether to enable internal scrolling. Set to false when placed inside
 *        another scrollable container (e.g., LazyColumn) to avoid nested scroll issues.
 * @param onLinkClick Optional callback for link clicks
 * @param isPreview When true, renders plain text instead of markdown (for Compose Preview compatibility)
 */
@Composable
fun StreamableMarkdownText(
    modifier: Modifier = Modifier,
    markdown: String,
    isStreaming: Boolean = false,
    enableScroll: Boolean = true,
    onLinkClick: ((String) -> Unit)? = null,
    isPreview: Boolean = false, // Default to false for production; pass true for previews
) {
    if (markdown.isEmpty()) return // Prevents spinner from showing when text is empty

    // In preview mode, use SimpleMarkdownText to avoid library initialization issues
    if (isPreview) {
        SimpleMarkdownText(
            text = markdown,
            modifier = modifier,
        )
    } else {
        Markdown(
            markdown = markdown,
            modifier = modifier,
            isStreaming = isStreaming,
            theme = darkMarkdownTheme(),
            enableScroll = enableScroll,
            onLinkClick = onLinkClick,
        )
    }
}

/**
 * Simple markdown rendering using AnnotatedString.
 * Supports: **bold** and `code`
 * 
 * This is a fallback renderer that doesn't require the external library.
 * Use this for previews when StreamingMarkdownText has initialization issues.
 */
@Composable
fun SimpleMarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val annotatedString = remember(text) {
        parseSimpleMarkdown(text)
    }
    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onBackground
        ),
        modifier = modifier,
    )
}

private fun parseSimpleMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        val processedText = text
        
        val boldRegex = Regex("\\*\\*(.+?)\\*\\*")
        val codeRegex = Regex("`([^`]+)`")
        
        val allMatches = mutableListOf<Pair<IntRange, () -> Unit>>()
        
        boldRegex.findAll(processedText).forEach { match ->
            allMatches.add(match.range to {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(match.groupValues[1])
                }
            })
        }
        
        codeRegex.findAll(processedText).forEach { match ->
            allMatches.add(match.range to {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                    append(match.groupValues[1])
                }
            })
        }
        
        allMatches.sortBy { it.first.first }
        
        for ((range, styleAction) in allMatches) {
            if (range.first >= currentIndex) {
                append(processedText.substring(currentIndex, range.first))
                styleAction()
                currentIndex = range.last + 1
            }
        }
        
        if (currentIndex < processedText.length) {
            append(processedText.substring(currentIndex))
        }
    }
}
