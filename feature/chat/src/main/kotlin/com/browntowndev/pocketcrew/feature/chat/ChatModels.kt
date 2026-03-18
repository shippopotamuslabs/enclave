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
) {
    val isGenerating: Boolean
        get() = messages.any {
            it.indicatorState is IndicatorState.Generating ||
                    it.indicatorState is IndicatorState.Thinking ||
                    it.indicatorState is IndicatorState.Processing
        }
}

/**
 * Used in previews.
 */
val fakeLongMessages = listOf(
    ChatMessage(
        id = 1,
        chatId = 1L,
        role = MessageRole.Assistant,
        content = ContentUi(
            text = "Here's a Kotlin example that demonstrates the pattern:\n\n" +
                "```kotlin\n" +
                "fun greet(name: String): String {\n" +
                "    return \"Hello, \$name!\"\n" +
                "}\n\n" +
                "fun main() {\n" +
                "    println(greet(\"Pocket Crew\"))\n" +
                "}\n" +
                "```\n\n" +
                "You can also represent the config as JSON:\n\n" +
                "```json\n" +
                "{\n" +
                "  \"model\": \"qwen3-8b\",\n" +
                "  \"temperature\": 0.7,\n" +
                "  \"max_tokens\": 2048\n" +
                "}\n" +
                "```\n\n" +
                "Let me know if you'd like me to extend this."
        ),
        formattedTimestamp = "10:29 AM",
        modelDisplayName = "Qwen 3 8B",
    ),
    ChatMessage(
        id = 2,
        chatId = 1L,
        role = MessageRole.User,
        content = ContentUi(text = "Tell me a joke."),
        formattedTimestamp = "10:28 AM",
    ),
    ChatMessage(
        id = 3,
        chatId = 1L,
        role = MessageRole.Assistant,
        content = ContentUi(
            text = "Why did the AI go to therapy? It had too many unresolved tokens!"
        ),
        formattedTimestamp = "10:28 AM",
        modelDisplayName = "Qwen 3 8B",
    ),
    ChatMessage(
        id = 4,
        chatId = 1L,
        role = MessageRole.User,
        content = ContentUi(text = "What is Jetpack Compose?"),
        formattedTimestamp = "10:27 AM",
    ),
    ChatMessage(
        id = 5,
        chatId = 1L,
        role = MessageRole.Assistant,
        content = ContentUi(
            text = "Jetpack Compose is Android's modern toolkit for building native UI. It replaces XML layouts with declarative Kotlin code."
        ),
        formattedTimestamp = "10:26 AM",
        modelDisplayName = "Qwen 3 8B",
    ),
)
