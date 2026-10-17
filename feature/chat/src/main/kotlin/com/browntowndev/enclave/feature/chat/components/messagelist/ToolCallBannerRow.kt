package com.browntowndev.enclave.feature.chat.components.messagelist

import com.browntowndev.enclave.feature.chat.ChatMessage
import com.browntowndev.enclave.feature.chat.MessageRole
import com.browntowndev.enclave.feature.chat.ToolCallBannerUi

internal fun resolveActiveToolCallBanner(
    message: ChatMessage,
    activeIndicatorMessageId: String?,
    activeToolCallBanner: ToolCallBannerUi?,
): ToolCallBannerUi? {
    if (message.role == MessageRole.User) return null
    return if (message.id.value == activeIndicatorMessageId) activeToolCallBanner else null
}
