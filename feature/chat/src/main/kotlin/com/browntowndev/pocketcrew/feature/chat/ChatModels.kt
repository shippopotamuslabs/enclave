package com.browntowndev.pocketcrew.feature.chat

import android.content.Context
import com.browntowndev.pocketcrew.feature.chat.R

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat

enum class ChatModeUi(
    @param:StringRes val displayNameRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    FAST(R.string.mode_fast, R.drawable.bolt),
    THINKING(R.string.mode_thinking, R.drawable.lightbulb),
    CREW(R.string.mode_crew, R.drawable.merge);

    fun getDisplayName(context: Context): String = context.getString(displayNameRes)

    fun getIcon(context: Context) = ContextCompat.getDrawable(context, iconRes)
}

enum class MessageRole {
    User,
    Assistant,
}

data class ChatMessage(
    val id: Long,
    val chatId: Long,
    val role: MessageRole,
    val content: ContentUi,
    val formattedTimestamp: String,
    val indicatorState: IndicatorState? = null,
    val modelDisplayName: String = ""
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val selectedMode: ChatModeUi = ChatModeUi.FAST,
    val isGlobalInferenceBlocked: Boolean = false,
    val shieldReason: String? = null,
    val hapticPress: Boolean = false,
    val hapticResponse: Boolean = false,
    val chatId: Long? = null,
) {
    val isGenerating: Boolean
        get() = messages.any {
            it.indicatorState is IndicatorState.Generating ||
                    it.indicatorState is IndicatorState.Thinking ||
                    it.indicatorState is IndicatorState.Processing
        }
}

/**
 * Used in previews - various indicator states.
 */
val fakeLongMessages = listOf(
    // Message with Processing indicator
    ChatMessage(
        id = 1,
        chatId = 1L,
        role = MessageRole.Assistant,
        content = ContentUi(text = ""),
        formattedTimestamp = "10:30 AM",
        indicatorState = IndicatorState.Processing,
        modelDisplayName = "Qwen 3 8B",
    ),
    // Message with Thinking indicator (streaming)
    ChatMessage(
        id = 2,
        chatId = 1L,
        role = MessageRole.Assistant,
        content = ContentUi(text = ""),
        formattedTimestamp = "10:29 AM",
        indicatorState = IndicatorState.Thinking(
            thinkingRaw = "# Analysis in Progress\n\n" +
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
        id = 3,
        chatId = 1L,
        role = MessageRole.User,
        content = ContentUi(text = "Can you explain how coroutines work in Kotlin?"),
        formattedTimestamp = "10:28 AM",
    ),
    // Message with Generating indicator (with thinking data)
    ChatMessage(
        id = 4,
        chatId = 1L,
        role = MessageRole.Assistant,
        content = ContentUi(text = "Message with Generating indicator (with thinking data)"),
        formattedTimestamp = "10:27 AM",
        indicatorState = IndicatorState.Generating(
            thinkingData = ThinkingDataUi(
                thinkingDurationSeconds = 15,
                thinkingRaw = "Kotlin coroutines are like lightweight threads. They allow you to write asynchronous code in a sequential manner. Key concepts: **suspend functions**, **Dispatchers**, and **Scopes**.\n\n- `launch` for fire-and-forget\n- `async` for results\n- `flow` for streams"
            )
        ),
        modelDisplayName = "Qwen 3 8B",
    ),
    // Message with Complete indicator (with thinking data)
    ChatMessage(
        id = 5,
        chatId = 1L,
        role = MessageRole.Assistant,
        content = ContentUi(text = "Message with Complete indicator (with thinking data)"),
        formattedTimestamp = "10:26 AM",
        indicatorState = IndicatorState.Complete(
            thinkingData = ThinkingDataUi(
                thinkingDurationSeconds = 8,
                thinkingRaw = "Let me think about this more carefully...\n\n" +
                    "## Key Points\n\n" +
                    "1. **Simplicity** - Compose reduces boilerplate\n" +
                    "2. **Declarative** - Describe UI, not steps\n" +
                    "3. **Reactive** - Automatic recomposition"
            )
        ),
        modelDisplayName = "Qwen 3 8B",
    ),
    // Completed message with response
    ChatMessage(
        id = 6,
        chatId = 1L,
        role = MessageRole.Assistant,
        content = ContentUi(
            text = "Jetpack Compose is Android's modern UI toolkit. It replaces XML layouts with declarative Kotlin code.\n\n" +
                "**Key Benefits:**\n" +
                "- Less boilerplate\n" +
                "- Declarative syntax\n" +
                "- Built-in state management\n\n" +
                "```kotlin\n" +
                "@Composable\n" +
                "fun Greeting(name: String) {\n" +
                "    Text(\"Hello, \$name!\")\n" +
                "}\n" +
                "```"
        ),
        formattedTimestamp = "10:25 AM",
        indicatorState = IndicatorState.Complete(thinkingData = null),
        modelDisplayName = "Qwen 3 8B",
    ),
    // User message
    ChatMessage(
        id = 7,
        chatId = 1L,
        role = MessageRole.User,
        content = ContentUi(text = "Show me a code example"),
        formattedTimestamp = "10:24 AM",
    ),
    // Simple completed message
    ChatMessage(
        id = 8,
        chatId = 1L,
        role = MessageRole.Assistant,
        content = ContentUi(
            text = "Here's a complete example in Kotlin:\n\n```kotlin\nfun main() {\n    println(\"Hello, World!\")\n}\n```"
        ),
        formattedTimestamp = "10:23 AM",
        modelDisplayName = "Qwen 3 8B",
    ),
)
