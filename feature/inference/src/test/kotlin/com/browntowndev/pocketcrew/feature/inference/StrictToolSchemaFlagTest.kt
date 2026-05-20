package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.AgentLoopMessage
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.util.FakeToolStreamGatePort
import com.browntowndev.pocketcrew.feature.inference.openai.StreamedOpenAiResponse
import com.openai.client.OpenAIClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.Optional

/**
 * FSIE-03, FSIE-04, FSIE-07
 *
 * Verifies that [BaseOpenAiSdkInferenceService.useStrictToolSchemas] controls the `strict`
 * flag passed through [toChatCompletionTool] when [completeSingleTurn] builds its request.
 */
class StrictToolSchemaFlagTest {
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val client = mockk<OpenAIClient>()

    // ── FSIE-03 ──────────────────────────────────────────────────────────────────

    @Test
    fun `FSIE-03 base class declares useStrictToolSchemas as false`() {
        val service =
            NonStrictService(
                client = client,
                loggingPort = loggingPort,
            )

        assertFalse(service.strictFlag, "useStrictToolSchemas must default to false")
    }

    // ── FSIE-04 ──────────────────────────────────────────────────────────────────

    @Test
    fun `FSIE-04 completeSingleTurn passes strict=false when useStrictToolSchemas is false`() =
        runTest {
            var capturedParams: ChatCompletionCreateParams? = null
            val service =
                CapturingNonStrictService(
                    client = client,
                    loggingPort = loggingPort,
                    onParams = { capturedParams = it },
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "hello", metadataJson = "null"),
                    ),
                systemPrompt = "",
                availableTools = listOf(ToolDefinition.WEB_SEARCH),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val tools = capturedParams!!.tools().get()
            assertEquals(1, tools.size, "Expected exactly 1 tool")
            val strictValue: Optional<Boolean> = tools[0].asFunction().function().strict()

            // After T-4/T-5/T-6: strict() must be absent OR Optional.of(false).
            // Currently toChatCompletionTool() hardcodes strict=true → this assertion fails RED.
            assertFalse(
                strictValue.isPresent && strictValue.get() == true,
                "strict must be absent or false when useStrictToolSchemas=false, " +
                    "but got strict=${if (strictValue.isPresent) strictValue.get() else "<absent>"}",
            )
        }

    // ── FSIE-07 ──────────────────────────────────────────────────────────────────

    @Test
    fun `FSIE-07 subclass overriding useStrictToolSchemas=true produces strict=true in params`() =
        runTest {
            var capturedParams: ChatCompletionCreateParams? = null
            val service =
                CapturingStrictService(
                    client = client,
                    loggingPort = loggingPort,
                    onParams = { capturedParams = it },
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "hello", metadataJson = "null"),
                    ),
                systemPrompt = "",
                availableTools = listOf(ToolDefinition.WEB_SEARCH),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val tools = capturedParams!!.tools().get()
            assertEquals(1, tools.size, "Expected exactly 1 tool")
            assertEquals(
                Optional.of(true),
                tools[0].asFunction().function().strict(),
                "strict must be Optional.of(true) when useStrictToolSchemas=true",
            )
        }

    // ── Test helpers ─────────────────────────────────────────────────────────────

    private val stubResponse =
        StreamedOpenAiResponse(
            emittedAny = false,
            functionCalls = emptyList(),
            responseId = "resp_stub",
            providerToolCallIds = emptyList(),
            providerToolItemIds = emptyList(),
            assistantMessageText = "",
        )

    /**
     * Exposes [useStrictToolSchemas] for direct inspection (FSIE-03).
     */
    private inner class NonStrictService(
        client: OpenAIClient,
        loggingPort: LoggingPort,
    ) : BaseOpenAiSdkInferenceService(
            client = client,
            modelId = "test-model",
            provider = "TEST",
            modelType = ModelType.FAST,
            baseUrl = null,
            loggingPort = loggingPort,
            toolStreamGatePort = FakeToolStreamGatePort(),
        ) {
        override val tag: String = "StrictToolSchemaFlagTest"

        /** Exposed for assertion. */
        val strictFlag: Boolean get() = useStrictToolSchemas

        override suspend fun streamChatCompletions(
            params: ChatCompletionCreateParams,
            chatId: ChatId?,
            userMessageId: MessageId?,
            assistantMessageId: MessageId?,
            emitEvent: suspend (InferenceEvent) -> Unit,
        ): StreamedOpenAiResponse = stubResponse
    }

    /**
     * Does NOT override [useStrictToolSchemas] (defaults to false).
     * Captures [ChatCompletionCreateParams] for FSIE-04.
     */
    private inner class CapturingNonStrictService(
        client: OpenAIClient,
        loggingPort: LoggingPort,
        private val onParams: (ChatCompletionCreateParams) -> Unit,
    ) : BaseOpenAiSdkInferenceService(
            client = client,
            modelId = "test-model",
            provider = "TEST",
            modelType = ModelType.FAST,
            baseUrl = null,
            loggingPort = loggingPort,
            toolStreamGatePort = FakeToolStreamGatePort(),
        ) {
        override val tag: String = "StrictToolSchemaFlagTest"

        // useStrictToolSchemas intentionally not overridden — uses parent's false default.

        override suspend fun streamChatCompletions(
            params: ChatCompletionCreateParams,
            chatId: ChatId?,
            userMessageId: MessageId?,
            assistantMessageId: MessageId?,
            emitEvent: suspend (InferenceEvent) -> Unit,
        ): StreamedOpenAiResponse {
            onParams(params)
            return stubResponse
        }
    }

    /**
     * Overrides [useStrictToolSchemas] to true.
     * Captures [ChatCompletionCreateParams] for FSIE-07.
     */
    private inner class CapturingStrictService(
        client: OpenAIClient,
        loggingPort: LoggingPort,
        private val onParams: (ChatCompletionCreateParams) -> Unit,
    ) : BaseOpenAiSdkInferenceService(
            client = client,
            modelId = "test-model",
            provider = "TEST",
            modelType = ModelType.FAST,
            baseUrl = null,
            loggingPort = loggingPort,
            toolStreamGatePort = FakeToolStreamGatePort(),
        ) {
        override val tag: String = "StrictToolSchemaFlagTest"

        override val useStrictToolSchemas: Boolean = true

        override suspend fun streamChatCompletions(
            params: ChatCompletionCreateParams,
            chatId: ChatId?,
            userMessageId: MessageId?,
            assistantMessageId: MessageId?,
            emitEvent: suspend (InferenceEvent) -> Unit,
        ): StreamedOpenAiResponse {
            onParams(params)
            return stubResponse
        }
    }
}
