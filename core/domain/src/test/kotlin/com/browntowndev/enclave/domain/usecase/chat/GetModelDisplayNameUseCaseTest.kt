package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.error.ActiveModelConfigurationDecryptException
import com.browntowndev.enclave.domain.model.inference.ModelType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetModelDisplayNameUseCaseTest {
    private val activeModelProvider = FakeActiveModelProvider()
    private val useCase = GetModelDisplayNameUseCase(activeModelProvider)

    @Test
    fun `SCN-USECASE-001 get model display name returns asset label`() =
        runTest {
            activeModelProvider.configuration =
                ActiveModelConfiguration(
                    id = LocalModelConfigurationId("cfg-fast"),
                    isLocal = true,
                    name = "Gemma 4 E4B",
                    modelId = "gemma-4-E4B-it.litertlm",
                    systemPrompt = null,
                    temperature = null,
                    topK = null,
                    topP = null,
                    maxTokens = null,
                    minP = null,
                    repetitionPenalty = null,
                    contextWindow = null,
                    thinkingEnabled = false,
                )

            assertEquals("Gemma 4 E4B", useCase(ModelType.FAST))
        }

    @Test
    fun `returns empty string when no active configuration`() =
        runTest {
            activeModelProvider.configuration = null

            assertEquals("", useCase(ModelType.FAST))
        }

    @Test
    fun `returns decrypt user message instead of propagating exception`() =
        runTest {
            activeModelProvider.failure =
                ActiveModelConfigurationDecryptException(ModelType.FAST)

            assertEquals(ActiveModelConfigurationDecryptException.USER_MESSAGE, useCase(ModelType.FAST))
        }
}
