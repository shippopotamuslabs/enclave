package com.browntowndev.pocketcrew.presentation.screen.chat

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.browntowndev.pocketcrew.presentation.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Robolectric tests for MessageList UI behavior.
 *
 * Verifies:
 * 1. Non-Crew Mode (Fast): Shows ProcessingIndicator, no thinking
 * 2. Non-Crew Mode (Thinking): Shows ThinkingIndicator, then "Thought For Xs" when done
 * 3. Crew Mode: Shows completed steps + ThinkingIndicator BELOW them during current step
 */
@RunWith(AndroidJUnit4::class)
class MessageListCrewModeRobolectricTest {

    @get:Rule
    val composeTestRule: AndroidComposeTestRule<*, MainActivity> = createAndroidComposeRule<MainActivity>()

    /**
     * Test: Fast mode shows ProcessingIndicator when processing
     * Expected: No thinking indicator, just processing
     */
    @Test
    fun `Fast mode shows ProcessingIndicator when processing`() {
        // Given: Fast mode with processing state
        val messages = listOf(
            ChatMessage(
                id = 1L,
                chatId = 1L,
                role = MessageRole.User,
                content = "Hello",
                formattedTimestamp = "10:00 AM"
            ),
            ChatMessage(
                id = 2L,
                chatId = 1L,
                role = MessageRole.Assistant,
                content = "",
                formattedTimestamp = "10:00 AM"
            )
        )

        // When: Render with PROCESSING state
        composeTestRule.setContent {
            com.browntowndev.pocketcrew.presentation.screen.chat.components.MessageList(
                messages = messages,
                responseState = ResponseState.PROCESSING,
                thinkingSteps = emptyList(),
                thinkingStartTime = 0L,
                thinkingModelDisplayName = ""
            )
        }

        // Then: No "Thought for" indicator should be visible (processing mode)
        composeTestRule.onNodeWithText("Thought for")
            .assertDoesNotExist()
    }

    /**
     * Test: Crew mode after Draft One shows completed step with thinking indicator below
     * Expected: "Thought For Xs", "Draft One Completed!", and ThinkingIndicator all visible
     */
    @Test
    fun `Crew mode after Draft One shows completed step with thinking indicator below`() {
        // Given: Crew mode after first step completes, still thinking on next step
        val completedSteps = listOf(
            StepCompletionData(
                stepName = "Draft One",
                stepOutput = "Draft output",
                durationSeconds = 43,
                thinkingSteps = listOf("Analyzing request"),
                modelDisplayName = "Gemma 3 1B"
            )
        )

        val messages = listOf(
            ChatMessage(
                id = 1L,
                chatId = 1L,
                role = MessageRole.User,
                content = "Write a story",
                formattedTimestamp = "10:00 AM"
            ),
            ChatMessage(
                id = 2L,
                chatId = 1L,
                role = MessageRole.Assistant,
                content = "",
                formattedTimestamp = "10:00 AM",
                completedSteps = completedSteps,
                thinkingData = ThinkingData(
                    durationSeconds = 43,
                    steps = listOf("Analyzing request"),
                    modelDisplayName = "Gemma 3 1B"
                )
            )
        )

        // When: Render with THINKING state and completed steps
        composeTestRule.setContent {
            com.browntowndev.pocketcrew.presentation.screen.chat.components.MessageList(
                messages = messages,
                responseState = ResponseState.THINKING,
                thinkingSteps = listOf("Drafting response..."),
                thinkingStartTime = System.currentTimeMillis(),
                thinkingModelDisplayName = "Gemma 3 1B"
            )
        }

        // Then: Should see "Draft One Completed!"
        composeTestRule.onNodeWithText("Draft One Completed!")
            .assertExists()

        // Then: Should see "Thought for" (from thinkingData)
        composeTestRule.onNodeWithText("Thought for")
            .assertExists()

        // Then: Should see ThinkingIndicator (shown below completed steps in Crew mode)
        // The ThinkingIndicator shows "Thinking" text
        composeTestRule.onNodeWithText("Thinking")
            .assertExists()
    }

    /**
     * Test: Crew mode after Draft Two shows both completed steps with thinking indicator below
     * Expected: Both drafts completed, plus thinking indicator for current step
     */
    @Test
    fun `Crew mode after Draft Two shows both completed steps with thinking indicator below`() {
        // Given: Crew mode after two steps complete
        val completedSteps = listOf(
            StepCompletionData(
                stepName = "Draft One",
                stepOutput = "Draft One output",
                durationSeconds = 30,
                thinkingSteps = listOf("Analyzing"),
                modelDisplayName = "Model 1"
            ),
            StepCompletionData(
                stepName = "Draft Two",
                stepOutput = "Draft Two output",
                durationSeconds = 25,
                thinkingSteps = listOf("Considering alternatives"),
                modelDisplayName = "Model 2"
            )
        )

        val messages = listOf(
            ChatMessage(
                id = 1L,
                chatId = 1L,
                role = MessageRole.User,
                content = "Write a story",
                formattedTimestamp = "10:00 AM"
            ),
            ChatMessage(
                id = 2L,
                chatId = 1L,
                role = MessageRole.Assistant,
                content = "",
                formattedTimestamp = "10:00 AM",
                completedSteps = completedSteps,
                thinkingData = ThinkingData(
                    durationSeconds = 25,
                    steps = listOf("Considering alternatives"),
                    modelDisplayName = "Model 2"
                )
            )
        )

        // When: Render with THINKING state and two completed steps
        composeTestRule.setContent {
            com.browntowndev.pocketcrew.presentation.screen.chat.components.MessageList(
                messages = messages,
                responseState = ResponseState.THINKING,
                thinkingSteps = listOf("Synthesizing..."),
                thinkingStartTime = System.currentTimeMillis(),
                thinkingModelDisplayName = "DeepSeek-R1"
            )
        }

        // Then: Should see both completed steps
        composeTestRule.onNodeWithText("Draft One Completed!")
            .assertExists()
        composeTestRule.onNodeWithText("Draft Two Completed!")
            .assertExists()

        // Then: Should see ThinkingIndicator (current step)
        composeTestRule.onNodeWithText("Thinking")
            .assertExists()
    }

    /**
     * Test: Crew mode shows no duplicate "Thought for" indicators
     * Expected: Only one "Thought for" from thinkingData, not from completedSteps
     */
    @Test
    fun `Crew mode shows only one Thought for indicator`() {
        // Given: Message with completed steps and thinkingData
        val completedSteps = listOf(
            StepCompletionData(
                stepName = "Draft One",
                stepOutput = "Output",
                durationSeconds = 30,
                thinkingSteps = listOf("Thinking"),
                modelDisplayName = "Model"
            )
        )

        val messages = listOf(
            ChatMessage(
                id = 1L,
                chatId = 1L,
                role = MessageRole.User,
                content = "Hello",
                formattedTimestamp = "10:00 AM"
            ),
            ChatMessage(
                id = 2L,
                chatId = 1L,
                role = MessageRole.Assistant,
                content = "",
                formattedTimestamp = "10:00 AM",
                completedSteps = completedSteps,
                thinkingData = ThinkingData(
                    durationSeconds = 30,
                    steps = listOf("Thinking"),
                    modelDisplayName = "Model"
                )
            )
        )

        // When: Render with thinking state
        composeTestRule.setContent {
            com.browntowndev.pocketcrew.presentation.screen.chat.components.MessageList(
                messages = messages,
                responseState = ResponseState.THINKING,
                thinkingSteps = listOf("Next step"),
                thinkingStartTime = System.currentTimeMillis(),
                thinkingModelDisplayName = "Model"
            )
        }

        // Then: Should have exactly ONE "Thought for" (not duplicate)
        // This tests that completedSteps doesn't add its own "Thought for"
        val thoughtForNodes = composeTestRule.onAllNodesWithText("Thought for")
        // Should be exactly 1 occurrence
        thoughtForNodes.assertCountEquals(1)
    }

    /**
     * Test: Non-crew thinking mode shows "Thought for" after completion
     * Expected: After thinking completes, "Thought for Xs" is visible on the message
     */
    @Test
    fun `Thinking mode shows Thought for after completion`() {
        // Given: Message with thinkingData (non-crew mode completed)
        val messages = listOf(
            ChatMessage(
                id = 1L,
                chatId = 1L,
                role = MessageRole.User,
                content = "Hello",
                formattedTimestamp = "10:00 AM"
            ),
            ChatMessage(
                id = 2L,
                chatId = 1L,
                role = MessageRole.Assistant,
                content = "Here's my response",
                formattedTimestamp = "10:00 AM",
                thinkingData = ThinkingData(
                    durationSeconds = 15,
                    steps = listOf("Analyzing request", "Generating response"),
                    modelDisplayName = "DeepSeek-R1"
                )
            )
        )

        // When: Render with NONE state (thinking completed)
        composeTestRule.setContent {
            com.browntowndev.pocketcrew.presentation.screen.chat.components.MessageList(
                messages = messages,
                responseState = ResponseState.NONE,
                thinkingSteps = emptyList(),
                thinkingStartTime = 0L,
                thinkingModelDisplayName = ""
            )
        }

        // Then: Should see "Thought for" on the assistant message
        composeTestRule.onNodeWithText("Thought for")
            .assertExists()
    }

    /**
     * Test: Fast mode with no thinking shows no thinking indicator
     * Expected: Processing shown, no thinking indicators
     */
    @Test
    fun `Fast mode with no thinking shows no thinking indicator`() {
        // Given: Fast mode message with no thinking data
        val messages = listOf(
            ChatMessage(
                id = 1L,
                chatId = 1L,
                role = MessageRole.User,
                content = "Hi",
                formattedTimestamp = "10:00 AM"
            ),
            ChatMessage(
                id = 2L,
                chatId = 1L,
                role = MessageRole.Assistant,
                content = "Hi there!",
                formattedTimestamp = "10:00 AM",
                thinkingData = null
            )
        )

        // When: Render
        composeTestRule.setContent {
            com.browntowndev.pocketcrew.presentation.screen.chat.components.MessageList(
                messages = messages,
                responseState = ResponseState.NONE,
                thinkingSteps = emptyList(),
                thinkingStartTime = 0L,
                thinkingModelDisplayName = ""
            )
        }

        // Then: Should NOT see "Thought for" (no thinking was done)
        composeTestRule.onNodeWithText("Thought for")
            .assertDoesNotExist()
    }
}
