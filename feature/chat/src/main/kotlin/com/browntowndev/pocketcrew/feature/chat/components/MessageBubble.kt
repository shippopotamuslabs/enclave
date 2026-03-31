package com.browntowndev.pocketcrew.feature.chat.components

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.browntowndev.pocketcrew.feature.chat.R
import com.browntowndev.pocketcrew.feature.chat.ChatMessage
import com.browntowndev.pocketcrew.feature.chat.ContentUi
import com.browntowndev.pocketcrew.feature.chat.MessageRole
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import kotlinx.coroutines.launch

@Composable
fun MessageBubble(
    modifier: Modifier = Modifier,
    message: ChatMessage,
    onEditClick: (String) -> Unit = {},
) {
    val clipboardManager = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    var showActions by remember { mutableStateOf(false) }

    val copyToClipboard: (String) -> Unit = { content ->
        coroutineScope.launch {
            clipboardManager.setClipEntry(
                ClipEntry(ClipData.newPlainText("Pocket Crew Response", content)),
            )
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            horizontalAlignment = Alignment.End,
        ) {
            // Bubble
            Surface(
                modifier = Modifier
                    .fillMaxWidth(fraction = .75f)
                    .zIndex(1f)
                    .clickable { showActions = !showActions },
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 4.dp,
                ),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    SelectionContainer(modifier = Modifier.fillMaxWidth()) {
                        val contentText = message.content.text
                        if (contentText.contains("```")) {
                            val codeContent = contentText
                                .substringAfter("```")
                                .substringBeforeLast("```")
                                .trim()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            ) {
                                Text(
                                    text = codeContent,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                IconButton(onClick = { copyToClipboard(codeContent) }) {
                                    Icon(
                                        painter = painterResource(R.drawable.content_copy),
                                        contentDescription = "Copy code",
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = contentText,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    Text(
                        text = message.formattedTimestamp,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .align(Alignment.End),
                    )
                }
            }

            // Action buttons — slide out from behind the bubble
            AnimatedVisibility(
                visible = showActions,
                modifier = Modifier.zIndex(-1f),
                enter = slideInVertically(initialOffsetY = { -it }) + expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Row(
                    modifier = Modifier.padding(top = 4.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Edit
                    IconButton(
                        onClick = { onEditClick(message.content.text) },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Message",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    // Copy
                    IconButton (
                        onClick = { copyToClipboard(message.content.text) },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.content_copy),
                            contentDescription = "Copy message",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

// ==================== PREVIEWS ====================

@Preview
@Composable
private fun PreviewMessageBubbleUser() {
    PocketCrewTheme {
        MessageBubble(
            message = ChatMessage(id = 1L, chatId = 1L, role = MessageRole.User, content = ContentUi(text = "Hello!"), formattedTimestamp = "10:30 AM"),
        )
    }
}

@Preview
@Composable
private fun PreviewMessageBubbleCode() {
    PocketCrewTheme {
        MessageBubble(
            message = ChatMessage(
                id = 3L,
                chatId = 1L,
                role = MessageRole.User,
                content = ContentUi(text = """
```
fun main() {
  println("Hello")
}
```
                """.trimIndent()),
                formattedTimestamp = "10:32 AM",
            ),
        )
    }
}

@Preview
@Composable
private fun PreviewMessageBubbleLong() {
    PocketCrewTheme {
        MessageBubble(
            message = ChatMessage(
                id = 4L,
                chatId = 1L,
                role = MessageRole.User,
                content = ContentUi(text = "This is a longer message that spans multiple lines to verify layout behavior and make sure the action buttons align properly beneath the bubble."),
                formattedTimestamp = "10:33 AM",
            ),
        )
    }
}
