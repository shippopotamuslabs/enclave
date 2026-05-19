package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.share.SharedChat

interface ShareRepositoryPort {
    suspend fun shareChat(messages: List<Message>): SharedChat

    suspend fun getSharedChat(
        shareId: String,
        decryptionKey: String? = null,
    ): SharedChat?
}
