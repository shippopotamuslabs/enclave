package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.usecase.FakeApiModelRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SaveApiModelUseCaseTest {

    private lateinit var repository: FakeApiModelRepository
    private lateinit var useCase: SaveApiModelUseCase

    @BeforeEach
    fun setUp() {
        repository = FakeApiModelRepository()
        useCase = SaveApiModelUseCase(repository)
    }

    // ========================================================================
    // Happy Path
    // ========================================================================

    @Test
    fun `save a valid Anthropic API model`() = runTest {
        val id = useCase(
            displayName = "My Claude",
            provider = ApiProvider.ANTHROPIC,
            modelId = "claude-sonnet-4-20250514",
            apiKey = "sk-ant-xxxx",
            maxTokens = 8192,
            temperature = 0.8,
            topP = 0.9,
            topK = 40,
            frequencyPenalty = 0.0,
            presencePenalty = 0.0,
        )

        assertEquals(1L, id)
        val models = repository.getAll()
        assertEquals(1, models.size)
        assertEquals("My Claude", models[0].displayName)
        assertEquals(ApiProvider.ANTHROPIC, models[0].provider)
        assertEquals("claude-sonnet-4-20250514", models[0].modelId)
        assertEquals(8192, models[0].maxTokens)
        assertEquals(0.8, models[0].temperature)
        assertEquals(0.9, models[0].topP)
        assertEquals(40, models[0].topK)
        assertEquals("sk-ant-xxxx", repository.savedKeys[1L])
    }

    @Test
    fun `save a valid OpenAI API model with all tuning parameters`() = runTest {
        val id = useCase(
            displayName = "GPT-4o",
            provider = ApiProvider.OPENAI,
            modelId = "gpt-4o",
            apiKey = "sk-xxxx",
            maxTokens = 16384,
            temperature = 0.3,
            topP = 1.0,
            topK = null,
            frequencyPenalty = 0.5,
            presencePenalty = 0.3,
            baseUrl = "https://api.custom.com/v1",
        )

        val saved = repository.getById(id)!!
        assertEquals(0.5, saved.frequencyPenalty)
        assertEquals(0.3, saved.presencePenalty)
        assertEquals("https://api.custom.com/v1", saved.baseUrl)
    }

    @Test
    fun `save a valid Google API model`() = runTest {
        val id = useCase(
            displayName = "Gemini Flash",
            provider = ApiProvider.GOOGLE,
            modelId = "gemini-2.5-flash",
            apiKey = "AIza-xxxx",
            topK = 40,
            topP = 0.95,
            temperature = 1.0,
        )

        val saved = repository.getById(id)!!
        assertEquals(40, saved.topK)
        assertEquals(ApiProvider.GOOGLE, saved.provider)
        assertNull(saved.baseUrl)
    }

    @Test
    fun `update an existing API model`() = runTest {
        useCase(
            displayName = "Original",
            provider = ApiProvider.ANTHROPIC,
            modelId = "claude-sonnet-4",
            apiKey = "sk-ant-old",
        )

        useCase(
            id = 1L,
            displayName = "Claude Renamed",
            provider = ApiProvider.ANTHROPIC,
            modelId = "claude-sonnet-4",
            apiKey = "sk-ant-new",
        )

        val models = repository.getAll()
        assertEquals(1, models.size)
        assertEquals("Claude Renamed", models[0].displayName)
        assertEquals("sk-ant-new", repository.savedKeys[1L])
    }

    @Test
    fun `save model preserves default tuning values when not specified`() = runTest {
        useCase(
            displayName = "Defaults Test",
            provider = ApiProvider.ANTHROPIC,
            modelId = "test-model",
            apiKey = "sk-test",
        )

        val saved = repository.getAll().first()
        assertEquals(4096, saved.maxTokens)
        assertEquals(4096, saved.contextWindow)
        assertEquals(0.7, saved.temperature)
        assertEquals(0.95, saved.topP)
        assertNull(saved.topK)
        assertEquals(0.0, saved.frequencyPenalty)
        assertEquals(0.0, saved.presencePenalty)
        assertTrue(saved.stopSequences.isEmpty())
    }

    // ========================================================================
    // Error Path — Validation
    // ========================================================================

    @Test
    fun `reject blank display name`() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(
                    displayName = "",
                    provider = ApiProvider.ANTHROPIC,
                    modelId = "claude-sonnet-4",
                    apiKey = "sk-ant-xxxx",
                )
            }
        }
    }

    @Test
    fun `reject whitespace-only display name`() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(
                    displayName = "   ",
                    provider = ApiProvider.ANTHROPIC,
                    modelId = "claude-sonnet-4",
                    apiKey = "sk-ant-xxxx",
                )
            }
        }
    }

    @Test
    fun `reject blank API key`() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(
                    displayName = "Test",
                    provider = ApiProvider.ANTHROPIC,
                    modelId = "claude-sonnet-4",
                    apiKey = "",
                )
            }
        }
    }

    @Test
    fun `reject blank model ID`() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(
                    displayName = "Test",
                    provider = ApiProvider.ANTHROPIC,
                    modelId = "",
                    apiKey = "sk-ant-xxxx",
                )
            }
        }
    }

    @Test
    fun `reject negative maxTokens`() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(
                    displayName = "Test",
                    provider = ApiProvider.ANTHROPIC,
                    modelId = "claude-sonnet-4",
                    apiKey = "sk-ant-xxxx",
                    maxTokens = -1,
                )
            }
        }
    }

    @Test
    fun `reject maxTokens of zero`() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(
                    displayName = "Test",
                    provider = ApiProvider.ANTHROPIC,
                    modelId = "claude-sonnet-4",
                    apiKey = "sk-ant-xxxx",
                    maxTokens = 0,
                )
            }
        }
    }

    @Test
    fun `reject negative contextWindow`() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(
                    displayName = "Test",
                    provider = ApiProvider.ANTHROPIC,
                    modelId = "claude-sonnet-4",
                    apiKey = "sk-ant-xxxx",
                    contextWindow = -1,
                )
            }
        }
    }

    @Test
    fun `reject contextWindow of zero`() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(
                    displayName = "Test",
                    provider = ApiProvider.ANTHROPIC,
                    modelId = "claude-sonnet-4",
                    apiKey = "sk-ant-xxxx",
                    contextWindow = 0,
                )
            }
        }
    }

    @Test
    fun `reject temperature above 2_0`() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(
                    displayName = "Test",
                    provider = ApiProvider.ANTHROPIC,
                    modelId = "claude-sonnet-4",
                    apiKey = "sk-ant-xxxx",
                    temperature = 2.5,
                )
            }
        }
    }

    @Test
    fun `reject temperature below 0_0`() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(
                    displayName = "Test",
                    provider = ApiProvider.ANTHROPIC,
                    modelId = "claude-sonnet-4",
                    apiKey = "sk-ant-xxxx",
                    temperature = -0.1,
                )
            }
        }
    }

    @Test
    fun `reject topP above 1_0`() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(
                    displayName = "Test",
                    provider = ApiProvider.ANTHROPIC,
                    modelId = "claude-sonnet-4",
                    apiKey = "sk-ant-xxxx",
                    topP = 1.5,
                )
            }
        }
    }

    @Test
    fun `reject topP below 0_0`() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(
                    displayName = "Test",
                    provider = ApiProvider.ANTHROPIC,
                    modelId = "claude-sonnet-4",
                    apiKey = "sk-ant-xxxx",
                    topP = -0.1,
                )
            }
        }
    }

    @Test
    fun `reject frequencyPenalty above 2_0`() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(
                    displayName = "Test",
                    provider = ApiProvider.ANTHROPIC,
                    modelId = "claude-sonnet-4",
                    apiKey = "sk-ant-xxxx",
                    frequencyPenalty = 2.5,
                )
            }
        }
    }

    @Test
    fun `reject frequencyPenalty below negative 2_0`() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(
                    displayName = "Test",
                    provider = ApiProvider.ANTHROPIC,
                    modelId = "claude-sonnet-4",
                    apiKey = "sk-ant-xxxx",
                    frequencyPenalty = -2.5,
                )
            }
        }
    }

    @Test
    fun `reject presencePenalty above 2_0`() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(
                    displayName = "Test",
                    provider = ApiProvider.ANTHROPIC,
                    modelId = "claude-sonnet-4",
                    apiKey = "sk-ant-xxxx",
                    presencePenalty = 2.5,
                )
            }
        }
    }

    @Test
    fun `reject topK less than 1 when non-null`() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(
                    displayName = "Test",
                    provider = ApiProvider.ANTHROPIC,
                    modelId = "claude-sonnet-4",
                    apiKey = "sk-ant-xxxx",
                    topK = 0,
                )
            }
        }
    }

    @Test
    fun `accept topK as null`() = runTest {
        val id = useCase(
            displayName = "Test",
            provider = ApiProvider.ANTHROPIC,
            modelId = "claude-sonnet-4",
            apiKey = "sk-ant-xxxx",
            topK = null,
        )
        assertNull(repository.getById(id)!!.topK)
    }

    @Test
    fun `accept boundary temperature 0_0`() = runTest {
        useCase(
            displayName = "Test",
            provider = ApiProvider.ANTHROPIC,
            modelId = "claude-sonnet-4",
            apiKey = "sk-ant-xxxx",
            temperature = 0.0,
        )
        assertEquals(0.0, repository.getAll().first().temperature)
    }

    @Test
    fun `accept boundary temperature 2_0`() = runTest {
        useCase(
            displayName = "Test",
            provider = ApiProvider.ANTHROPIC,
            modelId = "claude-sonnet-4",
            apiKey = "sk-ant-xxxx",
            temperature = 2.0,
        )
        assertEquals(2.0, repository.getAll().first().temperature)
    }

    @Test
    fun `accept maximum stopSequences count of 5`() = runTest {
        useCase(
            displayName = "Test",
            provider = ApiProvider.ANTHROPIC,
            modelId = "claude-sonnet-4",
            apiKey = "sk-ant-xxxx",
            stopSequences = listOf("a", "b", "c", "d", "e"),
        )
        assertEquals(5, repository.getAll().first().stopSequences.size)
    }

    @Test
    fun `reject more than 5 stop sequences`() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(
                    displayName = "Test",
                    provider = ApiProvider.ANTHROPIC,
                    modelId = "claude-sonnet-4",
                    apiKey = "sk-ant-xxxx",
                    stopSequences = listOf("a", "b", "c", "d", "e", "f"),
                )
            }
        }
    }

    // ========================================================================
    // Mutation Defense
    // ========================================================================

    @Test
    fun `blank API key with non-blank name and modelId must fail -- save never called`() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                useCase(
                    displayName = "Valid Name",
                    provider = ApiProvider.ANTHROPIC,
                    modelId = "valid-model",
                    apiKey = "",
                )
            }
        }
        runBlocking {
            assertTrue(repository.getAll().isEmpty(), "save should never have been called")
        }
    }
}
