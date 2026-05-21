package com.browntowndev.pocketcrew.feature.chat.components.messagelist

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.feature.chat.ChatMessage
import com.browntowndev.pocketcrew.feature.chat.MessageRole
import com.browntowndev.pocketcrew.feature.chat.ToolCallBannerUi
import com.browntowndev.pocketcrew.feature.chat.components.ToolCallBanner

internal fun resolveActiveToolCallBanner(
    message: ChatMessage,
    activeIndicatorMessageId: MessageId?,
    activeToolCallBanner: ToolCallBannerUi?,
): ToolCallBannerUi? = if (message.id == activeIndicatorMessageId) activeToolCallBanner else null

internal fun usesInlineToolCallBannerInTranscript(message: ChatMessage): Boolean =
    (message.role == MessageRole.Assistant) && message.transcriptSegments.isNotEmpty()

@Composable
internal fun ToolCallBannerRow(
    banner: ToolCallBannerUi?,
    modifier: Modifier = Modifier,
) {
    ToolCallBanner(
        banner = banner,
        modifier = modifier,
    )
}

@Composable
internal fun StandaloneToolCallBannerRow(
    message: ChatMessage,
    activeToolCallBanner: ToolCallBannerUi?,
    modifier: Modifier = Modifier,
) {
    if (usesInlineToolCallBannerInTranscript(message)) return

    ToolCallBannerRow(
        banner = activeToolCallBanner,
        modifier = modifier.padding(bottom = 8.dp),
    )
}
