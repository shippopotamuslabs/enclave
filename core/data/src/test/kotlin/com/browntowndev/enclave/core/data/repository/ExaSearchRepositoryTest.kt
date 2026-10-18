package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ExaSearchRepositoryTest {
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var apiKeyManager: ApiKeyManager
    private lateinit var repository: ExaSearchRepository

    @BeforeEach
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        apiKeyManager = testApiKeyManager(context, testScope, "exa_search_test_prefs")
        apiKeyManager.save(ApiKeyManager.EXA_SEARCH_ALIAS, "exa-key")
        repository = ExaSearchRepository(okHttpClientReturning(""), apiKeyManager)
    }

    /** Real OkHttpClient whose interceptor short-circuits every call with [body]. */
    private fun okHttpClientReturning(body: String): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                Response
                    .Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody())
                    .build()
            }.build()

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

        repository = ExaSearchRepository(okHttpClientReturning(jsonResponse), apiKeyManager)

        val result = repository.search("test query")

        assertTrue(result.contains("Exa Title"))
        assertTrue(result.contains("https://exa.ai"))
        assertTrue(result.contains("highlight 1"))
        assertTrue(result.contains("0.95"))
    }

    @Test
    fun `search throws when Exa key is missing`() {
        apiKeyManager.delete(ApiKeyManager.EXA_SEARCH_ALIAS)

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

        repository = ExaSearchRepository(okHttpClientReturning(jsonResponse), apiKeyManager)

        val result = repository.extract(listOf("https://exa.ai/page"))

        assertTrue(result.contains("https://exa.ai/page"))
        assertTrue(result.contains("Full page content"))
    }
}
