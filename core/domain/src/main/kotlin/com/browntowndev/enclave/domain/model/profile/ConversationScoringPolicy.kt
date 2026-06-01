package com.browntowndev.enclave.domain.model.profile

/**
 * Tunable weights and thresholds for conversation signal scoring.
 *
 * All feature scores are normalised to [0, 1] before weighting.
 * Weights need not sum to 1 — the scorer divides by the sum.
 */
object ConversationScoringPolicy {
    // Feature weights
    const val WEIGHT_VOLUME = 1.5f
    const val WEIGHT_FIRST_PERSON = 2.0f
    const val WEIGHT_AFFECT = 1.5f
    const val WEIGHT_STANCE = 2.0f
    const val WEIGHT_TITLE = 0.5f
    const val WEIGHT_RECENCY = 0.8f
    const val WEIGHT_NOISE_PENALTY = 1.2f

    // Volume sweet-spot: conversations below this are penalised; above this taper slightly
    const val VOLUME_MIN_TOKENS = 40
    const val VOLUME_SWEET_SPOT_TOKENS = 600
    const val VOLUME_MAX_TOKENS = 3000

    // MMR diversity parameter (0 = pure relevance, 1 = pure diversity)
    const val MMR_LAMBDA = 0.35f

    // Minimum signal score to be eligible for selection (filters pure utility noise)
    const val MIN_ELIGIBLE_SCORE = 0.10f

    // Affect word density ceiling at which the feature maxes out (prevents outlier dominance)
    const val AFFECT_DENSITY_CAP = 0.08f

    // Stance phrase density ceiling
    const val STANCE_DENSITY_CAP = 0.12f

    // Code-fence fraction above which the noise penalty engages fully
    const val NOISE_FULL_PENALTY_THRESHOLD = 0.60f

    /** Compact LIWC-derived affect / introspection lexicon. */
    val AFFECT_LEXICON: Set<String> = setOf(
        // Positive affect
        "love", "happy", "joy", "excited", "glad", "wonderful", "amazing", "grateful",
        "thankful", "proud", "hopeful", "optimistic", "enthusiastic", "delighted",
        "thrilled", "satisfied", "inspired", "passionate", "eager",
        // Negative affect
        "sad", "angry", "frustrated", "anxious", "worried", "afraid", "scared",
        "depressed", "overwhelmed", "stressed", "upset", "disappointed", "hurt",
        "lonely", "confused", "lost", "stuck", "struggling", "tired",
        // Introspection / self-disclosure
        "realized", "wonder", "noticed", "reflect", "reflection", "honest",
        "vulnerable", "understand", "understood", "believe", "doubt", "uncertain",
        "question", "meaning", "purpose", "identity", "growth", "change",
        "pattern", "habit", "fear", "hope", "dream", "regret", "mistake",
        "learn", "insight", "truth", "honest", "admit",
    )

    /** Stance / opinion markers indicating genuine engagement and pushback. */
    val STANCE_PHRASES: List<String> = listOf(
        "i think", "i feel", "i believe", "i don't think", "i disagree",
        "i agree", "in my opinion", "my view", "i'm not sure", "i wonder",
        "actually,", "actually i", "to be honest", "honestly,", "honestly i",
        "i'm concerned", "i'm worried", "i'm not convinced", "wait,", "no,",
        "but wait", "but i", "i would argue", "i'd argue", "i suspect",
        "i'm curious", "i've been", "i've noticed", "i realize", "i realized",
        "i find", "i found", "what i", "how i",
    )

    /** Title keywords suggesting reflective / personal conversations (boost). */
    val REFLECTIVE_TITLE_TOKENS: Set<String> = setOf(
        "why", "how", "what", "should", "feel", "think", "understand",
        "life", "work", "relationship", "decision", "future", "past",
        "change", "help", "advice", "problem", "career", "goal", "value",
        "meaning", "struggle", "anxiety", "depression", "fear", "love",
        "identity", "growth", "creative",
    )

    /** Title keywords suggesting purely utilitarian conversations (slight penalty). */
    val UTILITARIAN_TITLE_TOKENS: Set<String> = setOf(
        "code", "fix", "bug", "error", "script", "function", "implement",
        "regex", "sql", "json", "yaml", "html", "css", "python", "kotlin",
        "translate", "summarize", "summary", "write email", "draft",
        "format", "convert", "parse",
    )
}
