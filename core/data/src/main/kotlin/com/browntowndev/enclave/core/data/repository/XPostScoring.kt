package com.browntowndev.enclave.core.data.repository

import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.math.min
import kotlin.math.ln1p as log1p

internal object XPostScoring {
    // Engagement weights
    private const val BOOKMARK_WEIGHT = 0.40
    private const val QUOTE_WEIGHT = 0.30
    private const val REPLY_WEIGHT = 0.20
    private const val LIKE_WEIGHT = 0.10

    // Impact / author multiplier
    private const val VERIFIED_MULTIPLIER_BONUS = 1.25
    private const val MAX_RATIO_MULTIPLIER = 5.0
    private const val FOLLOWER_SIZE_SCALE = 15.0
    private const val MAX_AUTHOR_MULTIPLIER = 2.5
    private const val SIZE_FACTOR_FLOOR = 0.1
    private const val RATIO_FACTOR = 0.4

    // Recency decay
    private const val RECENCY_DECAY_HOURS = 48.0
    private const val MIN_RECENCY_MULTIPLIER = 0.6

    // Spam filtering
    private const val MAX_HASHTAGS = 3
    private const val MAX_CASHTAGS = 2
    internal const val MIN_TEXT_DEDUPE_LENGTH = 10
    private const val IMPRESSION_THRESHOLD = 1000
    private const val MIN_ENGAGEMENT_SCORE = 0.05

    fun normalizeText(text: String): String {
        var cleaned = text.replace(Regex("https?://\\S+"), "")
        cleaned = cleaned.replace(Regex("[^a-zA-Z0-9\\s]"), "")
        return cleaned.replace(Regex("\\s+"), " ").trim().lowercase()
    }

    fun calculateEngagementScore(metrics: XPublicMetrics?): Double {
        val bookmarkCount = metrics?.bookmark_count?.toDouble() ?: 0.0
        val quoteCount = metrics?.quote_count?.toDouble() ?: 0.0
        val replyCount = metrics?.reply_count?.toDouble() ?: 0.0
        val likeCount = metrics?.like_count?.toDouble() ?: 0.0

        return (
            (BOOKMARK_WEIGHT * log1p(bookmarkCount)) +
                (QUOTE_WEIGHT * log1p(quoteCount)) +
                (REPLY_WEIGHT * log1p(replyCount)) +
                (LIKE_WEIGHT * log1p(likeCount))
        )
    }

    fun isSpam(
        text: String,
        metrics: XPublicMetrics?,
        engagementScore: Double,
    ): Boolean {
        val hashtagsCount = text.split(Regex("\\s+")).count { it.startsWith("#") }
        if (hashtagsCount > MAX_HASHTAGS) return true

        val cashtagsCount =
            text.split(Regex("\\s+")).count { it.startsWith("$") && it.length > 1 && it[1].isLetter() }
        if (cashtagsCount > MAX_CASHTAGS) return true

        val containsSpamPhrase =
            listOf(
                "dm to collab",
                "send pic on",
                "whatsapp me",
                "telegram channel",
                "crypto signal",
                "giveaway",
                "airdrop",
            ).any { text.contains(it, ignoreCase = true) }
        if (containsSpamPhrase) return true

        val impressions = metrics?.impression_count ?: 0
        if (impressions > IMPRESSION_THRESHOLD && engagementScore < MIN_ENGAGEMENT_SCORE) {
            return true
        }

        return false
    }

    fun calculateImpactScore(
        tweet: XTweet,
        user: XUser?,
        engagementScore: Double,
        now: Instant,
    ): Double {
        val followers = user?.public_metrics?.followers_count?.toDouble() ?: 0.0
        val following = user?.public_metrics?.following_count?.toDouble() ?: 0.0

        var authorMultiplier = 1.0
        if (user?.verified == true) {
            authorMultiplier *= VERIFIED_MULTIPLIER_BONUS
        }
        val ratio = followers / max(following, 1.0)
        val sizeFactor = max(SIZE_FACTOR_FLOOR, min(1.0, log1p(followers) / FOLLOWER_SIZE_SCALE))
        authorMultiplier *= (1.0 + RATIO_FACTOR * min(ratio, MAX_RATIO_MULTIPLIER)) * sizeFactor
        authorMultiplier = min(authorMultiplier, MAX_AUTHOR_MULTIPLIER)

        val recencyMultiplier = calculateRecencyMultiplier(tweet.created_at, now)

        return engagementScore * authorMultiplier * recencyMultiplier
    }

    @Suppress("SwallowedException")
    private fun calculateRecencyMultiplier(
        createdAtStr: String?,
        now: Instant,
    ): Double =
        try {
            createdAtStr?.let { dateStr ->
                val createdAtInstant = Instant.parse(dateStr)
                val hoursOld = Duration.between(createdAtInstant, now).toMinutes() / 60.0
                max(MIN_RECENCY_MULTIPLIER, 1.0 - (hoursOld / RECENCY_DECAY_HOURS))
            } ?: 1.0
        } catch (e: Exception) {
            1.0
        }
}
