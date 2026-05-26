package com.browntowndev.enclave.domain.port.inference

import com.browntowndev.enclave.domain.model.chat.MessageId

/**
 * Stores the exact user content committed into an inference session for a persisted user message.
 *
 * This content is inference-only context and must not replace the UI-visible message text.
 */
interface CommittedInferencePromptPort {
    fun commit(
        userMessageId: MessageId,
        content: String,
    ): Unit

    fun get(userMessageId: MessageId): String?

    fun clear(userMessageId: MessageId): Unit
}

object NoopCommittedInferencePromptPort : CommittedInferencePromptPort {
    override fun commit(
        userMessageId: MessageId,
        content: String,
    ): Unit = Unit

    override fun get(userMessageId: MessageId): String? = null

    override fun clear(userMessageId: MessageId): Unit = Unit
}
