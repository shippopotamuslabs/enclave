package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.usecase.inference.LlmToolingOrchestrator
import com.openai.client.OpenAIClient
import com.openai.core.JsonValue
import com.openai.errors.BadRequestException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertFalse

class BaseOpenAiSdkInferenceServiceTest {

    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val client = mockk<OpenAIClient>()

    private val service = object : BaseOpenAiSdkInferenceService(
        client = client,
        modelId = "openai/gpt-5.2",
        provider = "OPENROUTER",
        modelType = ModelType.THINKING,
        baseUrl = "https://openrouter.ai/api/v1",
        loggingPort = loggingPort,
        orchestrator = LlmToolingOrchestrator(mockk(), loggingPort),
    ) {
        override val tag: String = "BaseOpenAiSdkInferenceServiceTest"

        override suspend fun executePrompt(
            prompt: String,
            options: GenerationOptions,
            requestHistory: List<ChatMessage>,
            emitEvent: suspend (InferenceEvent) -> Unit
        ) = Unit

        fun describe(throwable: Throwable): String = describeException(throwable)
    }

    @Test
    fun `describeException ignores malformed optional error fields`() {
        val exception = mockk<BadRequestException>()
        every { exception.message } returns "400 Bad Request"
        every { exception.statusCode() } returns 400
        every { exception.code() } throws IllegalStateException("code field malformed")
        every { exception.type() } returns java.util.Optional.of("invalid_request_error")
        every { exception.param() } returns java.util.Optional.of("model")
        every { exception.body() } returns JsonValue.from(mapOf("error" to mapOf("message" to "bad request")))

        val description = service.describe(exception)

        assertFalse(description.contains("code="))
        assertFalse(description.contains("IllegalStateException"))
        verify {
            loggingPort.warning(
                "BaseOpenAiSdkInferenceServiceTest",
                match { it.contains("Skipping malformed API error field") }
            )
        }
    }
}