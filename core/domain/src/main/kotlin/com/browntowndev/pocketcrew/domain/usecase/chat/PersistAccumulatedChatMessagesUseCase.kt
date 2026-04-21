package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository

internal class PersistAccumulatedChatMessagesUseCase(
    private val chatRepository: ChatRepository,
    private val extractedUrls: Set<String>,
) {
    suspend operator fun invoke(accumulatorManager: ChatGenerationAccumulatorManager) {
        // Apply extracted flags from the tracker before persisting.
        // This ensures the DB write has extracted=true even if the accumulator
        // snapshot was created before the extraction event was processed.
        val extractedUrls = extractedUrls
        if (extractedUrls.isNotEmpty()) {
            accumulatorManager.markSourcesExtracted(extractedUrls.toList())
        }

        accumulatorManager.messages.values.forEach { accumulator ->
            chatRepository.persistAllMessageData(
                messageId = accumulator.messageId,
                modelType = accumulator.modelType,
                thinkingStartTime = accumulator.thinkingStartTime ?: 0L,
                thinkingEndTime = accumulator.thinkingEndTime ?: 0L,
                thinkingDuration = accumulator.thinkingDurationSeconds.toInt(),
                thinkingRaw = accumulator.thinkingRaw.toString().ifBlank { null },
                content = sanitizePersistedContent(accumulator.content.toString()),
                messageState = accumulator.currentState,
                pipelineStep = getPipelineStepForModelType(accumulator.modelType),
                tavilySources = accumulator.tavilySources.toList(),
            )
        }

        // Re-apply extracted flags after persisting sources.
        // The extract tool may have called markExtracted before sources were persisted,
        // so the DAO update was a no-op. Now that sources exist in the DB, apply the flags.
        if (extractedUrls.isNotEmpty()) {
            chatRepository.markSourcesExtracted(extractedUrls.toList())
        }
    }

    private fun sanitizePersistedContent(content: String): String =
        TOOL_RESULT_TRACE_REGEX
            .replace(TOOL_CALL_TRACE_REGEX.replace(content, ""), "")
            .trim()

    private companion object {
        private val TOOL_CALL_TRACE_REGEX = Regex(
            """(?s)<!\[CDATA\[<tool>\s*\{.*?\}\s*</tool>\]\]>|<tool_call>\s*\{.*?\}\s*</tool_call>|,?\{"name"\s*:\s*"[^"]+"\s*,\s*"arguments"\s*:\s*\{[^}]*\}\s*\}\s*,?"""
        )
        private val TOOL_RESULT_TRACE_REGEX = Regex("(?s)<tool_result>.*?</tool_result>")
    }
}
