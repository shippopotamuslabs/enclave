package com.browntowndev.enclave.domain.usecase.inference

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.util.TokenCounter
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResolveCompactionSummaryModelUseCaseTest {
    private val tokenCounter =
        object : TokenCounter {
            override fun countTokens(
                text: String,
                modelName: String?,
            ): Int = text.length
        }

    @Test
    fun `uses FAST when hosted FAST context window fits summarize payload`() =
        runTest {
            val useCase =
                useCase(
                    fastConfig = apiFastConfig(contextWindow = 200_000),
                )
            val messages = listOf(ChatMessage(Role.USER, "x".repeat(1_000)))

            assertEquals(
                ModelType.FAST,
                useCase.invoke(ModelType.THINKING, messages, summaryOutputTokenReserve = 1024),
            )
        }

    @Test
    fun `uses active turn when FAST is local to avoid second on-device runtime`() =
        runTest {
            val useCase =
                useCase(
                    fastConfig = localFastConfig(contextWindow = 200_000),
                )

            assertEquals(
                ModelType.THINKING,
                useCase.invoke(ModelType.THINKING, listOf(ChatMessage(Role.USER, "hello"))),
            )
        }

    @Test
    fun `uses active turn when hosted FAST window is too small`() =
        runTest {
            val useCase =
                useCase(
                    fastConfig = apiFastConfig(contextWindow = 2_000),
                )
            val messages = listOf(ChatMessage(Role.USER, "x".repeat(2_000)))

            assertEquals(
                ModelType.THINKING,
                useCase.invoke(ModelType.THINKING, messages, summaryOutputTokenReserve = 1024),
            )
        }

    @Test
    fun `uses active turn when FAST is not configured`() =
        runTest {
            val useCase = useCase(fastConfig = null)

            assertEquals(
                ModelType.FAST,
                useCase.invoke(ModelType.FAST, listOf(ChatMessage(Role.USER, "hello"))),
            )
        }

    @Test
    fun `uses active turn when hosted FAST has unknown context window`() =
        runTest {
            val useCase = useCase(fastConfig = apiFastConfig(contextWindow = null))

            assertEquals(
                ModelType.THINKING,
                useCase.invoke(ModelType.THINKING, listOf(ChatMessage(Role.USER, "hello"))),
            )
        }

    private fun useCase(fastConfig: ActiveModelConfiguration?): ResolveCompactionSummaryModelUseCase {
        val activeModelProvider = mockk<ActiveModelProviderPort>()
        coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns fastConfig
        return ResolveCompactionSummaryModelUseCase(activeModelProvider, tokenCounter)
    }

    private fun apiFastConfig(contextWindow: Int?): ActiveModelConfiguration =
        ActiveModelConfiguration(
            id = ApiModelConfigurationId("api-fast"),
            isLocal = false,
            name = "Fast API",
            modelId = "gpt-fast",
            systemPrompt = null,
            contextWindow = contextWindow,
            thinkingEnabled = false,
            temperature = null,
            topK = null,
            topP = null,
            maxTokens = null,
            minP = null,
            repetitionPenalty = null,
        )

    private fun localFastConfig(contextWindow: Int): ActiveModelConfiguration =
        apiFastConfig(contextWindow).copy(
            id = LocalModelConfigurationId("local-fast"),
            isLocal = true,
            name = "Fast Local",
        )
}
