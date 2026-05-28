package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.security.ApiKeyManager
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

typealias XTweetPostMapper = (
    tweet: XTweet,
    usersMap: Map<String, XUser>,
    mediaMap: Map<String, XMedia>,
    now: Instant,
    anchorId: String?,
    seenTexts: MutableSet<String>,
    applyRanking: Boolean,
    forceInclude: Boolean,
) -> XPost?

@Singleton
class XUserTimelineFetcher
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val apiKeyManager: ApiKeyManager,
    ) {
        private val userIdCache = mutableMapOf<String, String>()

        fun fetchUserTimeline(
            username: String,
            maxResults: Int,
            paginationToken: String?,
            mapPost: XTweetPostMapper,
            searchFallback: (String, String?) -> XSearchPaginatedResult,
        ): XSearchPaginatedResult {
            val cleanUsername = username.removePrefix("@").trim()
            val sanitizedToken = sanitizePaginationToken(paginationToken)
            return try {
                fetchUserTimelineInternal(cleanUsername, maxResults, sanitizedToken, mapPost)
            } catch (e: IOException) {
                when {
                    sanitizedToken != null && isInvalidPaginationTokenError(e) ->
                        searchFallback(cleanUsername.lowercase(), sanitizedToken)
                    sanitizedToken == null && shouldFallbackTimelineToSearch(e) ->
                        searchFallback(cleanUsername.lowercase(), null)
                    else -> throw e
                }
            }
        }

        private fun fetchUserTimelineInternal(
            username: String,
            maxResults: Int,
            paginationToken: String?,
            mapPost: XTweetPostMapper,
        ): XSearchPaginatedResult {
            val apiKey =
                apiKeyManager.get(ApiKeyManager.X_SEARCH_ALIAS)
                    ?: throw IllegalStateException("X API key is required when X search is enabled")

            val cleanUsername = username.lowercase()
            val userId = resolveUserId(cleanUsername, apiKey)
            val cappedMax = maxResults.coerceIn(5, 100)

            val urlBuilder =
                "https://api.x.com/2/users/$userId/tweets"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("max_results", cappedMax.toString())
                    .addQueryParameter("tweet.fields", "public_metrics,created_at,author_id,entities,referenced_tweets")
                    .addQueryParameter("expansions", "author_id,attachments.media_keys")
                    .addQueryParameter("user.fields", "name,username,verified,public_metrics")
                    .addQueryParameter("media.fields", "type,url,alt_text")

            if (paginationToken != null) {
                urlBuilder.addQueryParameter("pagination_token", paginationToken)
            }

            val request =
                Request
                    .Builder()
                    .url(urlBuilder.build())
                    .header("Authorization", "Bearer $apiKey")
                    .get()
                    .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body.string()
                    throw IOException("X request failed with HTTP ${response.code}: $errorBody")
                }
                val body = response.body.string()
                val parsed = xApiJson.decodeFromString<XSearchResponse>(body)
                val tweets = parsed.data ?: return XSearchPaginatedResult(emptyList(), null)

                val usersMap = parsed.includes?.users?.associateBy { it.id } ?: emptyMap()
                val mediaMap = parsed.includes?.media?.associateBy { it.media_key } ?: emptyMap()
                val now = Instant.now()
                val seenTexts = mutableSetOf<String>()

                val posts =
                    tweets
                        .mapNotNull { tweet ->
                            // Timeline is author-owned content; skip discovery spam/dedupe filters.
                            mapPost(tweet, usersMap, mediaMap, now, null, seenTexts, false, true)
                        }.sortedByDescending { it.created_at ?: "" }

                return XSearchPaginatedResult(
                    data = posts,
                    next_token = sanitizePaginationToken(parsed.meta?.next_token),
                    pagination_source = "timeline",
                )
            }
        }

        private fun resolveUserId(
            username: String,
            apiKey: String,
        ): String {
            userIdCache[username]?.let { return it }

            val url =
                "https://api.x.com/2/users/by/username/$username"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("user.fields", "id,username")
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
                    throw IOException("X user lookup failed with HTTP ${response.code}: $errorBody")
                }
                val body = response.body.string()
                val parsed = xApiJson.decodeFromString<XUserLookupResponse>(body)
                val userId =
                    parsed.data?.id
                        ?: throw IOException("X user @$username not found")
                userIdCache[username] = userId
                return userId
            }
        }
    }
