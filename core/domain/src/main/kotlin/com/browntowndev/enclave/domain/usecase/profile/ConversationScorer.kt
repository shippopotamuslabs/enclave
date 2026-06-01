package com.browntowndev.enclave.domain.usecase.profile

import com.browntowndev.enclave.domain.model.profile.ConversationScoringPolicy
import kotlin.math.max
import kotlin.math.min

/**
 * Pure (no I/O) signal scorer for a single conversation.
 *
 * All features are normalised to [0, 1] before weighting; the final score is also [0, 1].
 * Call [score] once per conversation using pre-computed token counts and concatenated user text.
 */
@Suppress("MagicNumber")
object ConversationScorer {
    /**
     * @param userText Concatenated text of all user turns (lowercase preferred but not required).
     * @param title Conversation title.
     * @param userTokenCount Pre-computed CL100K token count for user text.
     * @param createdAtMs Conversation creation timestamp, null if unknown (gets recency = 0.5).
     * @param oldestMs Oldest conversation in the run (used for recency normalisation).
     * @param newestMs Newest conversation in the run (used for recency normalisation).
     */
    fun score(
        userText: String,
        title: String,
        userTokenCount: Int,
        createdAtMs: Long?,
        oldestMs: Long,
        newestMs: Long,
    ): Float {
        val lower = userText.lowercase()
        val words = lower.split(WHITESPACE_RE).filter { it.isNotBlank() }
        val wordCount = max(1, words.size)

        val volume = volumeScore(userTokenCount)
        val firstPerson = firstPersonScore(words, wordCount)
        val affect = affectScore(words, wordCount)
        val stance = stanceScore(lower)
        val title = titleScore(title.lowercase())
        val recency = recencyScore(createdAtMs, oldestMs, newestMs)
        val noise = noiseScore(userText)

        val p = ConversationScoringPolicy
        val weightedSum =
            p.WEIGHT_VOLUME * volume +
                p.WEIGHT_FIRST_PERSON * firstPerson +
                p.WEIGHT_AFFECT * affect +
                p.WEIGHT_STANCE * stance +
                p.WEIGHT_TITLE * title +
                p.WEIGHT_RECENCY * recency +
                p.WEIGHT_NOISE_PENALTY * noise

        val totalWeight =
            p.WEIGHT_VOLUME + p.WEIGHT_FIRST_PERSON + p.WEIGHT_AFFECT +
                p.WEIGHT_STANCE + p.WEIGHT_TITLE + p.WEIGHT_RECENCY + p.WEIGHT_NOISE_PENALTY

        return (weightedSum / totalWeight).coerceIn(0f, 1f)
    }

    // region feature extractors

    private fun volumeScore(tokenCount: Int): Float {
        val p = ConversationScoringPolicy
        return when {
            tokenCount < p.VOLUME_MIN_TOKENS -> (tokenCount.toFloat() / p.VOLUME_MIN_TOKENS) * 0.3f
            tokenCount <= p.VOLUME_SWEET_SPOT_TOKENS -> {
                0.3f + 0.7f * (tokenCount - p.VOLUME_MIN_TOKENS).toFloat() /
                    (p.VOLUME_SWEET_SPOT_TOKENS - p.VOLUME_MIN_TOKENS)
            }
            tokenCount <= p.VOLUME_MAX_TOKENS -> {
                // Mild taper from 1.0 to 0.7 for very long conversations
                1.0f - 0.3f * (tokenCount - p.VOLUME_SWEET_SPOT_TOKENS).toFloat() /
                    (p.VOLUME_MAX_TOKENS - p.VOLUME_SWEET_SPOT_TOKENS)
            }
            else -> 0.7f
        }
    }

    private fun firstPersonScore(words: List<String>, wordCount: Int): Float {
        val hits = words.count { it in FIRST_PERSON_WORDS }
        return min(1f, hits.toFloat() / (wordCount * 0.15f))
    }

    private fun affectScore(words: List<String>, wordCount: Int): Float {
        val hits = words.count { it in ConversationScoringPolicy.AFFECT_LEXICON }
        val density = hits.toFloat() / wordCount
        return min(1f, density / ConversationScoringPolicy.AFFECT_DENSITY_CAP)
    }

    private fun stanceScore(lowerText: String): Float {
        val hits = ConversationScoringPolicy.STANCE_PHRASES.count { lowerText.contains(it) }
        val density = hits.toFloat() / max(1, lowerText.length / 200)
        return min(1f, density / (ConversationScoringPolicy.STANCE_DENSITY_CAP * 10))
    }

    private fun titleScore(lowerTitle: String): Float {
        val tokens = lowerTitle.split(WHITESPACE_RE).filter { it.isNotBlank() }.toSet()
        val reflectiveHits = tokens.count { it in ConversationScoringPolicy.REFLECTIVE_TITLE_TOKENS }
        val utilitarianHits = tokens.count { it in ConversationScoringPolicy.UTILITARIAN_TITLE_TOKENS }
        return when {
            reflectiveHits > utilitarianHits -> 0.8f + min(0.2f, reflectiveHits * 0.05f)
            utilitarianHits > reflectiveHits -> max(0f, 0.3f - utilitarianHits * 0.05f)
            else -> 0.5f
        }
    }

    private fun recencyScore(createdAtMs: Long?, oldestMs: Long, newestMs: Long): Float {
        if (createdAtMs == null) return 0.5f
        val span = newestMs - oldestMs
        if (span <= 0L) return 1.0f
        return ((createdAtMs - oldestMs).toFloat() / span).coerceIn(0f, 1f)
    }

    private fun noiseScore(text: String): Float {
        // Fraction of text NOT in code fences / JSON blobs / log lines
        val totalLen = max(1, text.length)
        val codeFenceLen = CODE_FENCE_RE.findAll(text).sumOf { it.value.length }
        val noiseFraction = min(1f, codeFenceLen.toFloat() / totalLen)
        return when {
            noiseFraction >= ConversationScoringPolicy.NOISE_FULL_PENALTY_THRESHOLD -> 0f
            else -> 1f - noiseFraction / ConversationScoringPolicy.NOISE_FULL_PENALTY_THRESHOLD
        }
    }

    // endregion

    private val WHITESPACE_RE = Regex("\\s+")
    private val CODE_FENCE_RE = Regex("```[\\s\\S]*?```|`[^`]+`|\\{[\\s\\S]{20,}?\\}")
    private val FIRST_PERSON_WORDS = setOf("i", "me", "my", "myself", "i'm", "i've", "i'd", "i'll")
}
