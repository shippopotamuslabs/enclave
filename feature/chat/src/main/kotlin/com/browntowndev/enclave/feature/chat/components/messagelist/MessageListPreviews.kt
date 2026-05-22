package com.browntowndev.enclave.feature.chat.components.messagelist

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.inference.PipelineStep
import com.browntowndev.enclave.feature.chat.ChatMessage
import com.browntowndev.enclave.feature.chat.ChatModeUi
import com.browntowndev.enclave.feature.chat.ContentUi
import com.browntowndev.enclave.feature.chat.IndicatorState
import com.browntowndev.enclave.feature.chat.MessageRole
import com.browntowndev.enclave.feature.chat.ThinkingDataUi
import com.browntowndev.enclave.feature.chat.ToolCallBannerKind
import com.browntowndev.enclave.feature.chat.ToolCallBannerUi
import com.browntowndev.enclave.feature.chat.fakeLongMessages

@Preview
@Composable
internal fun PreviewMessageListEmpty() {
    EnclaveTheme {
        MessageList(messages = emptyList(), hasActiveIndicator = false, isPreview = true)
    }
}

@Preview
@Composable
internal fun PreviewMessageListLong() {
    EnclaveTheme(darkTheme = true) {
        MessageList(messages = fakeLongMessages, hasActiveIndicator = true, isPreview = true)
    }
}

@Preview
@Composable
internal fun PreviewMessageListThinking() {
    EnclaveTheme(darkTheme = true) {
        MessageList(
            isPreview = true,
            hasActiveIndicator = true,
            chatMode = ChatModeUi.THINKING,
            messages =
                listOf(
                    ChatMessage(
                        id = MessageId("1"),
                        chatId = ChatId("1"),
                        role = MessageRole.User,
                        content = ContentUi(text = "Explain quantum computing"),
                        formattedTimestamp = "10:30 AM",
                    ),
                    ChatMessage(
                        id = MessageId("2"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content = ContentUi(text = ""),
                        formattedTimestamp = "10:29 AM",
                        indicatorState =
                            IndicatorState.Thinking(
                                thinkingRaw =
                                    "# Quantum Computing Analysis\n\n" +
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
                    ),
                ),
        )
    }
}

@Preview
@Composable
internal fun PreviewMessageListProcessing() {
    EnclaveTheme(darkTheme = true) {
        MessageList(
            isPreview = true,
            hasActiveIndicator = true,
            messages =
                listOf(
                    ChatMessage(
                        id = MessageId("1"),
                        chatId = ChatId("1"),
                        role = MessageRole.User,
                        content = ContentUi(text = "Hello?"),
                        formattedTimestamp = "10:30 AM",
                    ),
                    ChatMessage(
                        id = MessageId("2"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content = ContentUi(text = ""),
                        formattedTimestamp = "10:29 AM",
                        indicatorState = IndicatorState.Processing,
                        modelDisplayName = "Qwen 3 8B",
                    ),
                ),
        )
    }
}

@Preview
@Composable
internal fun PreviewMessageListGenerating() {
    EnclaveTheme(darkTheme = true) {
        MessageList(
            isPreview = true,
            hasActiveIndicator = true,
            chatMode = ChatModeUi.FAST,
            messages =
                listOf(
                    ChatMessage(
                        id = MessageId("1"),
                        chatId = ChatId("1"),
                        role = MessageRole.User,
                        content = ContentUi(text = "What is Docker?"),
                        formattedTimestamp = "10:30 AM",
                    ),
                    ChatMessage(
                        id = MessageId("2"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content = ContentUi(text = ""),
                        formattedTimestamp = "10:29 AM",
                        indicatorState =
                            IndicatorState.Generating(
                                thinkingData =
                                    ThinkingDataUi(
                                        thinkingDurationSeconds = 5,
                                        thinkingRaw =
                                            "Docker is a containerization platform. Let me explain:\n\n" +
                                                "- **Containers** vs VMs\n" +
                                                "- **Dockerfile** for images\n" +
                                                "- **docker-compose** for multi-container apps",
                                    ),
                            ),
                        modelDisplayName = "Qwen 3 8B",
                    ),
                ),
        )
    }
}

// DRAFT_ONE - Generating with ThinkingData
@Preview(name = "Pipeline: DRAFT_ONE Generating with Thinking")
@Composable
internal fun PreviewPipelineDraftOneGeneratingWithThinking() {
    EnclaveTheme(darkTheme = true) {
        MessageList(
            isPreview = true,
            hasActiveIndicator = true,
            chatMode = ChatModeUi.COUNCIL,
            messages =
                listOf(
                    ChatMessage(
                        id = MessageId("1"),
                        chatId = ChatId("1"),
                        role = MessageRole.User,
                        content = ContentUi(text = "Write me a function to sort a list"),
                        formattedTimestamp = "10:30 AM",
                    ),
                    ChatMessage(
                        id = MessageId("2"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content = ContentUi(text = "", pipelineStep = PipelineStep.DRAFT_ONE),
                        formattedTimestamp = "10:29 AM",
                        indicatorState =
                            IndicatorState.Generating(
                                thinkingData =
                                    ThinkingDataUi(
                                        thinkingDurationSeconds = 4,
                                        thinkingRaw =
                                            "Analyzing sorting algorithms...\n\n" +
                                                "- Quick sort seems optimal here\n" +
                                                "- Need to handle edge cases...",
                                    ),
                            ),
                        modelDisplayName = "Qwen 3 8B",
                    ),
                ),
        )
    }
}

// DRAFT_ONE - Thinking (streaming)
@Preview(name = "Pipeline: DRAFT_ONE Thinking")
@Composable
internal fun PreviewPipelineDraftOneThinking() {
    EnclaveTheme(darkTheme = true) {
        MessageList(
            isPreview = true,
            hasActiveIndicator = true,
            messages =
                listOf(
                    ChatMessage(
                        id = MessageId("1"),
                        chatId = ChatId("1"),
                        role = MessageRole.User,
                        content = ContentUi(text = "Write me a function to sort a list"),
                        formattedTimestamp = "10:30 AM",
                    ),
                    ChatMessage(
                        id = MessageId("2"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content = ContentUi(text = "", pipelineStep = PipelineStep.DRAFT_ONE),
                        formattedTimestamp = "10:29 AM",
                        indicatorState =
                            IndicatorState.Thinking(
                                thinkingRaw =
                                    "# Draft 1 Analysis\n\n" +
                                        "Looking at the requirements:\n\n" +
                                        "1. Need efficient sorting\n" +
                                        "2. Should handle duplicates\n\n" +
                                        "```kotlin\n" +
                                        "fun sort(list: List<Int>): List<Int>\n" +
                                        "```",
                                thinkingDurationSeconds = 12,
                            ),
                        modelDisplayName = "Qwen 3 8B",
                    ),
                ),
        )
    }
}

// DRAFT_ONE - Processing
@Preview(name = "Pipeline: DRAFT_ONE Processing")
@Composable
internal fun PreviewPipelineDraftOneProcessing() {
    EnclaveTheme(darkTheme = true) {
        MessageList(
            isPreview = true,
            hasActiveIndicator = true,
            messages =
                listOf(
                    ChatMessage(
                        id = MessageId("1"),
                        chatId = ChatId("1"),
                        role = MessageRole.User,
                        content = ContentUi(text = "Write me a function to sort a list"),
                        formattedTimestamp = "10:30 AM",
                    ),
                    ChatMessage(
                        id = MessageId("2"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content = ContentUi(text = "", pipelineStep = PipelineStep.DRAFT_ONE),
                        formattedTimestamp = "10:29 AM",
                        indicatorState = IndicatorState.Processing,
                        modelDisplayName = "Qwen 3 8B",
                    ),
                ),
        )
    }
}

// Full chain: DRAFT_ONE -> DRAFT_TWO -> SYNTHESIS -> FINAL (all complete with varied thinkingData)
@Preview(name = "Pipeline: Full Chain with FINAL Thinking")
@Composable
internal fun PreviewPipelineFullChainWithFinalThinking() {
    EnclaveTheme(darkTheme = true) {
        MessageList(
            isPreview = true,
            hasActiveIndicator = true,
            messages =
                listOf(
                    ChatMessage(
                        id = MessageId("1"),
                        chatId = ChatId("1"),
                        role = MessageRole.User,
                        content = ContentUi(text = "Write me a function to sort a list"),
                        formattedTimestamp = "10:30 AM",
                    ),
                    // DRAFT_ONE - Complete with thinking
                    ChatMessage(
                        id = MessageId("2"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content =
                            ContentUi(
                                text = "Here's my first draft approach...",
                                pipelineStep = PipelineStep.DRAFT_ONE,
                            ),
                        formattedTimestamp = "10:29 AM",
                        indicatorState =
                            IndicatorState.Complete(
                                thinkingData =
                                    ThinkingDataUi(
                                        thinkingDurationSeconds = 5,
                                        thinkingRaw = "Brainstorming approaches...",
                                    ),
                            ),
                        modelDisplayName = "Qwen 3 8B",
                    ),
                    // DRAFT_TWO - Complete with thinking
                    ChatMessage(
                        id = MessageId("3"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content =
                            ContentUi(
                                text = "Refining the approach for efficiency...",
                                pipelineStep = PipelineStep.DRAFT_TWO,
                            ),
                        formattedTimestamp = "10:28 AM",
                        indicatorState =
                            IndicatorState.Complete(
                                thinkingData =
                                    ThinkingDataUi(
                                        thinkingDurationSeconds = 7,
                                        thinkingRaw =
                                            "Analyzing complexity...\n\n" +
                                                "- QuickSort O(n log n) ✓\n" +
                                                "- Handle edge cases ✓",
                                    ),
                            ),
                        modelDisplayName = "Qwen 3 8B",
                    ),
                    // SYNTHESIS - Complete without thinking
                    ChatMessage(
                        id = MessageId("4"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content =
                            ContentUi(
                                text = "Combining the best ideas...",
                                pipelineStep = PipelineStep.SYNTHESIS,
                            ),
                        formattedTimestamp = "10:27 AM",
                        indicatorState = IndicatorState.Complete(thinkingData = null),
                        modelDisplayName = "Qwen 3 8B",
                    ),
                    // FINAL - Complete WITH thinking data
                    ChatMessage(
                        id = MessageId("5"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content =
                            ContentUi(
                                text =
                                    "# Sorted List Function\n\n" +
                                        "Here's an efficient implementation:\n\n" +
                                        "```kotlin\n" +
                                        "fun <T> sort(list: List<T>): List<T> {\n" +
                                        "    return list.sorted()\n" +
                                        "}\n" +
                                        "```\n\n" +
                                        "This uses Kotlin's built-in `sorted()` which implements TimSort at O(n log n).",
                                pipelineStep = PipelineStep.FINAL,
                            ),
                        formattedTimestamp = "10:26 AM",
                        indicatorState =
                            IndicatorState.Complete(
                                thinkingData =
                                    ThinkingDataUi(
                                        thinkingDurationSeconds = 3,
                                        thinkingRaw =
                                            "Final review:\n\n" +
                                                "- Used built-in for simplicity\n" +
                                                "- Generic type support ✓\n" +
                                                "- Clear documentation ✓",
                                    ),
                            ),
                        modelDisplayName = "Qwen 3 8B",
                    ),
                ),
        )
    }
}

// Full chain: DRAFT_ONE -> DRAFT_TWO -> SYNTHESIS -> FINAL (all complete, FINAL without thinking)
@Preview(name = "Pipeline: Full Chain without FINAL Thinking")
@Composable
internal fun PreviewPipelineFullChainNoFinalThinking() {
    EnclaveTheme(darkTheme = true) {
        MessageList(
            isPreview = true,
            hasActiveIndicator = true,
            messages =
                listOf(
                    ChatMessage(
                        id = MessageId("1"),
                        chatId = ChatId("1"),
                        role = MessageRole.User,
                        content = ContentUi(text = "Write me a function to sort a list"),
                        formattedTimestamp = "10:30 AM",
                    ),
                    // DRAFT_ONE - Complete WITHOUT thinking
                    ChatMessage(
                        id = MessageId("2"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content =
                            ContentUi(
                                text = "First draft approach ready.",
                                pipelineStep = PipelineStep.DRAFT_ONE,
                            ),
                        formattedTimestamp = "10:29 AM",
                        indicatorState = IndicatorState.Complete(thinkingData = null),
                        modelDisplayName = "Qwen 3 8B",
                    ),
                    // DRAFT_TWO - Complete with thinking
                    ChatMessage(
                        id = MessageId("3"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content =
                            ContentUi(
                                text = "Refined for efficiency.",
                                pipelineStep = PipelineStep.DRAFT_TWO,
                            ),
                        formattedTimestamp = "10:28 AM",
                        indicatorState =
                            IndicatorState.Complete(
                                thinkingData =
                                    ThinkingDataUi(
                                        thinkingDurationSeconds = 6,
                                        thinkingRaw =
                                            "Analyzing performance...\n\n" +
                                                "- O(n log n) achieved ✓",
                                    ),
                            ),
                        modelDisplayName = "Qwen 3 8B",
                    ),
                    // SYNTHESIS - Complete with thinking
                    ChatMessage(
                        id = MessageId("4"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content =
                            ContentUi(
                                text = "Combined all approaches.",
                                pipelineStep = PipelineStep.SYNTHESIS,
                            ),
                        formattedTimestamp = "10:27 AM",
                        indicatorState =
                            IndicatorState.Complete(
                                thinkingData =
                                    ThinkingDataUi(
                                        thinkingDurationSeconds = 4,
                                        thinkingRaw = "Merging drafts...",
                                    ),
                            ),
                        modelDisplayName = "Qwen 3 8B",
                    ),
                    // FINAL - Complete WITHOUT thinking data
                    ChatMessage(
                        id = MessageId("5"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content =
                            ContentUi(
                                text =
                                    "# Sorted List\n\n" +
                                        "```kotlin\n" +
                                        "fun <T> sort(list: List<T>): List<T> {\n" +
                                        "    return list.sorted()\n" +
                                        "}\n" +
                                        "```",
                                pipelineStep = PipelineStep.FINAL,
                            ),
                        formattedTimestamp = "10:26 AM",
                        indicatorState = IndicatorState.Complete(thinkingData = null),
                        modelDisplayName = "Qwen 3 8B",
                    ),
                ),
        )
    }
}

// Pipeline in progress: Thinking -> Generating -> Complete
@Preview(name = "Pipeline: Various Steps In Progress")
@Composable
internal fun PreviewPipelineVariousStepsInProgress() {
    EnclaveTheme(darkTheme = true) {
        MessageList(
            isPreview = true,
            hasActiveIndicator = true,
            messages =
                listOf(
                    ChatMessage(
                        id = MessageId("1"),
                        chatId = ChatId("1"),
                        role = MessageRole.User,
                        content = ContentUi(text = "Explain photosynthesis"),
                        formattedTimestamp = "10:30 AM",
                    ),
                    // DRAFT_ONE - Thinking
                    ChatMessage(
                        id = MessageId("2"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content = ContentUi(text = "", pipelineStep = PipelineStep.DRAFT_ONE),
                        formattedTimestamp = "10:29 AM",
                        indicatorState =
                            IndicatorState.Thinking(
                                thinkingRaw =
                                    "# Draft 1\n\n" +
                                        "Photosynthesis is the process...",
                                thinkingDurationSeconds = 8,
                            ),
                        modelDisplayName = "Qwen 3 8B",
                    ),
                    // DRAFT_TWO - Generating
                    ChatMessage(
                        id = MessageId("3"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content = ContentUi(text = "", pipelineStep = PipelineStep.DRAFT_TWO),
                        formattedTimestamp = "10:28 AM",
                        indicatorState =
                            IndicatorState.Generating(
                                thinkingData =
                                    ThinkingDataUi(
                                        thinkingDurationSeconds = 5,
                                        thinkingRaw = "Analyzing chemical processes...",
                                    ),
                            ),
                        modelDisplayName = "Qwen 3 8B",
                    ),
                    // SYNTHESIS - Complete
                    ChatMessage(
                        id = MessageId("4"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content =
                            ContentUi(
                                text = "Synthesis complete.",
                                pipelineStep = PipelineStep.SYNTHESIS,
                            ),
                        formattedTimestamp = "10:27 AM",
                        indicatorState = IndicatorState.Complete(thinkingData = null),
                        modelDisplayName = "Qwen 3 8B",
                    ),
                    // FINAL - Processing
                    ChatMessage(
                        id = MessageId("5"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content = ContentUi(text = "", pipelineStep = PipelineStep.FINAL),
                        formattedTimestamp = "10:26 AM",
                        indicatorState = IndicatorState.Processing,
                        modelDisplayName = "Qwen 3 8B",
                    ),
                ),
        )
    }
}

// Single step: DRAFT_ONE in various states
@Preview(name = "Pipeline: DRAFT_ONE All States")
@Composable
internal fun PreviewPipelineDraftOneAllStates() {
    EnclaveTheme(darkTheme = true) {
        MessageList(
            isPreview = true,
            hasActiveIndicator = true,
            messages =
                listOf(
                    ChatMessage(
                        id = MessageId("1"),
                        chatId = ChatId("1"),
                        role = MessageRole.User,
                        content = ContentUi(text = "Task 1"),
                        formattedTimestamp = "10:30 AM",
                    ),
                    ChatMessage(
                        id = MessageId("2"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content = ContentUi(text = "", pipelineStep = PipelineStep.DRAFT_ONE),
                        formattedTimestamp = "10:29 AM",
                        indicatorState = IndicatorState.Thinking(thinkingRaw = "Thinking...", thinkingDurationSeconds = 5),
                        modelDisplayName = "Model",
                    ),
                    ChatMessage(
                        id = MessageId("3"),
                        chatId = ChatId("1"),
                        role = MessageRole.User,
                        content = ContentUi(text = "Task 2"),
                        formattedTimestamp = "10:28 AM",
                    ),
                    ChatMessage(
                        id = MessageId("4"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content = ContentUi(text = "", pipelineStep = PipelineStep.DRAFT_ONE),
                        formattedTimestamp = "10:27 AM",
                        indicatorState = IndicatorState.Processing,
                        modelDisplayName = "Model",
                    ),
                    ChatMessage(
                        id = MessageId("5"),
                        chatId = ChatId("1"),
                        role = MessageRole.User,
                        content = ContentUi(text = "Task 3"),
                        formattedTimestamp = "10:26 AM",
                    ),
                    ChatMessage(
                        id = MessageId("6"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content = ContentUi(text = "", pipelineStep = PipelineStep.DRAFT_ONE),
                        formattedTimestamp = "10:25 AM",
                        indicatorState =
                            IndicatorState.Generating(
                                thinkingData = ThinkingDataUi(thinkingDurationSeconds = 3, thinkingRaw = "Generating..."),
                            ),
                        modelDisplayName = "Model",
                    ),
                    ChatMessage(
                        id = MessageId("7"),
                        chatId = ChatId("1"),
                        role = MessageRole.User,
                        content = ContentUi(text = "Task 4"),
                        formattedTimestamp = "10:24 AM",
                    ),
                    ChatMessage(
                        id = MessageId("8"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content = ContentUi(text = "Draft with thinking.", pipelineStep = PipelineStep.DRAFT_ONE),
                        formattedTimestamp = "10:23 AM",
                        indicatorState =
                            IndicatorState.Complete(
                                thinkingData = ThinkingDataUi(thinkingDurationSeconds = 4, thinkingRaw = "Thought..."),
                            ),
                        modelDisplayName = "Model",
                    ),
                    ChatMessage(
                        id = MessageId("9"),
                        chatId = ChatId("1"),
                        role = MessageRole.User,
                        content = ContentUi(text = "Task 5"),
                        formattedTimestamp = "10:22 AM",
                    ),
                    ChatMessage(
                        id = MessageId("10"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content = ContentUi(text = "Draft no thinking.", pipelineStep = PipelineStep.DRAFT_ONE),
                        formattedTimestamp = "10:21 AM",
                        indicatorState = IndicatorState.Complete(thinkingData = null),
                        modelDisplayName = "Model",
                    ),
                ),
        )
    }
}

// FINAL step states
@Preview(name = "Pipeline: FINAL All States")
@Composable
internal fun PreviewPipelineFinalAllStates() {
    EnclaveTheme(darkTheme = true) {
        MessageList(
            isPreview = true,
            hasActiveIndicator = true,
            messages =
                listOf(
                    ChatMessage(
                        id = MessageId("1"),
                        chatId = ChatId("1"),
                        role = MessageRole.User,
                        content = ContentUi(text = "Q1"),
                        formattedTimestamp = "10:30 AM",
                    ),
                    ChatMessage(
                        id = MessageId("2"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content = ContentUi(text = "", pipelineStep = PipelineStep.FINAL),
                        formattedTimestamp = "10:29 AM",
                        indicatorState = IndicatorState.Thinking(thinkingRaw = "Final thinking...", thinkingDurationSeconds = 10),
                        modelDisplayName = "Model",
                    ),
                    ChatMessage(
                        id = MessageId("3"),
                        chatId = ChatId("1"),
                        role = MessageRole.User,
                        content = ContentUi(text = "Q2"),
                        formattedTimestamp = "10:28 AM",
                    ),
                    ChatMessage(
                        id = MessageId("4"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content = ContentUi(text = "", pipelineStep = PipelineStep.FINAL),
                        formattedTimestamp = "10:27 AM",
                        indicatorState = IndicatorState.Processing,
                        modelDisplayName = "Model",
                    ),
                    ChatMessage(
                        id = MessageId("5"),
                        chatId = ChatId("1"),
                        role = MessageRole.User,
                        content = ContentUi(text = "Q3"),
                        formattedTimestamp = "10:26 AM",
                    ),
                    ChatMessage(
                        id = MessageId("6"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content = ContentUi(text = "", pipelineStep = PipelineStep.FINAL),
                        formattedTimestamp = "10:25 AM",
                        indicatorState =
                            IndicatorState.Generating(
                                thinkingData = ThinkingDataUi(thinkingDurationSeconds = 5, thinkingRaw = "Final synthesis..."),
                            ),
                        modelDisplayName = "Model",
                    ),
                    ChatMessage(
                        id = MessageId("7"),
                        chatId = ChatId("1"),
                        role = MessageRole.User,
                        content = ContentUi(text = "Q4"),
                        formattedTimestamp = "10:24 AM",
                    ),
                    ChatMessage(
                        id = MessageId("8"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content = ContentUi(text = "Final WITH thinking.", pipelineStep = PipelineStep.FINAL),
                        formattedTimestamp = "10:23 AM",
                        indicatorState =
                            IndicatorState.Complete(
                                thinkingData = ThinkingDataUi(thinkingDurationSeconds = 6, thinkingRaw = "Final review..."),
                            ),
                        modelDisplayName = "Model",
                    ),
                    ChatMessage(
                        id = MessageId("9"),
                        chatId = ChatId("1"),
                        role = MessageRole.User,
                        content = ContentUi(text = "Q5"),
                        formattedTimestamp = "10:22 AM",
                    ),
                    ChatMessage(
                        id = MessageId("10"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content = ContentUi(text = "Final no thinking.", pipelineStep = PipelineStep.FINAL),
                        formattedTimestamp = "10:21 AM",
                        indicatorState = IndicatorState.Complete(thinkingData = null),
                        modelDisplayName = "Model",
                    ),
                ),
        )
    }
}

@Preview(name = "Message List with Tool Call Banner")
@Composable
internal fun PreviewMessageListWithToolCall() {
    EnclaveTheme(darkTheme = true) {
        val messageId = MessageId("active_msg")
        MessageList(
            isPreview = true,
            hasActiveIndicator = true,
            activeIndicatorMessageId = messageId,
            activeToolCallBanner =
                ToolCallBannerUi(
                    kind = ToolCallBannerKind.SEARCH,
                    label = "Searching for quantum computing basics...",
                ),
            messages =
                listOf(
                    ChatMessage(
                        id = MessageId("1"),
                        chatId = ChatId("1"),
                        role = MessageRole.User,
                        content = ContentUi(text = "What is quantum computing?"),
                        formattedTimestamp = "10:30 AM",
                    ),
                    ChatMessage(
                        id = MessageId("2"),
                        chatId = ChatId("1"),
                        role = MessageRole.Assistant,
                        content = ContentUi(text = ""),
                        formattedTimestamp = "10:31 AM",
                        indicatorState = IndicatorState.Processing,
                        modelDisplayName = "Qwen 3 8B",
                    ),
                ),
        )
    }
}
