package com.browntowndev.enclave.feature.chat.components.messagebubble

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.feature.chat.ChatMessage
import com.browntowndev.enclave.feature.chat.ContentUi
import com.browntowndev.enclave.feature.chat.MessageRole

@Preview(showBackground = true, name = "User bubble — dark")
@Composable
fun PreviewMessageBubbleUserDark() {
    EnclaveTheme(darkTheme = true) {
        MessageBubble(
            message =
                ChatMessage(
                    id = MessageId("1"),
                    chatId = ChatId("1"),
                    role = MessageRole.User,
                    content = ContentUi(text = "Hello!"),
                    formattedTimestamp = "10:30 AM",
                ),
        )
    }
}

@Preview(showBackground = true, name = "User bubble code — dark")
@Composable
fun PreviewMessageBubbleCodeDark() {
    EnclaveTheme(darkTheme = true) {
        MessageBubble(
            message =
                ChatMessage(
                    id = MessageId("3"),
                    chatId = ChatId("1"),
                    role = MessageRole.User,
                    content =
                        ContentUi(
                            text =
                                """
```
fun main() {
  println("Hello")
}
```
                                """.trimIndent(),
                        ),
                    formattedTimestamp = "10:32 AM",
                ),
        )
    }
}

@Preview(showBackground = true, name = "User bubble long — dark")
@Composable
fun PreviewMessageBubbleLongDark() {
    EnclaveTheme(darkTheme = true) {
        MessageBubble(
            message =
                ChatMessage(
                    id = MessageId("4"),
                    chatId = ChatId("1"),
                    role = MessageRole.User,
                    content =
                        ContentUi(
                            text =
                                "This is a longer message that spans multiple lines to verify layout " +
                                    "behavior and make sure the action buttons align properly beneath the bubble.",
                        ),
                    formattedTimestamp = "10:33 AM",
                ),
        )
    }
}
