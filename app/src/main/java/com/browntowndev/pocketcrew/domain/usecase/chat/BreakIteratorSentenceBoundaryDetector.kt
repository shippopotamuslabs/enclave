package com.browntowndev.pocketcrew.domain.usecase.chat

import android.icu.text.BreakIterator
import javax.inject.Inject

/**
 * Production implementation using Android's BreakIterator (IBM ICU engine) for locale-aware
 * sentence boundary detection.
 *
 * Handles streaming scenarios by filtering out incomplete final sentences
 * (those that don't end with proper punctuation + whitespace).
 */
class BreakIteratorSentenceBoundaryDetector @Inject constructor() : SentenceBoundaryDetector {

    private val sentenceBreaker: BreakIterator by lazy {
        BreakIterator.getSentenceInstance()
    }

    // Pattern to detect complete sentence endings: punctuation followed by whitespace or end of string
    private val completeSentenceEnd = Regex("""[.!?]\s*""")

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

            if (isCompleteSentence) {
                boundaries.add(Pair(current, next))
            } else {
                // If sentence is incomplete, stop here - don't include any further sentences
                // This handles streaming where last sentence might be partial
                return boundaries
            }

            current = next
            next = sentenceBreaker.next()
        }

        return boundaries
    }
}
