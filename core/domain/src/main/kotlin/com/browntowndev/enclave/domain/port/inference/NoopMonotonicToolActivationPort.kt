package com.browntowndev.enclave.domain.port.inference

import com.browntowndev.enclave.domain.model.chat.ChatId

object NoopMonotonicToolActivationPort : MonotonicToolActivationPort {
    override fun activatedNames(chatId: ChatId): Set<String> = emptySet()

    override fun recordActivation(
        chatId: ChatId,
        newNames: Set<String>,
    ) = Unit

    override fun clearSession(chatId: ChatId) = Unit
}
