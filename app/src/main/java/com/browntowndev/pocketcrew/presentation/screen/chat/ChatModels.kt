package com.browntowndev.pocketcrew.presentation.screen.chat

import android.content.Context
import com.browntowndev.pocketcrew.R

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat

enum class Mode(
    @param:StringRes val displayNameRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    FAST(R.string.mode_fast, R.drawable.bolt),
    CREW(R.string.mode_crew, R.drawable.merge);

    fun getDisplayName(context: Context): String = context.getString(displayNameRes)

    fun getIcon(context: Context) = ContextCompat.getDrawable(context, iconRes)
}

enum class MessageRole {
    User,
    Assistant,
}

/**
 * Thinking metadata attached to a completed assistant message.
 * Persists on the message so users can review chain-of-thought
 * for any historical response in the conversation.
 */
data class ThinkingData(
    val durationSeconds: Int,
    val steps: List<String>,
)

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val formattedTimestamp: String,
    val thinkingData: ThinkingData? = null,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val selectedMode: Mode = Mode.FAST,
    val isInputExpanded: Boolean = false,
    /** True while the model is actively thinking — drives the live [ThinkingIndicator]. */
    val isThinking: Boolean = false,
    /** Live steps for the in-progress generation — NOT persisted on messages. */
    val thinkingSteps: List<String> = emptyList(),
    /** Shows the "Use the Crew" popup after Fast mode response */
    val showUseTheCrewPopup: Boolean = false,
    val showShield: Boolean = false,
    val shieldReason: String = "",
    val hapticPress: Boolean = true,
    val hapticResponse: Boolean = true,
)

val fakeLongMessages = listOf(
    ChatMessage(
        id = "1",
        role = MessageRole.Assistant,
        content = "Here's a Kotlin example that demonstrates the pattern:\n\n" +
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
            "Let me know if you'd like me to extend this.",
        formattedTimestamp = "10:29 AM",
        thinkingData = ThinkingData(
            durationSeconds = 14,
            steps = listOf(
                "Agent A: Drafting direct Kotlin example with greeting function...",
                "Agent B: Adding JSON config representation for completeness...",
                "Synthesizer: Merging drafts into cohesive response...",
                "Refinement: Verifying code correctness and formatting...",
            ),
        ),
    ),
    ChatMessage(
        id = "2",
        role = MessageRole.User,
        content = "Tell me a joke.",
        formattedTimestamp = "10:28 AM",
    ),
    ChatMessage(
        id = "3",
        role = MessageRole.Assistant,
        content = "Why did the AI go to therapy? It had too many unresolved tokens!",
        formattedTimestamp = "10:28 AM",
        thinkingData = ThinkingData(
            durationSeconds = 3,
            steps = listOf("Quick mode — single-pass generation"),
        ),
    ),
    ChatMessage(
        id = "4",
        role = MessageRole.User,
        content = "What is Jetpack Compose?",
        formattedTimestamp = "10:27 AM",
    ),
    ChatMessage(
        id = "5",
        role = MessageRole.Assistant,
        content = "Jetpack Compose is Android's modern toolkit for building native UI. It replaces XML layouts with declarative Kotlin code.",
        formattedTimestamp = "10:26 AM",
        thinkingData = null, // Quick mode, no thinking
    ),
)
