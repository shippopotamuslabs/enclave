package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeSettingsRepository
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.port.repository.SettingsData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import org.robolectric.annotation.Config as RoboConfig

/**
 * Translated from MockK to a real OkHttpClient over a routing stub
 * interceptor (ENC-28): each test registers the same URL predicates the
 * MockK stubs matched on, the interceptor records every request (replacing
 * the MockK capture slots), and the executors/repository under test are
 * the real classes over a real ApiKeyManager and settings fake.
 */
@ExtendWith(RobolectricExtension::class)
@RoboConfig(sdk = [34])
class XPlatformToolsTest {
    private val threadTestJson = Json { ignoreUnknownKeys = true }

    private val interceptor = StubHttpInterceptor()
    private val okHttpClient = stubHttpClient(interceptor)
    private lateinit var apiKeyManager: ApiKeyManager
    private val settingsRepository = FakeSettingsRepository(SettingsData(xSearchEnabled = true, xApiKeyPresent = true))
    private val loggingPort = FakeLoggingPort()

    private lateinit var xSearchRepository: XSearchRepository
    private lateinit var getThreadExecutor: XGetThreadToolExecutor
    private lateinit var trendingExecutor: XTrendingToolExecutor
    private lateinit var userTweetsExecutor: XUserTweetsToolExecutor
    private lateinit var mentionSearchExecutor: XMentionSearchToolExecutor

    @BeforeEach
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        apiKeyManager =
            testApiKeyManager(context, CoroutineScope(SupervisorJob() + Dispatchers.Default), "x_platform_tools_prefs")
        xSearchRepository =
            XSearchRepository(
                okHttpClient,
                apiKeyManager,
                XUserTimelineFetcher(okHttpClient, apiKeyManager),
            )
        getThreadExecutor = XGetThreadToolExecutor(loggingPort, settingsRepository, xSearchRepository)
        trendingExecutor = XTrendingToolExecutor(loggingPort, settingsRepository, xSearchRepository)
        userTweetsExecutor = XUserTweetsToolExecutor(loggingPort, settingsRepository, xSearchRepository)
        mentionSearchExecutor = XMentionSearchToolExecutor(loggingPort, settingsRepository, xSearchRepository)

        apiKeyManager.save(ApiKeyManager.X_SEARCH_ALIAS, "mock-bearer-token")
    }

    @Test
    fun `SCENARIO_GET_THREAD_SUCCESS`() =
        runTest {
            val targetTweetJson =
                """
                {
                  "data": [
                    {
                      "id": "20",
                      "text": "just setting up my twttr",
                      "conversation_id": "20",
                      "author_id": "12",
                      "created_at": "2006-03-21T20:50:14.000Z"
                    }
                  ],
                  "includes": {
                    "users": [
                      {
                        "id": "12",
                        "name": "jack",
                        "username": "jack"
                      }
                    ]
                  }
                }
                """.trimIndent()

            val repliesJson =
                """
                {
                  "data": [
                    {
                      "id": "25",
                      "text": "cool project!",
                      "conversation_id": "20",
                      "author_id": "15",
                      "created_at": "2006-03-21T20:55:00.000Z"
                    }
                  ],
                  "includes": {
                    "users": [
                      {
                        "id": "15",
                        "name": "biz",
                        "username": "biz"
                      }
                    ]
                  }
                }
                """.trimIndent()

            interceptor.respondWhen(
                { it.url.toString().contains("/2/tweets") && !it.url.toString().contains("search/recent") },
                200,
                targetTweetJson,
            )
            interceptor.respondWhen({ it.url.toString().contains("search/recent") }, 200, repliesJson)

            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-1"),
                    userMessageId = MessageId("msg-1"),
                    toolName = "x_get_thread",
                    argumentsJson = """{"tweet_url": "https://x.com/jack/status/20"}""",
                    provider = "x",
                    modelType = ModelType.FAST,
                )

            val result = getThreadExecutor.execute(request)

            val thread = threadTestJson.decodeFromString<XThreadResponse>(result.resultJson)
            assertEquals("20", thread.anchor_tweet_id)
            assertEquals("20", thread.requested_post?.id)
            assertTrue(thread.requested_post?.is_anchor == true)
            assertTrue(thread.ancestor_posts.isEmpty())
            assertEquals(1, thread.data.size)
            assertEquals("20", thread.data.single().id)
        }

    @Test
    fun `SCENARIO_GET_THREAD_AS_REPLY`() =
        runTest {
            val targetTweetJson =
                """
                {
                  "data": [
                    {
                      "id": "25",
                      "text": "cool project!",
                      "conversation_id": "20",
                      "author_id": "15",
                      "created_at": "2006-03-21T20:55:00.000Z",
                      "referenced_tweets": [
                        {
                          "type": "replied_to",
                          "id": "20"
                        }
                      ]
                    }
                  ],
                  "includes": {
                    "users": [
                      {
                        "id": "15",
                        "name": "biz",
                        "username": "biz"
                      }
                    ]
                  }
                }
                """.trimIndent()

            val rootTweetJson =
                """
                {
                  "data": [
                    {
                      "id": "20",
                      "text": "just setting up my twttr",
                      "conversation_id": "20",
                      "author_id": "12",
                      "created_at": "2006-03-21T20:50:14.000Z"
                    }
                  ],
                  "includes": {
                    "users": [
                      {
                        "id": "12",
                        "name": "jack",
                        "username": "jack"
                      }
                    ]
                  }
                }
                """.trimIndent()

            val repliesJson =
                """
                {
                  "data": [
                    {
                      "id": "25",
                      "text": "cool project!",
                      "conversation_id": "20",
                      "author_id": "15",
                      "created_at": "2006-03-21T20:55:00.000Z"
                    }
                  ],
                  "includes": {
                    "users": [
                      {
                        "id": "15",
                        "name": "biz",
                        "username": "biz"
                      }
                    ]
                  }
                }
                """.trimIndent()

            interceptor.respondWhen(
                { it.url.toString().contains("/2/tweets") && it.url.queryParameter("ids") == "25" },
                200,
                targetTweetJson,
            )
            interceptor.respondWhen(
                { it.url.toString().contains("/2/tweets") && it.url.queryParameter("ids") == "20" },
                200,
                rootTweetJson,
            )
            interceptor.respondWhen({ it.url.toString().contains("search/recent") }, 200, repliesJson)

            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-1"),
                    userMessageId = MessageId("msg-1"),
                    toolName = "x_get_thread",
                    argumentsJson = """{"tweet_url": "https://x.com/biz/status/25"}""",
                    provider = "x",
                    modelType = ModelType.FAST,
                )

            val result = getThreadExecutor.execute(request)

            val thread = threadTestJson.decodeFromString<XThreadResponse>(result.resultJson)
            assertEquals("25", thread.anchor_tweet_id)
            assertEquals("25", thread.requested_post?.id)
            assertTrue(thread.requested_post?.is_anchor == true)
            assertEquals(1, thread.ancestor_posts.size)
            assertEquals("20", thread.ancestor_posts.single().id)
            assertFalse(thread.ancestor_posts.single().is_anchor)
            assertEquals(2, thread.data.size)
            assertEquals(listOf("20", "25"), thread.data.map { it.id })
        }

    @Test
    fun `SCENARIO_GET_THREAD_FETCHES_MISSING_AUTHOR`() =
        runTest {
            val targetTweetJson =
                """
                {
                  "data": [
                    {
                      "id": "25",
                      "text": "reply text",
                      "conversation_id": "20",
                      "author_id": "15",
                      "created_at": "2006-03-21T20:55:00.000Z",
                      "referenced_tweets": [
                        {
                          "type": "replied_to",
                          "id": "20"
                        }
                      ]
                    }
                  ],
                  "includes": {
                    "users": [
                      {
                        "id": "15",
                        "name": "Biz Stone",
                        "username": "biz"
                      }
                    ]
                  }
                }
                """.trimIndent()

            val parentTweetJson =
                """
                {
                  "data": [
                    {
                      "id": "20",
                      "text": "parent text",
                      "conversation_id": "20",
                      "author_id": "99",
                      "created_at": "2006-03-21T20:50:14.000Z"
                    }
                  ]
                }
                """.trimIndent()

            val parentUserJson =
                """
                {
                  "data": [
                    {
                      "id": "99",
                      "name": "Antigravity User",
                      "username": "antigravity_dev"
                    }
                  ]
                }
                """.trimIndent()

            val repliesJson =
                """
                {
                  "data": []
                }
                """.trimIndent()

            interceptor.respondWhen({ it.url.queryParameter("ids") == "25" }, 200, targetTweetJson)
            interceptor.respondWhen({ it.url.queryParameter("ids") == "20" }, 200, parentTweetJson)
            interceptor.respondWhen(
                { it.url.toString().contains("/2/users") && it.url.queryParameter("ids") == "99" },
                200,
                parentUserJson,
            )
            interceptor.respondWhen({ it.url.toString().contains("search/recent") }, 200, repliesJson)

            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-1"),
                    userMessageId = MessageId("msg-1"),
                    toolName = "x_get_thread",
                    argumentsJson = """{"tweet_url": "https://x.com/biz/status/25"}""",
                    provider = "x",
                    modelType = ModelType.FAST,
                )

            val result = getThreadExecutor.execute(request)
            val thread = threadTestJson.decodeFromString<XThreadResponse>(result.resultJson)

            assertEquals("antigravity_dev", thread.ancestor_posts.single().username)
            assertEquals("Antigravity User", thread.ancestor_posts.single().user_name)
            assertTrue(thread.ancestor_posts.single().user_name != "i")
        }

    @Test
    fun `SCENARIO_GET_THREAD_PHOTO_URL_USES_REPLY_ID`() =
        runTest {
            val targetTweetJson =
                """
                {
                  "data": [
                    {
                      "id": "25",
                      "text": "cool project!",
                      "conversation_id": "20",
                      "author_id": "15",
                      "created_at": "2006-03-21T20:55:00.000Z",
                      "referenced_tweets": [
                        {
                          "type": "replied_to",
                          "id": "20"
                        }
                      ]
                    }
                  ],
                  "includes": {
                    "users": [
                      {
                        "id": "15",
                        "name": "biz",
                        "username": "biz"
                      }
                    ]
                  }
                }
                """.trimIndent()

            val parentTweetJson =
                """
                {
                  "data": [
                    {
                      "id": "20",
                      "text": "just setting up my twttr",
                      "conversation_id": "20",
                      "author_id": "12",
                      "created_at": "2006-03-21T20:50:14.000Z"
                    }
                  ],
                  "includes": {
                    "users": [
                      {
                        "id": "12",
                        "name": "jack",
                        "username": "jack"
                      }
                    ]
                  }
                }
                """.trimIndent()

            val repliesJson =
                """
                {
                  "data": []
                }
                """.trimIndent()

            interceptor.respondWhen({ it.url.queryParameter("ids") == "25" }, 200, targetTweetJson)
            interceptor.respondWhen({ it.url.queryParameter("ids") == "20" }, 200, parentTweetJson)
            interceptor.respondWhen({ it.url.toString().contains("search/recent") }, 200, repliesJson)

            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-1"),
                    userMessageId = MessageId("msg-1"),
                    toolName = "x_get_thread",
                    argumentsJson = """{"tweet_url": "https://x.com/biz/status/25/photo/1"}""",
                    provider = "x",
                    modelType = ModelType.FAST,
                )

            val result = getThreadExecutor.execute(request)

            assertTrue(result.resultJson.contains("\"requested_post\""))
            assertTrue(result.resultJson.contains("\"anchor_tweet_id\":\"25\""))
            assertTrue(result.resultJson.contains("\"id\":\"25\",\"text\":\"cool project!\""))
        }

    @Test
    fun `SCENARIO_GET_THREAD_7DAY_WARNING`() =
        runTest {
            val targetTweetJson =
                """
                {
                  "data": [
                    {
                      "id": "20",
                      "text": "just setting up my twttr",
                      "conversation_id": "20",
                      "author_id": "12",
                      "created_at": "2006-03-21T20:50:14.000Z"
                    }
                  ],
                  "includes": {
                    "users": [
                      {
                        "id": "12",
                        "name": "jack",
                        "username": "jack"
                      }
                    ]
                  }
                }
                """.trimIndent()

            val repliesJson =
                """
                {
                  "data": []
                }
                """.trimIndent()

            interceptor.respondWhen(
                { it.url.toString().contains("/2/tweets") && !it.url.toString().contains("search/recent") },
                200,
                targetTweetJson,
            )
            interceptor.respondWhen({ it.url.toString().contains("search/recent") }, 200, repliesJson)

            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-1"),
                    userMessageId = MessageId("msg-1"),
                    toolName = "x_get_thread",
                    argumentsJson = """{"tweet_url": "https://x.com/jack/status/20"}""",
                    provider = "x",
                    modelType = ModelType.FAST,
                )

            val result = getThreadExecutor.execute(request)
            assertTrue(result.resultJson.contains("\"requested_post\""))
            assertTrue(result.resultJson.contains("\"anchor_tweet_id\":\"20\""))
            assertTrue(result.resultJson.contains("\"is_anchor\":true"))
            assertTrue(result.resultJson.contains("\"ancestor_posts\":[]"))
        }

    @Test
    fun `SCENARIO_TRENDS_SUCCESS`() =
        runTest {
            val rawTrendsJson =
                """
                {
                  "data": [
                    {
                      "trend_name": "#GoldStandard",
                      "tweet_count": 12345
                    }
                  ]
                }
                """.trimIndent()

            interceptor.respondWhen({ it.url.toString().contains("/2/trends/by/woeid/1") }, 200, rawTrendsJson)

            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-1"),
                    userMessageId = MessageId("msg-1"),
                    toolName = "x_trending",
                    argumentsJson = """{"location": "worldwide"}""",
                    provider = "x",
                    modelType = ModelType.FAST,
                )

            val result = trendingExecutor.execute(request)

            val expectedLiteral =
                """
                {
                  "data": [
                    {
                      "trend_name": "#GoldStandard",
                      "tweet_count": 12345
                    }
                  ]
                }
                """.trimIndent()

            val expectedNormalized = expectedLiteral.replace(Regex("\\s+"), "")
            val actualNormalized = result.resultJson.replace(Regex("\\s+"), "")
            assertEquals(expectedNormalized, actualNormalized)
        }

    @Test
    fun `SCENARIO_USER_TWEETS_SUCCESS`() =
        runTest {
            val userLookupJson =
                """
                {
                  "data": {
                    "id": "elon",
                    "name": "Elon Musk",
                    "username": "elonmusk"
                  }
                }
                """.trimIndent()

            val timelineJson =
                """
                {
                  "data": [
                    {
                      "id": "101",
                      "text": "second tweet",
                      "author_id": "elon",
                      "created_at": "2026-05-24T10:05:00.000Z"
                    },
                    {
                      "id": "100",
                      "text": "first tweet",
                      "author_id": "elon",
                      "created_at": "2026-05-24T10:00:00.000Z"
                    }
                  ],
                  "includes": {
                    "users": [
                      {
                        "id": "elon",
                        "name": "Elon Musk",
                        "username": "elonmusk"
                      }
                    ]
                  },
                  "meta": {
                    "result_count": 2,
                    "next_token": "tok123"
                  }
                }
                """.trimIndent()

            interceptor.respondWhen({ it.url.toString().contains("/users/by/username") }, 200, userLookupJson)
            interceptor.respondWhen({ it.url.toString().contains("/users/elon/tweets") }, 200, timelineJson)

            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-1"),
                    userMessageId = MessageId("msg-1"),
                    toolName = "x_user_tweets",
                    argumentsJson = """{"username": "@elonmusk", "search_text": null}""",
                    provider = "x",
                    modelType = ModelType.FAST,
                )

            val result = userTweetsExecutor.execute(request)

            val timelineRequest = interceptor.requests.last()
            assertTrue(timelineRequest.url.toString().contains("/users/elon/tweets"))
            assertTrue(timelineRequest.url.toString().contains("max_results=10"))
            assertTrue(!timelineRequest.url.toString().contains("exclude=retweets"))

            val expectedLiteral =
                """
                {
                  "data": [
                    {
                      "id": "101",
                      "text": "second tweet",
                      "author_id": "elon",
                      "user_name": "Elon Musk",
                      "username": "elonmusk",
                      "url": "https://x.com/elonmusk/status/101",
                      "created_at": "2026-05-24T10:05:00.000Z"
                    },
                    {
                      "id": "100",
                      "text": "first tweet",
                      "author_id": "elon",
                      "user_name": "Elon Musk",
                      "username": "elonmusk",
                      "url": "https://x.com/elonmusk/status/100",
                      "created_at": "2026-05-24T10:00:00.000Z"
                    }
                  ],
                  "next_token": "tok123",
                  "pagination_source": "timeline"
                }
                """.trimIndent()

            val expectedNormalized = expectedLiteral.replace(Regex("\\s+"), "")
            val actualNormalized = result.resultJson.replace(Regex("\\s+"), "")
            assertEquals(expectedNormalized, actualNormalized)
        }

    @Test
    fun `SCENARIO_USER_TIMELINE_RETURNS_ALL_POSTS_AND_NEXT_TOKEN`() =
        runTest {
            val userLookupJson =
                """
                {
                  "data": {
                    "id": "u1",
                    "name": "Test User",
                    "username": "testuser"
                  }
                }
                """.trimIndent()

            val timelineJson =
                """
                {
                  "data": [
                    {
                      "id": "1",
                      "text": "Whatsapp me for crypto signal",
                      "author_id": "u1",
                      "created_at": "2026-05-24T12:00:00.000Z"
                    },
                    {
                      "id": "2",
                      "text": "Normal timeline post",
                      "author_id": "u1",
                      "created_at": "2026-05-24T11:00:00.000Z"
                    },
                    {
                      "id": "3",
                      "text": "Older timeline post",
                      "author_id": "u1",
                      "created_at": "2026-05-24T10:00:00.000Z"
                    }
                  ],
                  "includes": {
                    "users": [
                      {
                        "id": "u1",
                        "name": "Test User",
                        "username": "testuser"
                      }
                    ]
                  },
                  "meta": {
                    "result_count": 3,
                    "next_token": "older_page_token"
                  }
                }
                """.trimIndent()

            interceptor.respondWhen({ it.url.toString().contains("/users/by/username") }, 200, userLookupJson)
            interceptor.respondWhen({ it.url.toString().contains("/users/u1/tweets") }, 200, timelineJson)

            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-1"),
                    userMessageId = MessageId("msg-1"),
                    toolName = "x_user_tweets",
                    argumentsJson = """{"username": "testuser"}""",
                    provider = "x",
                    modelType = ModelType.FAST,
                )

            val result = userTweetsExecutor.execute(request)
            val payload = Json.parseToJsonElement(result.resultJson).jsonObject
            val posts = payload["data"]!!.jsonArray

            assertEquals(3, posts.size)
            assertEquals("older_page_token", payload["next_token"]!!.jsonPrimitive.content)
            assertEquals("timeline", payload["pagination_source"]!!.jsonPrimitive.content)
        }

    @Test
    fun `SCENARIO_USER_TWEETS_TIMELINE_400_FALLS_BACK_TO_SEARCH`() =
        runTest {
            val userLookupJson =
                """
                {
                  "data": {
                    "id": "elon",
                    "name": "Elon Musk",
                    "username": "elonmusk"
                  }
                }
                """.trimIndent()

            val searchJson =
                """
                {
                  "data": [
                    {
                      "id": "100",
                      "text": "from search",
                      "author_id": "elon",
                      "created_at": "2026-05-24T10:00:00.000Z"
                    }
                  ],
                  "includes": {
                    "users": [
                      {
                        "id": "elon",
                        "name": "Elon Musk",
                        "username": "elonmusk"
                      }
                    ]
                  },
                  "meta": {
                    "result_count": 1,
                    "next_token": "search_page_2"
                  }
                }
                """.trimIndent()

            interceptor.respondWhen({ it.url.toString().contains("/users/by/username") }, 200, userLookupJson)
            interceptor.respondWhen(
                { it.url.toString().contains("/users/elon/tweets") },
                403,
                """{"errors":[{"message":"Forbidden"}]}""",
            )
            interceptor.respondWhen({ it.url.toString().contains("search/recent") }, 200, searchJson)

            val result =
                userTweetsExecutor.execute(
                    ToolCallRequest(
                        chatId = ChatId("chat-1"),
                        userMessageId = MessageId("msg-1"),
                        toolName = "x_user_tweets",
                        argumentsJson = """{"username": "elonmusk"}""",
                        provider = "x",
                        modelType = ModelType.FAST,
                    ),
                )

            val payload = Json.parseToJsonElement(result.resultJson).jsonObject
            assertEquals("search", payload["pagination_source"]!!.jsonPrimitive.content)
            assertEquals("search_page_2", payload["next_token"]!!.jsonPrimitive.content)
        }

    @Test
    fun `SCENARIO_USER_TWEETS_PAGINATION_RETRIES_SEARCH_ON_INVALID_TIMELINE_TOKEN`() =
        runTest {
            val userLookupJson =
                """
                {
                  "data": {
                    "id": "elon",
                    "name": "Elon Musk",
                    "username": "elonmusk"
                  }
                }
                """.trimIndent()

            val searchPageJson =
                """
                {
                  "data": [
                    {
                      "id": "200",
                      "text": "page two",
                      "author_id": "elon",
                      "created_at": "2026-05-23T10:00:00.000Z"
                    }
                  ],
                  "includes": {
                    "users": [
                      {
                        "id": "elon",
                        "name": "Elon Musk",
                        "username": "elonmusk"
                      }
                    ]
                  }
                }
                """.trimIndent()

            interceptor.respondWhen({ it.url.toString().contains("search/recent") }, 200, searchPageJson)
            interceptor.respondWhen(
                { it.url.toString().contains("/tweets") },
                400,
                """{"errors":[{"message":"Invalid pagination_token"}]}""",
            )
            interceptor.respondWhen({ true }, 200, userLookupJson)

            val result =
                userTweetsExecutor.execute(
                    ToolCallRequest(
                        chatId = ChatId("chat-1"),
                        userMessageId = MessageId("msg-1"),
                        toolName = "x_user_tweets",
                        argumentsJson = """{"username": "elonmusk", "next_token": "searchtok123"}""",
                        provider = "x",
                        modelType = ModelType.FAST,
                    ),
                )

            val searchUrl = interceptor.requests.last().url.toString()
            assertTrue(searchUrl.contains("search/recent"))
            assertTrue(searchUrl.contains("next_token=searchtok123"))

            val payload = Json.parseToJsonElement(result.resultJson).jsonObject
            assertEquals("search", payload["pagination_source"]!!.jsonPrimitive.content)
            assertEquals(
                "page two",
                payload["data"]!!
                    .jsonArray
                    .single()
                    .jsonObject["text"]!!
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun `SCENARIO_USER_TWEETS_CUSTOM_MAX_RESULTS`() =
        runTest {
            val userLookupJson =
                """
                {
                  "data": {
                    "id": "elon",
                    "name": "Elon Musk",
                    "username": "elonmusk"
                  }
                }
                """.trimIndent()

            val timelineJson =
                """
                {
                  "data": [],
                  "meta": { "result_count": 0 }
                }
                """.trimIndent()

            interceptor.respondWhen({ it.url.toString().contains("/users/by/username") }, 200, userLookupJson)
            interceptor.respondWhen({ it.url.toString().contains("/users/elon/tweets") }, 200, timelineJson)

            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-1"),
                    userMessageId = MessageId("msg-1"),
                    toolName = "x_user_tweets",
                    argumentsJson = """{"username": "elonmusk", "max_results": 25}""",
                    provider = "x",
                    modelType = ModelType.FAST,
                )

            userTweetsExecutor.execute(request)

            assertTrue(
                interceptor.requests
                    .last()
                    .url
                    .toString()
                    .contains("max_results=25"),
            )
        }

    @Test
    fun `SCENARIO_USER_TWEETS_MAX_RESULTS_CLAMPED`() =
        runTest {
            val userLookupJson =
                """
                {
                  "data": {
                    "id": "elon",
                    "name": "Elon Musk",
                    "username": "elonmusk"
                  }
                }
                """.trimIndent()

            val timelineJson =
                """
                {
                  "data": [],
                  "meta": { "result_count": 0 }
                }
                """.trimIndent()

            interceptor.respondWhen({ it.url.toString().contains("/users/by/username") }, 200, userLookupJson)
            interceptor.respondWhen({ it.url.toString().contains("/users/elon/tweets") }, 200, timelineJson)

            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-1"),
                    userMessageId = MessageId("msg-1"),
                    toolName = "x_user_tweets",
                    argumentsJson = """{"username": "elonmusk", "max_results": 500}""",
                    provider = "x",
                    modelType = ModelType.FAST,
                )

            userTweetsExecutor.execute(request)

            assertTrue(
                interceptor.requests
                    .last()
                    .url
                    .toString()
                    .contains("max_results=100"),
            )
        }

    @Test
    fun `SCENARIO_USER_TWEETS_PAGINATION`() =
        runTest {
            val userLookupJson =
                """
                {
                  "data": {
                    "id": "elon",
                    "name": "Elon Musk",
                    "username": "elonmusk"
                  }
                }
                """.trimIndent()

            val timelineJson =
                """
                {
                  "data": [],
                  "meta": {
                    "result_count": 0
                  }
                }
                """.trimIndent()

            interceptor.respondWhen({ it.url.toString().contains("/users/by/username") }, 200, userLookupJson)
            interceptor.respondWhen({ it.url.toString().contains("/users/elon/tweets") }, 200, timelineJson)

            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-1"),
                    userMessageId = MessageId("msg-1"),
                    toolName = "x_user_tweets",
                    argumentsJson = """{"username": "elonmusk", "search_text": null, "next_token": "tok123"}""",
                    provider = "x",
                    modelType = ModelType.FAST,
                )

            userTweetsExecutor.execute(request)

            val url = interceptor.requests.last().url.toString()
            assertTrue(url.contains("/users/elon/tweets"))
            assertTrue(url.contains("pagination_token=tok123"))
        }

    @Test
    fun `SCENARIO_USER_TWEETS_BLANK_NEXT_TOKEN_OMITTED`() =
        runTest {
            val userLookupJson =
                """
                {
                  "data": {
                    "id": "elon",
                    "name": "Elon Musk",
                    "username": "elonmusk"
                  }
                }
                """.trimIndent()

            val timelineJson =
                """
                {
                  "data": [],
                  "meta": {
                    "result_count": 0
                  }
                }
                """.trimIndent()

            interceptor.respondWhen({ it.url.toString().contains("/users/by/username") }, 200, userLookupJson)
            interceptor.respondWhen({ it.url.toString().contains("/users/elon/tweets") }, 200, timelineJson)

            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-1"),
                    userMessageId = MessageId("msg-1"),
                    toolName = "x_user_tweets",
                    argumentsJson = """{"username": "elonmusk", "search_text": null, "next_token": ""}""",
                    provider = "x",
                    modelType = ModelType.FAST,
                )

            userTweetsExecutor.execute(request)

            val timelineUrl = interceptor.requests.last().url.toString()
            assertTrue(timelineUrl.contains("/users/elon/tweets"))
            assertTrue(!timelineUrl.contains("pagination_token"))
        }

    @Test
    fun `SCENARIO_USER_TWEETS_KEYWORD_SEARCH`() =
        runTest {
            val searchJson =
                """
                {
                  "data": [],
                  "meta": {
                    "result_count": 0
                  }
                }
                """.trimIndent()

            interceptor.respondToAll(200, searchJson)

            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-1"),
                    userMessageId = MessageId("msg-1"),
                    toolName = "x_user_tweets",
                    argumentsJson = """{"username": "elonmusk", "search_text": "tesla", "next_token": "tok123"}""",
                    provider = "x",
                    modelType = ModelType.FAST,
                )

            userTweetsExecutor.execute(request)

            val url = interceptor.requests.single().url.toString()
            assertTrue(url.contains("query=from%3Aelonmusk%20tesla"))
            assertTrue(url.contains("sort_order=recency"))
            assertTrue(url.contains("next_token=tok123"))
        }

    @Test
    fun `SCENARIO_MENTION_SEARCH_SUCCESS`() =
        runTest {
            val searchJson =
                """
                {
                  "data": [],
                  "meta": {
                    "result_count": 0
                  }
                }
                """.trimIndent()

            interceptor.respondToAll(200, searchJson)

            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-1"),
                    userMessageId = MessageId("msg-1"),
                    toolName = "x_mention_search",
                    argumentsJson = """{"username": "@elonmusk", "search_text": "spacex", "next_token": null}""",
                    provider = "x",
                    modelType = ModelType.FAST,
                )

            mentionSearchExecutor.execute(request)

            val url = interceptor.requests.single().url.toString()
            assertTrue(url.contains("query=%40elonmusk%20spacex"))
        }

    @Test
    fun `SCENARIO_MUTATION_DEFENSE_USER_TWEETS`() =
        runTest {
            val searchJson =
                """
                {
                  "data": [
                    {
                      "id": "200",
                      "text": "mars mission",
                      "author_id": "nasa",
                      "created_at": "2026-05-24T10:00:00.000Z"
                    }
                  ],
                  "includes": {
                    "users": [
                      {
                        "id": "nasa",
                        "name": "NASA",
                        "username": "nasa"
                      }
                    ]
                  },
                  "meta": {
                    "result_count": 1,
                    "next_token": "next_nasa_token"
                  }
                }
                """.trimIndent()

            interceptor.respondToAll(200, searchJson)

            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-1"),
                    userMessageId = MessageId("msg-1"),
                    toolName = "x_user_tweets",
                    argumentsJson = """{"username": "nasa", "search_text": "mars", "next_token": "nasa_token"}""",
                    provider = "x",
                    modelType = ModelType.FAST,
                )

            val result = userTweetsExecutor.execute(request)

            val url = interceptor.requests.single().url.toString()
            assertTrue(url.contains("query=from%3Anasa%20mars"))
            assertTrue(url.contains("next_token=nasa_token"))
            assertTrue(result.resultJson.contains("NASA"))
            assertTrue(result.resultJson.contains("next_nasa_token"))
        }
}
