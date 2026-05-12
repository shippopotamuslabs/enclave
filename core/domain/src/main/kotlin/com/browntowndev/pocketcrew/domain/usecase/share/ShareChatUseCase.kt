package com.browntowndev.pocketcrew.domain.usecase.share

import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.share.ShareOption
import com.browntowndev.pocketcrew.domain.model.share.SharedChat
import com.browntowndev.pocketcrew.domain.port.repository.ShareRepositoryPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ShareChatUseCase @Inject constructor(
    private val shareRepository: ShareRepositoryPort,
    private val chatUseCases: ChatUseCases
) {
    suspend operator fun invoke(option: ShareOption): Result<SharedChat> {
        return runCatching {
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
    }

    suspend fun getSharedChat(shareId: String): Result<SharedChat?> {
        return runCatching {
            shareRepository.getSharedChat(shareId)
        }
    }
}
