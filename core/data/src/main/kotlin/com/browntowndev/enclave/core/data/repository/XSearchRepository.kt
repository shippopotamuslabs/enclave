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
import javax.inject.Inject
import javax.inject.Singleton

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

            // Relevancy scoring weights
            private const val MEDIA_PRESENCE_BONUS = 2.5
            private const val VERIFIED_AUTHOR_BONUS = 2.0
            private const val IMPRESSION_LOG_WEIGHT = 0.5
            private const val REPLY_COUNT_WEIGHT = 0.2
            private val json = Json { ignoreUnknownKeys = true }
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
                    .addQueryParameter("user.fields", "name,username,verified")
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

        internal fun mapResponse(body: String): String {
            val response = json.decodeFromString<XSearchResponse>(body)
            val tweets = response.data ?: return json.encodeToString(emptyList<XPost>())

            val usersMap = response.includes?.users?.associateBy { it.id } ?: emptyMap()
            val mediaMap = response.includes?.media?.associateBy { it.media_key } ?: emptyMap()

            val posts =
                tweets
                    .map { tweet ->
                        val metrics = tweet.public_metrics
                        val user = usersMap[tweet.author_id]
                        val media = tweet.attachments?.media_keys?.mapNotNull { mediaMap[it] }

                        // Relevancy signals: verified authors and media presence are high leverage
                        val mediaBonus = if (!media.isNullOrEmpty()) MEDIA_PRESENCE_BONUS else 0.0
                        val verifiedBonus = if (user?.verified == true) VERIFIED_AUTHOR_BONUS else 0.0

                        val impactScore =
                            if (metrics != null) {
                                // Weighted score: retweets and likes are high signal, impressions are low signal
                                kotlin.math.log10(metrics.like_count + 1.0) +
                                    2.0 * kotlin.math.log10(metrics.retweet_count + 1.0) +
                                    IMPRESSION_LOG_WEIGHT * kotlin.math.log10(metrics.impression_count + 1.0) +
                                    metrics.reply_count * REPLY_COUNT_WEIGHT +
                                    mediaBonus + verifiedBonus
                            } else {
                                mediaBonus + verifiedBonus
                            }

                        val urls = tweet.entities?.urls?.map { it.expanded_url }

                        // Identify if it's a retweet or quote
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
                if (count < 2) {
                    distinctPosts.add(post)
                    authorCounts[authorId] = count + 1
                }
                if (distinctPosts.size >= 12) break
            }

            return json.encodeToString(distinctPosts)
        }
    }
