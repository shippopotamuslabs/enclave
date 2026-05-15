package com.browntowndev.pocketcrew.core.data.repository

import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XSearchRepositoryTest {
    private val repository =
        XSearchRepository(
            okHttpClient = mockk(),
            apiKeyManager = mockk(),
        )

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
}
