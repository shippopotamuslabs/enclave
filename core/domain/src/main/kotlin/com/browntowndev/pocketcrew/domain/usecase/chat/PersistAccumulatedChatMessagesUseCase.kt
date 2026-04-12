package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository

internal class PersistAccumulatedChatMessagesUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(accumulatorManager: ChatGenerationAccumulatorManager) {
        accumulatorManager.messages.values.forEach { accumulator ->
            chatRepository.persistAllMessageData(
                messageId = accumulator.messageId,
                modelType = accumulator.modelType,
                thinkingStartTime = accumulator.thinkingStartTime ?: 0L,
                thinkingEndTime = accumulator.thinkingEndTime ?: 0L,
                thinkingDuration = accumulator.thinkingDurationSeconds.toInt(),
                thinkingRaw = accumulator.thinkingRaw.toString().ifBlank { null },
                content = sanitizePersistedContent(accumulator.content.toString()),
                messageState = if (accumulator.isComplete) {
                    MessageState.COMPLETE
                } else {
                    MessageState.PROCESSING
                },
                pipelineStep = getPipelineStepForModelType(accumulator.modelType),
            )
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
