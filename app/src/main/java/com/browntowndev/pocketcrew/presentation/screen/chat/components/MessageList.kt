package com.browntowndev.pocketcrew.presentation.screen.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.R
import com.browntowndev.pocketcrew.presentation.screen.chat.ChatMessage
import com.browntowndev.pocketcrew.presentation.screen.chat.MessageRole
import com.browntowndev.pocketcrew.presentation.screen.chat.ResponseState
import com.browntowndev.pocketcrew.presentation.screen.chat.fakeLongMessages
import com.browntowndev.pocketcrew.presentation.theme.PocketCrewTheme

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    responseState: ResponseState,
    thinkingSteps: List<String>,
    thinkingStartTime: Long,
    thinkingModelDisplayName: String,
    modifier: Modifier = Modifier,
) {
    val isThinking = responseState != ResponseState.NONE

    if (messages.isEmpty() && !isThinking) {
        EmptyState(modifier = modifier)
    } else {
        // Calculate most recent user index once, outside the loop to avoid O(n²) behavior
        val mostRecentUserIndex = remember(messages) {
            messages.indexOfLast { it.role == MessageRole.User }
        }

        LazyColumn(
            modifier = modifier,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 16.dp),
        ) {
            items(
                count = messages.size,
                key = { index: Int -> "msg_${messages[messages.size - 1 - index].id}" },
            ) { index: Int ->
                val message = messages[messages.size - 1 - index]
                // Show indicator ONLY on the most recent User message when response is active.
                val isMostRecentUserMessage = message.role == MessageRole.User &&
                    messages.indexOf(message) == mostRecentUserIndex

                // Check if there's an assistant message after this user message with completed steps (Crew mode)
                val nextMessageIndex = messages.indexOf(message) + 1
                val hasCompletedSteps = nextMessageIndex < messages.size &&
                    messages[nextMessageIndex].role == MessageRole.Assistant &&
                    !messages[nextMessageIndex].completedSteps.isNullOrEmpty()

                // For non-Crew mode: show ThinkingIndicator before assistant message
                // For Crew mode with completed steps: don't show here (it shows after assistant message)
                val showIndicator = isMostRecentUserMessage && isThinking && !hasCompletedSteps

                if (message.role == MessageRole.User) {
                    if (showIndicator) {
                        when (responseState) {
                            ResponseState.THINKING -> {
                                ThinkingIndicator(
                                    thinkingSteps = thinkingSteps,
                                    thinkingStartTime = thinkingStartTime,
                                    modelDisplayName = thinkingModelDisplayName,
                                )
                            }
                            ResponseState.PROCESSING -> {
                                ProcessingIndicator()
                            }
                            ResponseState.GENERATING -> {
                                // Show generating indicator for Crew mode non-final steps
                                GeneratingIndicator()
                            }
                            ResponseState.NONE -> {
                                // No indicator
                            }
                        }
                    }
                    MessageBubble(message = message)
                } else {
                    AssistantResponse(
                        message = message,
                        modelDisplayName = thinkingModelDisplayName,
                        // Pass thinking state for Crew mode live indicator below completed steps
                        thinkingSteps = thinkingSteps,
                        thinkingStartTime = thinkingStartTime,
                        responseState = responseState,
                    )
                }
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
        Icon(
            painter = painterResource(R.drawable.network_intelligence),
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary,
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

// ==================== PREVIEWS ====================

@Preview
@Composable
private fun PreviewMessageListEmpty() {
    PocketCrewTheme {
        MessageList(emptyList(), ResponseState.NONE, emptyList(), 0L, "")
    }
}

@Preview
@Composable
private fun PreviewMessageListLong() {
    PocketCrewTheme(darkTheme = true) {
        MessageList(fakeLongMessages, ResponseState.NONE, emptyList(), 0L, "")
    }
}

@Preview
@Composable
private fun PreviewMessageListThinking() {
    PocketCrewTheme(darkTheme = true) {
        MessageList(
            fakeLongMessages,
            ResponseState.THINKING,
            listOf("Drafting direct answer...", "Skeptical critique..."),
            System.currentTimeMillis() - 5000,
            "The Sentinel",
        )
    }
}

@Preview
@Composable
private fun PreviewMessageListThinkingEmpty() {
    PocketCrewTheme(darkTheme = true) {
        MessageList(emptyList(), ResponseState.THINKING, listOf("Analyzing query..."), System.currentTimeMillis() - 3000, "The Brainstormer")
    }
}

@Preview
@Composable
private fun PreviewMessageListMultipleUsersThinking() {
    // Multiple user messages - indicator should only appear under the LAST one
    val messages = listOf(
        ChatMessage(id = 5L, chatId = 1L, role = MessageRole.Assistant, content = "Response to first", formattedTimestamp = "10:30 AM"),
        ChatMessage(id = 4L, chatId = 1L, role = MessageRole.User, content = "First question?", formattedTimestamp = "10:29 AM"),
        ChatMessage(id = 3L, chatId = 1L, role = MessageRole.Assistant, content = "Response to second", formattedTimestamp = "10:28 AM"),
        ChatMessage(id = 2L, chatId = 1L, role = MessageRole.User, content = "Second question", formattedTimestamp = "10:27 AM"),
        ChatMessage(id = 1L, chatId = 1L, role = MessageRole.User, content = "Third question (MOST RECENT)", formattedTimestamp = "10:26 AM"),
    )
    PocketCrewTheme(darkTheme = true) {
        MessageList(
            messages,
            ResponseState.THINKING,
            listOf("Analyzing...", "Drafting response..."),
            System.currentTimeMillis() - 10000,
            "The Mastermind",
        )
    }
}
