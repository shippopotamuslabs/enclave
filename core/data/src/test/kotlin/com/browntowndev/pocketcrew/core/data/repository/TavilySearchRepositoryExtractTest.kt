package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

class TavilySearchRepositoryExtractTest {

    private val okHttpClient = mockk<OkHttpClient>()
    private val apiKeyManager = mockk<ApiKeyManager>()
    private val repository = TavilySearchRepository(okHttpClient, apiKeyManager)

    @Test
    fun `extract posts to extract endpoint and maps response`() {
        val call = mockk<Call>()
        val requestSlot = slot<Request>()
        every { apiKeyManager.get(ApiKeyManager.TAVILY_SEARCH_ALIAS) } returns "tavily-secret"
        every { okHttpClient.newCall(capture(requestSlot)) } returns call
        every { call.execute() } returns httpResponse(
            code = 200,
            body = """
                {"results":[{"url":"https://developer.android.com","raw_content":"# Title\nContent"}]}
            """.trimIndent()
        )

        val result = repository.extract(
            urls = listOf("https://developer.android.com"),
            extractDepth = "basic",
            format = "markdown",
        )

        assertEquals("https://api.tavily.com/extract", requestSlot.captured.url.toString())

        val bodyBuffer = Buffer()
        requestSlot.captured.body!!.writeTo(bodyBuffer)
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
        every { apiKeyManager.get(ApiKeyManager.TAVILY_SEARCH_ALIAS) } returns null

        val error = assertThrows<IllegalStateException> {
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
        val call = mockk<Call>()
        every { apiKeyManager.get(ApiKeyManager.TAVILY_SEARCH_ALIAS) } returns "tavily-secret"
        every { okHttpClient.newCall(any()) } returns call
        every { call.execute() } returns httpResponse(code = 503, body = """{"error":"unavailable"}""")

        val error = assertThrows<IOException> {
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
        val body = """
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

    private fun httpResponse(code: Int, body: String): Response =
        Response.Builder()
            .request(Request.Builder().url("https://api.tavily.com/extract").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(body.toResponseBody())
            .build()
}