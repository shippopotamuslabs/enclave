package com.browntowndev.pocketcrew.feature.chat.components.messagebubble

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.browntowndev.pocketcrew.feature.chat.ChatMessage

@Composable
fun MessageBubble(
    modifier: Modifier = Modifier,
    message: ChatMessage,
    onEditClick: (String) -> Unit = {},
) {
    UserBubble(
        modifier = modifier,
        message = message,
        onEditClick = onEditClick,
    )
}
