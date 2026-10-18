package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.share.SharedChat
import com.browntowndev.enclave.domain.port.repository.ShareRepositoryPort

/** In-memory [ShareRepositoryPort] fake (ENC-28): records calls; results are test-controlled vars. */
class FakeShareRepository : ShareRepositoryPort {
    /** Every (messages, chatId) pair passed to [shareChat], in call order. */
    val shareChatCalls = mutableListOf<Pair<List<Message>, ChatId?>>()

    /** Chat returned by [shareChat] when [shareChatFailure] is null. */
    var shareChatResult: SharedChat =
        SharedChat("share-1", emptyList(), createdAt = 0, expiresAt = 0, shareUrl = "url")

    /** When set, [shareChat] throws this after recording the call. */
    var shareChatFailure: Throwable? = null

    /** Every (shareId, decryptionKey) pair passed to [getSharedChat], in call order. */
    val getSharedChatCalls = mutableListOf<Pair<String, String?>>()

    /** Chat returned by [getSharedChat]. */
    var getSharedChatResult: SharedChat? = null

    override suspend fun shareChat(
        messages: List<Message>,
        chatId: ChatId?,
    ): SharedChat {
        shareChatCalls += messages to chatId
        shareChatFailure?.let { throw it }
        return shareChatResult
    }

    override suspend fun getSharedChat(
        shareId: String,
        decryptionKey: String?,
    ): SharedChat? {
        getSharedChatCalls += shareId to decryptionKey
        return getSharedChatResult
    }
}
