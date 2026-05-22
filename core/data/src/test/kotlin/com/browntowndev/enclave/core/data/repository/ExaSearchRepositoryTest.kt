package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.security.ApiKeyManager
import io.mockk.every
import io.mockk.mockk
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ExaSearchRepositoryTest {
    private val okHttpClient = mockk<OkHttpClient>()
    private val apiKeyManager = mockk<ApiKeyManager>()
    private lateinit var repository: ExaSearchRepository

    @BeforeEach
    fun setup() {
        repository = ExaSearchRepository(okHttpClient, apiKeyManager)
        every { apiKeyManager.get(ApiKeyManager.EXA_SEARCH_ALIAS) } returns "exa-key"
    }

    @Test
    fun `search posts Exa request and maps response`() {
        val jsonResponse =
            """
            {
              "results": [
                {
                  "title": "Exa Title",
                  "url": "https://exa.ai",
                  "highlights": ["highlight 1", "highlight 2"],
                  "score": 0.95
                }
              ]
            }
            """.trimIndent()

        val call = mockk<Call>()
        every { okHttpClient.newCall(any()) } returns call
        every { call.execute() } returns
            Response
                .Builder()
                .request(Request.Builder().url("https://api.exa.ai/search").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(jsonResponse.toResponseBody())
                .build()

        val result = repository.search("test query")

        assertTrue(result.contains("Exa Title"))
        assertTrue(result.contains("https://exa.ai"))
        assertTrue(result.contains("highlight 1"))
        assertTrue(result.contains("0.95"))
    }

    @Test
    fun `search throws when Exa key is missing`() {
        every { apiKeyManager.get(ApiKeyManager.EXA_SEARCH_ALIAS) } returns null

        val error =
            assertThrows<IllegalStateException> {
                repository.search("test query")
            }
        assertEquals("Exa API key is required when search is enabled and Exa is selected", error.message)
    }

    @Test
    fun `extract posts Exa contents request and maps response`() {
        val jsonResponse =
            """
            {
              "results": [
                {
                  "url": "https://exa.ai/page",
                  "text": "Full page content"
                }
              ]
            }
            """.trimIndent()

        val call = mockk<Call>()
        every { okHttpClient.newCall(any()) } returns call
        every { call.execute() } returns
            Response
                .Builder()
                .request(Request.Builder().url("https://api.exa.ai/contents").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(jsonResponse.toResponseBody())
                .build()

        val result = repository.extract(listOf("https://exa.ai/page"))

        assertTrue(result.contains("https://exa.ai/page"))
        assertTrue(result.contains("Full page content"))
    }
}
