package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.port.inference.CommittedInferenceTranscriptPort
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class InMemoryCommittedInferenceTranscriptStore
    @Inject
    constructor() : CommittedInferenceTranscriptPort {
        private val transcripts = ConcurrentHashMap<ChatId, List<ChatMessage>>()

        override fun commit(
            chatId: ChatId,
            messages: List<ChatMessage>,
        ) {
            transcripts[chatId] = messages.toList()
        }

        override fun get(chatId: ChatId): List<ChatMessage>? = transcripts[chatId]?.toList()

        override fun clear(chatId: ChatId) {
            transcripts.remove(chatId)
        }
    }
