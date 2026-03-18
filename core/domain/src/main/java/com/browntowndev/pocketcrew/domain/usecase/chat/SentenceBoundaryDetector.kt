package com.browntowndev.pocketcrew.domain.usecase.chat

/**
 * Port interface for sentence boundary detection.
 * Abstracts different implementations (BreakIterator for production, regex for tests).
 */
interface SentenceBoundaryDetector {
    /**
     * Finds all sentence boundaries in the given text.
     * @param text the text to analyze
     * @return list of boundary positions (pairs of start, end indices for each sentence)
     */
    fun findBoundaries(text: String): List<Pair<Int, Int>>
}
