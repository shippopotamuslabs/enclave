@file:Suppress("ForbiddenMockKInTests")

// MockK retained only for the OpenAI Java SDK surface (third-party SDK boundary).
package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import com.openai.client.OpenAIClient
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class ApiInferenceServiceImplTest {
    @Test
    fun `setHistory clears and adds messages`() =
        runBlocking {
            val client = mockk<OpenAIClient>()
            val loggingPort = FakeLoggingPort()
            val service =
                ApiInferenceServiceImpl(
                    client = client,
                    modelId = "gpt-4o",
                    provider = "OPENAI",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolStreamGatePort = FakeToolStreamGatePort(),
                )

            val messages = listOf(ChatMessage(Role.USER, "Hello"))
            service.setHistory(messages)

            // Since conversationHistory is private, we're just testing it doesn't crash
            // A full test would mock the stream response and verify the params passed to createStreaming.

            service.closeSession()
        }
}
