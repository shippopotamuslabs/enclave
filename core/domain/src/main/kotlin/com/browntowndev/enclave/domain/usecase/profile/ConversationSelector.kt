package com.browntowndev.enclave.domain.usecase.profile

import com.browntowndev.enclave.domain.model.profile.ConversationScoringPolicy

/**
 * Pure max-marginal-relevance (MMR) selector.
 *
 * Picks the highest-value, most-diverse subset of conversations that fits within
 * [tokenBudget]. Operates entirely in memory — no I/O.
 */
@Suppress("LoopWithTooManyJumpStatements")
object ConversationSelector {
    data class Candidate(
        val id: String,
        val signalScore: Float,
        val userTokenCount: Int,
        val embedding: FloatArray,
    )

    data class SelectionResult(
        val selectedIds: List<String>,
        val totalTokens: Int,
    )

    /**
     * @param candidates Pre-scored candidates (all must have embeddings of the same dimension).
     * @param tokenBudget Maximum total user-turn token count for the selected set.
     */
    fun select(
        candidates: List<Candidate>,
        tokenBudget: Int,
    ): SelectionResult {
        val eligible =
            candidates
                .filter { it.signalScore >= ConversationScoringPolicy.MIN_ELIGIBLE_SCORE }
                .sortedByDescending { it.signalScore }

        if (eligible.isEmpty()) return SelectionResult(emptyList(), 0)

        val selected = mutableListOf<Candidate>()
        val remaining = eligible.toMutableList()
        var totalTokens = 0

        while (remaining.isNotEmpty() && totalTokens < tokenBudget) {
            val next = pickNext(remaining, selected) ?: break
            if (totalTokens + next.userTokenCount > tokenBudget && selected.isNotEmpty()) break
            selected.add(next)
            remaining.remove(next)
            totalTokens += next.userTokenCount
        }

        return SelectionResult(
            selectedIds = selected.map { it.id },
            totalTokens = totalTokens,
        )
    }

    private fun pickNext(
        remaining: List<Candidate>,
        selected: List<Candidate>,
    ): Candidate? {
        if (selected.isEmpty()) return remaining.maxByOrNull { it.signalScore }

        val lambda = ConversationScoringPolicy.MMR_LAMBDA
        return remaining.maxByOrNull { candidate ->
            val maxSim = selected.maxOf { cosine(candidate.embedding, it.embedding) }
            candidate.signalScore - lambda * maxSim
        }
    }

    private fun cosine(
        a: FloatArray,
        b: FloatArray,
    ): Float {
        // Embeddings from MiniLM are L2-normalised → dot product = cosine similarity
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot.coerceIn(-1f, 1f)
    }
}
