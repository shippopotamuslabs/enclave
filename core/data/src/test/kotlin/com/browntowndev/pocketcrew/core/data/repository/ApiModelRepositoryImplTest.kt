package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ApiModelEntity
import com.browntowndev.pocketcrew.core.data.local.ApiModelsDao
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfig
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ApiModelRepositoryImplTest {

    private lateinit var dao: ApiModelsDao
    private lateinit var apiKeyManager: ApiKeyManager
    private lateinit var repository: ApiModelRepositoryImpl

    @BeforeEach
    fun setUp() {
        dao = mockk(relaxed = true)
        apiKeyManager = mockk(relaxed = true)
        repository = ApiModelRepositoryImpl(dao, apiKeyManager)
    }

    // ========================================================================
    // Happy Path
    // ========================================================================

    @Test
    fun `save persists entity to DAO and key to ApiKeyManager`() = runTest {
        val config = ApiModelConfig(
            displayName = "Test",
            provider = ApiProvider.ANTHROPIC,
            modelId = "claude-sonnet-4",
        )
        coEvery { dao.upsert(any()) } returns 1L

        repository.save(config, "sk-test")

        coVerify { dao.upsert(any()) }
        verify { apiKeyManager.save(any(), "sk-test") }
    }

    @Test
    fun `delete removes entity from DAO and key from ApiKeyManager`() = runTest {
        repository.delete(1L)

        coVerify { dao.deleteById(1L) }
        verify { apiKeyManager.delete(1L) }
    }

    @Test
    fun `entity to domain mapping preserves all tuning fields`() = runTest {
        val entity = ApiModelEntity(
            id = 1L,
            displayName = "Full Config",
            provider = ApiProvider.OPENAI,
            modelId = "gpt-4o",
            baseUrl = "https://api.custom.com/v1",
            isVision = true,
            maxTokens = 16384,
            temperature = 0.3,
            topP = 1.0,
            topK = 40,
            frequencyPenalty = 0.5,
            presencePenalty = 0.3,
            stopSequences = "stop1;;;stop2;;;stop3",
        )
        coEvery { dao.observeAll() } returns flowOf(listOf(entity))

        val result = repository.observeAll().first()

        assertEquals(1, result.size)
        val config = result[0]
        assertEquals(1L, config.id)
        assertEquals("Full Config", config.displayName)
        assertEquals(ApiProvider.OPENAI, config.provider)
        assertEquals("gpt-4o", config.modelId)
        assertEquals("https://api.custom.com/v1", config.baseUrl)
        assertEquals(true, config.isVision)
        assertEquals(16384, config.maxTokens)
        assertEquals(0.3, config.temperature)
        assertEquals(1.0, config.topP)
        assertEquals(40, config.topK)
        assertEquals(0.5, config.frequencyPenalty)
        assertEquals(0.3, config.presencePenalty)
        assertEquals(listOf("stop1", "stop2", "stop3"), config.stopSequences)
    }

    @Test
    fun `observeAll emits domain models`() = runTest {
        coEvery { dao.observeAll() } returns flowOf(
            listOf(
                ApiModelEntity(id = 1, displayName = "A", provider = ApiProvider.ANTHROPIC, modelId = "m1"),
                ApiModelEntity(id = 2, displayName = "B", provider = ApiProvider.GOOGLE, modelId = "m2"),
            ),
        )

        val result = repository.observeAll().first()
        assertEquals(2, result.size)
    }

    // ========================================================================
    // Edge Cases
    // ========================================================================

    @Test
    fun `save with id 0 triggers insert (autoGenerate)`() = runTest {
        val config = ApiModelConfig(id = 0, displayName = "New", provider = ApiProvider.GOOGLE, modelId = "gemini")
        coEvery { dao.upsert(any()) } returns 1L

        repository.save(config, "key")

        coVerify { dao.upsert(match { it.id == 0L }) }
    }

    @Test
    fun `mapping handles null baseUrl correctly`() = runTest {
        coEvery { dao.observeAll() } returns flowOf(
            listOf(ApiModelEntity(id = 1, displayName = "A", provider = ApiProvider.ANTHROPIC, modelId = "m1", baseUrl = null)),
        )

        val result = repository.observeAll().first()
        assertNull(result[0].baseUrl)
    }

    @Test
    fun `mapping handles null topK correctly`() = runTest {
        coEvery { dao.observeAll() } returns flowOf(
            listOf(ApiModelEntity(id = 1, displayName = "A", provider = ApiProvider.OPENAI, modelId = "m1", topK = null)),
        )

        val result = repository.observeAll().first()
        assertNull(result[0].topK)
    }
}
