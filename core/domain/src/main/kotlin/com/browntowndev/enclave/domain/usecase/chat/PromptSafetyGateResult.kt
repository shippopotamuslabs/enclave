package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.usecase.inference.PromptModerationResult

data class PromptSafetyGateRequest(
    val message: Message,
    val promptTextForModeration: String,
    val shouldModerate: Boolean,
    val assistantMessageId: MessageId,
)

sealed interface PromptSafetyGateResult {
    data class Allowed(
        val userResult: UserPromptResult,
        val assistantMessageId: MessageId,
        val wasNewChat: Boolean,
    ) : PromptSafetyGateResult

    data class Blocked(
        val moderationResult: PromptModerationResult,
    ) : PromptSafetyGateResult
}
