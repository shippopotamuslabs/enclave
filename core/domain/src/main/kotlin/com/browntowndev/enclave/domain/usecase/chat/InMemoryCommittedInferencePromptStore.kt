package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.port.inference.CommittedInferencePromptPort
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * In-memory cache of inference-only user content keyed by persisted message ID.
 */
class InMemoryCommittedInferencePromptStore
    @Inject
    constructor() : CommittedInferencePromptPort {
    private val prompts = ConcurrentHashMap<MessageId, String>()

    override fun commit(
        userMessageId: MessageId,
        content: String,
    ) {
        prompts[userMessageId] = content
    }

    override fun get(userMessageId: MessageId): String? = prompts[userMessageId]

    override fun clear(userMessageId: MessageId) {
        prompts.remove(userMessageId)
    }
}
