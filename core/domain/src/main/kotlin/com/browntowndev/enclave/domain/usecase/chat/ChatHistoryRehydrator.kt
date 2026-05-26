package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.ChatSummary
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageVisionAnalysis
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.port.inference.CommittedInferencePromptPort
import com.browntowndev.enclave.domain.port.inference.LlmInferencePort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.NoopCommittedInferencePromptPort
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Loads message history for a chat turn and hands it to [LlmInferencePort.setHistory].
 *
 * Responsibilities (AGENT-037):
 *  - Filter out the active user + assistant message pair
 *  - Apply the summary pointer to slice the DB result set
 *  - Prefer committed inference prompt content for prior USER messages when present
 *  - Attach vision-analysis text and file content
 *  - Prepend the saved summary as a SYSTEM message
 *  - Append timestamp metadata to USER messages
 *
 * FIFO trimming and rolling summarization are handled by the Rust preflight compaction
 * pipeline (AGENT-018) inside `create_session` — they are not performed here.
 */
class ChatHistoryRehydrator(
    private val messageRepository: MessageRepository,
    private val loggingPort: LoggingPort,
    private val committedInferencePrompts: CommittedInferencePromptPort = NoopCommittedInferencePromptPort,
) {
    private val dateTimeFormatter =
        DateTimeFormatter
            .ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a")
            .withZone(ZoneId.systemDefault())
            .withLocale(Locale.getDefault())

    suspend operator fun invoke(
        chatId: ChatId,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        service: LlmInferencePort,
    ) {
        val summary = messageRepository.getChatSummary(chatId)
        val allMessages = messageRepository.getMessagesForChat(chatId)

        val relevantMessages =
            if (summary != null) {
                if (summary.lastSummarizedMessageId != null) {
                    val lastSummarizedIndex = allMessages.indexOfFirst { it.id == summary.lastSummarizedMessageId }
                    if (lastSummarizedIndex != -1) {
                        allMessages.subList(lastSummarizedIndex + 1, allMessages.size)
                    } else {
                        allMessages
                    }
                } else {
                    emptyList()
                }
            } else {
                allMessages
            }

        val filteredMessages =
            relevantMessages
                .filter { it.content.text.isNotBlank() || it.content.imageUris.isNotEmpty() || it.content.fileAttachments.isNotEmpty() }
                .filter { it.id != userMessageId }
                .filter { it.id != assistantMessageId }

        val analysesByMessage = messageRepository.getVisionAnalysesForMessages(filteredMessages.map { it.id })
        val chatMessages = buildChatMessages(summary, filteredMessages, analysesByMessage)

        val messagesWithMetadata =
            chatMessages.map { message ->
                if (message.role == Role.USER) {
                    val isCommittedPrompt =
                        filteredMessages.any { filteredMessage ->
                            committedInferencePrompt(filteredMessage) == message.content
                        }
                    val original =
                        if (isCommittedPrompt) {
                            null
                        } else {
                            filteredMessages.firstOrNull {
                                it.content.text == message.content.substringBefore("\n\n[Metadata:")
                            }
                        }
                    if (original != null && original.createdAt != null) {
                        val formattedTime = dateTimeFormatter.format(java.time.Instant.ofEpochMilli(original.createdAt))
                        message.copy(content = "${message.content}\n\n[Metadata: Sent at $formattedTime]")
                    } else {
                        message
                    }
                } else {
                    message
                }
            }

        service.setHistory(messagesWithMetadata)
        loggingPort.info(TAG, "Rehydrated ${chatMessages.size} messages (summary included: ${summary != null})")
    }

    private fun buildChatMessages(
        summary: ChatSummary?,
        filteredMessages: List<Message>,
        analysesByMessage: Map<MessageId, List<MessageVisionAnalysis>>,
    ): List<ChatMessage> {
        val chatMessages = mutableListOf<ChatMessage>()
        if (summary != null) {
            chatMessages.add(ChatMessage(role = Role.SYSTEM, content = "Summary of previous conversation:\n${summary.content}"))
        }
        chatMessages.addAll(
            filteredMessages.map { message ->
                ChatMessage(
                    role = message.role,
                    content = buildHistoryContent(message, analysesByMessage[message.id].orEmpty()),
                )
            },
        )
        return chatMessages
    }

    private fun buildHistoryContent(
        message: Message,
        visionAnalyses: List<MessageVisionAnalysis>,
    ): String {
        committedInferencePrompt(message)?.let { return it }

        val fileContent =
            if (message.content.fileAttachments.isNotEmpty()) {
                message.content.fileAttachments.joinToString(separator = "\n\n") { file ->
                    "File attached: ${file.name}\nContent:\n${file.content}"
                }
            } else {
                null
            }

        val visionContent =
            if (visionAnalyses.isNotEmpty()) {
                visionAnalyses.joinToString(separator = "\n\n") { analysis ->
                    "Attached image description:\n${analysis.analysisText}"
                }
            } else {
                null
            }

        val mainText = message.content.text

        return listOfNotNull(fileContent, visionContent, mainText)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n\n")
            .ifBlank {
                if (message.content.imageUris.isNotEmpty()) "[User attached one or more images]" else ""
            }
    }

    private fun committedInferencePrompt(message: Message): String? =
        if (message.role == Role.USER) {
            committedInferencePrompts.get(message.id)?.takeIf { it.isNotBlank() }
        } else {
            null
        }

    private companion object {
        private const val TAG = "ChatHistoryRehydrator"
    }
}
