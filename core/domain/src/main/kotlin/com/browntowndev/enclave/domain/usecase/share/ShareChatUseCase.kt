package com.browntowndev.enclave.domain.usecase.share

import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.share.ShareOption
import com.browntowndev.enclave.domain.model.share.SharedChat
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.ShareRepositoryPort
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import kotlinx.coroutines.flow.first
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

open class ShareChatUseCase
    @Inject
    constructor(
        private val shareRepository: ShareRepositoryPort,
        private val chatUseCases: ChatUseCases,
        private val loggingPort: LoggingPort,
    ) {
        companion object {
            private const val TAG = "ShareChatUseCase"
        }

        open suspend operator fun invoke(option: ShareOption): Result<SharedChat> =
            try {
                Result.success(shareChat(option))
            } catch (e: Exception) {
                loggingPort.error(TAG, "Failed to share chat", e)
                Result.failure(Exception(ShareFailureMapper.userMessageFor(e), e))
            }

        open suspend fun getSharedChat(
            shareReference: String,
            decryptionKey: String? = null,
        ): Result<SharedChat?> =
            try {
                val parsed = ShareReference.parse(shareReference, decryptionKey)
                Result.success(shareRepository.getSharedChat(parsed.shareId, parsed.decryptionKey))
            } catch (e: Exception) {
                loggingPort.error(TAG, "Failed to load shared chat", e)
                Result.failure(Exception(ShareFailureMapper.userMessageFor(e), e))
            }

        private suspend fun shareChat(option: ShareOption): SharedChat {
            val chatId =
                when (option) {
                    is ShareOption.SingleTurn -> option.chatId
                    is ShareOption.FullConversation -> option.chatId
                }

            val allMessages = chatUseCases.getChat(chatId).first { it.isNotEmpty() }
            val messagesToShare =
                when (option) {
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

            return shareRepository.shareChat(messagesToShare, chatId)
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
