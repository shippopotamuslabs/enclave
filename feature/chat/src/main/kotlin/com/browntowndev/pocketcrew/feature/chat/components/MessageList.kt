package com.browntowndev.pocketcrew.feature.chat.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.pocketcrew.feature.chat.R
import com.browntowndev.pocketcrew.feature.chat.ChatMessage
import com.browntowndev.pocketcrew.feature.chat.ContentUi
import com.browntowndev.pocketcrew.feature.chat.IndicatorState
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.feature.chat.MessageRole
import com.browntowndev.pocketcrew.feature.chat.ThinkingDataUi
import com.browntowndev.pocketcrew.feature.chat.fakeLongMessages
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.core.ui.component.markdown.SimpleMarkdownText

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    isPreview: Boolean = false,
) {
    val listState = rememberLazyListState()
    val hasActiveIndicator = messages.any { it.indicatorState != null }

    // Auto-scroll to bottom (index 0 in reverseLayout) when messages are added
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    if (messages.isEmpty() && !hasActiveIndicator) {
        EmptyState(modifier = modifier)
    } else {
        LazyColumn(
            modifier = modifier,
            state = listState,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            items(
                count = messages.size,
                key = { index: Int -> "msg_${messages[messages.size - 1 - index].id}" },
            ) { index: Int ->
                val messageIndex = messages.size - 1 - index
                val message = messages[messageIndex]

                // ============================================================
                // Content rendering based on message role
                // ============================================================
                if (message.role == MessageRole.User) {
                    MessageBubble(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        message = message,
                    )
                } else {
                    AssistantResponse(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        message = message,
                        isPreview = isPreview,
                    )
                }

                // ============================================================
                // Indicator rendering - shows at TOP for Assistant messages
                // (below content for User messages since they have no indicators)
                // ============================================================
                Indicators(
                    modelDisplayName = message.modelDisplayName,
                    indicatorState = message.indicatorState
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        Image(
            painter = painterResource(R.drawable.pocket_crew_icon),
            contentDescription = null,
            modifier = Modifier.size(160.dp)
        )
        Text(
            text = "Pocket Crew is ready",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Ask us anything",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * Shows the appropriate indicator(s) based on the message's indicator state.
 */
@Composable
private fun Indicators(modelDisplayName: String, indicatorState: IndicatorState?) {
    if (indicatorState != null) {
        var isThinkingSheetVisible by remember { mutableStateOf(false) }

        // Hoist elapsed time counter to share between Indicator and BottomSheet
        var elapsedSeconds by remember { mutableIntStateOf(0) }
        val startTime = when (indicatorState) {
            is IndicatorState.Thinking -> indicatorState.thinkingStartTime
            is IndicatorState.Generating -> indicatorState.thinkingData?.thinkingStartTime ?: 0L
            is IndicatorState.Complete -> indicatorState.thinkingData?.thinkingStartTime ?: 0L
            else -> 0L
        }
        val isThinkingActive = indicatorState is IndicatorState.Thinking ||
                (indicatorState is IndicatorState.Generating && indicatorState.thinkingData?.thinkingDurationSeconds == 0L)

        if (startTime > 0 && isThinkingActive) {
            androidx.compose.runtime.LaunchedEffect(startTime) {
                while (true) {
                    elapsedSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                    kotlinx.coroutines.delay(1000L)
                }
            }
        } else {
            // For completed states, use the final duration
            elapsedSeconds = when (indicatorState) {
                is IndicatorState.Thinking -> indicatorState.thinkingDurationSeconds.toInt()
                is IndicatorState.Generating -> indicatorState.thinkingData?.thinkingDurationSeconds?.toInt() ?: 0
                is IndicatorState.Complete -> indicatorState.thinkingData?.thinkingDurationSeconds?.toInt() ?: 0
                else -> 0
            }
        }

        when (indicatorState) {
            is IndicatorState.Thinking -> {
                ThinkingIndicator(
                    modifier = Modifier.padding(horizontal = 5.dp),
                    thinkingRaw = indicatorState.thinkingRaw,
                    elapsedSeconds = elapsedSeconds,
                    modelDisplayName = modelDisplayName,
                    isExpanded = isThinkingSheetVisible,
                    onToggleDetails = {
                        isThinkingSheetVisible = !isThinkingSheetVisible
                    },
                )
            }
            is IndicatorState.Processing -> {
                ProcessingIndicator()
            }
            is IndicatorState.Generating -> {
                GeneratingIndicator(modifier = Modifier.offset(x = (-2).dp))

                val thinkingData = indicatorState.thinkingData
                if (thinkingData != null && thinkingData.thinkingRaw.isNotBlank()) {
                    ThoughtForHeader(
                        modifier = Modifier.padding(horizontal = 5.dp),
                        durationText = formatThinkingDuration(elapsedSeconds),
                        isExpanded = isThinkingSheetVisible,
                        onViewFullThinking = {
                            isThinkingSheetVisible = !isThinkingSheetVisible
                        }
                    )
                }
            }
            is IndicatorState.Complete -> {
                val thinkingData = indicatorState.thinkingData
                if (thinkingData != null && thinkingData.thinkingRaw.isNotBlank()) {
                    ThoughtForHeader(
                        modifier = Modifier.padding(horizontal = 5.dp),
                        durationText = formatThinkingDuration(elapsedSeconds),
                        isExpanded = isThinkingSheetVisible,
                        onViewFullThinking = {
                            isThinkingSheetVisible = !isThinkingSheetVisible
                        }
                    )
                }
            }
            is IndicatorState.None -> {
                // Show no indicator at all.
            }
        }

        if (isThinkingSheetVisible) {
            ThinkingDetailsBottomSheet(
                isVisible = isThinkingSheetVisible,
                thinkingRaw = indicatorState.let {
                    when (it) {
                        is IndicatorState.Thinking -> it.thinkingRaw
                        is IndicatorState.Generating -> it.thinkingData?.thinkingRaw ?: ""
                        is IndicatorState.Complete -> it.thinkingData?.thinkingRaw ?: ""
                        else -> ""
                    }
                },
                thinkingDurationSeconds = indicatorState.let {
                    when (it) {
                        is IndicatorState.Thinking -> it.thinkingDurationSeconds
                        is IndicatorState.Generating -> it.thinkingData?.thinkingDurationSeconds ?: 0
                        is IndicatorState.Complete -> it.thinkingData?.thinkingDurationSeconds ?: 0
                        else -> 0
                    }
                },
                elapsedSeconds = elapsedSeconds,
                onDismiss = { isThinkingSheetVisible = false }
            )
        }
    }
}

@Composable
private fun ThoughtForHeader(
    modifier: Modifier = Modifier,
    durationText: String,
    isExpanded: Boolean = false,
    onViewFullThinking: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "chevronRotation"
    )

    // Header row: lightbulb + "Thought for Xs" — clickable to open bottom sheet
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(vertical = 10.dp)
            .fillMaxWidth()
            .clickable { onViewFullThinking() }
    ) {
        Icon(
            painter = painterResource(R.drawable.lightbulb),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = if (isExpanded) "Collapse details" else "Expand details",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .rotate(rotation),
        )
    }
}

// ── "Thought for Xs" header that opens bottom sheet ──

private fun formatThinkingDuration(seconds: Int): String = when {
    seconds < 60 -> "${seconds}s"
    else -> "${seconds / 60}m ${seconds % 60}s"
}

// ==================== PREVIEWS ====================

@Preview
@Composable
private fun PreviewMessageListEmpty() {
    PocketCrewTheme {
        MessageList(emptyList(), isPreview =  true)
    }
}

@Preview
@Composable
private fun PreviewMessageListLong() {
    PocketCrewTheme(darkTheme = true) {
        MessageList(fakeLongMessages, isPreview =  true)
    }
}

@Preview
@Composable
private fun PreviewMessageListThinking() {
    PocketCrewTheme(darkTheme = true) {
        MessageList(
            isPreview =  true,
            messages = listOf(
                ChatMessage(
                    id = 1,
                    chatId = 1L,
                    role = MessageRole.User,
                    content = ContentUi(text = "Explain quantum computing"),
                    formattedTimestamp = "10:30 AM",
                ),
                ChatMessage(
                    id = 2,
                    chatId = 1L,
                    role = MessageRole.Assistant,
                    content = ContentUi(text = ""),
                    formattedTimestamp = "10:29 AM",
                    indicatorState = IndicatorState.Thinking(
                        thinkingRaw = "# Quantum Computing Analysis\n\n" +
                            "Let me break down quantum computing:\n\n" +
                            "## Key Concepts\n\n" +
                            "1. **Qubits** - Unlike bits, qubits can be 0, 1, or both\n" +
                            "2. **Superposition** - Multiple states at once\n" +
                            "3. **Entanglement** - Correlated qubit pairs\n\n" +
                            "```python\n" +
                            "# Example quantum circuit\n" +
                            "circuit = QuantumCircuit(2)\n" +
                            "circuit.h(0)  # Hadamard gate\n" +
                            "circuit.cx(0, 1)  # CNOT\n" +
                            "```",
                        thinkingDurationSeconds = 23,
                    ),
                    modelDisplayName = "Qwen 3 8B",
                )
            )
        )
    }
}

@Preview
@Composable
private fun PreviewMessageListProcessing() {
    PocketCrewTheme(darkTheme = true) {
        MessageList(
            isPreview =  true,
            messages = listOf(
                ChatMessage(
                    id = 1,
                    chatId = 1L,
                    role = MessageRole.User,
                    content = ContentUi(text = "Hello?"),
                    formattedTimestamp = "10:30 AM",
                ),
                ChatMessage(
                    id = 2,
                    chatId = 1L,
                    role = MessageRole.Assistant,
                    content = ContentUi(text = ""),
                    formattedTimestamp = "10:29 AM",
                    indicatorState = IndicatorState.Processing,
                    modelDisplayName = "Qwen 3 8B",
                )
            )
        )
    }
}

@Preview
@Composable
private fun PreviewMessageListGenerating() {
    PocketCrewTheme(darkTheme = true) {
        MessageList(
            isPreview =  true,
            messages = listOf(
                ChatMessage(
                    id = 1,
                    chatId = 1L,
                    role = MessageRole.User,
                    content = ContentUi(text = "What is Docker?"),
                    formattedTimestamp = "10:30 AM",
                ),
                ChatMessage(
                    id = 2,
                    chatId = 1L,
                    role = MessageRole.Assistant,
                    content = ContentUi(text = ""),
                    formattedTimestamp = "10:29 AM",
                    indicatorState = IndicatorState.Generating(
                        thinkingData = ThinkingDataUi(
                            thinkingDurationSeconds = 5,
                            thinkingRaw = "Docker is a containerization platform. Let me explain:\n\n" +
                                "- **Containers** vs VMs\n" +
                                "- **Dockerfile** for images\n" +
                                "- **docker-compose** for multi-container apps"
                        )
                    ),
                    modelDisplayName = "Qwen 3 8B",
                )
            )
        )
    }
}

// DRAFT_ONE - Generating with ThinkingData
@Preview(name = "Pipeline: DRAFT_ONE Generating with Thinking")
@Composable
private fun PreviewPipelineDraftOneGeneratingWithThinking() {
    PocketCrewTheme(darkTheme = true) {
        MessageList(
            isPreview =  true,
            messages = listOf(
                ChatMessage(
                    id = 1,
                    chatId = 1L,
                    role = MessageRole.User,
                    content = ContentUi(text = "Write me a function to sort a list"),
                    formattedTimestamp = "10:30 AM",
                ),
                ChatMessage(
                    id = 2,
                    chatId = 1L,
                    role = MessageRole.Assistant,
                    content = ContentUi(text = "", pipelineStep = PipelineStep.DRAFT_ONE),
                    formattedTimestamp = "10:29 AM",
                    indicatorState = IndicatorState.Generating(
                        thinkingData = ThinkingDataUi(
                            thinkingDurationSeconds = 4,
                            thinkingRaw = "Analyzing sorting algorithms...\n\n" +
                                "- Quick sort seems optimal here\n" +
                                "- Need to handle edge cases..."
                        )
                    ),
                    modelDisplayName = "Qwen 3 8B",
                )
            )
        )
    }
}

// DRAFT_ONE - Thinking (streaming)
@Preview(name = "Pipeline: DRAFT_ONE Thinking")
@Composable
private fun PreviewPipelineDraftOneThinking() {
    PocketCrewTheme(darkTheme = true) {
        MessageList(
            isPreview =  true,
            messages = listOf(
                ChatMessage(
                    id = 1,
                    chatId = 1L,
                    role = MessageRole.User,
                    content = ContentUi(text = "Write me a function to sort a list"),
                    formattedTimestamp = "10:30 AM",
                ),
                ChatMessage(
                    id = 2,
                    chatId = 1L,
                    role = MessageRole.Assistant,
                    content = ContentUi(text = "", pipelineStep = PipelineStep.DRAFT_ONE),
                    formattedTimestamp = "10:29 AM",
                    indicatorState = IndicatorState.Thinking(
                        thinkingRaw = "# Draft 1 Analysis\n\n" +
                            "Looking at the requirements:\n\n" +
                            "1. Need efficient sorting\n" +
                            "2. Should handle duplicates\n\n" +
                            "```kotlin\n" +
                            "fun sort(list: List<Int>): List<Int>\n" +
                            "```",
                        thinkingDurationSeconds = 12,
                    ),
                    modelDisplayName = "Qwen 3 8B",
                )
            )
        )
    }
}

// DRAFT_ONE - Processing
@Preview(name = "Pipeline: DRAFT_ONE Processing")
@Composable
private fun PreviewPipelineDraftOneProcessing() {
    PocketCrewTheme(darkTheme = true) {
        MessageList(
            isPreview =  true,
            messages = listOf(
                ChatMessage(
                    id = 1,
                    chatId = 1L,
                    role = MessageRole.User,
                    content = ContentUi(text = "Write me a function to sort a list"),
                    formattedTimestamp = "10:30 AM",
                ),
                ChatMessage(
                    id = 2,
                    chatId = 1L,
                    role = MessageRole.Assistant,
                    content = ContentUi(text = "", pipelineStep = PipelineStep.DRAFT_ONE),
                    formattedTimestamp = "10:29 AM",
                    indicatorState = IndicatorState.Processing,
                    modelDisplayName = "Qwen 3 8B",
                )
            )
        )
    }
}

// Full chain: DRAFT_ONE -> DRAFT_TWO -> SYNTHESIS -> FINAL (all complete with varied thinkingData)
@Preview(name = "Pipeline: Full Chain with FINAL Thinking")
@Composable
private fun PreviewPipelineFullChainWithFinalThinking() {
    PocketCrewTheme(darkTheme = true) {
        MessageList(
            isPreview =  true,
            messages = listOf(
                ChatMessage(
                    id = 1,
                    chatId = 1L,
                    role = MessageRole.User,
                    content = ContentUi(text = "Write me a function to sort a list"),
                    formattedTimestamp = "10:30 AM",
                ),
                // DRAFT_ONE - Complete with thinking
                ChatMessage(
                    id = 2,
                    chatId = 1L,
                    role = MessageRole.Assistant,
                    content = ContentUi(
                        text = "Here's my first draft approach...",
                        pipelineStep = PipelineStep.DRAFT_ONE
                    ),
                    formattedTimestamp = "10:29 AM",
                    indicatorState = IndicatorState.Complete(
                        thinkingData = ThinkingDataUi(
                            thinkingDurationSeconds = 5,
                            thinkingRaw = "Brainstorming approaches..."
                        )
                    ),
                    modelDisplayName = "Qwen 3 8B",
                ),
                // DRAFT_TWO - Complete with thinking
                ChatMessage(
                    id = 3,
                    chatId = 1L,
                    role = MessageRole.Assistant,
                    content = ContentUi(
                        text = "Refining the approach for efficiency...",
                        pipelineStep = PipelineStep.DRAFT_TWO
                    ),
                    formattedTimestamp = "10:28 AM",
                    indicatorState = IndicatorState.Complete(
                        thinkingData = ThinkingDataUi(
                            thinkingDurationSeconds = 7,
                            thinkingRaw = "Analyzing complexity...\n\n" +
                                "- QuickSort O(n log n) ✓\n" +
                                "- Handle edge cases ✓"
                        )
                    ),
                    modelDisplayName = "Qwen 3 8B",
                ),
                // SYNTHESIS - Complete without thinking
                ChatMessage(
                    id = 4,
                    chatId = 1L,
                    role = MessageRole.Assistant,
                    content = ContentUi(
                        text = "Combining the best ideas...",
                        pipelineStep = PipelineStep.SYNTHESIS
                    ),
                    formattedTimestamp = "10:27 AM",
                    indicatorState = IndicatorState.Complete(thinkingData = null),
                    modelDisplayName = "Qwen 3 8B",
                ),
                // FINAL - Complete WITH thinking data
                ChatMessage(
                    id = 5,
                    chatId = 1L,
                    role = MessageRole.Assistant,
                    content = ContentUi(
                        text = "# Sorted List Function\n\n" +
                            "Here's an efficient implementation:\n\n" +
                            "```kotlin\n" +
                            "fun <T> sort(list: List<T>): List<T> {\n" +
                            "    return list.sorted()\n" +
                            "}\n" +
                            "```\n\n" +
                            "This uses Kotlin's built-in `sorted()` which implements TimSort at O(n log n).",
                        pipelineStep = PipelineStep.FINAL
                    ),
                    formattedTimestamp = "10:26 AM",
                    indicatorState = IndicatorState.Complete(
                        thinkingData = ThinkingDataUi(
                            thinkingDurationSeconds = 3,
                            thinkingRaw = "Final review:\n\n" +
                                "- Used built-in for simplicity\n" +
                                "- Generic type support ✓\n" +
                                "- Clear documentation ✓"
                        )
                    ),
                    modelDisplayName = "Qwen 3 8B",
                )
            )
        )
    }
}

// Full chain: DRAFT_ONE -> DRAFT_TWO -> SYNTHESIS -> FINAL (all complete, FINAL without thinking)
@Preview(name = "Pipeline: Full Chain without FINAL Thinking")
@Composable
private fun PreviewPipelineFullChainNoFinalThinking() {
    PocketCrewTheme(darkTheme = true) {
        MessageList(
            isPreview =  true,
            messages = listOf(
                ChatMessage(
                    id = 1,
                    chatId = 1L,
                    role = MessageRole.User,
                    content = ContentUi(text = "Write me a function to sort a list"),
                    formattedTimestamp = "10:30 AM",
                ),
                // DRAFT_ONE - Complete WITHOUT thinking
                ChatMessage(
                    id = 2,
                    chatId = 1L,
                    role = MessageRole.Assistant,
                    content = ContentUi(
                        text = "First draft approach ready.",
                        pipelineStep = PipelineStep.DRAFT_ONE
                    ),
                    formattedTimestamp = "10:29 AM",
                    indicatorState = IndicatorState.Complete(thinkingData = null),
                    modelDisplayName = "Qwen 3 8B",
                ),
                // DRAFT_TWO - Complete with thinking
                ChatMessage(
                    id = 3,
                    chatId = 1L,
                    role = MessageRole.Assistant,
                    content = ContentUi(
                        text = "Refined for efficiency.",
                        pipelineStep = PipelineStep.DRAFT_TWO
                    ),
                    formattedTimestamp = "10:28 AM",
                    indicatorState = IndicatorState.Complete(
                        thinkingData = ThinkingDataUi(
                            thinkingDurationSeconds = 6,
                            thinkingRaw = "Analyzing performance...\n\n" +
                                "- O(n log n) achieved ✓"
                        )
                    ),
                    modelDisplayName = "Qwen 3 8B",
                ),
                // SYNTHESIS - Complete with thinking
                ChatMessage(
                    id = 4,
                    chatId = 1L,
                    role = MessageRole.Assistant,
                    content = ContentUi(
                        text = "Combined all approaches.",
                        pipelineStep = PipelineStep.SYNTHESIS
                    ),
                    formattedTimestamp = "10:27 AM",
                    indicatorState = IndicatorState.Complete(
                        thinkingData = ThinkingDataUi(
                            thinkingDurationSeconds = 4,
                            thinkingRaw = "Merging drafts..."
                        )
                    ),
                    modelDisplayName = "Qwen 3 8B",
                ),
                // FINAL - Complete WITHOUT thinking data
                ChatMessage(
                    id = 5,
                    chatId = 1L,
                    role = MessageRole.Assistant,
                    content = ContentUi(
                        text = "# Sorted List\n\n" +
                            "```kotlin\n" +
                            "fun <T> sort(list: List<T>): List<T> {\n" +
                            "    return list.sorted()\n" +
                            "}\n" +
                            "```",
                        pipelineStep = PipelineStep.FINAL
                    ),
                    formattedTimestamp = "10:26 AM",
                    indicatorState = IndicatorState.Complete(thinkingData = null),
                    modelDisplayName = "Qwen 3 8B",
                )
            )
        )
    }
}

// Pipeline in progress: Thinking -> Generating -> Complete
@Preview(name = "Pipeline: Various Steps In Progress")
@Composable
private fun PreviewPipelineVariousStepsInProgress() {
    PocketCrewTheme(darkTheme = true) {
        MessageList(
            isPreview =  true,
            messages = listOf(
                ChatMessage(
                    id = 1,
                    chatId = 1L,
                    role = MessageRole.User,
                    content = ContentUi(text = "Explain photosynthesis"),
                    formattedTimestamp = "10:30 AM",
                ),
                // DRAFT_ONE - Thinking
                ChatMessage(
                    id = 2,
                    chatId = 1L,
                    role = MessageRole.Assistant,
                    content = ContentUi(text = "", pipelineStep = PipelineStep.DRAFT_ONE),
                    formattedTimestamp = "10:29 AM",
                    indicatorState = IndicatorState.Thinking(
                        thinkingRaw = "# Draft 1\n\n" +
                            "Photosynthesis is the process...",
                        thinkingDurationSeconds = 8,
                    ),
                    modelDisplayName = "Qwen 3 8B",
                ),
                // DRAFT_TWO - Generating
                ChatMessage(
                    id = 3,
                    chatId = 1L,
                    role = MessageRole.Assistant,
                    content = ContentUi(text = "", pipelineStep = PipelineStep.DRAFT_TWO),
                    formattedTimestamp = "10:28 AM",
                    indicatorState = IndicatorState.Generating(
                        thinkingData = ThinkingDataUi(
                            thinkingDurationSeconds = 5,
                            thinkingRaw = "Analyzing chemical processes..."
                        )
                    ),
                    modelDisplayName = "Qwen 3 8B",
                ),
                // SYNTHESIS - Complete
                ChatMessage(
                    id = 4,
                    chatId = 1L,
                    role = MessageRole.Assistant,
                    content = ContentUi(
                        text = "Synthesis complete.",
                        pipelineStep = PipelineStep.SYNTHESIS
                    ),
                    formattedTimestamp = "10:27 AM",
                    indicatorState = IndicatorState.Complete(thinkingData = null),
                    modelDisplayName = "Qwen 3 8B",
                ),
                // FINAL - Processing
                ChatMessage(
                    id = 5,
                    chatId = 1L,
                    role = MessageRole.Assistant,
                    content = ContentUi(text = "", pipelineStep = PipelineStep.FINAL),
                    formattedTimestamp = "10:26 AM",
                    indicatorState = IndicatorState.Processing,
                    modelDisplayName = "Qwen 3 8B",
                )
            )
        )
    }
}

// Single step: DRAFT_ONE in various states
@Preview(name = "Pipeline: DRAFT_ONE All States")
@Composable
private fun PreviewPipelineDraftOneAllStates() {
    PocketCrewTheme(darkTheme = true) {
        MessageList(
            isPreview =  true,
            messages = listOf(
                ChatMessage(id = 1, chatId = 1L, role = MessageRole.User, content = ContentUi(text = "Task 1"), formattedTimestamp = "10:30 AM"),
                ChatMessage(id = 2, chatId = 1L, role = MessageRole.Assistant, content = ContentUi(text = "", pipelineStep = PipelineStep.DRAFT_ONE), formattedTimestamp = "10:29 AM", indicatorState = IndicatorState.Thinking(thinkingRaw = "Thinking...", thinkingDurationSeconds = 5), modelDisplayName = "Model"),
                ChatMessage(id = 3, chatId = 1L, role = MessageRole.User, content = ContentUi(text = "Task 2"), formattedTimestamp = "10:28 AM"),
                ChatMessage(id = 4, chatId = 1L, role = MessageRole.Assistant, content = ContentUi(text = "", pipelineStep = PipelineStep.DRAFT_ONE), formattedTimestamp = "10:27 AM", indicatorState = IndicatorState.Processing, modelDisplayName = "Model"),
                ChatMessage(id = 5, chatId = 1L, role = MessageRole.User, content = ContentUi(text = "Task 3"), formattedTimestamp = "10:26 AM"),
                ChatMessage(id = 6, chatId = 1L, role = MessageRole.Assistant, content = ContentUi(text = "", pipelineStep = PipelineStep.DRAFT_ONE), formattedTimestamp = "10:25 AM", indicatorState = IndicatorState.Generating(thinkingData = ThinkingDataUi(thinkingDurationSeconds = 3, thinkingRaw = "Generating...")), modelDisplayName = "Model"),
                ChatMessage(id = 7, chatId = 1L, role = MessageRole.User, content = ContentUi(text = "Task 4"), formattedTimestamp = "10:24 AM"),
                ChatMessage(id = 8, chatId = 1L, role = MessageRole.Assistant, content = ContentUi(text = "Draft with thinking.", pipelineStep = PipelineStep.DRAFT_ONE), formattedTimestamp = "10:23 AM", indicatorState = IndicatorState.Complete(thinkingData = ThinkingDataUi(thinkingDurationSeconds = 4, thinkingRaw = "Thought...")), modelDisplayName = "Model"),
                ChatMessage(id = 9, chatId = 1L, role = MessageRole.User, content = ContentUi(text = "Task 5"), formattedTimestamp = "10:22 AM"),
                ChatMessage(id = 10, chatId = 1L, role = MessageRole.Assistant, content = ContentUi(text = "Draft no thinking.", pipelineStep = PipelineStep.DRAFT_ONE), formattedTimestamp = "10:21 AM", indicatorState = IndicatorState.Complete(thinkingData = null), modelDisplayName = "Model")
            )
        )
    }
}

// FINAL step states
@Preview(name = "Pipeline: FINAL All States")
@Composable
private fun PreviewPipelineFinalAllStates() {
    PocketCrewTheme(darkTheme = true) {
        MessageList(
            isPreview =  true,
            messages = listOf(
                ChatMessage(id = 1, chatId = 1L, role = MessageRole.User, content = ContentUi(text = "Q1"), formattedTimestamp = "10:30 AM"),
                ChatMessage(id = 2, chatId = 1L, role = MessageRole.Assistant, content = ContentUi(text = "", pipelineStep = PipelineStep.FINAL), formattedTimestamp = "10:29 AM", indicatorState = IndicatorState.Thinking(thinkingRaw = "Final thinking...", thinkingDurationSeconds = 10), modelDisplayName = "Model"),
                ChatMessage(id = 3, chatId = 1L, role = MessageRole.User, content = ContentUi(text = "Q2"), formattedTimestamp = "10:28 AM"),
                ChatMessage(id = 4, chatId = 1L, role = MessageRole.Assistant, content = ContentUi(text = "", pipelineStep = PipelineStep.FINAL), formattedTimestamp = "10:27 AM", indicatorState = IndicatorState.Processing, modelDisplayName = "Model"),
                ChatMessage(id = 5, chatId = 1L, role = MessageRole.User, content = ContentUi(text = "Q3"), formattedTimestamp = "10:26 AM"),
                ChatMessage(id = 6, chatId = 1L, role = MessageRole.Assistant, content = ContentUi(text = "", pipelineStep = PipelineStep.FINAL), formattedTimestamp = "10:25 AM", indicatorState = IndicatorState.Generating(thinkingData = ThinkingDataUi(thinkingDurationSeconds = 5, thinkingRaw = "Final synthesis...")), modelDisplayName = "Model"),
                ChatMessage(id = 7, chatId = 1L, role = MessageRole.User, content = ContentUi(text = "Q4"), formattedTimestamp = "10:24 AM"),
                ChatMessage(id = 8, chatId = 1L, role = MessageRole.Assistant, content = ContentUi(text = "Final WITH thinking.", pipelineStep = PipelineStep.FINAL), formattedTimestamp = "10:23 AM", indicatorState = IndicatorState.Complete(thinkingData = ThinkingDataUi(thinkingDurationSeconds = 6, thinkingRaw = "Final review...")), modelDisplayName = "Model"),
                ChatMessage(id = 9, chatId = 1L, role = MessageRole.User, content = ContentUi(text = "Q5"), formattedTimestamp = "10:22 AM"),
                ChatMessage(id = 10, chatId = 1L, role = MessageRole.Assistant, content = ContentUi(text = "Final no thinking.", pipelineStep = PipelineStep.FINAL), formattedTimestamp = "10:21 AM", indicatorState = IndicatorState.Complete(thinkingData = null), modelDisplayName = "Model")
            )
        )
    }
}
