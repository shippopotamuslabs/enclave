package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageVisionAnalysis
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository

internal class ChatHistoryRehydrator(
    private val messageRepository: MessageRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        chatId: ChatId,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        service: LlmInferencePort,
    ) {
        val messages = messageRepository.getMessagesForChat(chatId)
            .filter { it.content.text.isNotBlank() || it.content.imageUri != null }
            .filter { it.id != userMessageId }
            .filter { it.id != assistantMessageId }
        val analysesByMessage = messageRepository.getVisionAnalysesForMessages(messages.map { it.id })

        val chatMessages = messages.map { message ->
            ChatMessage(
                role = message.role,
                content = buildHistoryContent(message, analysesByMessage[message.id].orEmpty()),
            )
        }

        service.setHistory(chatMessages)
        loggingPort.debug(TAG, "Rehydrated ${chatMessages.size} messages")
    }

    private fun buildHistoryContent(
        message: Message,
        visionAnalyses: List<MessageVisionAnalysis>,
    ): String {
        if (visionAnalyses.isEmpty()) {
            return message.content.text.ifBlank {
                if (message.content.imageUri != null) "[User attached an image]" else ""
            }
        }

        val analysisBlock = visionAnalyses.joinToString(separator = "\n\n") { analysis ->
            """
            Attached image description:
            ${analysis.analysisText}
            """.trimIndent()
        }

        return if (message.content.text.isBlank()) {
            analysisBlock
        } else {
            """
            $analysisBlock

            User request:
            ${message.content.text}
            """.trimIndent()
        }
    }

    private companion object {
        private const val TAG = "GenerateChatResponse"
    }
}
