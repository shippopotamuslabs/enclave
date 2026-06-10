package com.browntowndev.enclave.feature.inference

import com.anthropic.client.AnthropicClient
import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AnthropicInferenceServiceImplExtendedTest {
    private val client = mockk<AnthropicClient>()
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    private lateinit var service: AnthropicInferenceServiceImpl

    @BeforeEach
    fun setup() {
        service =
            AnthropicInferenceServiceImpl(
                client = client,
                modelId = "claude-sonnet-4-20250514",
                modelType = ModelType.MAIN,
                loggingPort = loggingPort,
                toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                toolStreamGatePort = FakeToolStreamGatePort(),
            )
    }

    @Test
    fun `sendPrompt is not supported on direct Anthropic service`() {
        assertThrows<IllegalStateException> {
            service.sendPrompt("Hello", closeConversation = false)
        }
    }

    @Test
    fun `setHistory and closeSession do not throw`() =
        runTest {
            service.setHistory(
                listOf(
                    ChatMessage(role = Role.USER, content = "Previous message"),
                    ChatMessage(role = Role.ASSISTANT, content = "Previous response"),
                ),
            )
            service.closeSession()
        }
}