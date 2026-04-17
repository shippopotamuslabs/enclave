package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.ChatSummary
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageVisionAnalysis
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.port.inference.CompactionPort
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.util.ChatHistoryCompressor
import com.browntowndev.pocketcrew.domain.util.ContextWindowPlanner
import com.browntowndev.pocketcrew.domain.util.JTokkitTokenCounter
import com.browntowndev.pocketcrew.domain.util.TokenCounter

internal class ChatHistoryRehydrator(
    private val messageRepository: MessageRepository,
    private val compactionPort: CompactionPort,
    private val loggingPort: LoggingPort,
    private val tokenCounter: TokenCounter = JTokkitTokenCounter,
) {
    private val rollingSummarizer = RollingSummarizer(loggingPort, tokenCounter)
    suspend operator fun invoke(
        chatId: ChatId,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        service: LlmInferencePort,
        contextWindowTokens: Int = 4096, // Fallback
        allowLocalSummarization: Boolean = true,
        currentPrompt: String = "",
        options: GenerationOptions = GenerationOptions(reasoningBudget = 0),
    ) {
        var summary = messageRepository.getChatSummary(chatId)
        val allMessages = messageRepository.getMessagesForChat(chatId)

        // If a summary exists, filter out messages that are older than or equal to the last summarized message
        var relevantMessages = if (summary?.lastSummarizedMessageId != null) {
            val lastSummarizedIndex = allMessages.indexOfFirst { it.id == summary.lastSummarizedMessageId }
            if (lastSummarizedIndex != -1) {
                allMessages.subList(lastSummarizedIndex + 1, allMessages.size)
            } else {
                allMessages
            }
        } else {
            allMessages
        }

        var filteredMessages = relevantMessages
            .filter { it.content.text.isNotBlank() || it.content.imageUri != null }
            .filter { it.id != userMessageId }
            .filter { it.id != assistantMessageId }

        var analysesByMessage = messageRepository.getVisionAnalysesForMessages(filteredMessages.map { it.id })

        var chatMessages = buildChatMessages(summary, filteredMessages, analysesByMessage)

        val budget = ContextWindowPlanner.budgetFor(
            contextWindowTokens = contextWindowTokens,
            options = options,
            tokenCounter = tokenCounter,
        )
        val estimatedPromptTokens = ContextWindowPlanner.estimatePromptTokens(
            history = chatMessages,
            systemPrompt = options.systemPrompt,
            currentPrompt = currentPrompt,
            tokenCounter = tokenCounter,
        )

        if (ContextWindowPlanner.shouldCompact(estimatedPromptTokens, budget)) {
            loggingPort.info(
                TAG,
                "Compaction triggered: estimatedTokens=$estimatedPromptTokens usablePromptTokens=${budget.usablePromptTokens} thresholdTokens=${budget.thresholdTokens} window=$contextWindowTokens"
            )
            val compactedText = compactionPort.compactHistory(chatMessages)
            if (compactedText != null) {
                val lastMessageId = filteredMessages.lastOrNull()?.id ?: summary?.lastSummarizedMessageId
                val newSummary = ChatSummary(
                    chatId = chatId,
                    content = compactedText,
                    lastSummarizedMessageId = lastMessageId,
                )
                messageRepository.saveChatSummary(newSummary)

                // Refresh local state after successful compaction
                summary = newSummary
                relevantMessages = emptyList() // All summarized
                filteredMessages = emptyList()
                analysesByMessage = emptyMap()
                chatMessages = buildChatMessages(summary, filteredMessages, analysesByMessage)
                loggingPort.info(
                    TAG,
                    "Compaction successful. New summary size: ${tokenCounter.countTokens(compactedText)} tokens",
                )
            } else {
                // Fallback: Rolling Summarization
                // Do not run local summarization for local models; it uses the same overloaded engine.
                if (!allowLocalSummarization) {
                    loggingPort.info(
                        TAG,
                        "Compaction provider disabled or failed, and local summarization is not allowed. Falling through to FIFO trimming.",
                    )
                } else {
                    loggingPort.info(TAG, "Compaction provider disabled or failed, attempting rolling summarization")
                    service.setHistory(emptyList())
                    val summaryText = rollingSummarizer.summarize(chatMessages, service)
                    if (summaryText != null) {
                        // Find the split point: we'll use the message index corresponding to the 50% token split
                        val systemPromptCount = if (chatMessages.firstOrNull()?.role == Role.SYSTEM) 1 else 0
                        val totalTokens = chatMessages.drop(systemPromptCount).sumOf { tokenCounter.countTokens(it.content) }
                        val targetTokens = totalTokens / 2

                        var accumulatedTokens = 0
                        var splitIndexInHistory = 0
                        for (i in filteredMessages.indices) {
                            accumulatedTokens += tokenCounter.countTokens(filteredMessages[i].content.text)
                            if (accumulatedTokens >= targetTokens) {
                                splitIndexInHistory = i
                                break
                            }
                        }

                        val lastSummarizedMsgId =
                            filteredMessages.getOrNull(splitIndexInHistory)?.id ?: summary?.lastSummarizedMessageId

                        val newSummary = ChatSummary(
                            chatId = chatId,
                            content = summaryText,
                            lastSummarizedMessageId = lastSummarizedMsgId,
                        )
                        messageRepository.saveChatSummary(newSummary)

                        // Refresh local state
                        summary = newSummary
                        loggingPort.info(TAG, "Rolling summarization successful")

                        // Re-calculate chatMessages for the current turn
                        val refreshedAllMessages = messageRepository.getMessagesForChat(chatId)
                        val refreshedRelevantMessages = if (newSummary.lastSummarizedMessageId != null) {
                            val lastIndex = refreshedAllMessages.indexOfFirst { it.id == newSummary.lastSummarizedMessageId }
                            if (lastIndex != -1) {
                                refreshedAllMessages.subList(lastIndex + 1, refreshedAllMessages.size)
                            } else {
                                refreshedAllMessages
                            }
                        } else refreshedAllMessages

                        val refreshedFilteredMessages = refreshedRelevantMessages
                            .filter { it.content.text.isNotBlank() || it.content.imageUri != null }
                            .filter { it.id != userMessageId }
                            .filter { it.id != assistantMessageId }

                        chatMessages = buildChatMessages(
                            summary,
                            refreshedFilteredMessages,
                            messageRepository.getVisionAnalysesForMessages(refreshedFilteredMessages.map { it.id }),
                        )
                    }
                }
            }
        }

        chatMessages = ChatHistoryCompressor.compressHistory(
            history = chatMessages,
            systemPrompt = options.systemPrompt.orEmpty(),
            currentPrompt = currentPrompt,
            contextWindowTokens = contextWindowTokens,
            bufferTokens = budget.reservedTokens,
            tokenCounter = tokenCounter,
        )
        service.setHistory(chatMessages)
        loggingPort.debug(
            TAG,
            "Rehydrated ${chatMessages.size} messages (summary included: ${summary != null})",
        )
    }

    private fun buildChatMessages(
        summary: ChatSummary?,
        filteredMessages: List<Message>,
        analysesByMessage: Map<MessageId, List<MessageVisionAnalysis>>,
    ): List<ChatMessage> {
        val chatMessages = mutableListOf<ChatMessage>()

        // Prepend summary if it exists
        if (summary != null) {
            chatMessages.add(
                ChatMessage(
                    role = Role.SYSTEM,
                    content = "Summary of previous conversation:\n${summary.content}",
                )
            )
        }

        chatMessages.addAll(filteredMessages.map { message ->
            ChatMessage(
                role = message.role,
                content = buildHistoryContent(message, analysesByMessage[message.id].orEmpty()),
            )
        })

        return chatMessages
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
