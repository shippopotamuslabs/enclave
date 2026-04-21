package com.browntowndev.pocketcrew.core.ui.component.markdown
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.browntowndev.pocketcrew.core.ui.theme.LlmBodyTextStyle
import com.browntowndev.pocketcrew.core.ui.theme.darkMarkdownTheme
import com.hrm.markdown.renderer.Markdown
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive


private val boldRegex = Regex("\\*\\*(.+?)\\*\\*")
private val codeRegex = Regex("`([^`]+)`")

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

    // Debounce / throttle logic for streaming text
    val targetText by rememberUpdatedState(markdown)
    var displayedText by remember { mutableStateOf(if (isStreaming) "" else markdown) }

    // When streaming stops, immediately sync displayedText to prevent
    // the typewriter animation from freezing with stale partial content.
    if (!isStreaming && displayedText != targetText) {
        displayedText = targetText
    }

    LaunchedEffect(isStreaming) {
        if (!isStreaming) {
            return@LaunchedEffect
        }

        while (isActive) {
            val target = targetText

            if (displayedText == target) {
                // Suspend until target changes
                snapshotFlow { targetText }.first { it != displayedText }
            } else {
                if (target.length < displayedText.length || !target.startsWith(displayedText)) {
                    // Reset case: text was cleared or replaced (e.g., new message or rollback)
                    displayedText = target
                } else {
                    // Advance case: smooth typewriter effect with adaptive speed
                    val diff = target.length - displayedText.length
                    val charsToAdd = when {
                        diff > 500 -> 50 // Extremely far behind, catch up very fast
                        diff > 200 -> 20
                        diff > 100 -> 10
                        diff > 50 -> 5
                        else -> 1        // Smooth character-by-character typing
                    }

                    val nextIndex = minOf(displayedText.length + charsToAdd, target.length)
                    displayedText = target.substring(0, nextIndex)

                    // Small delay to pace the typewriter effect (approx. 60fps rendering)
                    delay(15)
                }
            }
        }
    }

    val textToRender = displayedText

    // In preview mode, use SimpleMarkdownText to avoid library initialization issues
    if (isPreview) {
        SimpleMarkdownText(
            text = textToRender,
            modifier = modifier,
        )
    } else {
        SelectionContainer(modifier = modifier) {
            Markdown(
                markdown = textToRender,
                modifier = Modifier,
                isStreaming = isStreaming,
                theme = darkMarkdownTheme(),
                enableScroll = enableScroll,
                onLinkClick = onLinkClick,
            )
        }
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
        style = LlmBodyTextStyle.copy(
            color = MaterialTheme.colorScheme.onBackground
        ),
        modifier = modifier,
    )
}

private fun parseSimpleMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        val processedText = text
        
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
