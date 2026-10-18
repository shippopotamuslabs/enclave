package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SaveLocalModelConfigurationUseCaseTest {
    private val repository = FakeLocalModelRepository()
    private lateinit var useCase: SaveLocalModelConfigurationUseCase

    @BeforeEach
    fun setup() {
        useCase = SaveLocalModelConfigurationUseCaseImpl(repository)
    }

    @Test
    fun `invoke calls repository saveConfiguration`() =
        runTest {
            val config =
                LocalModelConfiguration(
                    id = LocalModelConfigurationId("1"),
                    localModelId = LocalModelId("model-1"),
                    displayName = "Test Config",
                    maxTokens = 2048,
                    contextWindow = 4096,
                    temperature = 0.7,
                    topP = 0.95,
                    topK = 40,
                    repetitionPenalty = 1.1,
                    systemPrompt = "",
                )
            val expectedId = LocalModelConfigurationId("1")

            val result = useCase(config)

            assertTrue(result.isSuccess)
            assertEquals(expectedId, result.getOrNull())
            assertEquals(listOf(config), repository.savedConfigurations)
        }
}
