package com.browntowndev.pocketcrew.domain.usecase.share

import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.share.ShareOption
import com.browntowndev.pocketcrew.domain.model.share.SharedChat
import com.browntowndev.pocketcrew.domain.port.repository.ShareRepositoryPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import kotlinx.coroutines.flow.first
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

class ShareChatUseCase
    @Inject
    constructor(
        private val shareRepository: ShareRepositoryPort,
        private val chatUseCases: ChatUseCases,
    ) {
    suspend operator fun invoke(option: ShareOption): Result<SharedChat> =
        runCatching {
            val chatId = when (option) {
                is ShareOption.SingleTurn -> option.chatId
                is ShareOption.FullConversation -> option.chatId
            }

            val allMessages = chatUseCases.getChat(chatId).first { it.isNotEmpty() }
            val messagesToShare = when (option) {
                is ShareOption.SingleTurn -> {
                    val assistantIdx = allMessages.indexOfFirst { it.id == option.assistantMessageId }
                    require(assistantIdx >= 0) { "Assistant message not found" }
                    if (assistantIdx > 0 && allMessages[assistantIdx - 1].role == Role.USER) {
                        listOf(allMessages[assistantIdx - 1], allMessages[assistantIdx])
                    } else {
                        listOf(allMessages[assistantIdx])
                    }
                }
                is ShareOption.FullConversation -> allMessages
            }

            shareRepository.shareChat(messagesToShare)
        }

    suspend fun getSharedChat(
        shareReference: String,
        decryptionKey: String? = null,
    ): Result<SharedChat?> =
        runCatching {
            val parsed = ShareReference.parse(shareReference, decryptionKey)
            shareRepository.getSharedChat(parsed.shareId, parsed.decryptionKey)
        }

    private data class ShareReference(
        val shareId: String,
        val decryptionKey: String?,
    ) {
        companion object {
            fun parse(
                reference: String,
                explicitDecryptionKey: String?,
            ): ShareReference {
                val parsedFromReference = parseReference(reference)
                return parsedFromReference.copy(
                    decryptionKey = explicitDecryptionKey ?: parsedFromReference.decryptionKey,
                )
            }

            private fun parseReference(reference: String): ShareReference {
                val trimmed = reference.trim()
                val uri = runCatching { URI(trimmed) }.getOrNull()
                val queryKey = uri?.rawQuery?.extractParameter(KEY_PARAMETER)
                val shareId =
                    when {
                        uri?.path?.isNotBlank() == true -> uri.path.substringAfterLast("/")
                        else -> trimmed.substringBefore("?").substringBefore("#")
                    }
                return ShareReference(
                    shareId = shareId,
                    decryptionKey = queryKey,
                )
            }

            private fun String.extractParameter(name: String): String? =
                split("&")
                    .firstOrNull { segment -> segment.substringBefore("=") == name }
                    ?.substringAfter("=", missingDelimiterValue = "")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }

            private const val KEY_PARAMETER = "key"
        }
    }
}
