package com.browntowndev.pocketcrew.presentation.screen.chat.components

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.pocketcrew.R
import com.browntowndev.pocketcrew.presentation.screen.chat.ChatMessage
import com.browntowndev.pocketcrew.presentation.screen.chat.MessageRole
import com.browntowndev.pocketcrew.presentation.screen.chat.Mode
import com.browntowndev.pocketcrew.presentation.screen.chat.ProcessingIndicatorState
import com.browntowndev.pocketcrew.presentation.screen.chat.StepCompletionData
import com.browntowndev.pocketcrew.presentation.screen.chat.ThinkingData
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.presentation.theme.PocketCrewTheme
import kotlinx.coroutines.launch


private sealed interface ContentSegment {
    data class TextSegment(val text: String) : ContentSegment
    data class CodeBlock(val language: String, val code: String) : ContentSegment
}

private fun parseContent(raw: String): List<ContentSegment> {
    val segments = mutableListOf<ContentSegment>()
    var remaining = raw
    val fencePattern = Regex("```(\\w*)\\s*\\n")

    while (remaining.isNotEmpty()) {
        val openMatch = fencePattern.find(remaining)
        if (openMatch == null) {
            val text = remaining.trim()
            if (text.isNotEmpty()) segments += ContentSegment.TextSegment(text)
            break
        }

        val textBefore = remaining.substring(0, openMatch.range.first).trim()
        if (textBefore.isNotEmpty()) segments += ContentSegment.TextSegment(textBefore)

        val language = openMatch.groupValues[1].lowercase()
        val afterOpen = remaining.substring(openMatch.range.last + 1)

        val closeIndex = afterOpen.indexOf("```")
        if (closeIndex == -1) {
            val code = afterOpen.trimEnd()
            if (code.isNotEmpty()) segments += ContentSegment.CodeBlock(language, code)
            break
        }

        val code = afterOpen.substring(0, closeIndex).trimEnd()
        if (code.isNotEmpty()) segments += ContentSegment.CodeBlock(language, code)

        remaining = afterOpen.substring(closeIndex + 3)
    }

    return segments
}

// ── Main composable ──

@Composable
fun AssistantResponse(
    message: ChatMessage,
    processingIndicatorState: ProcessingIndicatorState = ProcessingIndicatorState.NONE,
    thinkingData: ThinkingData? = null,
    selectedMode: Mode = Mode.FAST,
    showThoughtForHeader: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Use deterministic mode from enum - not inferring from completedSteps
    val crewMode = selectedMode == Mode.CREW

    if (crewMode) {
        CrewAssistantContent(
            message = message,
            processingIndicatorState = processingIndicatorState,
            thinkingData = thinkingData,
            modifier = modifier
        )
    } else {
        NormalAssistantContent(
            message = message,
            processingIndicatorState = processingIndicatorState,
            thinkingData = thinkingData,
            showThoughtForHeader = showThoughtForHeader,
            modifier = modifier
        )
    }
}

// ── Normal Mode (Fast/Thinking) Composable ──

@Composable
private fun NormalAssistantContent(
    message: ChatMessage,
    processingIndicatorState: ProcessingIndicatorState = ProcessingIndicatorState.NONE,
    thinkingData: ThinkingData? = null,
    showThoughtForHeader: Boolean = false,
    modifier: Modifier = Modifier
) {
    val segments = remember(message.content) { parseContent(message.content) }
    var showThinkingDetails by remember { mutableStateOf(false) }

    // Determine which thinking data to use: passed parameter takes precedence over message data
    val effectiveThinkingData = thinkingData ?: message.thinkingData

    Column(modifier = modifier.fillMaxWidth()) {
        // Handle live indicator states for non-Crew mode
        // Processing/Generating/Thinking indicators are now shown in MessageList on the most recent User message
        // ThoughtForHeader shows for ALL messages with thinking data (when showThoughtForHeader is true)
        when (processingIndicatorState) {
            // Show ThoughtForHeader if thinking completed
            ProcessingIndicatorState.PROCESSING -> {
                if (showThoughtForHeader && effectiveThinkingData != null) {
                    ThoughtForHeader(
                        thinkingData = effectiveThinkingData,
                        onViewFullThinking = { showThinkingDetails = true }
                    )
                }
                // Processing indicator is shown in MessageList on user message
            }
            // Show ThoughtForHeader if thinking completed
            ProcessingIndicatorState.GENERATING -> {
                if (showThoughtForHeader && effectiveThinkingData != null) {
                    ThoughtForHeader(
                        thinkingData = effectiveThinkingData,
                        onViewFullThinking = { showThinkingDetails = true }
                    )
                }
                // Generating indicator is shown in MessageList on user message
            }
            // NONE: show animated ThinkingIndicator if thinking in progress (duration = 0)
            ProcessingIndicatorState.NONE -> {
                // If showThoughtForHeader is true, show it (response complete with thinking)
                // This shows for ALL messages with thinking data
                if (showThoughtForHeader && effectiveThinkingData != null) {
                    ThoughtForHeader(
                        thinkingData = effectiveThinkingData,
                        onViewFullThinking = { showThinkingDetails = true }
                    )
                }
                // Thinking indicator is shown in MessageList on user message
            }
        }

        // Response content
        segments.forEach { segment ->
            when (segment) {
                is ContentSegment.TextSegment -> TextBlock(segment.text)
                is ContentSegment.CodeBlock -> FencedCodeBlock(
                    language = segment.language,
                    code = segment.code,
                )
            }
        }

        // Timestamp
        Text(
            text = message.formattedTimestamp,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
                .align(Alignment.End),
        )
    }

    // Bottom sheet for thinking details — only if there are thinking steps
    // Use effectiveThinkingData (passed parameter takes precedence)
    if (effectiveThinkingData != null && effectiveThinkingData.steps.isNotEmpty()) {
        ThinkingDetailsBottomSheet(
            isVisible = showThinkingDetails,
            thinkingSteps = effectiveThinkingData.steps,
            thinkingDurationSeconds = effectiveThinkingData.thinkingDurationSeconds,
            modelDisplayName = effectiveThinkingData.modelDisplayName,
            onDismiss = { showThinkingDetails = false }
        )
    }
}

// ── Crew Mode Composable ──

@Composable
private fun CrewAssistantContent(
    message: ChatMessage,
    processingIndicatorState: ProcessingIndicatorState,
    thinkingData: ThinkingData?,
    modifier: Modifier = Modifier
) {
    val segments = remember(message.content) { parseContent(message.content) }
    var thinkingDataForDetailsSheet by remember { mutableStateOf<ThinkingData?>(null) }
    var selectedStepForCompletionDetails by remember { mutableStateOf<StepCompletionData?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Interleave "Thought For" indicators with completed steps
        // For each step in order: "Thought For Xs" → "✓ Step Completed!"
        message.completedSteps?.forEach { step ->
            // Show "Thought For" for THIS step if it has thinking
            // Both thinkingDurationSeconds > 0 AND steps must be non-empty for a thinking step
            if (step.thinkingComplete) {
                val stepThinkingData = ThinkingData(
                    thinkingDurationSeconds = step.thinkingDurationSeconds,
                    steps = step.thinkingSteps,
                    modelDisplayName = step.modelDisplayName
                )
                ThoughtForHeader(
                    thinkingData = stepThinkingData,
                    onViewFullThinking = { thinkingDataForDetailsSheet = stepThinkingData }
                )
            }

            // Show "✓ Step Completed!" for THIS step (except FINAL)
            if (step.stepType != PipelineStep.FINAL) {
                CompletedStepRow(
                    stepCompletion = step,
                    onClick = { selectedStepForCompletionDetails = step }
                )
            }
        }

        // Live indicator below completed steps
        // In Crew mode: shows current step's thinking + generating, then Processing when step completes
        when (processingIndicatorState) {
            // Step is still generating text - show "Thought For" + generating indicator
            ProcessingIndicatorState.GENERATING -> {
                if (thinkingData != null && thinkingData.thinkingDurationSeconds > 0) {
                    ThoughtForHeader(
                        thinkingData = thinkingData,
                        onViewFullThinking = { thinkingDataForDetailsSheet = thinkingData }
                    )
                }
                GeneratingIndicator()
            }
            // Step completed, waiting for next step
            ProcessingIndicatorState.PROCESSING -> {
                ProcessingIndicator()
            }
            // Active thinking (no text generated yet) - show animated indicator
            ProcessingIndicatorState.NONE -> {
                if (thinkingData != null) {
                    ThinkingIndicator(
                        thinkingSteps = thinkingData.steps,
                        thinkingStartTime = thinkingData.thinkingStartTime,
                        modelDisplayName = thinkingData.modelDisplayName,
                    )
                }
            }
        }

        // Response content
        segments.forEach { segment ->
            when (segment) {
                is ContentSegment.TextSegment -> TextBlock(segment.text)
                is ContentSegment.CodeBlock -> FencedCodeBlock(
                    language = segment.language,
                    code = segment.code,
                )
            }
        }

        // Timestamp
        Text(
            text = message.formattedTimestamp,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
                .align(Alignment.End),
        )
    }

    // Bottom sheet for thinking details — from a specific step
    if (thinkingDataForDetailsSheet != null) {
        ThinkingDetailsBottomSheet(
            isVisible = true,
            thinkingSteps = thinkingDataForDetailsSheet!!.steps,
            thinkingDurationSeconds = thinkingDataForDetailsSheet!!.thinkingDurationSeconds,
            modelDisplayName = thinkingDataForDetailsSheet!!.modelDisplayName,
            onDismiss = { thinkingDataForDetailsSheet = null }
        )
    }

    // Bottom sheet for step completion details (output)
    if (selectedStepForCompletionDetails != null) {
        StepCompletionBottomSheet(
            isVisible = true,
            stepCompletion = selectedStepForCompletionDetails!!,
            onDismiss = { selectedStepForCompletionDetails = null }
        )
    }
}

// ── "Thought for Xs" header that opens bottom sheet ──

private fun formatThinkingDuration(seconds: Int): String = when {
    seconds < 60 -> "${seconds}s"
    else -> "${seconds / 60}m ${seconds % 60}s"
}

@Composable
private fun ThoughtForHeader(
    thinkingData: ThinkingData,
    onViewFullThinking: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val durationText = formatThinkingDuration(thinkingData.thinkingDurationSeconds)

    // Header row: cognition + "Thought for Xs" — clickable to open bottom sheet
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onViewFullThinking() }
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.cognition),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Thought for $durationText",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Completed Steps header for Crew mode ──

@Composable
private fun CompletedStepsHeader(
    completedSteps: List<StepCompletionData>,
    onStepClick: (StepCompletionData) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Filter out FINAL step - its output is already displayed as the chat response
    val visibleSteps = completedSteps.filter { it.stepType != PipelineStep.FINAL }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
    ) {
        visibleSteps.forEach { step ->
            CompletedStepRow(
                stepCompletion = step,
                onClick = { onStepClick(step) }
            )
        }
    }
}

// ── Single completed step row (used for interleaved rendering) ──

@Composable
private fun CompletedStepRow(
    stepCompletion: StepCompletionData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stepName = stepCompletion.stepName
    val modelDisplayName = stepCompletion.modelDisplayName
    val stepOutput = stepCompletion.stepOutput

    // Preview text - "Tap to View" as requested
    val previewText = "Tap to View"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Model message: "$modelDisplayName: I have completed my task for the Crew. Passing on to the next member."
        val completionMessage = remember(modelDisplayName) {
            buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(modelDisplayName)
                }
                append(": I have completed my task for the Crew. Passing on to the next member.")
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = completionMessage,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Card that looks like Claude's design
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                .clickable { onClick() }
                .padding(12.dp)
        ) {
            // Document icon - double size, rotated counterclockwise
            // Position extends below card so bottom gets clipped by container
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .rotate(-25f)
            ) {
                Icon(
                    painter = painterResource(R.drawable.document),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(56.dp)
                        .offset(y = 14.dp),
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            // Text content on the right - moved further right
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "$stepName Results",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 23.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

// ── Bottom sheet for step completion details ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepCompletionBottomSheet(
    isVisible: Boolean,
    stepCompletion: StepCompletionData,
    onDismiss: () -> Unit,
) {
    if (isVisible) {
        val sheetState = rememberModalBottomSheetState()

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            contentWindowInsets = { WindowInsets.safeDrawing }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header with step name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${stepCompletion.stepName} Output",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Step output content
                SelectionContainer {
                    Text(
                        text = stepCompletion.stepOutput,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Duration info - show TOTAL time (thinking + generation), not just thinking time
                Text(
                    text = "Duration: ${formatThinkingDuration(stepCompletion.totalDurationSeconds)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ── Text and code block composables ──

@Composable
private fun TextBlock(text: String) {
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    // Create a very light, transparent background matching the primary color
    val inlineBackgroundColor = tertiaryColor.copy(alpha = 0.05f)

    val annotatedString = remember(text, tertiaryColor, inlineBackgroundColor) {
        buildAnnotatedString {
            val inlineCodeRegex = Regex("`([^`]+)`")
            var lastIndex = 0
            val matches = inlineCodeRegex.findAll(text)

            for (match in matches) {
                // Append text before the match
                append(text.substring(lastIndex, match.range.first))

                // Append styled code without backticks
                withStyle(
                    style = SpanStyle(
                        color = tertiaryColor,
                        fontWeight = FontWeight.Bold,
                        background = inlineBackgroundColor,
                        fontFamily = FontFamily.Monospace // Distinguish as code
                    ),
                ) {
                    // Wrapping the text in thin spaces (\u2009) adds a tiny bit
                    // of visual padding inside the background rectangle.
                    append("\u2009${match.groupValues[1]}\u2009")
                }
                lastIndex = match.range.last + 1
            }
            // Append remaining text
            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
    }

    SelectionContainer(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = annotatedString,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun FencedCodeBlock(
    language: String,
    code: String,
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val codeBlockBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val displayLanguage = language.replaceFirstChar { it.uppercase() }.ifEmpty { "Code" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(codeBlockBackground),
    ) {
        // Header row: language label + copy button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = displayLanguage,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(
                onClick = {
                    scope.launch {
                        clipboardManager.setClipEntry(
                            ClipEntry(ClipData.newPlainText(displayLanguage, code)),
                        )
                    }
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.content_copy),
                    contentDescription = "Copy $displayLanguage code",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Code content — horizontally scrollable, monospace
        SelectionContainer {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// ==================== PREVIEWS ====================

@Preview
@Composable
private fun PreviewAssistantPlainText() {
    PocketCrewTheme(darkTheme = true) {
        AssistantResponse(
            ChatMessage(id = 1L, chatId = 1L, role = MessageRole.Assistant, content = "Hello from assistant!", formattedTimestamp = "10:30 AM"),
        )
    }
}

@Preview
@Composable
private fun PreviewAssistantWithThinking() {
    PocketCrewTheme(darkTheme = true) {
        AssistantResponse(
            ChatMessage(
                id = 2L,
                chatId = 1L,
                role = MessageRole.Assistant,
                content = "Here is my well-considered answer after deep analysis.",
                formattedTimestamp = "10:31 AM",
                thinkingData = ThinkingData(
                    thinkingDurationSeconds = 14,
                    steps = listOf(
                        "Agent A: Drafting direct answer with examples...",
                        "Agent B: Generating skeptical counterarguments...",
                        "Synthesizer: Merging drafts into cohesive response...",
                        "Refinement: Checking for logical contradictions...",
                    ),
                ),
            ),
        )
    }
}

@Preview
@Composable
private fun PreviewAssistantQuickThinking() {
    PocketCrewTheme(darkTheme = true) {
        AssistantResponse(
            ChatMessage(
                id = 3L,
                chatId = 1L,
                role = MessageRole.Assistant,
                content = "Quick answer — no deep thinking needed.",
                formattedTimestamp = "10:32 AM",
                thinkingData = ThinkingData(
                    thinkingDurationSeconds = 2,
                    steps = listOf("Quick mode — single-pass generation"),
                ),
            ),
        )
    }
}

@Preview
@Composable
private fun PreviewAssistantWithKotlinCode() {
    PocketCrewTheme(darkTheme = true) {
        AssistantResponse(
            ChatMessage(
                id = 4L,
                chatId = 1L,
                role = MessageRole.Assistant,
                content = "Here's a Kotlin example:\n\n" +
                    "```kotlin\n" +
                    "fun greet(name: String): String {\n" +
                    "    return \"Hello, \$name!\"\n" +
                    "}\n" +
                    "```\n\n" +
                    "This function takes a name and returns a greeting.",
                formattedTimestamp = "10:33 AM",
                thinkingData = ThinkingData(
                    thinkingDurationSeconds = 8,
                    steps = listOf(
                        "Generating Kotlin example with idiomatic patterns...",
                        "Verifying code compiles and output is correct...",
                    ),
                ),
            ),
        )
    }
}

@Preview
@Composable
private fun PreviewAssistantWithJson() {
    PocketCrewTheme(darkTheme = true) {
        AssistantResponse(
            ChatMessage(
                id = 5L,
                chatId = 1L,
                role = MessageRole.Assistant,
                content = "The API returns the following JSON:\n\n" +
                    "```json\n" +
                    "{\n" +
                    "  \"status\": \"ok\",\n" +
                    "  \"data\": {\n" +
                    "    \"users\": [\n" +
                    "      { \"id\": 1, \"name\": \"Sean\" }\n" +
                    "    ]\n" +
                    "  }\n" +
                    "}\n" +
                    "```\n\n" +
                    "You can parse this with kotlinx.serialization.",
                formattedTimestamp = "10:34 AM",
            ),
        )
    }
}

@Preview
@Composable
private fun PreviewAssistantMultipleBlocks() {
    PocketCrewTheme(darkTheme = true) {
        AssistantResponse(
            ChatMessage(
                id = 6L,
                chatId = 1L,
                role = MessageRole.Assistant,
                content = "Here's the XML layout:\n\n" +
                    "```xml\n" +
                    "<LinearLayout\n" +
                    "    android:layout_width=\"match_parent\"\n" +
                    "    android:layout_height=\"wrap_content\">\n" +
                    "    <TextView android:text=\"Hello\" />\n" +
                    "</LinearLayout>\n" +
                    "```\n\n" +
                    "And the corresponding Compose:\n\n" +
                    "```kotlin\n" +
                    "Column(Modifier.fillMaxWidth()) {\n" +
                    "    Text(\"Hello\")\n" +
                    "}\n" +
                    "```\n\n" +
                    "Both produce the same result.",
                formattedTimestamp = "10:35 AM",
            ),
        )
    }
}

@Preview
@Composable
private fun PreviewAssistantNoThinking() {
    PocketCrewTheme(darkTheme = true) {
        AssistantResponse(
            ChatMessage(
                id = 7L,
                chatId = 1L,
                role = MessageRole.Assistant,
                content = "A response with no thinking data at all — quick mode.",
                formattedTimestamp = "10:36 AM",
                thinkingData = null,
            ),
        )
    }
}

@Preview
@Composable
private fun PreviewAssistantComplexMarkdown() {
    PocketCrewTheme(darkTheme = true) {
        AssistantResponse(
            ChatMessage(
                id = 8L,
                chatId = 1L,
                role = MessageRole.Assistant,
                content = """Here is your markdown code block:
                    ```markdown
# Using the Sum Function

Here's how to use the **`sum`** function with *`Int`* parameters.

You can call `sum(a = 5, b = 3)` or use default values like `sum()`.

## Examples

- Inline code: `value?.length ?: 0`
- Bold text: **important**
- Italic text: *emphasis*
- Strikethrough: ~~deprecated~~
- Links: [documentation](https://example.com)```

> You can also mix **bold** and *italic* together for rich formatting.""",
                formattedTimestamp = "10:37 AM",
            ),
        )
    }
}

@Preview
@Composable
private fun PreviewAssistantMixedInlineAndFenced() {
    PocketCrewTheme(darkTheme = true) {
        AssistantResponse(
            ChatMessage(
                id = 9L,
                chatId = 1L,
                role = MessageRole.Assistant,
                content = "To iterate, use a `for` loop:\n\n" +
                    "```kotlin\n" +
                    "for (i in 1..10) {\n" +
                    "    println(i)\n" +
                    "}\n" +
                    "```\n\n" +
                    "The `..` operator creates a range, and you can also use `until` like `0 until 10` " +
                    "to exclude the end value.",
                formattedTimestamp = "10:38 AM",
            ),
        )
    }
}

@Preview
@Composable
private fun PreviewAssistantInlineCodeEdgeCases() {
    PocketCrewTheme(darkTheme = true) {
        AssistantResponse(
            ChatMessage(
                id = 10L,
                chatId = 1L,
                role = MessageRole.Assistant,
                content = "Use `null` checks with `?.` or `?:`. For example: `value?.length ?: 0`. " +
                    "Inline code at start: `println(\"hello\")` is the basic output. " +
                    "At end: use `String` type. Mixed: `List<String>` and `Map<String, Int>`.",
                formattedTimestamp = "10:39 AM",
            ),
        )
    }
}
