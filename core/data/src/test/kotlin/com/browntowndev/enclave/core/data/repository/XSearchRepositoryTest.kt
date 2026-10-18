package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import org.robolectric.annotation.Config as RoboConfig

/**
 * Translated from MockK (ENC-28): only the pure `mapResponse` mapping is
 * exercised, so the repository is the real class wired over a real
 * OkHttpClient and a real ApiKeyManager over cleared Robolectric prefs
 * (its `get` returns null, as the former stub did). No HTTP call is made.
 */
@ExtendWith(RobolectricExtension::class)
@RoboConfig(sdk = [34])
class XSearchRepositoryTest {
    private lateinit var repository: XSearchRepository

    @BeforeEach
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val apiKeyManager: ApiKeyManager =
            testApiKeyManager(context, CoroutineScope(SupervisorJob() + Dispatchers.Default), "x_search_test_prefs")
        repository =
            XSearchRepository(
                okHttpClient = OkHttpClient(),
                apiKeyManager = apiKeyManager,
                userTimelineFetcher = XUserTimelineFetcher(OkHttpClient(), apiKeyManager),
            )
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `mapResponse flattens data and calculates impact scores correctly`() {
        val rawResponse =
            """
            {
              "data": [
                {
                  "id": "1",
                  "text": "Low impact post",
                  "author_id": "u1",
                  "public_metrics": { "retweet_count": 1, "reply_count": 0, "like_count": 1, "impression_count": 10 }
                },
                {
                  "id": "2",
                  "text": "High impact post",
                  "author_id": "u2",
                  "public_metrics": { "retweet_count": 100, "reply_count": 10, "like_count": 500, "impression_count": 10000 }
                }
              ],
              "includes": {
                "users": [
                  { "id": "u1", "name": "User One", "username": "user1" },
                  { "id": "u2", "name": "User Two", "username": "user2" }
                ]
              }
            }
            """.trimIndent()

        val mappedJson = repository.mapResponse(rawResponse)
        val posts = json.parseToJsonElement(mappedJson).jsonArray

        assertEquals(2, posts.size)

        // High impact should be first
        val firstPost = posts[0].jsonObject
        assertEquals("2", firstPost["id"]?.jsonPrimitive?.content)
        assertEquals("User Two", firstPost["user_name"]?.jsonPrimitive?.content)

        val score1 = firstPost["impact_score"]?.jsonPrimitive?.content?.toDouble() ?: 0.0
        val score2 =
            posts[1]
                .jsonObject["impact_score"]
                ?.jsonPrimitive
                ?.content
                ?.toDouble() ?: 0.0

        assertTrue(score1 > score2, "High impact post should have higher score than low impact post")
    }

    @Test
    fun `mapResponse enforces per-author limit`() {
        val rawResponse =
            """
            {
              "data": [
                { "id": "1", "text": "Post 1", "author_id": "u1", "public_metrics": { "like_count": 10, "retweet_count": 0, "impression_count": 0 } },
                { "id": "2", "text": "Post 2", "author_id": "u1", "public_metrics": { "like_count": 9, "retweet_count": 0, "impression_count": 0 } },
                { "id": "3", "text": "Post 3", "author_id": "u1", "public_metrics": { "like_count": 8, "retweet_count": 0, "impression_count": 0 } },
                { "id": "4", "text": "Post 4", "author_id": "u2", "public_metrics": { "like_count": 11, "retweet_count": 0, "impression_count": 0 } }
              ],
              "includes": {
                "users": [
                  { "id": "u1", "name": "User One", "username": "user1" },
                  { "id": "u2", "name": "User Two", "username": "user2" }
                ]
              }
            }
            """.trimIndent()

        val mappedJson = repository.mapResponse(rawResponse)
        val posts = json.parseToJsonElement(mappedJson).jsonArray

        // Should have 3 posts: Post 4 (u2), Post 1 (u1), Post 2 (u1). Post 3 (u1) should be dropped.
        assertEquals(3, posts.size)
        val authorIds = posts.map { it.jsonObject["author_id"]?.jsonPrimitive?.content }
        assertEquals(listOf("u2", "u1", "u1"), authorIds)
    }

    @Test
    fun `mapResponse handles media and entities`() {
        val rawResponse =
            """
            {
              "data": [
                {
                  "id": "1",
                  "text": "Post with media and url",
                  "author_id": "u1",
                  "attachments": { "media_keys": ["m1"] },
                  "entities": { "urls": [{ "expanded_url": "https://example.com" }] }
                }
              ],
              "includes": {
                "users": [{ "id": "u1", "name": "User One", "username": "user1" }],
                "media": [{ "media_key": "m1", "type": "photo", "url": "https://x.com/m1.jpg" }]
              }
            }
            """.trimIndent()

        val mappedJson = repository.mapResponse(rawResponse)
        val posts = json.parseToJsonElement(mappedJson).jsonArray
        val post = posts[0].jsonObject

        val media = post["media"]?.jsonArray
        assertEquals(1, media?.size)
        assertEquals(
            "photo",
            media
                ?.get(0)
                ?.jsonObject
                ?.get("type")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(
            "https://x.com/m1.jpg",
            media
                ?.get(0)
                ?.jsonObject
                ?.get("url")
                ?.jsonPrimitive
                ?.content,
        )

        val urls = post["urls"]?.jsonArray
        assertEquals(1, urls?.size)
        assertEquals("https://example.com", urls?.get(0)?.jsonPrimitive?.content)
    }

    @Test
    fun `mapResponse filters out hashtag stuffing and cashtag stuffing`() {
        val rawResponse =
            """
            {
              "data": [
                {
                  "id": "1",
                  "text": "Valid post #tag1 #tag2 #tag3",
                  "author_id": "u1",
                  "public_metrics": { "like_count": 10 }
                },
                {
                  "id": "2",
                  "text": "Hashtag stuffing #tag1 #tag2 #tag3 #tag4",
                  "author_id": "u1",
                  "public_metrics": { "like_count": 100 }
                },
                {
                  "id": "3",
                  "text": "Cashtag stuffing ${'$'}BTC ${'$'}ETH ${'$'}SOL",
                  "author_id": "u1",
                  "public_metrics": { "like_count": 100 }
                }
              ],
              "includes": {
                "users": [{ "id": "u1", "name": "User One", "username": "user1" }]
              }
            }
            """.trimIndent()

        val mappedJson = repository.mapResponse(rawResponse)
        val posts = json.parseToJsonElement(mappedJson).jsonArray

        // Only "Valid post" should remain, others filtered
        assertEquals(1, posts.size)
        assertEquals("1", posts[0].jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `mapResponse filters out spam phrases`() {
        val rawResponse =
            """
            {
              "data": [
                {
                  "id": "1",
                  "text": "Valid tech discussion on AI",
                  "author_id": "u1",
                  "public_metrics": { "like_count": 10 }
                },
                {
                  "id": "2",
                  "text": "Whatsapp me for crypto signal",
                  "author_id": "u1",
                  "public_metrics": { "like_count": 100 }
                },
                {
                  "id": "3",
                  "text": "Send pic on direct message to collab",
                  "author_id": "u1",
                  "public_metrics": { "like_count": 100 }
                }
              ],
              "includes": {
                "users": [{ "id": "u1", "name": "User One", "username": "user1" }]
              }
            }
            """.trimIndent()

        val mappedJson = repository.mapResponse(rawResponse)
        val posts = json.parseToJsonElement(mappedJson).jsonArray

        assertEquals(1, posts.size)
        assertEquals("1", posts[0].jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `mapResponse filters out low engagement ratio posts`() {
        val rawResponse =
            """
            {
              "data": [
                {
                  "id": "1",
                  "text": "Post with 0 engagement but high impressions",
                  "author_id": "u1",
                  "public_metrics": { "impression_count": 2000, "like_count": 0, "reply_count": 0, "retweet_count": 0 }
                },
                {
                  "id": "2",
                  "text": "Post with engagement and high impressions",
                  "author_id": "u1",
                  "public_metrics": { "impression_count": 2000, "like_count": 10 }
                }
              ],
              "includes": {
                "users": [{ "id": "u1", "name": "User One", "username": "user1" }]
              }
            }
            """.trimIndent()

        val mappedJson = repository.mapResponse(rawResponse)
        val posts = json.parseToJsonElement(mappedJson).jsonArray

        assertEquals(1, posts.size)
        assertEquals("2", posts[0].jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `mapResponse deduplicates posts with similar normalized text`() {
        val rawResponse =
            """
            {
              "data": [
                {
                  "id": "1",
                  "text": "Breaking: AI breakthrough announced today! https://example.com/ai",
                  "author_id": "u1",
                  "public_metrics": { "like_count": 10 }
                },
                {
                  "id": "2",
                  "text": "BREAKING: AI Breakthrough announced today! https://different.com/news",
                  "author_id": "u2",
                  "public_metrics": { "like_count": 5 }
                }
              ],
              "includes": {
                "users": [
                  { "id": "u1", "name": "User One", "username": "user1" },
                  { "id": "u2", "name": "User Two", "username": "user2" }
                ]
              }
            }
            """.trimIndent()

        val mappedJson = repository.mapResponse(rawResponse)
        val posts = json.parseToJsonElement(mappedJson).jsonArray

        // Only first post should be kept, second is a duplicate signature
        assertEquals(1, posts.size)
        assertEquals("1", posts[0].jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `mapResponse correctly applies verification and followers metrics`() {
        val rawResponse =
            """
            {
              "data": [
                {
                  "id": "1",
                  "text": "Standard user post",
                  "author_id": "u1",
                  "public_metrics": { "like_count": 10 }
                },
                {
                  "id": "2",
                  "text": "Verified giant user post",
                  "author_id": "u2",
                  "public_metrics": { "like_count": 10 }
                }
              ],
              "includes": {
                "users": [
                  { 
                    "id": "u1", 
                    "name": "User One", 
                    "username": "user1",
                    "verified": false,
                    "public_metrics": { "followers_count": 10, "following_count": 10 }
                  },
                  { 
                    "id": "u2", 
                    "name": "User Two", 
                    "username": "user2",
                    "verified": true,
                    "public_metrics": { "followers_count": 1000000, "following_count": 100 }
                  }
                ]
              }
            }
            """.trimIndent()

        val mappedJson = repository.mapResponse(rawResponse)
        val posts = json.parseToJsonElement(mappedJson).jsonArray

        assertEquals(2, posts.size)
        val post1Score =
            posts[0]
                .jsonObject["impact_score"]
                ?.jsonPrimitive
                ?.content
                ?.toDouble() ?: 0.0
        val post2Score =
            posts[1]
                .jsonObject["impact_score"]
                ?.jsonPrimitive
                ?.content
                ?.toDouble() ?: 0.0

        // User Two is verified and has huge followers, should rank first with significantly higher score
        assertEquals("2", posts[0].jsonObject["id"]?.jsonPrimitive?.content)
        assertTrue(post1Score > post2Score, "Giant verified user should have higher impact score than standard user for same likes")
    }
}
