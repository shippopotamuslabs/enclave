package com.browntowndev.enclave.domain.port.inference

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatMessage

interface CommittedInferenceTranscriptPort {
    fun commit(
        chatId: ChatId,
        messages: List<ChatMessage>,
    )

    fun get(chatId: ChatId): List<ChatMessage>?

    fun clear(chatId: ChatId)
}

object NoopCommittedInferenceTranscriptPort : CommittedInferenceTranscriptPort {
    override fun commit(
        chatId: ChatId,
        messages: List<ChatMessage>,
    ) = Unit

    override fun get(chatId: ChatId): List<ChatMessage>? = null

    override fun clear(chatId: ChatId) = Unit
}
