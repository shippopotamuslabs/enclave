package com.browntowndev.enclave.feature.chat.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.feature.chat.ChatMessage
import com.browntowndev.enclave.feature.chat.ContentUi
import com.browntowndev.enclave.feature.chat.MessageRole
import com.browntowndev.enclave.feature.chat.components.messagelist.MessageList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that key(message.id) in the active-interaction Column causes Compose to track
 * items by identity rather than position.
 *
 * The critical test (composition node state not transferred on ID change): when a user
 * message at position N is replaced by a message with a different ID but the same text,
 * the MessageBubble's locally-remembered `isExpanded` state must NOT carry over to the
 * replacement message.  Without key(), Compose reuses the old composition node at the
 * same position; the new message's ExpandableTruncatableMessageText sees the same text
 * so `remember(text)` keeps the old expanded flag.  With key(), the old node is disposed
 * and a fresh node is created for the new ID.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageListKeyTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `active interaction renders both user and assistant messages`() {
        composeTestRule.setContent {
            EnclaveTheme {
                MessageList(
                    messages =
                        listOf(
                            userMessage("u1", "What is Kotlin?"),
                            assistantMessage("a1", "Kotlin is a modern JVM language."),
                        ),
                    hasActiveIndicator = false,
                    isPreview = true,
                )
            }
        }

        composeTestRule.onNodeWithText("What is Kotlin?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kotlin is a modern JVM language.").assertIsDisplayed()
    }

    @Test
    fun `streaming text growth in active interaction updates displayed content`() {
        var messages by mutableStateOf(
            listOf(
                userMessage("u1", "Explain coroutines"),
                assistantMessage("a1", "Coroutines are"),
            ),
        )

        composeTestRule.setContent {
            EnclaveTheme {
                MessageList(
                    messages = messages,
                    hasActiveIndicator = true,
                    isPreview = true,
                )
            }
        }

        composeTestRule.onNodeWithText("Coroutines are").assertIsDisplayed()

        messages =
            listOf(
                userMessage("u1", "Explain coroutines"),
                assistantMessage("a1", "Coroutines are a concurrency primitive."),
            )

        composeTestRule.onNodeWithText("Coroutines are a concurrency primitive.").assertIsDisplayed()
    }

    /**
     * Without key(): replacing the user message at position 0 with a different ID but the
     * same long text reuses the old composition node.  `remember(text)` inside
     * ExpandableTruncatableMessageText returns the existing `isExpanded = true` state, so
     * the new message incorrectly shows "Collapse message".
     *
     * With key(): the old node is disposed and a new one created, so isExpanded defaults
     * to false and the new message correctly shows "Expand message".
     */
    @Test
    fun `replacing a user message with a new ID resets its local expand state`() {
        val longText = (1..15).joinToString("\n") { "Line $it: ${"word ".repeat(10)}" }

        var messages by mutableStateOf(
            listOf(
                userMessage("u-original", longText),
                assistantMessage("a1", "Short answer"),
            ),
        )

        composeTestRule.setContent {
            EnclaveTheme {
                MessageList(
                    messages = messages,
                    hasActiveIndicator = false,
                    isPreview = true,
                )
            }
        }

        // Expand the user message bubble.
        composeTestRule.onNodeWithContentDescription("Expand message").performClick()
        composeTestRule.onNodeWithContentDescription("Collapse message").assertIsDisplayed()

        // Replace the user message with a different ID but the same text content.
        messages =
            listOf(
                userMessage("u-replacement", longText),
                assistantMessage("a1", "Short answer"),
            )

        // The replacement message must have fresh (collapsed) expand state, not the old
        // node's expanded state.  "Expand message" should be visible; "Collapse" should not.
        composeTestRule.onNodeWithContentDescription("Expand message").assertIsDisplayed()
    }
}

private fun userMessage(
    id: String,
    text: String,
) = ChatMessage(
    id = MessageId(id),
    chatId = ChatId("test-chat"),
    role = MessageRole.User,
    content = ContentUi(text = text),
    formattedTimestamp = "10:00 AM",
)

private fun assistantMessage(
    id: String,
    text: String,
) = ChatMessage(
    id = MessageId(id),
    chatId = ChatId("test-chat"),
    role = MessageRole.Assistant,
    content = ContentUi(text = text),
    formattedTimestamp = "10:01 AM",
)
