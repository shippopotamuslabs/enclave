package com.browntowndev.enclave.feature.inference

import com.anthropic.client.AnthropicClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.RawMessageStreamEvent
import com.anthropic.services.blocking.MessageService
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import com.browntowndev.enclave.feature.inference.openai.StreamedOpenAiResponse
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.openai.client.OpenAIClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PromptCachingTest {
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    @Test
    @DisplayName("SC-REG-01 OpenAI separates stable system and sidecar")
    fun SC_REG_01_test_PC_01_OpenAiSeparatesSystemPromptAndSidecars() =
        runTest {
            val client = mockk<OpenAIClient>()
            val capturedParams = mutableListOf<ChatCompletionCreateParams>()

            val service =
                object : BaseOpenAiSdkInferenceService(
                    client = client,
                    modelId = "gpt-4o",
                    provider = "OPENAI",
                    modelType = ModelType.FAST,
                    loggingPort = loggingPort,
                    toolStreamGatePort = FakeToolStreamGatePort(),
                ) {
                    override val tag: String = "TestOpenAi"

                    override suspend fun streamChatCompletions(
                        params: ChatCompletionCreateParams,
                        chatId: com.browntowndev.enclave.domain.model.chat.ChatId?,
                        userMessageId: com.browntowndev.enclave.domain.model.chat.MessageId?,
                        assistantMessageId: com.browntowndev.enclave.domain.model.chat.MessageId?,
                        emitEvent: suspend (InferenceEvent) -> Unit,
                    ): StreamedOpenAiResponse {
                        capturedParams.add(params)
                        return StreamedOpenAiResponse(
                            emittedAny = true,
                            functionCalls = emptyList(),
                            responseId = "resp_1",
                            providerToolCallIds = emptyList(),
                            providerToolItemIds = emptyList(),
                            assistantMessageText = "mock-response",
                        )
                    }
                }

            val sidecar = """State ledger JSON: {"key":"value"}"""
            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "msg1", role = Role.USER, content = "Hello"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = sidecar,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            assertTrue(capturedParams.isNotEmpty())
            val messages = capturedParams.first().messages()
            assertEquals(2, messages.size)
            assertTrue(messages[0].isSystem())
            assertEquals("You are a helpful assistant.", messages[0].asSystem().content().asText())
            assertTrue(messages[1].isUser())
            val userText = messages[1].asUser().content().asText()
            assertTrue(userText.startsWith("Hello"))
            assertTrue(userText.contains("[Sidecar-"))
            assertTrue(userText.contains(sidecar))
            assertEquals(
                1,
                requireNotNull(service.appendOnlyContextForTests()).frozenSidecarsForWireReplay().size,
            )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "msg1", role = Role.USER, content = "Hello"),
                        AgentLoopMessage(id = "msg2", role = Role.ASSISTANT, content = "Hi"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = sidecar,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            assertEquals(
                1,
                requireNotNull(service.appendOnlyContextForTests()).frozenSidecarsForWireReplay().size,
            )
        }

    @Test
    fun test_PC_01_multi_turn_tail_placement() =
        runTest {
            val client = mockk<OpenAIClient>()
            val capturedParams = slot<ChatCompletionCreateParams>()

            val service =
                object : BaseOpenAiSdkInferenceService(
                    client = client,
                    modelId = "gpt-4o",
                    provider = "OPENAI",
                    modelType = ModelType.FAST,
                    loggingPort = loggingPort,
                    toolStreamGatePort = FakeToolStreamGatePort(),
                ) {
                    override val tag: String = "TestOpenAi"

                    override suspend fun streamChatCompletions(
                        params: ChatCompletionCreateParams,
                        chatId: com.browntowndev.enclave.domain.model.chat.ChatId?,
                        userMessageId: com.browntowndev.enclave.domain.model.chat.MessageId?,
                        assistantMessageId: com.browntowndev.enclave.domain.model.chat.MessageId?,
                        emitEvent: suspend (InferenceEvent) -> Unit,
                    ): StreamedOpenAiResponse {
                        capturedParams.captured = params
                        return StreamedOpenAiResponse(
                            emittedAny = true,
                            functionCalls = emptyList(),
                            responseId = "resp_1",
                            providerToolCallIds = emptyList(),
                            providerToolItemIds = emptyList(),
                            assistantMessageText = "mock-response",
                        )
                    }
                }

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "Follow up"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = "State ledger JSON: {\"key\":\"value\"}",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val messages = capturedParams.captured.messages()
            assertEquals(3, messages.size)
            assertEquals("You are a helpful assistant.", messages[0].asSystem().content().asText())
            assertEquals("Hello", messages[1].asUser().content().asText())
            val followUpText = messages[2].asUser().content().asText()
            assertTrue(followUpText.startsWith("Follow up"))
            assertTrue(followUpText.contains("[Sidecar-"))
            assertTrue(followUpText.contains("""State ledger JSON: {"key":"value"}"""))
            assertFalse(messages[1].isSystem())
        }

    @Test
    fun test_PC_02_AnthropicAttachesEphemeralCacheControl() =
        runTest {
            val client = mockk<AnthropicClient>()
            val messageService = mockk<MessageService>()
            val streamResponse = mockk<com.anthropic.core.http.StreamResponse<RawMessageStreamEvent>>()
            val capturedParams = slot<MessageCreateParams>()

            val service =
                AnthropicInferenceServiceImpl(
                    client = client,
                    modelId = "claude-3-5-sonnet-20241022",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                )

            every { client.messages() } returns messageService
            every { messageService.createStreaming(capture(capturedParams)) } returns streamResponse
            every { streamResponse.stream() } returns
                java.util.stream.Stream.of(
                    anthropicMessageStopEvent(),
                )
            every { streamResponse.close() } returns Unit

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                        AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Hi!"),
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "Explain the plan."),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = "Volatile sidecar content",
                availableTools =
                    listOf(
                        ToolDefinition.WEB_SEARCH,
                        ToolDefinition.APPEND_BLOCKS,
                    ),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            verify(exactly = 1) { messageService.createStreaming(any<MessageCreateParams>()) }
            assertTrue(capturedParams.isCaptured)
            val params = capturedParams.captured

            // System prompt assertions
            val system = params.system().orElse(null) ?: error("System prompt not set")
            assertTrue(system.isTextBlockParams())
            val systemBlocks = system.asTextBlockParams()
            assertEquals(1, systemBlocks.size)
            assertEquals("You are a helpful assistant.", systemBlocks[0].text())
            assertTrue(systemBlocks[0].cacheControl().isPresent)

            // Tools assertions
            val tools = params.tools().orElse(null) ?: error("Tools not set")
            assertEquals(2, tools.size)
            assertEquals("web_search", tools[0].tool().get().name())
            assertFalse(
                tools[0]
                    .tool()
                    .get()
                    .cacheControl()
                    .isPresent,
            )
            assertEquals("append_blocks", tools[1].tool().get().name())
            assertTrue(
                tools[1]
                    .tool()
                    .get()
                    .cacheControl()
                    .isPresent,
            )

            // Messages assertions
            val messages = params.messages()
            assertEquals(3, messages.size)
            // U1 "Hello"
            val u1Content = messages[0].content()
            assertTrue(u1Content.isBlockParams())
            val u1Blocks = u1Content.asBlockParams()
            assertEquals(1, u1Blocks.size)
            assertEquals("Hello", u1Blocks[0].asText().text())
            assertFalse(u1Blocks[0].asText().cacheControl().isPresent)

            // A1 "Hi!" — stable history tail gets the third cache breakpoint
            val a1Content = messages[1].content()
            assertTrue(a1Content.isBlockParams())
            val a1Blocks = a1Content.asBlockParams()
            assertEquals(1, a1Blocks.size)
            assertEquals("Hi!", a1Blocks[0].asText().text())
            assertTrue(a1Blocks[0].asText().cacheControl().isPresent)

            // U2 live turn: sidecars present, no cache_control on volatile content
            val u2Content = messages[2].content()
            assertTrue(u2Content.isBlockParams())
            val u2Blocks = u2Content.asBlockParams()
            assertEquals(1, u2Blocks.size)
            val u2Text = u2Blocks[0].asText()
            assertFalse(u2Text.cacheControl().isPresent)
            assertTrue(u2Text.text().contains("Volatile sidecar content"))
            assertTrue(u2Text.text().contains("Explain the plan."))
        }

    @Test
    fun test_PC_03_AnthropicLimitsCacheControlToExactly3Breakpoints() =
        runTest {
            val client = mockk<AnthropicClient>()
            val messageService = mockk<MessageService>()
            val streamResponse = mockk<com.anthropic.core.http.StreamResponse<RawMessageStreamEvent>>()
            val capturedParams = slot<MessageCreateParams>()

            val service =
                AnthropicInferenceServiceImpl(
                    client = client,
                    modelId = "claude-3-5-sonnet-20241022",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                )

            every { client.messages() } returns messageService
            every { messageService.createStreaming(capture(capturedParams)) } returns streamResponse
            every { streamResponse.stream() } returns
                java.util.stream.Stream.of(
                    anthropicMessageStopEvent(),
                )
            every { streamResponse.close() } returns Unit

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "U1"),
                        AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "A1"),
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "U2"),
                        AgentLoopMessage(id = "a2", role = Role.ASSISTANT, content = "A2"),
                        AgentLoopMessage(id = "u3", role = Role.USER, content = "U3"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = "Volatile sidecars JSON",
                availableTools =
                    listOf(
                        ToolDefinition.WEB_SEARCH,
                        ToolDefinition.APPEND_BLOCKS,
                        ToolDefinition.GENERATE_IMAGE,
                    ),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            verify(exactly = 1) { messageService.createStreaming(any<MessageCreateParams>()) }
            assertTrue(capturedParams.isCaptured)
            val params = capturedParams.captured

            // Count the cache breakpoints
            var cacheControlCount = 0

            val system = params.system().orElse(null)
            if (system != null && system.isTextBlockParams()) {
                cacheControlCount += system.asTextBlockParams().count { it.cacheControl().isPresent }
            }

            val tools = params.tools().orElse(emptyList())
            cacheControlCount += tools.count { it.tool().flatMap { t -> t.cacheControl() }.isPresent }

            val messages = params.messages()
            messages.forEach { msg ->
                if (msg.content().isBlockParams()) {
                    msg.content().asBlockParams().forEach { block ->
                        val hasCc =
                            when {
                                block.isText() -> block.asText().cacheControl().isPresent
                                block.isToolUse() -> block.asToolUse().cacheControl().isPresent
                                block.isToolResult() -> block.asToolResult().cacheControl().isPresent
                                else -> false
                            }
                        if (hasCc) cacheControlCount++
                    }
                }
            }

            assertEquals(3, cacheControlCount)

            // Check that the breakpoints are located on:
            // 1. The system prompt block.
            val systemBlocks = system?.asTextBlockParams() ?: emptyList()
            assertEquals(1, systemBlocks.size)
            assertTrue(systemBlocks[0].cacheControl().isPresent)

            // 2. The third tool (last tool) in the tools list.
            assertEquals(3, tools.size)
            assertFalse(tools[0].tool().flatMap { it.cacheControl() }.isPresent)
            assertFalse(tools[1].tool().flatMap { it.cacheControl() }.isPresent)
            assertTrue(tools[2].tool().flatMap { it.cacheControl() }.isPresent)

            // 3. The stable history tail (A2), not the live user turn with sidecars.
            assertEquals(5, messages.size)
            assertFalse(
                messages[0]
                    .content()
                    .asBlockParams()[0]
                    .asText()
                    .cacheControl()
                    .isPresent,
            )
            assertFalse(
                messages[1]
                    .content()
                    .asBlockParams()[0]
                    .asText()
                    .cacheControl()
                    .isPresent,
            )
            assertFalse(
                messages[2]
                    .content()
                    .asBlockParams()[0]
                    .asText()
                    .cacheControl()
                    .isPresent,
            )
            assertTrue(
                messages[3]
                    .content()
                    .asBlockParams()[0]
                    .asText()
                    .cacheControl()
                    .isPresent,
            )
            assertFalse(
                messages[4]
                    .content()
                    .asBlockParams()[0]
                    .asText()
                    .cacheControl()
                    .isPresent,
            )
            assertTrue(
                messages[4]
                    .content()
                    .asBlockParams()[0]
                    .asText()
                    .text()
                    .contains("Volatile sidecars JSON"),
            )
        }

    @Test
    fun test_PC_04_GoogleGenAiCreatesCachedContentOnExceededThreshold() =
        runTest {
            val sdkClient = mockk<GoogleGenAiSdkClient>()
            val service =
                GoogleInferenceServiceImpl(
                    sdkClient = sdkClient,
                    modelId = "gemini-2.5-flash",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                )

            // Mock caching endpoints
            coEvery { sdkClient.createCachedContent(any(), any(), any(), any(), any()) } returns "cachedContents/mock-cache-123"
            coEvery { sdkClient.generateContentStream(any(), any(), any(), any(), any()) } returns
                GoogleSdkResult(
                    emittedAny = true,
                    functionCall = null,
                    assistantContent = null,
                    finishReason = "STOP",
                )

            val padding = "padding ".repeat(300)
            val messages =
                (1..10).map { i ->
                    if (i == 10) {
                        AgentLoopMessage(id = "u10", role = Role.USER, content = "User message content 10")
                    } else if (i % 2 == 1) {
                        AgentLoopMessage(id = "u$i", role = Role.USER, content = "User message content $i $padding")
                    } else {
                        AgentLoopMessage(id = "a$i", role = Role.ASSISTANT, content = "Assistant response $i")
                    }
                }

            service.completeSingleTurn(
                sessionMessages = messages,
                systemPrompt = "System instruction prompt",
                contextSidecars = "Sidecar key value json",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val contentsSlot = slot<List<Content>>()
            val systemSlot = slot<Content>()
            coVerify {
                sdkClient.createCachedContent(
                    modelId = "gemini-2.5-flash",
                    contents = capture(contentsSlot),
                    systemInstruction = capture(systemSlot),
                    tools = any(),
                    ttlSeconds = 300L,
                )
            }
            assertEquals(9, contentsSlot.captured.size)
            assertEquals(
                "System instruction prompt",
                systemSlot.captured
                    .parts()
                    .orElse(null)
                    ?.firstOrNull()
                    ?.text()
                    ?.orElse(null),
            )

            val configSlot = slot<GenerateContentConfig>()
            val contentsGenSlot = slot<List<Content>>()
            coVerify {
                sdkClient.generateContentStream(
                    modelId = "gemini-2.5-flash",
                    contents = capture(contentsGenSlot),
                    config = capture(configSlot),
                    allowToolCall = false,
                    emitEvent = any(),
                )
            }
            assertEquals("cachedContents/mock-cache-123", configSlot.captured.cachedContent().orElse(null))
            assertEquals(1, contentsGenSlot.captured.size)
            val lastMsgPart =
                contentsGenSlot.captured[0]
                    .parts()
                    .orElse(null)
                    ?.firstOrNull()
            assertTrue(lastMsgPart?.text()?.orElse(null)?.contains("User message content 10") == true)
            assertTrue(lastMsgPart?.text()?.orElse(null)?.contains("Sidecar key value json") == true)
        }

    @Test
    fun test_PC_05_GoogleGenAiReusesCachedContentAndTransmitsOnlyDeltas() =
        runTest {
            val sdkClient = mockk<GoogleGenAiSdkClient>()
            val service =
                GoogleInferenceServiceImpl(
                    sdkClient = sdkClient,
                    modelId = "gemini-2.5-flash",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                )

            // Setup active cache
            // Under green implementation, the activeCacheName "cachedContents/mock-cache-123" is set.
            // Also the cachedMessages list stores the previously cached messages (U1, A1, U2, A2).
            // Let's call completeSingleTurn once to establish cache, then a second time.
            // Wait, to keep tests focused, since we can't write internal state directly, we mock createCachedContent for the first turn,
            // and check that the second completeSingleTurn call utilizes the same cachedContent name and only generates delta.
            coEvery { sdkClient.createCachedContent(any(), any(), any(), any(), any()) } returns "cachedContents/mock-cache-123"
            coEvery { sdkClient.generateContentStream(any(), any(), any(), any(), any()) } returns
                GoogleSdkResult(
                    emittedAny = true,
                    functionCall = null,
                    assistantContent = null,
                    finishReason = "STOP",
                )

            val padding = "padding ".repeat(1200)
            // First turn: U1, A1, U2, A2, latest is U3 (which gets cached on next turn, or U1..U2 cached now, latest User is U3)
            val turn1Messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "User message 1. $padding"),
                    AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Assistant response 1"),
                    AgentLoopMessage(id = "u2", role = Role.USER, content = "User message 2. $padding"),
                    AgentLoopMessage(id = "a2", role = Role.ASSISTANT, content = "Assistant response 2"),
                    AgentLoopMessage(id = "u3", role = Role.USER, content = "User message 3"),
                )

            service.completeSingleTurn(
                sessionMessages = turn1Messages,
                systemPrompt = "System Prompt",
                contextSidecars = "Sidecar 1",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            // Second turn: turn history is U1, A1, U2, A2, U3, A3, latest is U4
            val turn2Messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "User message 1. $padding"),
                    AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Assistant response 1"),
                    AgentLoopMessage(id = "u2", role = Role.USER, content = "User message 2. $padding"),
                    AgentLoopMessage(id = "a2", role = Role.ASSISTANT, content = "Assistant response 2"),
                    AgentLoopMessage(id = "u3", role = Role.USER, content = "User message 3"),
                    AgentLoopMessage(id = "a3", role = Role.ASSISTANT, content = "Assistant response 3"),
                    AgentLoopMessage(id = "u4", role = Role.USER, content = "User message 4"),
                )

            // Clear verify records
            io.mockk.clearMocks(sdkClient)
            coEvery { sdkClient.createCachedContent(any(), any(), any(), any(), any()) } returns "cachedContents/mock-cache-123"
            coEvery { sdkClient.generateContentStream(any(), any(), any(), any(), any()) } returns
                GoogleSdkResult(
                    emittedAny = true,
                    functionCall = null,
                    assistantContent = null,
                    finishReason = "STOP",
                )

            service.completeSingleTurn(
                sessionMessages = turn2Messages,
                systemPrompt = "System Prompt",
                contextSidecars = "Sidecar 2",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            // createCachedContent should NOT be called on second turn (cache reused)
            coVerify(exactly = 0) {
                sdkClient.createCachedContent(any(), any(), any(), any(), any())
            }

            // generateContentStream should be called with cachedContents/mock-cache-123 and delta contents: [U3, A3, U4 with sidecars]
            val contentsGenSlot = slot<List<Content>>()
            val configSlot = slot<GenerateContentConfig>()
            coVerify {
                sdkClient.generateContentStream(
                    modelId = "gemini-2.5-flash",
                    contents = capture(contentsGenSlot),
                    config = capture(configSlot),
                    allowToolCall = false,
                    emitEvent = any(),
                )
            }
            assertEquals("cachedContents/mock-cache-123", configSlot.captured.cachedContent().orElse(null))
            assertEquals(3, contentsGenSlot.captured.size)
            // contents must be U3, A3, U4
            val u3Part =
                contentsGenSlot.captured[0]
                    .parts()
                    .orElse(null)
                    ?.firstOrNull()
            val a3Part =
                contentsGenSlot.captured[1]
                    .parts()
                    .orElse(null)
                    ?.firstOrNull()
            val u4Part =
                contentsGenSlot.captured[2]
                    .parts()
                    .orElse(null)
                    ?.firstOrNull()

            assertTrue(u3Part?.text()?.orElse(null)?.contains("User message 3") == true)
            assertTrue(a3Part?.text()?.orElse(null)?.contains("Assistant response 3") == true)
            assertTrue(u4Part?.text()?.orElse(null)?.contains("User message 4") == true)
            assertTrue(u4Part?.text()?.orElse(null)?.contains("Sidecar 2") == true)
        }

    @Test
    fun test_PC_06_GoogleGenAiDeletesOldCacheAndRecreatesOnDeltaExceeded() =
        runTest {
            val sdkClient = mockk<GoogleGenAiSdkClient>()
            val service =
                GoogleInferenceServiceImpl(
                    sdkClient = sdkClient,
                    modelId = "gemini-2.5-flash",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                )

            coEvery { sdkClient.createCachedContent(any(), any(), any(), any(), any()) } returnsMany
                listOf("cachedContents/mock-cache-123", "cachedContents/mock-cache-456")
            coEvery { sdkClient.deleteCachedContent(any()) } returns Unit
            coEvery { sdkClient.generateContentStream(any(), any(), any(), any(), any()) } returns
                GoogleSdkResult(
                    emittedAny = true,
                    functionCall = null,
                    assistantContent = null,
                    finishReason = "STOP",
                )

            val padding = "padding ".repeat(1200)
            // First turn: U1, A1, U2, A2, latest is U3
            val turn1Messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "User message 1. $padding"),
                    AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Assistant response 1"),
                    AgentLoopMessage(id = "u2", role = Role.USER, content = "User message 2. $padding"),
                    AgentLoopMessage(id = "a2", role = Role.ASSISTANT, content = "Assistant response 2"),
                    AgentLoopMessage(id = "u3", role = Role.USER, content = "User message 3"),
                )

            service.completeSingleTurn(
                sessionMessages = turn1Messages,
                systemPrompt = "System Prompt",
                contextSidecars = "Sidecar 1",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            // Clear verify records
            io.mockk.clearMocks(sdkClient)
            coEvery { sdkClient.createCachedContent(any(), any(), any(), any(), any()) } returns "cachedContents/mock-cache-456"
            coEvery { sdkClient.deleteCachedContent(any()) } returns Unit
            coEvery { sdkClient.generateContentStream(any(), any(), any(), any(), any()) } returns
                GoogleSdkResult(
                    emittedAny = true,
                    functionCall = null,
                    assistantContent = null,
                    finishReason = "STOP",
                )

            // Second turn: U1, A1, U2, A2, U3, A3, latest is U4.
            // Delta (U3, A3) is set to be very large (exceeding 1024 tokens).
            val turn2Messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "User message 1. $padding"),
                    AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Assistant response 1"),
                    AgentLoopMessage(id = "u2", role = Role.USER, content = "User message 2. $padding"),
                    AgentLoopMessage(id = "a2", role = Role.ASSISTANT, content = "Assistant response 2"),
                    AgentLoopMessage(id = "u3", role = Role.USER, content = "User message 3. " + "padding ".repeat(1100)), // exceeds 1024 tokens
                    AgentLoopMessage(id = "a3", role = Role.ASSISTANT, content = "Assistant response 3"),
                    AgentLoopMessage(id = "u4", role = Role.USER, content = "User message 4"),
                )

            service.completeSingleTurn(
                sessionMessages = turn2Messages,
                systemPrompt = "System Prompt",
                contextSidecars = "Sidecar 2",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            // Verify old cache was deleted
            coVerify(exactly = 1) {
                sdkClient.deleteCachedContent("cachedContents/mock-cache-123")
            }

            // Verify new cache created with all history [U1, A1, U2, A2, U3, A3]
            val contentsSlot = slot<List<Content>>()
            coVerify {
                sdkClient.createCachedContent(
                    modelId = "gemini-2.5-flash",
                    contents = capture(contentsSlot),
                    systemInstruction = any(),
                    tools = any(),
                    ttlSeconds = 300L,
                )
            }
            assertEquals(6, contentsSlot.captured.size)

            // Verify generateContentStream called with cachedContents/mock-cache-456 and only latest message U4
            val contentsGenSlot = slot<List<Content>>()
            val configSlot = slot<GenerateContentConfig>()
            coVerify {
                sdkClient.generateContentStream(
                    modelId = "gemini-2.5-flash",
                    contents = capture(contentsGenSlot),
                    config = capture(configSlot),
                    allowToolCall = false,
                    emitEvent = any(),
                )
            }
            assertEquals("cachedContents/mock-cache-456", configSlot.captured.cachedContent().orElse(null))
            assertEquals(1, contentsGenSlot.captured.size)
            assertTrue(
                contentsGenSlot.captured[0]
                    .parts()
                    .orElse(null)
                    ?.firstOrNull()
                    ?.text()
                    ?.orElse(null)
                    ?.contains("User message 4") == true,
            )
        }

    @Test
    fun test_PC_07_GoogleGenAiDeletesCachedContentOnSessionClose() =
        runTest {
            val sdkClient = mockk<GoogleGenAiSdkClient>()
            val service =
                GoogleInferenceServiceImpl(
                    sdkClient = sdkClient,
                    modelId = "gemini-2.5-flash",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                )

            coEvery { sdkClient.createCachedContent(any(), any(), any(), any(), any()) } returns "cachedContents/mock-cache-123"
            coEvery { sdkClient.deleteCachedContent(any()) } returns Unit
            coEvery { sdkClient.generateContentStream(any(), any(), any(), any(), any()) } returns
                GoogleSdkResult(
                    emittedAny = true,
                    functionCall = null,
                    assistantContent = null,
                    finishReason = "STOP",
                )

            // Establish a cache first
            val padding = "padding ".repeat(1200)
            val messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "User message 1. $padding"),
                    AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Assistant response 1"),
                    AgentLoopMessage(id = "u2", role = Role.USER, content = "User message 2"),
                )

            service.completeSingleTurn(
                sessionMessages = messages,
                systemPrompt = "System Prompt",
                contextSidecars = "Sidecar 1",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            // When closeSession is called
            service.closeSession()

            // Verify activeCacheName was deleted from server
            coVerify(exactly = 1) {
                sdkClient.deleteCachedContent("cachedContents/mock-cache-123")
            }
        }

    @Test
    fun test_PC_08_GoogleGenAiDoesNotCreateCacheWhenBelowThreshold() =
        runTest {
            val sdkClient = mockk<GoogleGenAiSdkClient>()
            val service =
                GoogleInferenceServiceImpl(
                    sdkClient = sdkClient,
                    modelId = "gemini-2.5-flash",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                )

            coEvery { sdkClient.generateContentStream(any(), any(), any(), any(), any()) } returns
                GoogleSdkResult(
                    emittedAny = true,
                    functionCall = null,
                    assistantContent = null,
                    finishReason = "STOP",
                )

            // Short messages (calculated count below 1024 threshold)
            val messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                    AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Hi"),
                    AgentLoopMessage(id = "u2", role = Role.USER, content = "How are you?"),
                )

            service.completeSingleTurn(
                sessionMessages = messages,
                systemPrompt = "System Prompt",
                contextSidecars = "Volatile sidecar content",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            // verify createCachedContent was NOT called
            coVerify(exactly = 0) {
                sdkClient.createCachedContent(any(), any(), any(), any(), any())
            }

            // verify generateContentStream called with cachedContent = null in GenerateContentConfig and all 3 messages
            val contentsGenSlot = slot<List<Content>>()
            val configSlot = slot<GenerateContentConfig>()
            coVerify {
                sdkClient.generateContentStream(
                    modelId = "gemini-2.5-flash",
                    contents = capture(contentsGenSlot),
                    config = capture(configSlot),
                    allowToolCall = false,
                    emitEvent = any(),
                )
            }
            assertTrue(configSlot.captured.cachedContent().orElse(null) == null)
            assertEquals(3, contentsGenSlot.captured.size)
            val u2Part =
                contentsGenSlot.captured[2]
                    .parts()
                    .orElse(null)
                    ?.firstOrNull()
            assertTrue(u2Part?.text()?.orElse(null)?.contains("How are you?") == true)
            assertTrue(u2Part?.text()?.orElse(null)?.contains("Volatile sidecar content") == true)
        }

    @Test
    @DisplayName("SC-AG-01 first hop suffix-embeds at tail index")
    fun SC_AG_01_anthropicFirstHopSidecarAtFreezeIndex() =
        runTest {
            val client = mockk<AnthropicClient>()
            val messageService = mockk<MessageService>()
            val streamResponse = mockk<com.anthropic.core.http.StreamResponse<RawMessageStreamEvent>>()
            val capturedParams = mutableListOf<MessageCreateParams>()
            val service =
                AnthropicInferenceServiceImpl(
                    client = client,
                    modelId = "claude-3-5-sonnet-20241022",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                )
            every { client.messages() } returns messageService
            every { messageService.createStreaming(any<MessageCreateParams>()) } answers {
                capturedParams.add(invocation.args[0] as MessageCreateParams)
                streamResponse
            }
            every { streamResponse.stream() } answers { java.util.stream.Stream.of(anthropicMessageStopEvent()) }
            every { streamResponse.close() } returns Unit

            val sidecar = """State ledger JSON: {"key":"value"}"""
            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = sidecar,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                        AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Hi"),
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "Follow up"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = sidecar,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            fun anthropicUserText(params: MessageCreateParams, index: Int): String =
                params.messages()[index].content().asBlockParams()[0].asText().text()

            val hop1 = capturedParams.first()
            val hop1UserText = anthropicUserText(hop1, 0)
            assertTrue(hop1UserText.startsWith("Hello"))
            assertTrue(hop1UserText.contains("[Sidecar-"))
            assertTrue(hop1UserText.endsWith("$sidecar]"))
            val frozen = requireNotNull(service.appendOnlyContextForTests()).frozenSidecarsForWireReplay()
            assertEquals(1, frozen.size)
            assertEquals(0, frozen[0].mappedInsertIndex)

            val hop2 = capturedParams[1]
            val hop2FrozenText = anthropicUserText(hop2, 0)
            assertTrue(hop2FrozenText.contains("[Sidecar-"))
            assertTrue(hop2FrozenText.contains("Hello"))
            assertEquals("Follow up", anthropicUserText(hop2, 2))
            assertFalse(anthropicUserText(hop2, 2).contains("[Sidecar-"))
        }

    @Test
    @DisplayName("SC-AG-02 three-hop identical sidecar multi-turn")
    fun SC_AG_02_anthropicThreeHopIdenticalSidecarDedupes() =
        runTest {
            val client = mockk<AnthropicClient>()
            val messageService = mockk<MessageService>()
            val streamResponse = mockk<com.anthropic.core.http.StreamResponse<RawMessageStreamEvent>>()
            val capturedParams = mutableListOf<MessageCreateParams>()
            val service =
                AnthropicInferenceServiceImpl(
                    client = client,
                    modelId = "claude-3-5-sonnet-20241022",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                )
            every { client.messages() } returns messageService
            every { messageService.createStreaming(any<MessageCreateParams>()) } answers {
                capturedParams.add(invocation.args[0] as MessageCreateParams)
                streamResponse
            }
            every { streamResponse.stream() } answers { java.util.stream.Stream.of(anthropicMessageStopEvent()) }
            every { streamResponse.close() } returns Unit

            val sidecar = "Volatile sidecar content"
            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = sidecar,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                        AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Hi"),
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "Explain the plan."),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = sidecar,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                        AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Hi"),
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "Explain the plan."),
                        AgentLoopMessage(id = "a2", role = Role.ASSISTANT, content = "Done"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = sidecar,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            assertEquals(
                1,
                requireNotNull(service.appendOnlyContextForTests()).frozenSidecarsForWireReplay().size,
            )
            fun anthropicUserText(params: MessageCreateParams, index: Int): String =
                params.messages()[index].content().asBlockParams()[0].asText().text()

            val hop2 = capturedParams[1]
            val hop2FrozenText = anthropicUserText(hop2, 0)
            assertTrue(hop2FrozenText.contains("[Sidecar-"))
            assertTrue(hop2FrozenText.contains("Hello"))
            assertEquals("Explain the plan.", anthropicUserText(hop2, 2))
            assertFalse(anthropicUserText(hop2, 2).contains("[Sidecar-"))

            val hop3 = capturedParams[2]
            val hop3FrozenText = anthropicUserText(hop3, 0)
            assertTrue(hop3FrozenText.contains("[Sidecar-"))
            assertFalse(anthropicUserText(hop3, 2).contains("[Sidecar-"))
        }

    @Test
    @DisplayName("SC-AG-03 Google first hop sidecar at freeze index")
    fun SC_AG_03_googleFirstHopSidecarAtFreezeIndex() =
        runTest {
            val sdkClient = mockk<GoogleGenAiSdkClient>()
            val service =
                GoogleInferenceServiceImpl(
                    sdkClient = sdkClient,
                    modelId = "gemini-2.5-flash",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                )
            coEvery { sdkClient.createCachedContent(any(), any(), any(), any(), any()) } returns "cachedContents/mock-cache-123"
            val capturedContents = mutableListOf<List<Content>>()
            coEvery { sdkClient.generateContentStream(any(), any(), any(), any(), any()) } coAnswers {
                @Suppress("UNCHECKED_CAST")
                capturedContents.add(invocation.args[1] as List<Content>)
                GoogleSdkResult(
                    emittedAny = true,
                    functionCall = null,
                    assistantContent = null,
                    finishReason = "STOP",
                )
            }

            val padding = "padding ".repeat(1200)
            val hop1Messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "User message content 1 $padding"),
                    AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Assistant response 1"),
                    AgentLoopMessage(id = "u2", role = Role.USER, content = "User message content 2 $padding"),
                    AgentLoopMessage(id = "a2", role = Role.ASSISTANT, content = "Assistant response 2"),
                    AgentLoopMessage(id = "u3", role = Role.USER, content = "User message content 3"),
                )
            val sidecar = "Sidecar key value json"
            service.completeSingleTurn(
                sessionMessages = hop1Messages,
                systemPrompt = "System instruction prompt",
                contextSidecars = sidecar,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val hop2Messages =
                hop1Messages +
                    listOf(
                        AgentLoopMessage(id = "a3", role = Role.ASSISTANT, content = "Assistant response 3"),
                        AgentLoopMessage(id = "u4", role = Role.USER, content = "User message content 4"),
                    )
            service.completeSingleTurn(
                sessionMessages = hop2Messages,
                systemPrompt = "System instruction prompt",
                contextSidecars = sidecar,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val hop2Contents = capturedContents.last()
            fun contentTextAt(contents: List<Content>, index: Int): String? =
                contents.getOrNull(index)
                    ?.parts()
                    ?.orElse(null)
                    ?.firstOrNull()
                    ?.text()
                    ?.orElse(null)
            val frozen = requireNotNull(service.appendOnlyContextForTests()).frozenSidecarsForWireReplay()
            assertEquals(1, frozen.size)
            assertEquals(4, frozen[0].mappedInsertIndex)
            assertTrue(contentTextAt(hop2Contents, 0)?.contains(sidecar) == true)
            assertTrue(contentTextAt(hop2Contents, 0)?.contains("User message content 3") == true)
            assertEquals("User message content 4", contentTextAt(hop2Contents, 2))
            assertFalse(contentTextAt(hop2Contents, 2)?.contains(sidecar) == true)
        }

    @Test
    @DisplayName("SC-AG-04 two distinct sidecars across turns")
    fun SC_AG_04_googleFrozenSidecarIndexStability() =
        runTest {
            val sdkClient = mockk<GoogleGenAiSdkClient>()
            val service =
                GoogleInferenceServiceImpl(
                    sdkClient = sdkClient,
                    modelId = "gemini-2.5-flash",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                )
            coEvery { sdkClient.createCachedContent(any(), any(), any(), any(), any()) } returns "cachedContents/mock-cache-123"
            val capturedContents = mutableListOf<List<Content>>()
            coEvery { sdkClient.generateContentStream(any(), any(), any(), any(), any()) } coAnswers {
                @Suppress("UNCHECKED_CAST")
                capturedContents.add(invocation.args[1] as List<Content>)
                GoogleSdkResult(
                    emittedAny = true,
                    functionCall = null,
                    assistantContent = null,
                    finishReason = "STOP",
                )
            }

            val padding = "padding ".repeat(1200)
            val hop1Messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "User message 1. $padding"),
                    AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Assistant response 1"),
                    AgentLoopMessage(id = "u2", role = Role.USER, content = "User message 2. $padding"),
                    AgentLoopMessage(id = "a2", role = Role.ASSISTANT, content = "Assistant response 2"),
                    AgentLoopMessage(id = "u3", role = Role.USER, content = "User message 3"),
                )
            service.completeSingleTurn(
                sessionMessages = hop1Messages,
                systemPrompt = "System Prompt",
                contextSidecars = "Sidecar 1",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val hop2Messages =
                hop1Messages +
                    listOf(
                        AgentLoopMessage(id = "a3", role = Role.ASSISTANT, content = "Assistant response 3"),
                        AgentLoopMessage(id = "u4", role = Role.USER, content = "User message 4"),
                    )
            service.completeSingleTurn(
                sessionMessages = hop2Messages,
                systemPrompt = "System Prompt",
                contextSidecars = "Sidecar 2",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            assertEquals(
                2,
                requireNotNull(service.appendOnlyContextForTests()).frozenSidecarsForWireReplay().size,
            )
            val hop2Contents = capturedContents.last()
            fun contentTextAt(contents: List<Content>, index: Int): String? =
                contents.getOrNull(index)
                    ?.parts()
                    ?.orElse(null)
                    ?.firstOrNull()
                    ?.text()
                    ?.orElse(null)
            assertTrue(contentTextAt(hop2Contents, 0)?.contains("Sidecar 1") == true)
            assertFalse(contentTextAt(hop2Contents, 0)?.contains("Sidecar 2") == true)
            assertTrue(contentTextAt(hop2Contents, 2)?.contains("Sidecar 2") == true)
        }

    @Test
    @DisplayName("SC-AG-05 frozen at index 0 not suffix-embedded at index 2")
    fun SC_AG_05_indexInterleavedReplayNotLatestUserOnly() =
        runTest {
            val client = mockk<AnthropicClient>()
            val messageService = mockk<MessageService>()
            val streamResponse = mockk<com.anthropic.core.http.StreamResponse<RawMessageStreamEvent>>()
            val capturedParams = mutableListOf<MessageCreateParams>()
            val service =
                AnthropicInferenceServiceImpl(
                    client = client,
                    modelId = "claude-3-5-sonnet-20241022",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                )
            every { client.messages() } returns messageService
            every { messageService.createStreaming(any<MessageCreateParams>()) } answers {
                capturedParams.add(invocation.args[0] as MessageCreateParams)
                streamResponse
            }
            every { streamResponse.stream() } answers { java.util.stream.Stream.of(anthropicMessageStopEvent()) }
            every { streamResponse.close() } returns Unit

            val frozenSidecar = "FROZEN_A"
            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u0", role = Role.USER, content = "First"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = frozenSidecar,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u0", role = Role.USER, content = "First"),
                        AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Hi"),
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "Second"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = frozenSidecar,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            fun anthropicUserText(params: MessageCreateParams, index: Int): String =
                params.messages()[index].content().asBlockParams()[0].asText().text()

            val hop2 = capturedParams[1]
            val hop2FrozenText = anthropicUserText(hop2, 0)
            assertTrue(hop2FrozenText.startsWith("First"))
            assertTrue(hop2FrozenText.contains("[Sidecar-"))
            assertTrue(hop2FrozenText.contains(frozenSidecar))
            assertEquals("Second", anthropicUserText(hop2, 2))
            assertFalse(anthropicUserText(hop2, 2).contains("[Sidecar-"))
            assertEquals(
                1,
                requireNotNull(service.appendOnlyContextForTests()).frozenSidecarsForWireReplay().size,
            )
        }
}
