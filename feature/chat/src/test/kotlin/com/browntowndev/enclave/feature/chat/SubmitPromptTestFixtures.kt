package com.browntowndev.enclave.feature.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.SubmitPromptRequest
import com.browntowndev.enclave.domain.usecase.chat.UserPromptResult
import io.mockk.coEvery

internal fun mockSubmitPrompt(
    chatUseCases: ChatUseCases,
    userMessageId: MessageId = MessageId("user-1"),
    chatId: ChatId = ChatId("chat-1"),
    userMessageCreatedAtMillis: Long = 100L,
) {
    coEvery { chatUseCases.submitPrompt(any()) } coAnswers {
        UserPromptResult(
            userMessageId = userMessageId,
            chatId = chatId,
            userMessageCreatedAtMillis = userMessageCreatedAtMillis,
        )
    }
}

internal fun mockSubmitPromptThrows(
    chatUseCases: ChatUseCases,
    throwable: Throwable,
) {
    coEvery { chatUseCases.submitPrompt(any()) } throws throwable
}
