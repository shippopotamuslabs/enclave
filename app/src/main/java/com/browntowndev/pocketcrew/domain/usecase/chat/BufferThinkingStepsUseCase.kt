package com.browntowndev.pocketcrew.domain.usecase.chat

import javax.inject.Inject

/**
 * Use case for buffering thinking text into discrete steps.
 *
 * Groups incoming sentences into cohesive chunks (2-4 sentences) before emitting
 * as a single thought. Uses sentence boundary detection and transition
 * word detection to create natural reasoning boundaries.
 *
 * Hybrid structural + semantic priority chunking:
 * - Structural patterns (highest priority): markdown headers, numbered steps, bullets,
 *   paragraph breaks, colon-terminated short lines - force emit if chunk exists
 * - Semantic patterns (secondary): reflection phrases ("Wait," "Actually,"),
 *   verification phrases ("Let me verify,"), planning intros
 *
 * This produces progressive, polished thinking steps similar to production AI apps.
 */
class BufferThinkingStepsUseCase @Inject constructor(
    private val sentenceDetector: SentenceBoundaryDetector
) {

    companion object {
        private val TRANSITION_STARTERS = setOf(
            "first", "next", "then", "however", "therefore", "thus",
            "additionally", "finally", "moreover", "so", "now", "step",
            "but", "also", "furthermore", "meanwhile", "otherwise",
            "consequently", "hence", "instead", "yet", "rather",
            // Observed in reasoning traces (o1-preview, Claude extended thinking)
            "alternatively", "wait", "given", "suppose", "this seems",
            "upon"
        )

        private val COMMON_INTROS_LOWER = setOf(
            "okay", "alright", "let me think", "well", "so", "alright so",
            // Observed in reasoning traces (o1-preview, Claude extended thinking)
            "the user is asking me to", "i need to", "we are given",
            "hm", "actually"
        )

        private const val MIN_SENTENCES_PER_CHUNK = 2
        private const val SOFT_MAX_WORDS_PER_CHUNK = 80
        private const val HARD_MAX_CHARS_BEFORE_FORCE = 500

        // Structural patterns (highest priority - force emit if chunk exists)
        private val MARKDOWN_HEADER = Regex("""\*\*[A-Za-z][A-Za-z ]+\*\*:""")
        private val NUMBERED_STEP = Regex("""\d+[\.\)]\s""")
        private val BULLET_POINT = Regex("""^[\-\*\+]\s""", RegexOption.MULTILINE)
        private val PARAGRAPH_BREAK = Regex("""\n\n+""")
        private val COLON_TERMINATED_SHORT = Regex("""^.{1,120}:\s*$""", RegexOption.MULTILINE)

        // Semantic/reflection patterns (secondary triggers)
        private val REFLECTION_STARTERS = Regex(
            """\b(Wait|Actually|Hold on|Let's pause|On second thought|Hmm|Correction|Rethink this)\b""",
            RegexOption.IGNORE_CASE
        )
        private val VERIFICATION_PHRASES = Regex(
            """\b(Let me verify|Double-check|To confirm|Upon re-evaluation|Edge cases|All in all|Let me check)\b""",
            RegexOption.IGNORE_CASE
        )
        private val PLANNING_INTROS = Regex(
            """\b(Let's break this down|First, let's understand)\b""",
            RegexOption.IGNORE_CASE
        )
    }

    private val buffer = StringBuilder(1024)
    private val currentChunkSentences = mutableListOf<String>()
    private var strippedIntro = false

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

        // Check for structural break points in accumulated content
        // If we have content and detect a strong break, emit before continuing
        if (currentChunkSentences.isNotEmpty() && isStrongBreakPoint(text)) {
            val thought = currentChunkSentences.joinToString(" ")
            emitted.add(thought)
            currentChunkSentences.clear()
        }

        // Use the injected sentence detector to find boundaries
        val boundaries = sentenceDetector.findBoundaries(text)

        // Handle case where no sentence boundaries found but we have too much text
        if (boundaries.isEmpty()) {
            if (buffer.length > HARD_MAX_CHARS_BEFORE_FORCE) {
                val content = buffer.toString().trim()
                if (content.isNotBlank()) {
                    emitted.add(content)
                    buffer.clear()
                }
            }
            return emitted
        }

        var hadCompleteSentence = false

        for ((start, end) in boundaries) {
            // Extract the sentence
            val sentence = text.substring(start, end).trim()

            if (sentence.isNotBlank()) {
                val cleaned = clean(sentence)
                if (cleaned.isNotBlank()) {
                    currentChunkSentences.add(cleaned)
                    hadCompleteSentence = true

                    // Check if we should emit
                    if (shouldEmitChunk()) {
                        val thought = currentChunkSentences.joinToString(" ")
                        emitted.add(thought)
                        currentChunkSentences.clear()
                    }
                }
            }
        }

        // Keep unprocessed portion (incomplete last sentence) in buffer
        // or clear buffer if all text was processed
        val lastBoundaryEnd = boundaries.lastOrNull()?.second ?: 0
        if (lastBoundaryEnd > 0) {
            if (lastBoundaryEnd >= text.length) {
                // All text was processed - clear the buffer
                buffer.clear()
            } else if (lastBoundaryEnd < text.length) {
                // Partial text processed - keep the rest
                buffer.delete(0, lastBoundaryEnd)
            }
        }

        // Emit any remaining sentences if we've completed at least one sentence
        if (currentChunkSentences.isNotEmpty() && hadCompleteSentence) {
            val thought = currentChunkSentences.joinToString(" ")
            emitted.add(thought)
            currentChunkSentences.clear()
        }

        // Safety: force emit very long pending content with no complete sentences
        if (buffer.length > HARD_MAX_CHARS_BEFORE_FORCE && currentChunkSentences.isEmpty()) {
            val content = buffer.toString().trim()
            if (content.isNotBlank()) {
                emitted.add(content)
                buffer.clear()
            }
        }

        return emitted
    }

    private fun shouldEmitChunk(): Boolean {
        if (currentChunkSentences.isEmpty()) return false

        val wordCount = currentChunkSentences.sumOf { it.split(Regex("\\s+")).size }
        val combinedText = currentChunkSentences.joinToString(" ")

        return when {
            currentChunkSentences.size >= 4 -> true  // Too greedy - emit
            currentChunkSentences.size >= MIN_SENTENCES_PER_CHUNK -> true  // Minimum reached - emit
            wordCount >= SOFT_MAX_WORDS_PER_CHUNK -> true  // Enough words - emit
            isTransitionStart(currentChunkSentences.last()) -> true  // Transition word - emit
            isStrongBreakPoint(combinedText) -> true  // Structural/semantic break point - emit
            else -> false
        }
    }

    private fun clean(sentence: String): String {
        var text = sentence.trim()

        // Note: Intro stripping is disabled to preserve full first sentence
        // This keeps the original thinking text intact for the user to see
        if (!strippedIntro) {
            // Currently disabled - uncomment if you want to strip intros
            // val lower = text.lowercase()
            // for (intro in COMMON_INTROS_LOWER) {
            //     if (lower.startsWith(intro)) {
            //         text = text.drop(intro.length)
            //             .dropWhile { it == ',' || it == '.' || it.isWhitespace() }
            //         break
            //     }
            // }
            strippedIntro = true
        }

        return text.trim()
    }

    private fun isTransitionStart(sentence: String): Boolean {
        val firstWord = sentence.split(Regex("\\s+")).firstOrNull()?.lowercase() ?: return false
        return firstWord in TRANSITION_STARTERS || Regex("^\\d+[.)]").containsMatchIn(sentence.lowercase())
    }

    /**
     * Checks if the text contains structural or semantic break points that should trigger
     * a new chunk emission.
     *
     * Priority order:
     * 1. Structural (force emit if chunk exists): markdown headers, numbered steps, bullets, paragraph breaks
     * 2. Semantic (secondary triggers): reflection starters, verification phrases, planning intros
     */
    private fun isStrongBreakPoint(text: String): Boolean {
        // Check for structural breaks first (highest priority)
        if (currentChunkSentences.isNotEmpty()) {
            if (MARKDOWN_HEADER.containsMatchIn(text)) return true
            if (NUMBERED_STEP.containsMatchIn(text)) return true
            if (BULLET_POINT.containsMatchIn(text)) return true
            if (PARAGRAPH_BREAK.containsMatchIn(text)) return true
            if (COLON_TERMINATED_SHORT.containsMatchIn(text)) return true
        }
        // Check semantic/reflection triggers
        if (REFLECTION_STARTERS.containsMatchIn(text)) return true
        if (VERIFICATION_PHRASES.containsMatchIn(text)) return true
        if (PLANNING_INTROS.containsMatchIn(text)) return true
        return false
    }

    /**
     * Flushes any remaining text in the buffer as a final thought.
     * Call this when thinking is complete.
     *
     * @return a thought string with remaining text, or null if buffer is empty
     */
    fun flush(): String? {
        if (currentChunkSentences.isEmpty() && buffer.isBlank()) return null

        val remaining = (currentChunkSentences.joinToString(" ") + " " + buffer.toString().trim())
            .trim()
            .ifBlank { null }

        reset()
        return remaining
    }

    /**
     * Resets the buffer. Call this when starting a new thinking session.
     */
    fun reset() {
        buffer.clear()
        currentChunkSentences.clear()
        strippedIntro = false
    }

    /**
     * Gets the current buffered steps without resetting.
     * Used for capturing thinking steps at step completion.
     */
    fun getBufferedSteps(): List<String> {
        return currentChunkSentences.toList()
    }
}
