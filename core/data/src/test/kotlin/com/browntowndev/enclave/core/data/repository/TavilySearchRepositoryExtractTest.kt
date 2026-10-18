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
class TavilySearchRepositoryExtractTest {
    private val interceptor = StubHttpInterceptor()
    private lateinit var apiKeyManager: ApiKeyManager
    private lateinit var repository: TavilySearchRepository

    @BeforeEach
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        apiKeyManager =
            testApiKeyManager(context, CoroutineScope(SupervisorJob() + Dispatchers.Default), "tavily_extract_test_prefs")
        repository = TavilySearchRepository(stubHttpClient(interceptor), apiKeyManager)
    }

    @Test
    fun `extract posts to extract endpoint and maps response`() {
        apiKeyManager.save(ApiKeyManager.TAVILY_SEARCH_ALIAS, "tavily-secret")
        interceptor.respondToAll(
            code = 200,
            body =
                """
                {"results":[{"url":"https://developer.android.com","raw_content":"# Title\nContent"}]}
                """.trimIndent(),
        )

        val result =
            repository.extract(
                urls = listOf("https://developer.android.com"),
                extractDepth = "basic",
                format = "markdown",
            )

        val request = interceptor.requests.single()
        assertEquals("https://api.tavily.com/extract", request.url.toString())

        val bodyBuffer = Buffer()
        request.body!!.writeTo(bodyBuffer)
        val requestBody = bodyBuffer.readUtf8()
        assertTrue(requestBody.contains("\"urls\""), "Request body should contain 'urls'")
        assertTrue(requestBody.contains("https://developer.android.com"), "Request body should contain the URL")
        assertTrue(requestBody.contains("\"api_key\":\"tavily-secret\""), "Request body should contain api_key")
        assertTrue(requestBody.contains("\"extract_depth\":\"basic\""), "Request body should contain extract_depth")
        assertTrue(requestBody.contains("\"format\":\"markdown\""), "Request body should contain format")

        assertTrue(result.contains("https://developer.android.com"), "Result should contain the URL")
        assertTrue(result.contains("raw_content"), "Result should contain raw_content field")
    }

    @Test
    fun `extract throws when API key is missing`() {
        val error =
            assertThrows<IllegalStateException> {
                repository.extract(
                    urls = listOf("https://example.com"),
                    extractDepth = "basic",
                    format = "markdown",
                )
            }

        assertEquals("Tavily API key is required when search is enabled", error.message)
    }

    @Test
    fun `extract throws on non-successful response`() {
        apiKeyManager.save(ApiKeyManager.TAVILY_SEARCH_ALIAS, "tavily-secret")
        interceptor.respondToAll(code = 503, body = """{"error":"unavailable"}""")

        val error =
            assertThrows<IOException> {
                repository.extract(
                    urls = listOf("https://example.com"),
                    extractDepth = "basic",
                    format = "markdown",
                )
            }

        assertEquals("Tavily request failed with HTTP 503", error.message)
    }

    @Test
    fun `mapExtractResponse normalizes extract payload`() {
        val body =
            """
            {
              "results": [
                {"url":"https://developer.android.com","raw_content":"# Title\nContent"}
              ]
            }
            """.trimIndent()

        val mapped = repository.mapExtractResponse(body)

        assertTrue(mapped.contains("https://developer.android.com"), "Mapped response should contain the URL")
        assertTrue(mapped.contains("raw_content"), "Mapped response should contain raw_content")
        assertTrue(mapped.contains("# Title"), "Mapped response should contain the raw content text")
    }
}
