package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.port.inference.MonotonicToolActivationPort
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject

/**
 * In-memory implementation of [MonotonicToolActivationPort].
 * Keyed by [ChatId]. No persistence; cleared when the chat is deleted.
 */
class InMemoryMonotonicToolActivationStore
    @Inject
    constructor() : MonotonicToolActivationPort {
        private val store = ConcurrentHashMap<ChatId, MutableSet<String>>()

        override fun activatedNames(chatId: ChatId): Set<String> =
            store[chatId]?.toSet() ?: emptySet()

        override fun recordActivation(
            chatId: ChatId,
            newNames: Set<String>,
        ) {
            if (newNames.isEmpty()) return
            val existing = store.getOrPut(chatId) { CopyOnWriteArraySet() }
            existing.addAll(newNames)
        }

        override fun clearSession(chatId: ChatId) {
            store.remove(chatId)
        }
    }
