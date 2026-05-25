package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import com.browntowndev.enclave.domain.port.repository.MessageRepository

internal class PersistAccumulatedChatMessagesUseCase(
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val embeddingEngine: EmbeddingEnginePort,
    private val extractedUrls: Set<String>,
) {
    suspend operator fun invoke(accumulatorManager: ChatGenerationAccumulatorManager) {
        // Apply extracted flags from the tracker before persisting.
        // This ensures the DB write has extracted=true even if the accumulator
        // snapshot was created before the extraction event was processed.
        val extractedUrls = extractedUrls
        if (extractedUrls.isNotEmpty()) {
            accumulatorManager.markWebSearchSourcesExtracted(extractedUrls.toList())
        }

        accumulatorManager.messages.values.forEach { accumulator ->
            val content = sanitizePersistedContent(accumulator.content.toString())
            chatRepository.persistAllMessageData(
                messageId = accumulator.messageId,
                modelType = accumulator.modelType,
                thinkingStartTime = accumulator.thinkingStartTime ?: 0L,
                thinkingEndTime = accumulator.thinkingEndTime ?: 0L,
                thinkingDuration = accumulator.thinkingDurationSeconds.toInt(),
                thinkingRaw = accumulator.thinkingRaw.toString().ifBlank { null },
                content = content,
                messageState = accumulator.currentState,
                pipelineStep = getPipelineStepForModelType(accumulator.modelType),
                webSearchSources = accumulator.webSearchSources.toList(),
                artifacts = accumulator.artifacts.toList(),
                generatedMediaUris = accumulator.generatedMediaUris.toList(),
                generatedMediaAssetIds = accumulator.generatedMediaAssetIds.toList(),
                generatedMediaTypes = accumulator.generatedMediaTypes.toList(),
                generatedMediaPrompts = accumulator.generatedMediaPrompts.toList(),
                transcriptSegments = accumulator.toTranscriptSegments(),
                telemetry = accumulator.telemetryRows.toList(),
            )

            // Generate and save embedding for the assistant message
            if (content.isNotBlank()) {
                val embedding = embeddingEngine.getEmbedding(content)
                messageRepository.saveEmbedding(accumulator.messageId, embedding)
            }
        }

        // Re-apply extracted flags after persisting sources.
        // The extract tool may have called markExtracted before sources were persisted,
        // so the DAO update was a no-op. Now that sources exist in the DB, apply the flags.
        if (extractedUrls.isNotEmpty()) {
            chatRepository.markWebSearchSourcesExtracted(extractedUrls.toList())
        }
    }

    private fun sanitizePersistedContent(content: String): String {
        val withoutToolCalls = TOOL_CALL_TRACE_REGEX.replace(content, "")
        return TOOL_RESULT_TRACE_REGEX.replace(withoutToolCalls, "").trim().take(MAX_PERSIST_CONTENT_CHARS)
    }

    private companion object {
        private const val MAX_PERSIST_CONTENT_CHARS = 100_000

        @Suppress("MaxLineLength")
        private val TOOL_CALL_TRACE_REGEX =
            Regex(
                """(?s)<tool_call\b[^>]*>.*?</tool_call>|<!\[CDATA\[<tool>\s*\{.*?\}\s*</tool>\]\]>|autocomplete\s*\{.*?\}\s*autocomplete|,?\{"name"\s*:\s*"[^"]+"\s*,\s*"arguments"\s*:\s*\{[^}]*\}\s*\}\s*,?""",
            )
        private val TOOL_RESULT_TRACE_REGEX = Regex("(?s)<tool_result>.*?</tool_result>")
    }
}
