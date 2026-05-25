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
data class XPublicMetrics(
    val retweet_count: Int = 0,
    val reply_count: Int = 0,
    val like_count: Int = 0,
    val quote_count: Int = 0,
    val bookmark_count: Int = 0,
    val impression_count: Int = 0,
)

@Serializable
data class XMedia(
    val media_key: String,
    val type: String,
    val url: String? = null,
    val alt_text: String? = null,
)

@Serializable
data class XPost(
    val id: String,
    val text: String,
    val author_id: String? = null,
    /** Display name from the author's profile. */
    val user_name: String? = null,
    /** Screen name without @ (e.g. elonmusk). */
    val username: String? = null,
    val url: String? = null,
    val created_at: String? = null,
    val metrics: XPublicMetrics? = null,
    val media: List<XMedia>? = null,
    val urls: List<String>? = null,
    val type: String? = null,
    val impact_score: Double = 0.0,
    val is_anchor: Boolean = false,
)

enum class XPostOrdering {
    IMPACT,
    RECENCY,
}

@Serializable
data class XTrendItem(
    val trend_name: String,
    val tweet_count: Int? = null,
)

@Serializable
data class XTrendsResponse(
    val data: List<XTrendItem>,
)

@Serializable
data class XSearchPaginatedResult(
    val data: List<XPost>,
    val next_token: String? = null,
    /**
     * How this page was fetched: `timeline` (GET /users/:id/tweets) or `search`
     * (recent search). Pass back with next_token so pagination uses the same API.
     */
    val pagination_source: String? = null,
)

@Singleton
class XSearchRepository
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val apiKeyManager: ApiKeyManager,
        private val userTimelineFetcher: XUserTimelineFetcher,
    ) {
        private val cache = mutableMapOf<String, CachedResponse>()

        companion object {
            private const val SEARCH_URL = "https://api.x.com/2/tweets/search/recent"
            private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
            private val json = Json { ignoreUnknownKeys = true }
            private val threadJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

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
            const val MAX_POSTS_PER_AUTHOR = 2
            const val MAX_TOTAL_POSTS = 12

            private             const val IMPRESSION_THRESHOLD = 1000
            private const val MIN_ENGAGEMENT_SCORE = 0.05

            private const val THREAD_RECENCY_WARNING =
                "Some ancestor posts may be missing: conversation search is limited to the last 7 days " +
                    "and parent lookup depends on referenced_tweets from the API."
            private const val MAX_CONVERSATION_SEARCH_PAGES = 5
            private const val TWEET_LOOKUP_EXPANSIONS =
                "author_id,attachments.media_keys,referenced_tweets.id,referenced_tweets.id.author_id"
        }

        private data class ResolvedAuthor(
            val displayName: String,
            val username: String?,
            val statusUrl: String,
        )

        private data class CachedResponse(
            val body: String,
            val timestamp: Long,
        )

        private fun fetchRecentSearchRaw(
            query: String,
            sortOrder: String = "relevancy",
            nextToken: String? = null,
            apiKey: String
        ): String {
            val filters = listOf("-is:retweet", "-is:nullcast")
            var refinedQuery = query
            filters.forEach { filter ->
                if (!refinedQuery.contains(filter, ignoreCase = true)) {
                    refinedQuery = "$refinedQuery $filter"
                }
            }

            val urlBuilder = SEARCH_URL
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("query", refinedQuery)
                .addQueryParameter("max_results", "100")
                .addQueryParameter("sort_order", sortOrder)
                .addQueryParameter("tweet.fields", "public_metrics,created_at,author_id,entities,referenced_tweets")
                .addQueryParameter("expansions", TWEET_LOOKUP_EXPANSIONS)
                .addQueryParameter("user.fields", "name,username,verified,public_metrics")
                .addQueryParameter("media.fields", "type,url,alt_text")

            val sanitizedNext = sanitizePaginationToken(nextToken)
            if (sanitizedNext != null) {
                urlBuilder.addQueryParameter("next_token", sanitizedNext)
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body.string()
                    throw IOException("X request failed with HTTP ${response.code}: $errorBody")
                }
                return response.body.string()
            }
        }

        fun searchPaginated(
            query: String,
            sortOrder: String = "relevancy",
            maxPostsPerAuthor: Int = MAX_POSTS_PER_AUTHOR,
            maxTotalPosts: Int = MAX_TOTAL_POSTS,
            nextToken: String? = null,
            ordering: XPostOrdering = XPostOrdering.IMPACT,
            skipDiscoveryFilters: Boolean = false,
        ): XSearchPaginatedResult {
            val apiKey = apiKeyManager.get(ApiKeyManager.X_SEARCH_ALIAS)
                ?: throw IllegalStateException("X API key is required when X search is enabled")

            val sanitizedNext = sanitizePaginationToken(nextToken)
            val cacheKey = "${query.trim().lowercase()}|$sortOrder|${sanitizedNext ?: ""}"
            val cached = cache[cacheKey]
            val body = if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
                cached.body
            } else {
                val responseBody = fetchRecentSearchRaw(query, sortOrder, sanitizedNext, apiKey)
                cache[cacheKey] = CachedResponse(responseBody, System.currentTimeMillis())
                responseBody
            }

            val response = xApiJson.decodeFromString<XSearchResponse>(body)
            val tweets = response.data ?: return XSearchPaginatedResult(emptyList(), null)

            val allUsers = response.includes?.users.orEmpty().toMutableList()
            ensureUsersForAuthorIds(tweets.map { it.author_id }, allUsers, apiKey)
            val usersMap = allUsers.associateBy { it.id }
            val mediaMap = response.includes?.media?.associateBy { it.media_key } ?: emptyMap()

            val seenTexts = mutableSetOf<String>()
            val now = Instant.now()

            val posts = tweets.mapNotNull { tweet ->
                val text = tweet.text
                val metrics = tweet.public_metrics
                val user = usersMap[tweet.author_id]

                val engagementScore = calculateEngagementScore(metrics)

                if (!skipDiscoveryFilters) {
                    if (isSpam(text, metrics, engagementScore)) return@mapNotNull null

                    val normalized = normalizeText(text)
                    if (normalized.length >= MIN_TEXT_DEDUPE_LENGTH && !seenTexts.add(normalized)) {
                        return@mapNotNull null
                    }
                }

                val impactScore = calculateImpactScore(tweet, user, engagementScore, now)

                val media = tweet.attachments?.media_keys?.mapNotNull { mediaMap[it] }
                val urls = tweet.entities?.urls?.map { it.expanded_url }
                val type = tweet.referenced_tweets?.firstOrNull()?.type
                val author = resolveAuthor(user, tweet.id)

                XPost(
                    id = tweet.id,
                    text = tweet.text,
                    author_id = tweet.author_id,
                    user_name = author.displayName,
                    username = author.username,
                    url = author.statusUrl,
                    created_at = tweet.created_at,
                    metrics = metrics,
                    media = media,
                    urls = urls,
                    type = type,
                    impact_score = impactScore,
                )
            }.let { ranked ->
                when (ordering) {
                    XPostOrdering.IMPACT -> ranked.sortedByDescending { it.impact_score }
                    XPostOrdering.RECENCY -> ranked.sortedByDescending { it.created_at ?: "" }
                }
            }

            val distinctPosts =
                when (ordering) {
                    XPostOrdering.RECENCY -> posts.take(maxTotalPosts)
                    XPostOrdering.IMPACT -> {
                        val capped = mutableListOf<XPost>()
                        val authorCounts = mutableMapOf<String, Int>()
                        for (post in posts) {
                            val authorId = post.author_id ?: "unknown"
                            val count = authorCounts.getOrDefault(authorId, 0)
                            if (count < maxPostsPerAuthor) {
                                capped.add(post)
                                authorCounts[authorId] = count + 1
                            }
                            if (capped.size >= maxTotalPosts) break
                        }
                        capped
                    }
                }

            return XSearchPaginatedResult(
                data = distinctPosts,
                next_token = sanitizePaginationToken(response.meta?.next_token),
                pagination_source = "search",
            )
        }

        fun fetchUserTimeline(
            username: String,
            maxResults: Int = 10,
            paginationToken: String? = null,
        ): XSearchPaginatedResult =
            userTimelineFetcher.fetchUserTimeline(
                username = username,
                maxResults = maxResults,
                paginationToken = paginationToken,
                mapPost = ::mapTweetToPost,
                searchFallback = { cleanUser, token ->
                    searchPaginated(
                        query = "from:$cleanUser",
                        sortOrder = "recency",
                        maxPostsPerAuthor = maxResults,
                        maxTotalPosts = maxResults,
                        nextToken = token,
                        ordering = XPostOrdering.RECENCY,
                        skipDiscoveryFilters = true,
                    )
                },
            )

        fun search(query: String, sortOrder: String = "relevancy"): String {
            val result = searchPaginated(query, sortOrder, MAX_POSTS_PER_AUTHOR, MAX_TOTAL_POSTS, null)
            return json.encodeToString(result.data)
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
            val response = xApiJson.decodeFromString<XSearchResponse>(body)
            val tweets = response.data ?: return json.encodeToString(emptyList<XPost>())

            val allUsers = response.includes?.users.orEmpty().toMutableList()
            apiKeyManager.get(ApiKeyManager.X_SEARCH_ALIAS)?.let { apiKey ->
                ensureUsersForAuthorIds(tweets.map { it.author_id }, allUsers, apiKey)
            }
            val usersMap = allUsers.associateBy { it.id }
            val mediaMap = response.includes?.media?.associateBy { it.media_key } ?: emptyMap()

            val seenTexts = mutableSetOf<String>()
            val now = Instant.now()

            val posts =
                tweets
                    .mapNotNull { tweet ->
                        mapTweetToPost(
                            tweet = tweet,
                            usersMap = usersMap,
                            mediaMap = mediaMap,
                            now = now,
                            anchorId = null,
                            seenTexts = seenTexts,
                            applyRanking = true,
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

    fun getThread(tweetUrlOrId: String): String {
        val apiKey = apiKeyManager.get(ApiKeyManager.X_SEARCH_ALIAS)
            ?: throw IllegalStateException("X API key is required when X search is enabled")

        val requestedId = parseTweetIdFromUrl(tweetUrlOrId)

        val targetResponse = fetchTweetsByIds(listOf(requestedId), apiKey)
        val targetTweet = targetResponse.data?.firstOrNull()
            ?: throw IOException("Tweet not found or inaccessible")

        val anchorId = targetTweet.id
        val tweetsById = linkedMapOf<String, XTweet>()
        val allUsers = mutableListOf<XUser>()
        val allMedia = mutableListOf<XMedia>()

        mergeTweetLookupResponse(targetResponse, tweetsById, allUsers, allMedia)

        var ancestorIds = collectAncestorIds(targetTweet, apiKey, tweetsById, allUsers, allMedia)
        if (ancestorIds.isEmpty()) {
            ancestorIds =
                collectAncestorIdsFromConversation(
                    targetTweet,
                    apiKey,
                    tweetsById,
                    allUsers,
                    allMedia,
                )
        }

        ensureUsersForAuthorIds(tweetsById.values.map { it.author_id }, allUsers, apiKey)
        val usersMap = allUsers.distinctBy { it.id }.associateBy { it.id }
        val mediaMap = allMedia.distinctBy { it.media_key }.associateBy { it.media_key }
        val now = Instant.now()

        val requestedPost =
            mapTweetToPost(
                tweet = targetTweet,
                usersMap = usersMap,
                mediaMap = mediaMap,
                now = now,
                anchorId = anchorId,
                seenTexts = mutableSetOf(),
                applyRanking = false,
                forceInclude = true,
            )?.copy(is_anchor = true)
                ?: return threadJson.encodeToString(
                    XThreadResponse(
                        requested_post = null,
                        ancestor_posts = emptyList(),
                        data = emptyList(),
                        anchor_tweet_id = anchorId,
                        warning = "Requested tweet could not be mapped.",
                    ),
                )

        val ancestorPosts =
            ancestorIds.mapNotNull { ancestorId ->
                tweetsById[ancestorId]?.let { ancestorTweet ->
                    mapTweetToPost(
                        tweet = ancestorTweet,
                        usersMap = usersMap,
                        mediaMap = mediaMap,
                        now = now,
                        anchorId = anchorId,
                        seenTexts = mutableSetOf(),
                        applyRanking = false,
                        forceInclude = true,
                    )
                }
            }.sortedWith(compareBy<XPost> { it.created_at ?: "" }.thenBy { it.id })

        val finalPosts =
            (ancestorPosts + requestedPost).sortedWith(
                compareBy<XPost> { it.created_at ?: "" }.thenBy { it.id },
            )

        val warning =
            if (ancestorIds.isNotEmpty() && ancestorPosts.size < ancestorIds.size) {
                THREAD_RECENCY_WARNING
            } else if (ancestorIds.isEmpty() && targetTweet.referenced_tweets?.any { it.type == "replied_to" } == true) {
                THREAD_RECENCY_WARNING
            } else {
                null
            }

        return threadJson.encodeToString(
            XThreadResponse(
                requested_post = requestedPost,
                ancestor_posts = ancestorPosts,
                data = finalPosts,
                anchor_tweet_id = anchorId,
                warning = warning,
            ),
        )
    }

    private fun parseTweetIdFromUrl(tweetUrlOrId: String): String {
        val trimmed = tweetUrlOrId.trim()
        if (trimmed.all { it.isDigit() }) return trimmed

        val statusMatches = Regex("""/status/(\d+)""", RegexOption.IGNORE_CASE).findAll(trimmed).toList()
        if (statusMatches.isNotEmpty()) {
            return statusMatches.last().groupValues[1]
        }

        return trimmed
            .substringAfter("/status/", missingDelimiterValue = trimmed)
            .substringBefore("?")
            .substringBefore("/")
            .trim()
    }

    private fun mergeTweetLookupResponse(
        response: XSearchResponse,
        tweetsById: MutableMap<String, XTweet>,
        users: MutableList<XUser>,
        media: MutableList<XMedia>,
    ) {
        response.data?.forEach { tweet -> tweetsById[tweet.id] = tweet }
        response.includes?.tweets?.forEach { tweet -> tweetsById.putIfAbsent(tweet.id, tweet) }
        response.includes?.users?.let { users.addAll(it) }
        response.includes?.media?.let { media.addAll(it) }
    }

    private fun collectAncestorIds(
        start: XTweet,
        apiKey: String,
        tweetsById: MutableMap<String, XTweet>,
        users: MutableList<XUser>,
        media: MutableList<XMedia>,
    ): LinkedHashSet<String> {
        val ancestorIds = linkedSetOf<String>()
        var current: XTweet? = start
        while (current != null) {
            val parentId =
                current.referenced_tweets
                    ?.firstOrNull { it.type == "replied_to" }
                    ?.id
            if (parentId == null || parentId in ancestorIds) {
                break
            }
            ancestorIds.add(parentId)
            if (!tweetsById.containsKey(parentId)) {
                val parentResponse = fetchTweetsByIds(listOf(parentId), apiKey)
                mergeTweetLookupResponse(parentResponse, tweetsById, users, media)
            }
            current = tweetsById[parentId]
        }
        return ancestorIds
    }

    private fun collectAncestorIdsFromConversation(
        target: XTweet,
        apiKey: String,
        tweetsById: MutableMap<String, XTweet>,
        users: MutableList<XUser>,
        media: MutableList<XMedia>,
    ): LinkedHashSet<String> {
        val conversationId = target.conversation_id ?: return linkedSetOf()
        fetchConversationTweets(conversationId, apiKey, tweetsById, users, media)
        if (!tweetsById.containsKey(conversationId)) {
            val rootResponse = fetchTweetsByIds(listOf(conversationId), apiKey)
            mergeTweetLookupResponse(rootResponse, tweetsById, users, media)
        }
        return collectAncestorIds(target, apiKey, tweetsById, users, media)
    }

    private fun fetchConversationTweets(
        conversationId: String,
        apiKey: String,
        tweetsById: MutableMap<String, XTweet>,
        users: MutableList<XUser>,
        media: MutableList<XMedia>,
    ) {
        var nextToken: String? = null
        repeat(MAX_CONVERSATION_SEARCH_PAGES) {
            val body =
                fetchRecentSearchRaw(
                    query = "conversation_id:$conversationId",
                    sortOrder = "recency",
                    nextToken = nextToken,
                    apiKey = apiKey,
                )
            val response = xApiJson.decodeFromString<XSearchResponse>(body)
            mergeTweetLookupResponse(response, tweetsById, users, media)
            nextToken = sanitizePaginationToken(response.meta?.next_token)
            if (nextToken == null) return
        }
    }

    private fun mapTweetToPost(
        tweet: XTweet,
        usersMap: Map<String, XUser>,
        mediaMap: Map<String, XMedia>,
        now: Instant,
        anchorId: String?,
        seenTexts: MutableSet<String>,
        applyRanking: Boolean,
        forceInclude: Boolean = false,
    ): XPost? {
        val isAnchor = tweet.id == anchorId
        if (!forceInclude && !isAnchor) {
            val engagementScore = calculateEngagementScore(tweet.public_metrics)
            if (isSpam(tweet.text, tweet.public_metrics, engagementScore)) return null

            val normalized = normalizeText(tweet.text)
            if (normalized.length >= MIN_TEXT_DEDUPE_LENGTH && !seenTexts.add(normalized)) {
                return null
            }
        }

        val user = usersMap[tweet.author_id]
        val engagementScore = calculateEngagementScore(tweet.public_metrics)
        val impactScore =
            if (applyRanking) {
                calculateImpactScore(tweet, user, engagementScore, now)
            } else {
                0.0
            }
        val media = tweet.attachments?.media_keys?.mapNotNull { mediaMap[it] }
        val urls = tweet.entities?.urls?.map { it.expanded_url }
        val type = tweet.referenced_tweets?.firstOrNull()?.type
        val author = resolveAuthor(user, tweet.id)

        return XPost(
            id = tweet.id,
            text = tweet.text,
            author_id = tweet.author_id,
            user_name = author.displayName,
            username = author.username,
            url = author.statusUrl,
            created_at = tweet.created_at,
            metrics = tweet.public_metrics,
            media = media,
            urls = urls,
            type = type,
            impact_score = impactScore,
            is_anchor = isAnchor,
        )
    }

    private fun resolveAuthor(user: XUser?, tweetId: String): ResolvedAuthor {
        val handle = user?.username?.takeIf { it.isNotBlank() }
        val displayName = user?.name?.takeIf { it.isNotBlank() } ?: handle ?: "Unknown user"
        val statusUrl =
            if (!handle.isNullOrBlank()) {
                "https://x.com/$handle/status/$tweetId"
            } else {
                "https://x.com/i/status/$tweetId"
            }
        return ResolvedAuthor(
            displayName = displayName,
            username = handle,
            statusUrl = statusUrl,
        )
    }

    private fun ensureUsersForAuthorIds(
        authorIds: Collection<String?>,
        users: MutableList<XUser>,
        apiKey: String,
    ) {
        val knownIds = users.map { it.id }.toSet()
        val missing = authorIds.filterNotNull().filter { it !in knownIds }.distinct()
        missing.chunked(100).forEach { chunk ->
            users.addAll(fetchUsersByIds(chunk, apiKey))
        }
    }

    private fun fetchUsersByIds(ids: List<String>, apiKey: String): List<XUser> {
        if (ids.isEmpty()) return emptyList()
        val url =
            "https://api.x.com/2/users"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("ids", ids.joinToString(","))
                .addQueryParameter("user.fields", "name,username,verified,public_metrics")
                .build()

        val request =
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return emptyList()
            }
            val body = response.body.string()
            val parsed = xApiJson.decodeFromString<XUsersByIdsResponse>(body)
            return parsed.data.orEmpty()
        }
    }

    private fun fetchTweetsByIds(ids: List<String>, apiKey: String): XSearchResponse {
        if (ids.isEmpty()) return XSearchResponse()
        val url = "https://api.x.com/2/tweets"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("ids", ids.joinToString(","))
            .addQueryParameter("tweet.fields", "public_metrics,created_at,author_id,entities,referenced_tweets,conversation_id")
            .addQueryParameter("expansions", TWEET_LOOKUP_EXPANSIONS)
            .addQueryParameter("user.fields", "name,username,verified,public_metrics")
            .addQueryParameter("media.fields", "type,url,alt_text")
            .build()

        val request = Request.Builder()
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
            return xApiJson.decodeFromString<XSearchResponse>(body)
        }
    }

    fun getTrends(location: String): String {
        val apiKey = apiKeyManager.get(ApiKeyManager.X_SEARCH_ALIAS)
            ?: throw IllegalStateException("X API key is required when X search is enabled")

        val woeid = when (location.trim().lowercase()) {
            "worldwide" -> "1"
            "united states" -> "23424977"
            "united kingdom" -> "23424975"
            "canada" -> "23424960"
            "australia" -> "23424900"
            "germany" -> "23424901"
            "france" -> "23424902"
            "japan" -> "23424903"
            "india" -> "23424904"
            "brazil" -> "23424905"
            else -> "1"
        }

        val url = "https://api.x.com/2/trends/by/woeid/$woeid"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("trend.fields", "tweet_count")
            .build()

        val request = Request.Builder()
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
            val parsed = json.decodeFromString<XTrendsResponse>(body)
            return json.encodeToString(parsed)
        }
    }
}

@Serializable
data class XThreadResponse(
    /** The post from the URL/ID the caller requested (never the parent it replies to). */
    val requested_post: XPost? = null,
    /** Posts the requested tweet is replying to, oldest first. */
    val ancestor_posts: List<XPost> = emptyList(),
    /** requested_post plus ancestor_posts in chronological order. */
    val data: List<XPost>,
    val anchor_tweet_id: String,
    val warning: String? = null,
)
