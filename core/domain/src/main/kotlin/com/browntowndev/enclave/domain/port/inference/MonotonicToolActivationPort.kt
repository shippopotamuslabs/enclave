package com.browntowndev.enclave.domain.port.inference

import com.browntowndev.enclave.domain.model.chat.ChatId

/**
 * Chat-scoped set of tools activated by intent classification or `search_tools`.
 * Once a tool name is recorded it stays until [clearSession].
 */
interface MonotonicToolActivationPort {
    /** Tools activated in prior turns for [chatId] (intent + search). */
    fun activatedNames(chatId: ChatId): Set<String>

    /** Merges [newNames] into the persisted set for [chatId]. */
    fun recordActivation(
        chatId: ChatId,
        newNames: Set<String>,
    )

    /** Clears the record for [chatId] (chat deleted or session ended). */
    fun clearSession(chatId: ChatId)
}
