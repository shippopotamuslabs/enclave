package com.browntowndev.enclave.feature.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.PromptSafetyGateRequest
import com.browntowndev.enclave.domain.usecase.chat.PromptSafetyGateResult
import com.browntowndev.enclave.domain.usecase.chat.UserPromptResult
import com.browntowndev.enclave.domain.usecase.inference.PromptModerationResult
import io.mockk.coEvery

internal fun mockAllowedSafetyGate(
    chatUseCases: ChatUseCases,
    userMessageId: MessageId = MessageId("user-1"),
    chatId: ChatId = ChatId("chat-1"),
    userMessageCreatedAtMillis: Long = 100L,
    wasNewChat: Boolean = true,
) {
    coEvery { chatUseCases.processPromptWithSafetyGate(any()) } coAnswers {
        val request = firstArg<PromptSafetyGateRequest>()
        PromptSafetyGateResult.Allowed(
            userResult =
                UserPromptResult(
                    userMessageId = userMessageId,
                    chatId = chatId,
                    userMessageCreatedAtMillis = userMessageCreatedAtMillis,
                ),
            assistantMessageId = request.assistantMessageId,
            wasNewChat = wasNewChat,
        )
    }
}

internal fun mockBlockedSafetyGate(
    chatUseCases: ChatUseCases,
    triggeredLabels: List<String> = listOf("sexual/minors"),
    combinedIllegalScore: Double = 0.9,
) {
    coEvery { chatUseCases.processPromptWithSafetyGate(any()) } returns
        PromptSafetyGateResult.Blocked(
            moderationResult =
                PromptModerationResult(
                    blocked = true,
                    scoresByLabel = emptyMap(),
                    canonicalScores = emptyMap(),
                    triggeredLabels = triggeredLabels,
                    combinedIllegalScore = combinedIllegalScore,
                ),
        )
}

internal fun mockGateThrows(
    chatUseCases: ChatUseCases,
    throwable: Throwable,
) {
    coEvery { chatUseCases.processPromptWithSafetyGate(any()) } throws throwable
}
