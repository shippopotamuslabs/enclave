package com.browntowndev.pocketcrew.domain.usecase.chat

import javax.inject.Inject

/**
 * Use case for buffering thinking text into discrete steps using Streaming Markdown Block Detection.
 *
 * This replaces the linguistic sentence-boundary approach (BreakIterator) with structural
 * Markdown boundary detection designed for 1.2B-2B parameter models that format reasoning
 * using Markdown (lists, code blocks, math equations).
 *
 * State machine behavior:
 * - Tracks isInCodeBlock: toggled by matched pairs of ```
 * - Tracks isInMathBlock: toggled by matched pairs of $ or \[ \]
 *
 * When state locks are ACTIVE: accumulate text but DO NOT emit a chunk
 * When state locks are INACTIVE: split at definitive Markdown block boundaries
 *
 * Block boundaries:
 * - Double newlines (\n\n) representing paragraph breaks
 * - List item markers (\n- , \n* , \n1. , etc.)
 *
 * Length fallback: Only applies if unstructured paragraph runs excessively long,
 * but never triggers mid-code-block or mid-math-block.
 */
class BufferThinkingStepsUseCase @Inject constructor() {

    companion object {
        // Matches double newlines for paragraph breaks
        private val PARAGRAPH_BREAK_REGEX = Regex("""\n\s*\n""")

        // Matches list markers: - , * , + at start of line (after newline)
        private val LIST_MARKER_REGEX = Regex("""\n\s*([-*+]|\d+[.)])(\s*)""")

        private const val HARD_MAX_CHARS_BEFORE_FORCE = 500
    }

    // State machine state
    private var isInCodeBlock = false
    private var isInMathBlock = false

    private val buffer = StringBuilder(1024)

    /**
     * Processes incoming thinking text and returns any completed thoughts.
     * May return multiple thoughts if several chunks completed.
     *
     * @param incomingText new text chunk from the inference stream
     * @return list of completed thought strings (can be empty)
     */
    operator fun invoke(incomingText: String): List<String> {
        if (incomingText.isBlank()) return emptyList()

        buffer.append(incomingText)
        return processBuffer()
    }

    private fun processBuffer(): List<String> {
        val emitted = mutableListOf<String>()
        val text = buffer.toString()

        // Store previous state to detect transitions
        val wasInCodeBlock = isInCodeBlock
        val wasInMathBlock = isInMathBlock

        // Update state locks - check entire buffer to determine current state
        updateStateLocks(text)

        // Detect EXIT from protected environment - emit everything
        if (wasInCodeBlock && !isInCodeBlock) {
            // Exited code block - emit everything
            val content = text.trim()
            if (content.isNotBlank()) {
                emitted.add(content)
            }
            buffer.clear()
            return emitted
        }

        if (wasInMathBlock && !isInMathBlock) {
            // Exited math block - emit everything
            val content = text.trim()
            if (content.isNotBlank()) {
                emitted.add(content)
            }
            buffer.clear()
            return emitted
        }

        // If inside a protected environment (code block or math block), don't emit
        if (isInCodeBlock || isInMathBlock) {
            return emitted
        }

        // Check if we have complete code blocks (balanced delimiters) - emit them
        val codeBlockCount = text.split("```").size - 1
        if (codeBlockCount > 0 && codeBlockCount % 2 == 0) {
            // We have complete code block(s) - emit everything
            val content = text.trim()
            if (content.isNotBlank()) {
                emitted.add(content)
            }
            buffer.clear()
            return emitted
        }

        // Find paragraph breaks first
        val paragraphMatches = PARAGRAPH_BREAK_REGEX.findAll(text)

        // Find list markers
        val listMatches = LIST_MARKER_REGEX.findAll(text)

        // Combine and sort all boundary positions
        val paragraphPositions = paragraphMatches.map { it.range.first }.toList()
        val listPositions = listMatches.map { it.range.first }.toList()
        val allBoundaries = (paragraphPositions + listPositions).distinct().sorted()

        // If no boundaries found, check for length fallback
        if (allBoundaries.isEmpty()) {
            if (buffer.length > HARD_MAX_CHARS_BEFORE_FORCE) {
                val content = buffer.toString().trim()
                if (content.isNotBlank()) {
                    emitted.add(content)
                    buffer.clear()
                }
            }
            return emitted
        }

        // Process chunks up to each boundary
        var lastBoundaryEnd = 0
        for (boundaryStart in allBoundaries) {
            // Find the actual boundary end by checking what type it is
            val isParagraphBreak = paragraphMatches.any { it.range.first == boundaryStart }
            val listMatch = listMatches.find { it.range.first == boundaryStart }

            val boundaryEnd = when {
                isParagraphBreak -> {
                    // For paragraph break, find the end of the \n\s*\n match
                    val match = PARAGRAPH_BREAK_REGEX.find(text, boundaryStart)
                    match?.range?.last?.plus(1) ?: (boundaryStart + 1)
                }
                listMatch != null -> {
                    // For list marker, include the marker and following whitespace in the NEXT chunk
                    // So we end the current chunk at the boundary start
                    boundaryStart
                }
                else -> boundaryStart + 1
            }

            // Extract the chunk before this boundary
            val chunk = text.substring(lastBoundaryEnd, boundaryStart).trim()
            if (chunk.isNotBlank()) {
                emitted.add(chunk)
            }

            lastBoundaryEnd = boundaryEnd
        }

        // Keep unprocessed portion in buffer
        if (lastBoundaryEnd > 0) {
            if (lastBoundaryEnd >= text.length) {
                buffer.clear()
            } else {
                buffer.delete(0, lastBoundaryEnd)
            }
        }

        return emitted
    }

    /**
     * Updates the state locks by checking the entire buffer.
     * This correctly tracks whether we're inside a protected Markdown environment.
     */
    private fun updateStateLocks(text: String) {
        if (text.isEmpty()) return

        // Count code block delimiters (```)
        val codeDelimiters = text.split("```").size - 1
        isInCodeBlock = codeDelimiters % 2 != 0

        // For math blocks, we need more careful handling
        // Inline math: $...$
        // Block math: \[ ... \]
        val inlineMathDelimiters = text.split("$").size - 1

        // If we have an odd number of $, we're in math mode
        isInMathBlock = inlineMathDelimiters % 2 != 0

        // Also check for block math \[ \] - simpler approach
        // Count occurrences of \[ and \]
        val openBrackets = text.count { it == '[' }
        val closeBrackets = text.count { it == ']' }

        // If we have more opens than closes, we're in block math
        if (openBrackets > closeBrackets) {
            isInMathBlock = true
        }
    }

    /**
     * Flushes any remaining text in the buffer as a final thought.
     * Call this when thinking is complete.
     *
     * @return a thought string with remaining text, or null if buffer is empty
     */
    fun flush(): String? {
        if (buffer.isBlank()) {
            reset()
            return null
        }

        // For flush, we emit everything regardless of protected state
        // This ensures no content is lost
        val remaining = buffer.toString().trim()
            .ifBlank { null }

        reset()
        return remaining
    }

    /**
     * Resets the buffer and state. Call this when starting a new thinking session.
     */
    fun reset() {
        buffer.clear()
        isInCodeBlock = false
        isInMathBlock = false
    }

    /**
     * Gets the current buffered steps without resetting.
     * Used for capturing thinking steps at step completion.
     */
    fun getBufferedSteps(): List<String> {
        return if (buffer.isBlank()) {
            emptyList()
        } else {
            listOf(buffer.toString())
        }
    }

    /**
     * Checks if currently inside a protected Markdown environment.
     */
    fun isInProtectedEnvironment(): Boolean = isInCodeBlock || isInMathBlock
}
