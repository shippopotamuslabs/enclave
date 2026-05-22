package com.browntowndev.enclave.domain.port.repository

import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.share.SharedChat

interface ShareRepositoryPort {
    suspend fun shareChat(messages: List<Message>): SharedChat

    suspend fun getSharedChat(
        shareId: String,
        decryptionKey: String? = null,
    ): SharedChat?
}
