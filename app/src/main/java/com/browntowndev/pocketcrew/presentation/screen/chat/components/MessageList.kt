package com.browntowndev.pocketcrew.presentation.screen.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.R
import com.browntowndev.pocketcrew.presentation.screen.chat.ChatMessage
import com.browntowndev.pocketcrew.presentation.screen.chat.MessageRole
import com.browntowndev.pocketcrew.presentation.screen.chat.fakeLongMessages
import com.browntowndev.pocketcrew.presentation.theme.PocketCrewTheme

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    isThinking: Boolean,
    thinkingSteps: List<String>,
    modifier: Modifier = Modifier,
) {
    if (messages.isEmpty() && !isThinking) {
        EmptyState(modifier = modifier)
    } else {
        LazyColumn(
            modifier = modifier,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        ) {
            items(
                count = messages.size,
                key = { index: Int -> "msg_${messages[messages.size - 1 - index].id}" },
            ) { index: Int ->
                val message = messages[messages.size - 1 - index]
                if (message.role == MessageRole.User) {
                    if (isThinking) {
                        ThinkingIndicator(thinkingSteps = thinkingSteps)
                    }
                    MessageBubble(message = message)
                } else {
                    AssistantResponse(message = message)
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
        MessageList(emptyList(), false, emptyList())
    }
}

@Preview
@Composable
private fun PreviewMessageListLong() {
    PocketCrewTheme(darkTheme = true) {
        MessageList(fakeLongMessages, false, emptyList())
    }
}

@Preview
@Composable
private fun PreviewMessageListThinking() {
    PocketCrewTheme(darkTheme = true) {
        MessageList(
            fakeLongMessages,
            true,
            listOf("Agent A: Drafting direct answer...", "Agent B: Skeptical critique..."),
        )
    }
}

@Preview
@Composable
private fun PreviewMessageListThinkingEmpty() {
    PocketCrewTheme(darkTheme = true) {
        MessageList(emptyList(), true, listOf("Analyzing query..."))
    }
}
