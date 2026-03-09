package com.browntowndev.pocketcrew.domain.port.inference

/**
 * Detailed breakdown of prompt complexity analysis.
 * Useful for debugging, tuning, and understanding routing decisions.
 *
 * @property totalScore The weighted sum of all dimension scores (0-100)
 * @property complexityLevel The final tier determination
 * @property dimensionScores Map of dimension name to its raw score (0-100)
 * @property wordCount Number of words in the prompt
 * @property charCount Number of characters in the prompt
 * @property requiresReasoning Convenience boolean - true if tier is COMPLEX or REASONING
 */
data class ComplexityAnalysis(
    val totalScore: Int,
    val complexityLevel: ComplexityLevel,
    val dimensionScores: Map<String, Int>,
    val wordCount: Int,
    val charCount: Int,
    val requiresReasoning: Boolean
) {
    companion object {
        /**
         * Creates a default analysis for empty or invalid prompts.
         */
        fun empty(): ComplexityAnalysis = ComplexityAnalysis(
            totalScore = 0,
            complexityLevel = ComplexityLevel.SIMPLE,
            dimensionScores = emptyMap(),
            wordCount = 0,
            charCount = 0,
            requiresReasoning = false
        )
    }
}
