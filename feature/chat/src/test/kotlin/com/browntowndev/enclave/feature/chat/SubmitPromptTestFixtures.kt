package com.browntowndev.enclave.feature.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.usecase.chat.UserPromptResult

internal fun mockSubmitPrompt(
    chatUseCases: FakeChatUseCases,
    userMessageId: MessageId = MessageId("user-1"),
    chatId: ChatId = ChatId("chat-1"),
    userMessageCreatedAtMillis: Long = 100L,
) {
    chatUseCases.submitPromptUseCase.result =
        UserPromptResult(
            userMessageId = userMessageId,
            chatId = chatId,
            userMessageCreatedAtMillis = userMessageCreatedAtMillis,
        )
    chatUseCases.submitPromptUseCase.failure = null
}

internal fun mockSubmitPromptThrows(
    chatUseCases: FakeChatUseCases,
    throwable: Throwable,
) {
    chatUseCases.submitPromptUseCase.failure = throwable
}
