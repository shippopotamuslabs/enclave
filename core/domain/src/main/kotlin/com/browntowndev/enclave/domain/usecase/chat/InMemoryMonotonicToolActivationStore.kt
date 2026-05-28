package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.port.inference.MonotonicToolActivationPort
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * In-memory implementation of [MonotonicToolActivationPort].
 * Keyed by [ChatId]. No persistence; cleared when the session ends or a new chat starts.
 */
class InMemoryMonotonicToolActivationStore
    @Inject
    constructor() : MonotonicToolActivationPort {
        private val store = ConcurrentHashMap<ChatId, MutableSet<String>>()

        override fun union(
            chatId: ChatId,
            newNames: Set<String>,
        ): Set<String> {
            val existing = store.getOrPut(chatId) { java.util.concurrent.CopyOnWriteArraySet() }
            existing.addAll(newNames)
            return existing.toSet()
        }

        override fun clearSession(chatId: ChatId) {
            store.remove(chatId)
        }
    }
