package com.browntowndev.pocketcrew.domain.usecase.chat

import javax.inject.Inject

/**
 * Use case for processing streaming text chunks and detecting thinking tokens.
 *
 * This handles the chunk-by-chunk processing of LLM output, detecting
 * "<think>" and "</think>" tokens to determine whether content is reasoning
 * or final response.
 *
 * It also handles split tokens - when a token like "<think>" is split
 * across multiple chunks (e.g., "<" then "think>").
 */
class ProcessThinkingTokensUseCase @Inject constructor() {

    companion object {
        private const val START_TOKEN = "<think>"
        private const val END_TOKEN = "</think>"
    }

    /**
     * Process a chunk of text and update the thinking state.
     *
     * @param currentBuffer The current buffer of pending text (may be empty)
     * @param newChunk The new chunk from the stream
     * @param isThinking Whether we're currently in thinking mode
     * @return Triple of (isThinking, textToEmit, newBuffer)
     *   - isThinking: Updated thinking state
     *   - textToEmit: Text that should be emitted as either thinking or response
     *   - newBuffer: Updated buffer for next iteration
     */
    operator fun invoke(
        currentBuffer: String,
        newChunk: String,
        isThinking: Boolean
    ): ThinkingState {
        var buffer = currentBuffer + newChunk
        val textToEmit = StringBuilder()
        var thinking = isThinking

        // Process until no more complete tokens in buffer
        while (buffer.isNotEmpty()) {
            val startIdx = buffer.indexOf(START_TOKEN)
            val endIdx = buffer.indexOf(END_TOKEN)

            when {
                // Both tokens present - process first
                startIdx >= 0 && endIdx >= 0 -> {
                    if (startIdx < endIdx) {
                        // Start token first
                        if (startIdx > 0) {
                            textToEmit.append(buffer.substring(0, startIdx))
                        }
                        thinking = true
                        val afterStart = buffer.substring(startIdx + START_TOKEN.length)

                        if (afterStart.startsWith(END_TOKEN)) {
                            // Both tokens back-to-back
                            val afterEnd = afterStart.substring(END_TOKEN.length)
                            thinking = false
                            if (afterEnd.isNotEmpty()) {
                                textToEmit.append(afterEnd)
                            }
                            buffer = ""
                        } else {
                            buffer = afterStart
                        }
                    } else {
                        // End token first
                        if (endIdx > 0) {
                            textToEmit.append(buffer.substring(0, endIdx))
                        }
                        thinking = false
                        buffer = buffer.substring(endIdx + END_TOKEN.length)
                    }
                }
                // Only start token
                startIdx >= 0 -> {
                    if (startIdx > 0) {
                        textToEmit.append(buffer.substring(0, startIdx))
                    }
                    thinking = true
                    buffer = buffer.substring(startIdx + START_TOKEN.length)
                }
                // Only end token
                endIdx >= 0 -> {
                    if (endIdx > 0) {
                        textToEmit.append(buffer.substring(0, endIdx))
                    }
                    thinking = false
                    buffer = buffer.substring(endIdx + END_TOKEN.length)
                }
                // No complete tokens - buffer if looks like partial token
                else -> {
                    if (buffer.startsWith("<")) {
                        // Partial token - keep buffering
                        // This handles cases like "<t" + "hink>" = "<think>" which isn't "<think>"
                        // but could become a valid token with more characters
                        break
                    } else {
                        textToEmit.append(buffer)
                        buffer = ""
                    }
                }
            }
        }

        return ThinkingState(
            isThinking = thinking,
            textToEmit = textToEmit.toString(),
            buffer = buffer
        )
    }

    data class ThinkingState(
        val isThinking: Boolean,
        val textToEmit: String,
        val buffer: String
    )
}