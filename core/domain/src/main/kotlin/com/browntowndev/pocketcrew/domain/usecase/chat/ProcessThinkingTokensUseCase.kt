package com.browntowndev.pocketcrew.domain.usecase.chat

import javax.inject.Inject
import kotlin.math.max

/**
 * Streaming parser for mixed visible assistant text and hidden thinking text.
 *
 * Supports:
 * 1) Interleaved visible text + <think>...</think> blocks (and other token types like [THINK])
 * 2) Standard "all thinking first, then answer" flows
 * 3) Tokens split across chunk boundaries
 *
 * Example:
 * "Hello <thi" + "nk>reasoning</th" + "ink> world"
 *
 * Output:
 * VISIBLE("Hello ")
 * THINKING("reasoning")
 * VISIBLE(" world")
 */
class ProcessThinkingTokensUseCase @Inject constructor() {

    companion object {
        private val START_TOKENS = listOf("<think>", "[THINK]")
        private val END_TOKENS = listOf("</think>", "[/THINK]")
        private val ALL_TOKENS = START_TOKENS + END_TOKENS
    }

    /**
     * Processes a newly arrived chunk.
     *
     * @param currentBuffer leftover partial-token buffer from the previous call
     * @param newChunk new streamed text chunk
     * @param isThinking whether parser is currently inside a <think> block
     *
     * @return updated parser state plus typed emitted segments
     */
    operator fun invoke(
        currentBuffer: String,
        newChunk: String,
        isThinking: Boolean
    ): ThinkingState {
        var buffer = currentBuffer + newChunk
        var thinking = isThinking
        val emitted = mutableListOf<EmittedSegment>()

        while (buffer.isNotEmpty()) {
            // Find the earliest start token occurrence (regardless of current thinking state)
            var startIdx = -1
            var matchedStartToken = ""
            for (token in START_TOKENS) {
                val idx = buffer.indexOf(token)
                if (idx >= 0 && (startIdx == -1 || idx < startIdx)) {
                    startIdx = idx
                    matchedStartToken = token
                }
            }

            // Find the earliest end token occurrence
            var endIdx = -1
            var matchedEndToken = ""
            for (token in END_TOKENS) {
                val idx = buffer.indexOf(token)
                if (idx >= 0 && (endIdx == -1 || idx < endIdx)) {
                    endIdx = idx
                    matchedEndToken = token
                }
            }

            when {
                // Both tokens present - process the first one found
                startIdx >= 0 && endIdx >= 0 -> {
                    if (startIdx < endIdx) {
                        // Start token comes first
                        if (startIdx > 0) {
                            emitted += EmittedSegment(
                                kind = if (thinking) SegmentKind.THINKING else SegmentKind.VISIBLE,
                                text = buffer.substring(0, startIdx)
                            )
                        }
                        thinking = true
                        buffer = buffer.substring(startIdx + matchedStartToken.length)
                    } else {
                        // End token comes first
                        if (endIdx > 0) {
                            var textToEmit = buffer.substring(0, endIdx)
                            // Trim leading newline when transitioning from thinking to visible
                            // The END_TOKEN is "\n</think>\n" so after it there's often a leading newline
                            if (textToEmit.startsWith("\n")) {
                                textToEmit = textToEmit.drop(1)
                            }
                            emitted += EmittedSegment(
                                kind = if (thinking) SegmentKind.THINKING else SegmentKind.VISIBLE,
                                text = textToEmit
                            )
                        }
                        thinking = false
                        buffer = buffer.substring(endIdx + matchedEndToken.length)
                    }
                }
                // Only start token
                startIdx >= 0 -> {
                    if (startIdx > 0) {
                        emitted += EmittedSegment(
                            kind = if (thinking) SegmentKind.THINKING else SegmentKind.VISIBLE,
                            text = buffer.substring(0, startIdx)
                        )
                    }
                    thinking = true
                    buffer = buffer.substring(startIdx + matchedStartToken.length)
                }
                // Only end token
                endIdx >= 0 -> {
                    if (endIdx > 0) {
                        var textToEmit = buffer.substring(0, endIdx)
                        // Trim leading newline when transitioning from thinking to visible
                        if (textToEmit.startsWith("\n")) {
                            textToEmit = textToEmit.drop(1)
                        }
                        emitted += EmittedSegment(
                            kind = if (thinking) SegmentKind.THINKING else SegmentKind.VISIBLE,
                            text = textToEmit
                        )
                    }
                    thinking = false
                    buffer = buffer.substring(endIdx + matchedEndToken.length)
                }
                // No complete tokens - check for partial tokens
                else -> {
                    val suffixToKeep = longestPossibleTokenPrefixSuffix(buffer)

                    val safeEmitLength = buffer.length - suffixToKeep.length
                    if (safeEmitLength > 0) {
                        emitted += EmittedSegment(
                            kind = if (thinking) SegmentKind.THINKING else SegmentKind.VISIBLE,
                            text = buffer.substring(0, safeEmitLength)
                        )
                        buffer = buffer.substring(safeEmitLength)
                    } else {
                        // Entire buffer could still become a token prefix, so hold it.
                        break
                    }
                }
            }
        }

        return ThinkingState(
            isThinking = thinking,
            buffer = buffer,
            emittedSegments = emitted.mergeAdjacent()
        )
    }

    /**
     * Finds the longest suffix of [text] that could be the prefix of either token.
     *
     * Examples:
     * - "abc<th"     -> "<th"
     * - "xyz</thi"   -> "</thi"
     * - "hello"      -> ""
     */
    private fun longestPossibleTokenPrefixSuffix(text: String): String {
        val maxTokenLength = ALL_TOKENS.maxOf { it.length }
        val start = max(0, text.length - maxTokenLength + 1)

        for (i in start until text.length) {
            val suffix = text.substring(i)
            if (ALL_TOKENS.any { token -> token.startsWith(suffix) }) {
                return suffix
            }
        }
        return ""
    }

    /**
     * Merge consecutive segments of the same kind to reduce noisy downstream handling.
     */
    private fun List<EmittedSegment>.mergeAdjacent(): List<EmittedSegment> {
        if (isEmpty()) return emptyList()

        val merged = mutableListOf<EmittedSegment>()
        var current = first()

        for (i in 1 until size) {
            val next = this[i]
            current = if (current.kind == next.kind) {
                current.copy(text = current.text + next.text)
            } else {
                merged += current
                next
            }
        }

        merged += current
        return merged
    }

    data class ThinkingState(
        val isThinking: Boolean,
        val buffer: String,
        val emittedSegments: List<EmittedSegment>
    ) {
        /**
         * Convenience accessors if the caller wants split channels.
         */
        val visibleTextToEmit: String
            get() = emittedSegments
                .asSequence()
                .filter { it.kind == SegmentKind.VISIBLE }
                .joinToString(separator = "") { it.text }

        val thinkingTextToEmit: String
            get() = emittedSegments
                .asSequence()
                .filter { it.kind == SegmentKind.THINKING }
                .joinToString(separator = "") { it.text }

        /**
         * Kept for easier migration, but ambiguous for mixed-mode output.
         * Prefer visibleTextToEmit / thinkingTextToEmit / emittedSegments.
         */
        val textToEmit: String
            get() = emittedSegments.joinToString(separator = "") { it.text }
    }

    data class EmittedSegment(
        val kind: SegmentKind,
        val text: String
    )

    enum class SegmentKind {
        VISIBLE,
        THINKING
    }
}
