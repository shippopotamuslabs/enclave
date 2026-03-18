package com.browntowndev.pocketcrew.domain.usecase.chat

import android.icu.text.BreakIterator
import javax.inject.Inject

/**
 * Production implementation using Android's BreakIterator (IBM ICU engine) for locale-aware
 * sentence boundary detection.
 *
 * Handles streaming scenarios by filtering out incomplete final sentences
 * (those that don't end with proper punctuation + whitespace).
 *
 * Also protects against false sentence boundaries from:
 * - Common abbreviations (vs., e.g., i.e., etc., dr., mr., etc.)
 * - Footnote-style markers (word4., word5., etc.)
 */
class BreakIteratorSentenceBoundaryDetector @Inject constructor() : SentenceBoundaryDetector {

    private val sentenceBreaker: BreakIterator by lazy {
        BreakIterator.getSentenceInstance()
    }

    // Pattern to detect complete sentence endings: punctuation followed by whitespace or end of string
    private val completeSentenceEnd = Regex("""[.!?]\s*""")

    // Protected patterns that should NOT be treated as sentence endings
    // 1. Common abbreviations: vs., e.g., i.e., etc., dr., mr., etc.
    // 2. Footnote-style numbers: word4., word5., etc. (digit + period at end of clause)
    // Matches: "vs.", "e.g.", "i.e.", "etc.", "dr.", "mr.", "about4.", "relevance5."
    private val protectedEndings = Regex(
        """\b(vs|e\.g\.|i\.e\.|etc|dr|mr|mrs|ms|j r|s r|inc|ltd|corp|co|no|fig)\.(\s|$)""" +
        """|\b\w+\d+\.(\s|$)""",  // footnote pattern: word followed by digit + period
        RegexOption.IGNORE_CASE
    )

    override fun findBoundaries(text: String): List<Pair<Int, Int>> {
        if (text.isEmpty()) return emptyList()

        val boundaries = mutableListOf<Pair<Int, Int>>()
        sentenceBreaker.setText(text)

        var current = sentenceBreaker.first()
        var next = sentenceBreaker.next()

        while (next != BreakIterator.DONE) {
            // Check if this sentence ends with complete punctuation
            // Only include it if it ends with .! ? followed by whitespace or end of string
            val sentenceText = text.substring(current, next)
            val isCompleteSentence = completeSentenceEnd.containsMatchIn(sentenceText)

            // Filter out false boundaries from abbreviations and footnote markers
            val isProtectedEnding = protectedEndings.containsMatchIn(sentenceText)

            if (isCompleteSentence && !isProtectedEnding) {
                boundaries.add(Pair(current, next))
            } else if (!isCompleteSentence) {
                // If sentence is incomplete, stop here - don't include any further sentences
                // This handles streaming where last sentence might be partial
                return boundaries
            }
            // If isProtectedEnding is true but not complete, skip this boundary

            current = next
            next = sentenceBreaker.next()
        }

        return boundaries
    }
}
