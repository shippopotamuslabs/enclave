package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.IOException
import org.robolectric.annotation.Config as RoboConfig

/**
 * Translated from MockK to a real OkHttpClient over a stub interceptor
 * (ENC-28): the interceptor records the outgoing request and serves the
 * canned response, and the API key manager is the real class over
 * Robolectric SharedPreferences (the [testApiKeyManager] pattern).
 */
@ExtendWith(RobolectricExtension::class)
@RoboConfig(sdk = [34])
class TavilySearchRepositoryTest {
    private val interceptor = StubHttpInterceptor()
    private lateinit var apiKeyManager: ApiKeyManager
    private lateinit var repository: TavilySearchRepository

    @BeforeEach
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        apiKeyManager =
            testApiKeyManager(context, CoroutineScope(SupervisorJob() + Dispatchers.Default), "tavily_search_test_prefs")
        repository = TavilySearchRepository(stubHttpClient(interceptor), apiKeyManager)
    }

    @Test
    fun `search posts Tavily request and maps response`() {
        apiKeyManager.save(ApiKeyManager.TAVILY_SEARCH_ALIAS, "tavily-secret")
        interceptor.respondToAll(
            code = 200,
            body =
                """
                {"results":[{"title":"Android 17","url":"https://developer.android.com","content":"Local network permission","score":0.91}]}
                """.trimIndent(),
        )

        val result = repository.search("android 17 local network")

        assertTrue(result.contains("\"query\":\"android 17 local network\""))
        assertTrue(result.contains("\"title\":\"Android 17\""))
        assertTrue(result.contains("\"url\":\"https://developer.android.com\""))
        val request = interceptor.requests.single()
        assertEquals("https://api.tavily.com/search", request.url.toString())

        val bodyBuffer = Buffer()
        request.body!!.writeTo(bodyBuffer)
        val requestBody = bodyBuffer.readUtf8()
        assertTrue(requestBody.contains("\"query\":\"android 17 local network\""))
        assertTrue(requestBody.contains("\"api_key\":\"tavily-secret\""))
    }

    @Test
    fun `search throws when Tavily key is missing`() {
        val error =
            assertThrows<IllegalStateException> {
                repository.search("android 17 local network")
            }

        assertEquals("Tavily API key is required when search is enabled", error.message)
    }

    @Test
    fun `search throws on non successful response`() {
        apiKeyManager.save(ApiKeyManager.TAVILY_SEARCH_ALIAS, "tavily-secret")
        interceptor.respondToAll(code = 503, body = """{"error":"unavailable"}""")

        val error =
            assertThrows<IOException> {
                repository.search("android 17 local network")
            }

        assertEquals("Tavily request failed with HTTP 503", error.message)
    }

    @Test
    fun `mapResponse normalizes Tavily payload`() {
        val mapped =
            repository.mapResponse(
                query = "android 17 local network",
                body =
                    """
                    {
                      "results": [
                        {"title":"Android 17","url":"https://developer.android.com","content":"Permission change","score":0.8},
                        {"title":"Issue tracker","url":"https://issuetracker.google.com","content":"Regression","score":0.6}
                      ]
                    }
                    """.trimIndent(),
            )

        assertTrue(mapped.contains("\"query\":\"android 17 local network\""))
        assertTrue(mapped.contains("\"title\":\"Android 17\""))
        assertTrue(mapped.contains("\"title\":\"Issue tracker\""))
    }
}
