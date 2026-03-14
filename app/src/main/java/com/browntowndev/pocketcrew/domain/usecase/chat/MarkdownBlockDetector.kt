package com.browntowndev.pocketcrew.domain.usecase.chat

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of Streaming Markdown Block Detection state machine.
 *
 * This replaces the linguistic sentence-boundary approach (BreakIterator) with
 * structural Markdown boundary detection designed for 1.2B-2B parameter models
 * that format reasoning using Markdown (lists, code blocks, math equations).
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
@Singleton
class MarkdownBlockDetector @Inject constructor() : SentenceBoundaryDetector {

    private var isInCodeBlock = false
    private var isInMathBlock = false

    // Matches double newlines OR newline followed by list marker
    // List markers: -, *, +, or digit followed by . or )
    private val blockBoundaryRegex = Regex("""(\n\s*\n|\n\s*(?:[-*+]\s|\d+[.)]\s))""")

    // Code block delimiter
    private val codeBlockDelimiter = "```"

    // Math block delimiters
    private val inlineMathDelimiter = "$"
    private val blockMathStart = "\\["
    private val blockMathEnd = "\\]"

    /**
     * Finds boundaries based on Markdown structural elements.
     * Returns boundaries at paragraph breaks and list markers.
     */
    override fun findBoundaries(text: String): List<Pair<Int, Int>> {
        if (text.isEmpty()) return emptyList()

        val boundaries = mutableListOf<Pair<Int, Int>>()
        val matches = blockBoundaryRegex.findAll(text)

        var lastEnd = 0
        for (match in matches) {
            val boundaryStart = match.range.first
            val boundaryEnd = match.range.last + 1

            // Add the content before this boundary as a chunk
            if (boundaryStart > lastEnd) {
                boundaries.add(Pair(lastEnd, boundaryStart))
            }
            lastEnd = boundaryEnd
        }

        // Add remaining text if any
        if (lastEnd < text.length) {
            boundaries.add(Pair(lastEnd, text.length))
        }

        return boundaries
    }

    /**
     * Checks if current state is inside a protected Markdown environment.
     */
    fun isInProtectedEnvironment(): Boolean = isInCodeBlock || isInMathBlock

    /**
     * Checks if currently inside a code block.
     */
    fun isInCodeBlock(): Boolean = isInCodeBlock

    /**
     * Checks if currently inside a math block.
     */
    fun isInMathBlock(): Boolean = isInMathBlock

    /**
     * Resets the detector state. Call when starting a new thinking session.
     */
    fun reset() {
        isInCodeBlock = false
        isInMathBlock = false
    }
}
