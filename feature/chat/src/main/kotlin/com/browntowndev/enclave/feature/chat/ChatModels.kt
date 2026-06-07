package com.browntowndev.enclave.feature.chat

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.feature.chat.stats.ChatStatsSheetUiState
import com.browntowndev.enclave.feature.chat.stats.ResponseStatsSheetUiState

enum class ChatModeUi(
    @param:StringRes val displayNameRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    FAST(R.string.mode_fast, R.drawable.bolt),
    THINKING(R.string.mode_thinking, R.drawable.lightbulb),
    COUNCIL(R.string.mode_council, R.drawable.merge),
    ;

    fun getDisplayName(context: Context): String = context.getString(displayNameRes)

    fun getIcon(context: Context) = ContextCompat.getDrawable(context, iconRes)
}

enum class MessageRole {
    User,
    Assistant,
}

enum class ToolCallBannerKind {
    SEARCH,
    EXTRACT,
    IMAGE,
    MEMORY,
    ARTIFACT,
    X_SEARCH,
    MEDIA,
}

enum class ResponseHapticPhase {
    None,
    ThinkingPulse,
    GeneratingBuzz,
}

@Immutable
data class ToolCallBannerUi(
    val kind: ToolCallBannerKind,
    val label: String,
    val webSearchSources: List<com.browntowndev.enclave.domain.model.chat.WebSearchSource> = emptyList(),
)

@Immutable
sealed class TranscriptSegmentUi {
    abstract val ordinal: Int

    @Immutable
    data class ThinkingBlock(
        override val ordinal: Int,
        val text: String,
        val durationSeconds: Long?,
        val thinkingStartTimeMs: Long? = null,
        val isStreaming: Boolean = false,
    ) : TranscriptSegmentUi()

    @Immutable
    data class AnswerBlock(
        override val ordinal: Int,
        val text: String,
        val isStreaming: Boolean = false,
    ) : TranscriptSegmentUi()
}

@Immutable
data class ChatMessage(
    val id: MessageId,
    val chatId: ChatId,
    val role: MessageRole,
    val content: ContentUi,
    val formattedTimestamp: String,
    val indicatorState: IndicatorState? = null,
    val modelDisplayName: String = "",
    val webSearchSources: List<com.browntowndev.enclave.domain.model.chat.WebSearchSource> = emptyList(),
    val transcriptSegments: List<TranscriptSegmentUi> = emptyList(),
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val speechState: SpeechState = SpeechState.Idle,
    val selectedImageUris: List<String> = emptyList(),
    val selectedFileName: String? = null,
    val isPhotoAttachmentEnabled: Boolean = false,
    val photoAttachmentMaxItems: Int = 1,
    val photoAttachmentDisabledReason: String? = null,
    val selectedMode: ChatModeUi = ChatModeUi.FAST,
    val isGlobalInferenceBlocked: Boolean = false,
    val shieldReason: String? = null,
    val hapticResponse: Boolean = false,
    val responseHapticPhase: ResponseHapticPhase = ResponseHapticPhase.None,
    val responseHapticPulseKey: Long = 0L,
    val chatId: ChatId? = null,
    val isGenerating: Boolean = false,
    val canStop: Boolean = true,
    val hasActiveIndicator: Boolean = false,
    val activeToolCallBanner: ToolCallBannerUi? = null,
    val activeIndicatorMessageId: MessageId? = null,
    val backgroundInferenceEnabled: Boolean = true,
    val hasTtsProviderAssigned: Boolean = false,
    val liveVoiceState: LiveVoiceUiState = LiveVoiceUiState.Idle,
    val selectedModeModelName: String? = null,
    val modeModelNames: Map<ChatModeUi, String?> = emptyMap(),
    val hasLiveVoiceProviderAssigned: Boolean = false,
    val isPlayingTts: Boolean = false,
    val isLoadingTts: Boolean = false,
    val isPausedTts: Boolean = false,
    val shareState: ShareState = ShareState.Idle,
    val isReadOnly: Boolean = false,
    val artifactExportSheet: ArtifactExportSheetState = ArtifactExportSheetState.Hidden,
    val responseStatsSheet: ResponseStatsSheetUiState = ResponseStatsSheetUiState(),
    val chatStatsSheet: ChatStatsSheetUiState = ChatStatsSheetUiState(),
)

sealed interface ShareState {
    data object Idle : ShareState

    data object ShowingSheet : ShareState

    data object Loading : ShareState

    data class Success(
        val shareUrl: String,
    ) : ShareState

    data class Error(
        val message: String,
    ) : ShareState
}

sealed interface ShareUiEvent {
    data class OpenShareSheet(
        val shareUrl: String,
    ) : ShareUiEvent

    data class ImportedChat(
        val chatId: com.browntowndev.enclave.domain.model.chat.ChatId,
    ) : ShareUiEvent
}

/**
 * Used in previews - various indicator states.
 */
val fakeLongMessages =
    listOf(
        // Message with Processing indicator
        ChatMessage(
            id = MessageId("1"),
            chatId = ChatId("1"),
            role = MessageRole.Assistant,
            content = ContentUi(text = ""),
            formattedTimestamp = "10:30 AM",
            indicatorState = IndicatorState.Processing,
            modelDisplayName = "Qwen 3 8B",
        ),
        // Message with Thinking indicator (streaming)
        ChatMessage(
            id = MessageId("2"),
            chatId = ChatId("1"),
            role = MessageRole.Assistant,
            content = ContentUi(text = ""),
            formattedTimestamp = "10:29 AM",
            indicatorState =
                IndicatorState.Thinking(
                    thinkingRaw =
                        "# Analysis in Progress\n\n" +
                            "Let me break down this problem step by step:\n\n" +
                            "1. First, I need to understand the core requirements\n" +
                            "2. Then analyze the existing codebase structure\n" +
                            "3. Finally, design an optimal solution\n\n" +
                            "```kotlin\n" +
                            "fun solve() {\n" +
                            "    val problem = analyze()\n" +
                            "    val solution = design(problem)\n" +
                            "    return implement(solution)\n" +
                            "}\n" +
                            "```",
                    thinkingDurationSeconds = 42,
                ),
            modelDisplayName = "Qwen 3 8B",
        ),
        // User message
        ChatMessage(
            id = MessageId("3"),
            chatId = ChatId("1"),
            role = MessageRole.User,
            content = ContentUi(text = "Can you explain how coroutines work in Kotlin?"),
            formattedTimestamp = "10:28 AM",
        ),
        // Message with Generating indicator (with thinking data)
        ChatMessage(
            id = MessageId("4"),
            chatId = ChatId("1"),
            role = MessageRole.Assistant,
            content = ContentUi(text = "Message with Generating indicator (with thinking data)"),
            formattedTimestamp = "10:27 AM",
            indicatorState =
                IndicatorState.Generating(
                    thinkingData =
                        ThinkingDataUi(
                            thinkingDurationSeconds = 15,
                            thinkingRaw =
                                "Kotlin coroutines are like lightweight threads. They allow you to write " +
                                    "asynchronous code in a sequential manner. Key concepts: **suspend functions**, " +
                                    "**Dispatchers**, and **Scopes**.\n\n- `launch` for fire-and-forget\n" +
                                    "- `async` for results\n- `flow` for streams",
                        ),
                ),
            modelDisplayName = "Qwen 3 8B",
        ),
        // Message with Complete indicator (with thinking data)
        ChatMessage(
            id = MessageId("5"),
            chatId = ChatId("1"),
            role = MessageRole.Assistant,
            content = ContentUi(text = "Message with Complete indicator (with thinking data)"),
            formattedTimestamp = "10:26 AM",
            indicatorState =
                IndicatorState.Complete(
                    thinkingData =
                        ThinkingDataUi(
                            thinkingDurationSeconds = 8,
                            thinkingRaw =
                                "Let me think about this more carefully...\n\n" +
                                    "## Key Points\n\n" +
                                    "1. **Simplicity** - Compose reduces boilerplate\n" +
                                    "2. **Declarative** - Describe UI, not steps\n" +
                                    "3. **Reactive** - Automatic recomposition",
                        ),
                ),
            modelDisplayName = "Qwen 3 8B",
        ),
        // Completed message with response
        ChatMessage(
            id = MessageId("6"),
            chatId = ChatId("1"),
            role = MessageRole.Assistant,
            content =
                ContentUi(
                    text =
                        "Jetpack Compose is Android's modern UI toolkit. It replaces XML layouts with declarative Kotlin code.\n\n" +
                            "**Key Benefits:**\n" +
                            "- Less boilerplate\n" +
                            "- Declarative syntax\n" +
                            "- Built-in state management\n\n" +
                            "```kotlin\n" +
                            "@Composable\n" +
                            "fun Greeting(name: String) {\n" +
                            "    Text(\"Hello, \$name!\")\n" +
                            "}\n" +
                            "```",
                ),
            formattedTimestamp = "10:25 AM",
            indicatorState = IndicatorState.Complete(thinkingData = null),
            modelDisplayName = "Qwen 3 8B",
        ),
        // User message
        ChatMessage(
            id = MessageId("7"),
            chatId = ChatId("1"),
            role = MessageRole.User,
            content = ContentUi(text = "Show me a code example"),
            formattedTimestamp = "10:24 AM",
        ),
        // Simple completed message
        ChatMessage(
            id = MessageId("8"),
            chatId = ChatId("1"),
            role = MessageRole.Assistant,
            content =
                ContentUi(
                    text = "Here's a complete example in Kotlin:\n\n```kotlin\nfun main() {\n    println(\"Hello, World!\")\n}\n```",
                ),
            formattedTimestamp = "10:23 AM",
            modelDisplayName = "Qwen 3 8B",
        ),
    )

/**
 * Focused sample for the Message List typography preview: user sans, Thought-for chrome, serif markdown body.
 */
val typographyPreviewMessages =
    listOf(
        ChatMessage(
            id = MessageId("typo-user"),
            chatId = ChatId("1"),
            role = MessageRole.User,
            content =
                ContentUi(
                    text =
                        "Explain how structured concurrency keeps cancellation safe when many " +
                            "child jobs are running.",
                ),
            formattedTimestamp = "10:31 AM",
        ),
        ChatMessage(
            id = MessageId("typo-assistant"),
            chatId = ChatId("1"),
            role = MessageRole.Assistant,
            content =
                ContentUi(
                    text =
                        """
                        ## Structured concurrency

                        Structured concurrency ties every child coroutine to a parent **scope** so nothing outlives the work you started. When the scope is cancelled, every child receives cancellation promptly.

                        ### Why it matters

                        1. **No leaked jobs** after a screen closes
                        2. **Predictable teardown** for streaming inference
                        3. **Easier debugging** — failure surfaces in one place

                        ```kotlin
                        suspend fun loadModel(path: String) = coroutineScope {
                            val weights = async { readWeights(path) }
                            val vocab = async { readVocab(path) }
                            Model(weights.await(), vocab.await())
                        }
                        ```

                        > Use `coroutineScope` or `supervisorScope` at feature boundaries; avoid `GlobalScope` in UI code.
                        """.trimIndent(),
                ),
            formattedTimestamp = "10:32 AM",
            indicatorState =
                IndicatorState.Complete(
                    thinkingData =
                        ThinkingDataUi(
                            thinkingDurationSeconds = 18,
                            thinkingRaw =
                                "Break down structured concurrency: parent scope, cancellation propagation, " +
                                    "and how it applies to streaming chat.",
                        ),
                ),
            modelDisplayName = "Qwen 3 8B",
        ),
    )
