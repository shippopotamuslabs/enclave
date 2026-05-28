package com.browntowndev.enclave.feature.chat.components.messagelist

import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.feature.chat.ChatMessage
import com.browntowndev.enclave.feature.chat.MessageRole
import com.browntowndev.enclave.feature.chat.ToolCallBannerUi

internal fun resolveActiveToolCallBanner(
    message: ChatMessage,
    activeIndicatorMessageId: MessageId?,
    activeToolCallBanner: ToolCallBannerUi?,
): ToolCallBannerUi? {
    if (message.role == MessageRole.User) return null
    return if (message.id == activeIndicatorMessageId) activeToolCallBanner else null
}
