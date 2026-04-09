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

class TavilySearchRepositoryTest {

    private val okHttpClient = mockk<OkHttpClient>()
    private val apiKeyManager = mockk<ApiKeyManager>()
    private val repository = TavilySearchRepository(okHttpClient, apiKeyManager)

    @Test
    fun `search posts Tavily request and maps response`() {
        val call = mockk<Call>()
        val requestSlot = slot<Request>()
        every { apiKeyManager.get(ApiKeyManager.TAVILY_SEARCH_ALIAS) } returns "tavily-secret"
        every { okHttpClient.newCall(capture(requestSlot)) } returns call
        every { call.execute() } returns httpResponse(
            code = 200,
            body = """
                {"results":[{"title":"Android 17","url":"https://developer.android.com","content":"Local network permission","score":0.91}]}
            """.trimIndent()
        )

        val result = repository.search("android 17 local network")

        assertTrue(result.contains("\"query\":\"android 17 local network\""))
        assertTrue(result.contains("\"title\":\"Android 17\""))
        assertTrue(result.contains("\"url\":\"https://developer.android.com\""))
        assertEquals("https://api.tavily.com/search", requestSlot.captured.url.toString())

        val bodyBuffer = Buffer()
        requestSlot.captured.body!!.writeTo(bodyBuffer)
        val requestBody = bodyBuffer.readUtf8()
        assertTrue(requestBody.contains("\"query\":\"android 17 local network\""))
        assertTrue(requestBody.contains("\"api_key\":\"tavily-secret\""))
    }

    @Test
    fun `search throws when Tavily key is missing`() {
        every { apiKeyManager.get(ApiKeyManager.TAVILY_SEARCH_ALIAS) } returns null

        val error = assertThrows<IllegalStateException> {
            repository.search("android 17 local network")
        }

        assertEquals("Tavily API key is required when search is enabled", error.message)
    }

    @Test
    fun `search throws on non successful response`() {
        val call = mockk<Call>()
        every { apiKeyManager.get(ApiKeyManager.TAVILY_SEARCH_ALIAS) } returns "tavily-secret"
        every { okHttpClient.newCall(any()) } returns call
        every { call.execute() } returns httpResponse(code = 503, body = """{"error":"unavailable"}""")

        val error = assertThrows<IOException> {
            repository.search("android 17 local network")
        }

        assertEquals("Tavily request failed with HTTP 503", error.message)
    }

    @Test
    fun `mapResponse normalizes Tavily payload`() {
        val mapped = repository.mapResponse(
            query = "android 17 local network",
            body = """
                {
                  "results": [
                    {"title":"Android 17","url":"https://developer.android.com","content":"Permission change","score":0.8},
                    {"title":"Issue tracker","url":"https://issuetracker.google.com","content":"Regression","score":0.6}
                  ]
                }
            """.trimIndent()
        )

        assertTrue(mapped.contains("\"query\":\"android 17 local network\""))
        assertTrue(mapped.contains("\"title\":\"Android 17\""))
        assertTrue(mapped.contains("\"title\":\"Issue tracker\""))
    }

    private fun httpResponse(code: Int, body: String): Response =
        Response.Builder()
            .request(Request.Builder().url("https://api.tavily.com/search").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(body.toResponseBody())
            .build()
}
