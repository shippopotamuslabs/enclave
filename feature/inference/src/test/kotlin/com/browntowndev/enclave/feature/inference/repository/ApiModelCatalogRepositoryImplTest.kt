package com.browntowndev.enclave.feature.inference.repository

import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.feature.inference.anthropic.AnthropicSdkBaseUrl
import com.browntowndev.enclave.feature.inference.google.GoogleGenAiClientProvider
import com.browntowndev.enclave.feature.inference.openai.OpenAiClientProvider
import com.google.genai.types.Model
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Optional

class ApiModelCatalogRepositoryImplTest {
    private val openAiClientProvider = mockk<OpenAiClientProvider>()
    private val googleGenAiClientProvider = mockk<GoogleGenAiClientProvider>()
    private val httpClient = mockk<Call.Factory>()
    private val googleModelA = mockk<Model>()
    private val googleModelB = mockk<Model>()

    private lateinit var repo: ApiModelCatalogRepositoryImpl

    @BeforeEach
    fun setup() {
        repo =
            ApiModelCatalogRepositoryImpl(
                openAiClientProvider = openAiClientProvider,
                googleGenAiClientProvider = googleGenAiClientProvider,
                httpClient = httpClient,
            )

        every { googleGenAiClientProvider.listModels(any(), any(), any(), any()) } returns listOf(googleModelA, googleModelB)
        every { googleModelA.name() } returns Optional.of("models/gemini-2.5-flash")
        every { googleModelB.name() } returns Optional.of("publishers/google/models/gemini-2.5-pro")
        every { googleModelA.inputTokenLimit() } returns Optional.of(1_048_576)
        every { googleModelA.outputTokenLimit() } returns Optional.of(8_192)
        every { googleModelA.supportedActions() } returns Optional.of(listOf("generateContent"))
        every { googleModelB.inputTokenLimit() } returns Optional.of(2_097_152)
        every { googleModelB.outputTokenLimit() } returns Optional.of(65_536)
        every { googleModelB.supportedActions() } returns Optional.of(listOf("generateContent"))
    }

    @Test
    fun `fetchModels filters compatible model discovery to native Anthropic shape`() =
        runTest {
            val call = mockk<Call>()
            val requestSlot = io.mockk.slot<Request>()
            every { httpClient.newCall(capture(requestSlot)) } returns call
            every { call.execute() } returns
                jsonResponse(
                    request = Request.Builder().url("https://opencode.ai/zen/go/v1/models").build(),
                    body =
                        """
                        {
                          "data": [
                            {
                              "id": "claude-sonnet-4-20250514",
                              "display_name": "Claude Sonnet 4",
                              "created_at": "2025-05-14T00:00:00Z",
                              "capabilities": {
                                "image_input": {
                                  "supported": true
                                }
                              }
                            },
                            {
                              "id": "opencode-go-default",
                              "object": "model",
                              "created": 1764547200,
                              "context_window": 262144,
                              "max_output_tokens": 32768
                            }
                          ]
                        }
                        """.trimIndent(),
                )

            val catalog =
                repo.fetchModels(
                    provider = ApiProvider.ANTHROPIC,
                    apiKey = "key",
                    baseUrl = "https://opencode.ai/zen/go/v1",
                )

            assertEquals(
                listOf(
                    DiscoveredApiModel(
                        id = "claude-sonnet-4-20250514",
                        name = "Claude Sonnet 4",
                        created = OffsetDateTime.of(2025, 5, 14, 0, 0, 0, 0, ZoneOffset.UTC).toEpochSecond(),
                        isMultimodal = true,
                        isChatCapable = true,
                    ),
                    DiscoveredApiModel(
                        id = "opencode-go-default",
                        name = "opencode-go-default",
                        created = 1764547200,
                        contextWindowTokens = 262_144,
                        maxOutputTokens = 32_768,
                        isChatCapable = true,
                    ),
                ),
                catalog.models,
            )
            assertEquals("https://opencode.ai/zen/go/v1/models", requestSlot.captured.url.toString())
            assertEquals("key", requestSlot.captured.header("x-api-key"))
            assertEquals("Bearer key", requestSlot.captured.header("Authorization"))
        }

    @Test
    fun `normalize trims version segment from Anthropic-compatible base URLs`() {
        assertEquals(
            "https://opencode.ai/zen/go",
            AnthropicSdkBaseUrl.normalize("https://opencode.ai/zen/go/v1"),
        )
        assertEquals(
            "https://opencode.ai/zen/go",
            AnthropicSdkBaseUrl.normalize(" https://opencode.ai/zen/go/v1/ "),
        )
        assertEquals(
            "https://api.anthropic.com",
            AnthropicSdkBaseUrl.normalize("https://api.anthropic.com"),
        )
    }

    @Test
    fun `parseAnthropicModels keeps compatible model payload without display name`() {
        val models =
            repo.parseAnthropicModels(
                """
                {
                  "data": [
                    {
                      "id": "opencode-go-default",
                      "object": "model",
                      "created": 1764547200,
                      "context_length": 128000
                    }
                  ]
                }
                """.trimIndent(),
            )

        assertEquals(
            listOf(
                DiscoveredApiModel(
                    id = "opencode-go-default",
                    name = "opencode-go-default",
                    created = 1764547200,
                    contextWindowTokens = 128_000,
                    isChatCapable = true,
                ),
            ),
            models,
        )
    }

    @Test
    fun `fetchModels uses google model discovery path for google provider`() =
        runTest {
            val catalog = repo.fetchModels(ApiProvider.GOOGLE, apiKey = "key", baseUrl = null)

            assertEquals(
                listOf(
                    DiscoveredApiModel(
                        id = "gemini-2.5-flash",
                        contextWindowTokens = 1_048_576,
                        maxOutputTokens = 8_192,
                        isChatCapable = true,
                    ),
                    DiscoveredApiModel(
                        id = "gemini-2.5-pro",
                        contextWindowTokens = 2_097_152,
                        maxOutputTokens = 65_536,
                        isChatCapable = true,
                    ),
                ),
                catalog.models,
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
        val models =
            repo.parseOpenRouterModels(
                """
                {
                  "data": [
                    {
                      "id": "openai/gpt-5.2",
                      "name": "GPT 5.2",
                      "created": 1715644800,
                      "architecture": {
                        "input_modalities": ["text", "image"]
                      },
                      "pricing": {
                        "prompt": "0.000005",
                        "completion": "0.000015"
                      },
                      "context_length": 400000,
                      "top_provider": {
                        "max_completion_tokens": 128000
                      }
                    },
                    {
                      "id": "anthropic/claude-sonnet-4",
                      "architecture": {
                        "input_modalities": ["text"]
                      },
                      "context_length": 200000
                    }
                  ]
                }
                """.trimIndent(),
            )

        assertEquals(
            listOf(
                DiscoveredApiModel(
                    id = "anthropic/claude-sonnet-4",
                    contextWindowTokens = 200_000,
                    isMultimodal = false,
                    isChatCapable = true,
                ),
                DiscoveredApiModel(
                    id = "openai/gpt-5.2",
                    name = "GPT 5.2",
                    created = 1715644800L,
                    promptPrice = 5.0,
                    completionPrice = 15.0,
                    contextWindowTokens = 400_000,
                    maxOutputTokens = 128_000,
                    isMultimodal = true,
                    isChatCapable = true,
                ),
            ),
            models,
        )
    }

    @Test
    fun `parseXaiModels extracts correct fields`() {
        val models =
            repo.parseXaiModels(
                responseBody =
                    """
                    {
                      "data": [
                        {
                          "id": "grok-2",
                          "created": 1723507200,
                          "input_modalities": ["text", "image"],
                          "prompt_token_price": 2000,
                          "completion_token_price": 4000
                        }
                      ]
                    }
                    """.trimIndent(),
                isChat = true,
            )

        assertEquals(
            listOf(
                DiscoveredApiModel(
                    id = "grok-2",
                    name = "grok-2",
                    created = 1723507200L,
                    promptPrice = 0.2,
                    completionPrice = 0.4,
                    isMultimodal = true,
                    isChatCapable = true,
                    isImageGenerationCapable = false,
                    isVideoGenerationCapable = false,
                ),
            ),
            models,
        )
    }

    @Test
    fun `parseXaiModels supports language-models payload`() {
        val models =
            repo.parseXaiModels(
                responseBody =
                    """
                    {
                      "models": [
                        {
                          "id": "grok-4.1-fast-reasoning",
                          "created": 1743465600,
                          "input_modalities": ["text"],
                          "prompt_text_token_price": 2000,
                          "completion_text_token_price": 5000,
                          "max_prompt_length": 131072
                        }
                      ]
                    }
                    """.trimIndent(),
                isChat = true,
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
                    isMultimodal = false,
                    isChatCapable = true,
                    isImageGenerationCapable = false,
                    isVideoGenerationCapable = false,
                ),
            ),
            models,
        )
    }

    @Test
    fun `parseXaiModelDetail keeps requested alias and extracts context window`() {
        val model =
            repo.parseXaiModelDetail(
                responseBody =
                    """
                    {
                      "id": "grok-4-fast-reasoning-2025-04-09",
                      "name": "Grok 4 Fast",
                      "aliases": ["grok-4-fast-reasoning"],
                      "created": 1743465600,
                      "input_modalities": ["text", "image"],
                      "prompt_text_token_price": 2000,
                      "completion_text_token_price": 5000,
                      "max_prompt_length": 256000
                    }
                    """.trimIndent(),
                requestedModelId = "grok-4-fast-reasoning",
                isChat = true,
            )

        assertEquals(
            DiscoveredApiModel(
                id = "grok-4-fast-reasoning",
                name = "Grok 4 Fast",
                created = 1743465600L,
                promptPrice = 0.2,
                completionPrice = 0.5,
                contextWindowTokens = 256_000,
                isMultimodal = true,
                isChatCapable = true,
                isImageGenerationCapable = false,
                isVideoGenerationCapable = false,
            ),
            model,
        )
    }

    @Test
    fun `parseXaiModels marks models from image-generation-models as image generation capable`() {
        val models =
            repo.parseXaiModels(
                responseBody =
                    """
                    {
                      "models": [
                        {
                          "id": "grok-imagine-image",
                          "created": 1743465600,
                          "input_modalities": ["text"],
                          "image_price": 200
                        }
                      ]
                    }
                    """.trimIndent(),
                isImageGen = true,
            )

        assertEquals(1, models.size)
        val model = models[0]
        assertEquals(false, model.isChatCapable)
        assertEquals(true, model.isImageGenerationCapable)
        assertEquals(false, model.isVideoGenerationCapable)
    }

    @Test
    fun `parseXaiModels marks models from video-generation-models as video generation capable`() {
        val models =
            repo.parseXaiModels(
                responseBody =
                    """
                    {
                      "models": [
                        {
                          "id": "grok-imagine-video",
                          "created": 1743465600,
                          "input_modalities": ["text"]
                        }
                      ]
                    }
                    """.trimIndent(),
                isVideoGen = true,
            )

        assertEquals(1, models.size)
        val model = models[0]
        assertEquals(false, model.isChatCapable)
        assertEquals(false, model.isImageGenerationCapable)
        assertEquals(true, model.isVideoGenerationCapable)
    }

    @Test
    fun `fetchModels filters out blacklisted OpenAI models`() =
        runTest {
            every { openAiClientProvider.getClient(any(), any(), any()) } returns
                mockk {
                    every { models() } returns
                        mockk {
                            every { list() } returns
                                mockk {
                                    every { data() } returns
                                        listOf(
                                            mockk {
                                                every { id() } returns "gpt-4o"
                                                every { created() } returns 123L
                                            },
                                            mockk {
                                                every { id() } returns "dall-e-2"
                                                every { created() } returns 123L
                                            },
                                            mockk {
                                                every { id() } returns "whisper-1"
                                                every { created() } returns 123L
                                            },
                                        )
                                }
                        }
                }

            val catalog = repo.fetchModels(ApiProvider.OPENAI, apiKey = "key", baseUrl = null)

            assertEquals(1, catalog.models.size)
            assertEquals("gpt-4o", catalog.models[0].id)
        }

    @Test
    fun `parseFalModels decodes platform v1 response with endpoint_id and metadata`() {
        val models =
            repo.parseFalModels(
                """
                {
                  "models": [
                    {
                      "endpoint_id": "fal-ai/flux/dev",
                      "metadata": {
                        "display_name": "FLUX.1 [dev]",
                        "category": "text-to-image",
                        "description": "Fast text-to-image generation",
                        "status": "active",
                        "tags": ["fast", "pro"],
                        "updated_at": "2025-01-15T12:00:00Z",
                        "is_favorited": false,
                        "thumbnail_url": "https://fal.media/files/example.jpg",
                        "model_url": "https://fal.run/fal-ai/flux/dev",
                        "date": "2024-08-01T00:00:00Z",
                        "highlighted": false,
                        "pinned": false
                      }
                    },
                    {
                      "endpoint_id": "fal-ai/kling-video/v2.1/master/image-to-video",
                      "metadata": {
                        "display_name": "Kling Video 2.1",
                        "category": "image-to-video",
                        "description": "Image to video",
                        "status": "active",
                        "tags": [],
                        "updated_at": "2025-01-15T12:00:00Z",
                        "is_favorited": null,
                        "thumbnail_url": "https://fal.media/files/kling.jpg",
                        "model_url": "https://fal.run/fal-ai/kling-video/v2.1/master/image-to-video",
                        "date": "2024-08-01T00:00:00Z",
                        "highlighted": false,
                        "pinned": false
                      }
                    }
                  ],
                  "next_cursor": null,
                  "has_more": false
                }
                """.trimIndent(),
            )

        assertEquals(2, models.size)
        val flux = models.first { it.id == "fal-ai/flux/dev" }
        val kling = models.first { it.id == "fal-ai/kling-video/v2.1/master/image-to-video" }
        assertEquals("FLUX.1 [dev]", flux.name)
        assertEquals(true, flux.isImageGenerationCapable)
        assertEquals(false, flux.isChatCapable)
        assertEquals(setOf(com.browntowndev.enclave.domain.model.config.MediaTask.TEXT_TO_IMAGE), flux.tasks)
        assertEquals("Kling Video 2.1", kling.name)
        assertEquals(true, kling.isVideoGenerationCapable)
        assertEquals(setOf(com.browntowndev.enclave.domain.model.config.MediaTask.IMAGE_TO_VIDEO), kling.tasks)
    }

    @Test
    fun `parseFalModels classifies media capabilities and keeps fal media-only`() {
        val models =
            repo.parseFalModels(
                """
                {
                  "models": [
                    {"id": "fal-ai/flux-pro", "category": "image"},
                    {"id": "fal-ai/kling-video/v2", "category": "video"},
                    {"id": "fal-ai/stable-audio", "category": "audio"},
                    {"id": "fal-ai/minimax/speech-02-hd", "tags": ["speech"]},
                    {"id": "fal-ai/mystery-model", "description": "custom endpoint"}
                  ]
                }
                """.trimIndent(),
            )

        val flux = models.first { it.id == "fal-ai/flux-pro" }
        val video = models.first { it.id == "fal-ai/kling-video/v2" }
        val audio = models.first { it.id == "fal-ai/stable-audio" }
        val speech = models.first { it.id == "fal-ai/minimax/speech-02-hd" }
        val mystery = models.first { it.id == "fal-ai/mystery-model" }

        assertEquals(false, flux.isChatCapable)
        assertEquals(true, flux.isImageGenerationCapable)
        assertEquals(true, video.isVideoGenerationCapable)
        assertEquals(true, audio.isAudioGenerationCapable)
        assertEquals(true, speech.isAudioGenerationCapable)
        assertEquals(true, mystery.isImageGenerationCapable)
    }

    @Test
    fun `fetchModels falProvider truncatesAtMaxPages`() =
        runTest {
            val call = mockk<Call>()
            every { httpClient.newCall(any()) } returns call
            every { call.execute() } answers {
                jsonResponse(
                    request = Request.Builder().url("https://api.fal.ai/v1/models").build(),
                    body = """{"models": [], "next_cursor": "more-data"}""",
                )
            }

            val catalog = repo.fetchModels(ApiProvider.FAL, apiKey = "key", baseUrl = null)

            verify(exactly = 50) { httpClient.newCall(any()) }
            assertTrue(catalog.isTruncated)
            assertTrue(catalog.models.isNotEmpty())
        }

    private fun jsonResponse(
        request: Request,
        body: String,
        code: Int = 200,
    ): Response =
        Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
}
