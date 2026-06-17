package com.browntowndev.enclave.feature.chat.components.messagebubble

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.browntowndev.enclave.feature.chat.ChatMessage

/** User message entry point; renders [UserBubble] with neo-brutalist chrome. */
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
