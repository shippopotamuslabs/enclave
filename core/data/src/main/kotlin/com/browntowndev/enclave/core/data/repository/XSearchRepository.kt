package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.security.ApiKeyManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln1p as log1p
import kotlin.math.max
import kotlin.math.min

@Serializable
private data class XSearchResponse(
    val data: List<XTweet>? = null,
    val includes: XIncludes? = null,
    val meta: XMeta? = null,
)

@Serializable
private data class XTweet(
    val id: String,
    val text: String,
    val author_id: String? = null,
    val created_at: String? = null,
    val public_metrics: XPublicMetrics? = null,
    val attachments: XAttachments? = null,
    val entities: XEntities? = null,
    val referenced_tweets: List<XReferencedTweet>? = null,
)

@Serializable
private data class XPublicMetrics(
    val retweet_count: Int = 0,
    val reply_count: Int = 0,
    val like_count: Int = 0,
    val quote_count: Int = 0,
    val bookmark_count: Int = 0,
    val impression_count: Int = 0,
)

@Serializable
private data class XAttachments(
    val media_keys: List<String>? = null,
)

@Serializable
private data class XEntities(
    val urls: List<XUrl>? = null,
)

@Serializable
private data class XUrl(
    val expanded_url: String,
)

@Serializable
private data class XReferencedTweet(
    val type: String,
    val id: String,
)

@Serializable
private data class XUserPublicMetrics(
    val followers_count: Int = 0,
    val following_count: Int = 0,
)

@Serializable
private data class XIncludes(
    val users: List<XUser>? = null,
    val media: List<XMedia>? = null,
)

@Serializable
private data class XUser(
    val id: String,
    val name: String,
    val username: String,
    val verified: Boolean? = null,
    val public_metrics: XUserPublicMetrics? = null,
)

@Serializable
private data class XMedia(
    val media_key: String,
    val type: String,
    val url: String? = null,
    val alt_text: String? = null,
)

@Serializable
private data class XMeta(
    val result_count: Int = 0,
    val next_token: String? = null,
)

@Serializable
private data class XPost(
    val id: String,
    val text: String,
    val author_id: String? = null,
    val user_name: String? = null,
    val url: String? = null,
    val created_at: String? = null,
    val metrics: XPublicMetrics? = null,
    val media: List<XMedia>? = null,
    val urls: List<String>? = null,
    val type: String? = null,
    val impact_score: Double = 0.0,
)

@Singleton
class XSearchRepository
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val apiKeyManager: ApiKeyManager,
    ) {
        private val cache = mutableMapOf<String, CachedResponse>()

        companion object {
            private const val SEARCH_URL = "https://api.x.com/2/tweets/search/recent"
            private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
            private val json = Json { ignoreUnknownKeys = true }

            // Scoring constants
            private const val BOOKMARK_WEIGHT = 0.40
            private const val QUOTE_WEIGHT = 0.30
            private const val REPLY_WEIGHT = 0.20
            private const val LIKE_WEIGHT = 0.10

            private const val VERIFIED_MULTIPLIER_BONUS = 1.25
            private const val MAX_RATIO_MULTIPLIER = 5.0
            private const val FOLLOWER_SIZE_SCALE = 15.0
            private const val MAX_AUTHOR_MULTIPLIER = 2.5
            private const val SIZE_FACTOR_FLOOR = 0.1
            private const val RATIO_FACTOR = 0.4

            private const val RECENCY_DECAY_HOURS = 48.0
            private const val MIN_RECENCY_MULTIPLIER = 0.6

            // Spam filtering limits
            private const val MAX_HASHTAGS = 3
            private const val MAX_CASHTAGS = 2
            private const val MIN_TEXT_DEDUPE_LENGTH = 10
            private const val MAX_POSTS_PER_AUTHOR = 2
            private const val MAX_TOTAL_POSTS = 12

            private const val IMPRESSION_THRESHOLD = 1000
            private const val MIN_ENGAGEMENT_SCORE = 0.05
        }

        private data class CachedResponse(
            val body: String,
            val timestamp: Long,
        )

        fun search(
            query: String,
            sortOrder: String = "relevancy",
        ): String {
            val normalizedQuery = query.trim().lowercase()
            val cached = cache[normalizedQuery]
            if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
                return mapResponse(cached.body)
            }

            val apiKey =
                apiKeyManager.get(ApiKeyManager.X_SEARCH_ALIAS)
                    ?: throw IllegalStateException("X API key is required when X search is enabled")

            val filters = listOf("-is:retweet", "-is:nullcast")
            var refinedQuery = query
            filters.forEach { filter ->
                if (!refinedQuery.contains(filter, ignoreCase = true)) {
                    refinedQuery = "$refinedQuery $filter"
                }
            }

            val url =
                SEARCH_URL
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("query", refinedQuery)
                    .addQueryParameter("max_results", "100")
                    .addQueryParameter("sort_order", sortOrder)
                    .addQueryParameter("tweet.fields", "public_metrics,created_at,author_id,entities,referenced_tweets")
                    .addQueryParameter("expansions", "author_id,attachments.media_keys")
                    .addQueryParameter("user.fields", "name,username,verified,public_metrics")
                    .addQueryParameter("media.fields", "type,url,alt_text")
                    .build()

            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Authorization", "Bearer $apiKey")
                    .get()
                    .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body.string()
                    throw IOException("X request failed with HTTP ${response.code}: $errorBody")
                }

                val body = response.body.string()
                cache[normalizedQuery] = CachedResponse(body, System.currentTimeMillis())
                return mapResponse(body)
            }
        }

        private fun normalizeText(text: String): String {
            // Remove URLs
            var cleaned = text.replace(Regex("https?://\\S+"), "")
            // Remove non-alphanumeric characters, except spaces
            cleaned = cleaned.replace(Regex("[^a-zA-Z0-9\\s]"), "")
            // Replace multiple spaces/newlines with single space, lowercase and trim
            return cleaned.replace(Regex("\\s+"), " ").trim().lowercase()
        }

        private fun calculateEngagementScore(metrics: XPublicMetrics?): Double {
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

        private fun isSpam(text: String, metrics: XPublicMetrics?, engagementScore: Double): Boolean {
            val hashtagsCount = text.split(Regex("\\s+")).count { it.startsWith("#") }
            if (hashtagsCount > MAX_HASHTAGS) return true

            val cashtagsCount = text.split(Regex("\\s+")).count { it.startsWith("$") && it.length > 1 && it[1].isLetter() }
            if (cashtagsCount > MAX_CASHTAGS) return true

            val containsSpamPhrase = listOf(
                "dm to collab", "send pic on", "whatsapp me",
                "telegram channel", "crypto signal", "giveaway", "airdrop"
            ).any { text.contains(it, ignoreCase = true) }
            if (containsSpamPhrase) return true

            val impressions = metrics?.impression_count ?: 0
            if (impressions > IMPRESSION_THRESHOLD && engagementScore < MIN_ENGAGEMENT_SCORE) {
                return true
            }

            return false
        }

        private fun calculateImpactScore(
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
        private fun calculateRecencyMultiplier(createdAtStr: String?, now: Instant): Double {
            return try {
                createdAtStr?.let { dateStr ->
                    val createdAtInstant = Instant.parse(dateStr)
                    val hoursOld = Duration.between(createdAtInstant, now).toMinutes() / 60.0
                    max(MIN_RECENCY_MULTIPLIER, 1.0 - (hoursOld / RECENCY_DECAY_HOURS))
                } ?: 1.0
            } catch (e: Exception) {
                1.0
            }
        }

        internal fun mapResponse(body: String): String {
            val response = json.decodeFromString<XSearchResponse>(body)
            val tweets = response.data ?: return json.encodeToString(emptyList<XPost>())

            val usersMap = response.includes?.users?.associateBy { it.id } ?: emptyMap()
            val mediaMap = response.includes?.media?.associateBy { it.media_key } ?: emptyMap()

            val seenTexts = mutableSetOf<String>()
            val now = Instant.now()

            val posts =
                tweets
                    .mapNotNull { tweet ->
                        val text = tweet.text
                        val metrics = tweet.public_metrics
                        val user = usersMap[tweet.author_id]

                        val engagementScore = calculateEngagementScore(metrics)

                        if (isSpam(text, metrics, engagementScore)) return@mapNotNull null

                        val normalized = normalizeText(text)
                        if (normalized.length >= MIN_TEXT_DEDUPE_LENGTH && !seenTexts.add(normalized)) {
                            return@mapNotNull null
                        }

                        val impactScore = calculateImpactScore(tweet, user, engagementScore, now)

                        val media = tweet.attachments?.media_keys?.mapNotNull { mediaMap[it] }
                        val urls = tweet.entities?.urls?.map { it.expanded_url }
                        val type = tweet.referenced_tweets?.firstOrNull()?.type
                        val username = user?.username ?: "i"

                        XPost(
                            id = tweet.id,
                            text = tweet.text,
                            author_id = tweet.author_id,
                            user_name = user?.name ?: username,
                            url = "https://x.com/$username/status/${tweet.id}",
                            created_at = tweet.created_at,
                            metrics = metrics,
                            media = media,
                            urls = urls,
                            type = type,
                            impact_score = impactScore,
                        )
                    }.sortedByDescending { it.impact_score }

            val distinctPosts = mutableListOf<XPost>()
            val authorCounts = mutableMapOf<String, Int>()

            for (post in posts) {
                val authorId = post.author_id ?: "unknown"
                val count = authorCounts.getOrDefault(authorId, 0)
                if (count < MAX_POSTS_PER_AUTHOR) {
                    distinctPosts.add(post)
                    authorCounts[authorId] = count + 1
                }
                if (distinctPosts.size >= MAX_TOTAL_POSTS) break
            }

            return json.encodeToString(distinctPosts)
        }
    }
