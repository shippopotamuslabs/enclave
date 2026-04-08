package com.browntowndev.pocketcrew.core.data.repository

import com.anthropic.client.AnthropicClient
import com.anthropic.core.AutoPager
import com.anthropic.models.models.ModelInfo
import com.anthropic.models.models.ModelListPage
import com.anthropic.services.blocking.ModelService
import com.browntowndev.pocketcrew.core.data.anthropic.AnthropicClientProvider
import com.browntowndev.pocketcrew.core.data.google.GoogleGenAiClientProvider
import com.browntowndev.pocketcrew.core.data.openai.OpenAiClientProvider
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.DiscoveredApiModel
import com.google.genai.types.Model
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Call
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Optional
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ApiModelCatalogRepositoryImplTest {

    private val openAiClientProvider = mockk<OpenAiClientProvider>()
    private val anthropicClientProvider = mockk<AnthropicClientProvider>()
    private val googleGenAiClientProvider = mockk<GoogleGenAiClientProvider>()
    private val httpClient = mockk<Call.Factory>()
    private val anthropicClient = mockk<AnthropicClient>()
    private val modelService = mockk<ModelService>()
    private val modelListPage = mockk<ModelListPage>()
    private val autoPager = mockk<AutoPager<ModelInfo>>()
    private val modelInfoA = mockk<ModelInfo>()
    private val modelInfoB = mockk<ModelInfo>()
    private val googleModelA = mockk<Model>()
    private val googleModelB = mockk<Model>()

    private lateinit var repo: ApiModelCatalogRepositoryImpl

    @BeforeEach
    fun setup() {
        repo = ApiModelCatalogRepositoryImpl(
            openAiClientProvider = openAiClientProvider,
            anthropicClientProvider = anthropicClientProvider,
            googleGenAiClientProvider = googleGenAiClientProvider,
            httpClient = httpClient,
        )

        every { anthropicClientProvider.getClient(any(), any(), any()) } returns anthropicClient
        every { anthropicClient.models() } returns modelService
        every { modelService.list() } returns modelListPage
        every { modelListPage.autoPager() } returns autoPager
        every { autoPager.iterator() } returns listOf(modelInfoA, modelInfoB).iterator()
        every { modelInfoA.id() } returns "claude-sonnet-4-20250514"
        every { modelInfoA.displayName() } returns "Claude Sonnet 4"
        every { modelInfoA.createdAt() } returns OffsetDateTime.of(2025, 5, 14, 0, 0, 0, 0, ZoneOffset.UTC)
        every { modelInfoB.id() } returns "claude-haiku-4-5-20251001"
        every { modelInfoB.displayName() } returns "Claude Haiku 4.5"
        every { modelInfoB.createdAt() } returns OffsetDateTime.of(2025, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC)

        every { googleGenAiClientProvider.listModels(any(), any(), any(), any()) } returns listOf(googleModelA, googleModelB)
        every { googleModelA.name() } returns Optional.of("models/gemini-2.5-flash")
        every { googleModelB.name() } returns Optional.of("publishers/google/models/gemini-2.5-pro")
        every { googleModelA.inputTokenLimit() } returns Optional.of(1_048_576)
        every { googleModelA.outputTokenLimit() } returns Optional.of(8_192)
        every { googleModelB.inputTokenLimit() } returns Optional.of(2_097_152)
        every { googleModelB.outputTokenLimit() } returns Optional.of(65_536)
    }

    @Test
    fun `fetchModels uses anthropic model discovery path for anthropic provider`() = runTest {
        val models = repo.fetchModels(ApiProvider.ANTHROPIC, apiKey = "key", baseUrl = null)

        assertEquals(
            listOf(
                DiscoveredApiModel(
                    id = "claude-haiku-4-5-20251001",
                    name = "Claude Haiku 4.5",
                    created = OffsetDateTime.of(2025, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC).toEpochSecond()
                ),
                DiscoveredApiModel(
                    id = "claude-sonnet-4-20250514",
                    name = "Claude Sonnet 4",
                    created = OffsetDateTime.of(2025, 5, 14, 0, 0, 0, 0, ZoneOffset.UTC).toEpochSecond()
                ),
            ),
            models,
        )
    }

    @Test
    fun `fetchModels uses google model discovery path for google provider`() = runTest {
        val models = repo.fetchModels(ApiProvider.GOOGLE, apiKey = "key", baseUrl = null)

        assertEquals(
            listOf(
                DiscoveredApiModel(
                    id = "gemini-2.5-flash",
                    contextWindowTokens = 1_048_576,
                    maxOutputTokens = 8_192,
                ),
                DiscoveredApiModel(
                    id = "gemini-2.5-pro",
                    contextWindowTokens = 2_097_152,
                    maxOutputTokens = 65_536,
                ),
            ),
            models,
        )
        verify {
            googleGenAiClientProvider.listModels(
                apiKey = "key",
                baseUrl = ApiProvider.GOOGLE.defaultBaseUrl(),
                headers = emptyMap(),
                apiVersion = any(),
            )
        }
    }

    @Test
    fun `parseOpenRouterModels extracts context window and max tokens when present`() {
        val models = repo.parseOpenRouterModels(
            """
            {
              "data": [
                {
                  "id": "openai/gpt-5.2",
                  "name": "GPT 5.2",
                  "created": 1715644800,
                  "pricing": {
                    "prompt": "0.01",
                    "completion": "0.03"
                  },
                  "context_length": 400000,
                  "top_provider": {
                    "max_completion_tokens": 128000
                  }
                },
                {
                  "id": "anthropic/claude-sonnet-4",
                  "context_length": 200000
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(
                DiscoveredApiModel(
                    id = "anthropic/claude-sonnet-4",
                    contextWindowTokens = 200_000,
                ),
                DiscoveredApiModel(
                    id = "openai/gpt-5.2",
                    name = "GPT 5.2",
                    created = 1715644800L,
                    promptPrice = 0.01,
                    completionPrice = 0.03,
                    contextWindowTokens = 400_000,
                    maxOutputTokens = 128_000,
                ),
            ),
            models,
        )
    }

    @Test
    fun `parseXaiModels extracts correct fields`() {
        val models = repo.parseXaiModels(
            """
            {
              "data": [
                {
                  "id": "grok-2",
                  "created": 1723507200,
                  "prompt_token_price": 0.002,
                  "completion_token_price": 0.004
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(
                DiscoveredApiModel(
                    id = "grok-2",
                    name = "grok-2",
                    created = 1723507200L,
                    promptPrice = 0.002,
                    completionPrice = 0.004,
                )
            ),
            models,
        )
    }

    @Test
    fun `parseXaiModels supports language-models payload`() {
        val models = repo.parseXaiModels(
            """
            {
              "models": [
                {
                  "id": "grok-4.1-fast-reasoning",
                  "created": 1743465600,
                  "prompt_text_token_price": 0.2,
                  "completion_text_token_price": 0.5,
                  "max_prompt_length": 131072
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(
                DiscoveredApiModel(
                    id = "grok-4.1-fast-reasoning",
                    name = "grok-4.1-fast-reasoning",
                    created = 1743465600L,
                    promptPrice = 0.2,
                    completionPrice = 0.5,
                    contextWindowTokens = 131_072,
                )
            ),
            models,
        )
    }

    @Test
    fun `parseXaiModelDetail keeps requested alias and extracts context window`() {
        val model = repo.parseXaiModelDetail(
            responseBody = """
            {
              "id": "grok-4-fast-reasoning-2025-04-09",
              "name": "Grok 4 Fast",
              "aliases": ["grok-4-fast-reasoning"],
              "created": 1743465600,
              "prompt_text_token_price": 0.2,
              "completion_text_token_price": 0.5,
              "max_prompt_length": 256000
            }
            """.trimIndent(),
            requestedModelId = "grok-4-fast-reasoning",
        )

        assertEquals(
            DiscoveredApiModel(
                id = "grok-4-fast-reasoning",
                name = "Grok 4 Fast",
                created = 1743465600L,
                promptPrice = 0.2,
                completionPrice = 0.5,
                contextWindowTokens = 256_000,
            ),
            model,
        )
    }
}
