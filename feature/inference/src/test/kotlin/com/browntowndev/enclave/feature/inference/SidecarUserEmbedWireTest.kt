package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import com.browntowndev.enclave.feature.inference.openai.StreamedOpenAiResponse
import com.openai.client.OpenAIClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

class SidecarUserEmbedWireTest {
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    @Test
    @DisplayName("S-WIRE-01 single user message with live sidecar embeds as user-content suffix")
    fun S_WIRE_01_single_user_live_sidecar_embeds_as_suffix() = runTest {
        val captured = captureOpenAiParams(provider = "OPENAI", modelId = "gpt-4o")
        captured.service.completeSingleTurn(
            sessionMessages = listOf(AgentLoopMessage(id = "msg1", role = Role.USER, content = "Hello")),
            systemPrompt = "You are a helpful assistant.",
            contextSidecars = """State ledger JSON: {"key":"value"}""",
            availableTools = emptyList(),
            allowToolCall = false,
            onTextToken = {},
            onThinkingToken = {},
            options = GenerationOptions(reasoningBudget = 0),
        )
        val messages = captured.params.single().messages()
        assertEquals(2, messages.size)
        assertTrue(messages[0].isSystem())
        assertEquals("You are a helpful assistant.", messages[0].asSystem().content().asText())
        assertTrue(messages[1].isUser())
        val userText = messages[1].asUser().content().asText()
        assertTrue(userText.startsWith("Hello"))
        assertTrue(userText.contains("[Sidecar-"))
        assertTrue(userText.contains("""State ledger JSON: {"key":"value"}"""))
        assertFalse(messages.drop(1).any { it.isSystem() })
    }

    @Test
    @DisplayName("S-WIRE-02 frozen sidecar replays byte-identically at original user anchor")
    fun S_WIRE_02_frozen_replay_byte_identical() = runTest {
        val captured = captureOpenAiParams(provider = "XAI", modelId = "grok-4.3")
        val fixture = toolLoopFixture()
        captured.service.completeSingleTurn(
            sessionMessages = listOf(fixture.userMessage),
            systemPrompt = "You are a helpful assistant.",
            contextSidecars = """State ledger JSON: {"turn":1}""",
            availableTools = emptyList(),
            allowToolCall = true,
            onTextToken = {},
            onThinkingToken = {},
            options = GenerationOptions(reasoningBudget = 0),
        )
        val firstUserText = captured.params.first().messages()
            .first { it.isUser() }.asUser().content().asText()

        captured.service.completeSingleTurn(
            sessionMessages = listOf(fixture.userMessage, fixture.assistantWithTool, fixture.toolResult),
            systemPrompt = "You are a helpful assistant.",
            contextSidecars = """State ledger JSON: {"turn":2}""",
            availableTools = emptyList(),
            allowToolCall = true,
            onTextToken = {},
            onThinkingToken = {},
            options = GenerationOptions(reasoningBudget = 0),
        )
        val secondUserText = captured.params.last().messages()
            .first { it.isUser() }.asUser().content().asText()
        // The frozen turn-1 sidecar must replay byte-identically. The live
        // turn-2 sidecar may also be appended on this message when the tail
        // is a tool result (trailing-anchor case); verify the frozen block
        // is present and unchanged, not that the entire message is identical.
        assertTrue(firstUserText.contains("[Sidecar-"))
        assertTrue(firstUserText.contains("""State ledger JSON: {"turn":1}"""))
        val frozenBlock = firstUserText.substring(firstUserText.indexOf("[Sidecar-"))
        assertTrue(
            secondUserText.contains(frozenBlock),
            "frozen turn-1 sidecar block must replay byte-identically: $frozenBlock",
        )
    }

    @Test
    @DisplayName("S-WIRE-03 live sidecar ships only on the tail user message")
    fun S_WIRE_03_live_sidecar_on_tail_only() = runTest {
        val captured = captureOpenAiParams(provider = "OPENAI", modelId = "gpt-4o")
        captured.service.completeSingleTurn(
            sessionMessages = listOf(
                AgentLoopMessage(id = "u1", role = Role.USER, content = "First"),
                AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Ack"),
                AgentLoopMessage(id = "u2", role = Role.USER, content = "Second"),
            ),
            systemPrompt = "You are a helpful assistant.",
            contextSidecars = """State ledger JSON: {"k":1}""",
            availableTools = emptyList(),
            allowToolCall = false,
            onTextToken = {},
            onThinkingToken = {},
            options = GenerationOptions(reasoningBudget = 0),
        )
        val messages = captured.params.single().messages()
        val userMessagesWithSidecar = messages.filter { it.isUser() }
            .filter { it.asUser().content().asText().contains("[Sidecar-") }
        assertEquals(1, userMessagesWithSidecar.size)
        assertTrue(userMessagesWithSidecar.single().asUser().content().asText().startsWith("Second"))
        val firstUser = messages.first { it.isUser() }
        assertFalse(firstUser.asUser().content().asText().contains("[Sidecar-"))
    }

    @Test
    @DisplayName("S-WIRE-04 deduped turn ships no live suffix; frozen anchors unchanged")
    fun S_WIRE_04_deduped_no_live_suffix() = runTest {
        val captured = captureOpenAiParams(provider = "OPENAI", modelId = "gpt-4o")
        val sidecar = """State ledger JSON: {"turn":1}"""
        captured.service.completeSingleTurn(
            sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello")),
            systemPrompt = "You are a helpful assistant.",
            contextSidecars = sidecar,
            availableTools = emptyList(),
            allowToolCall = false,
            onTextToken = {},
            onThinkingToken = {},
            options = GenerationOptions(reasoningBudget = 0),
        )
        captured.service.completeSingleTurn(
            sessionMessages = listOf(
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
        val context = captured.service.appendOnlyContextForTests()
        assertEquals(1, context!!.frozenSidecarsForWireReplay().size)
    }

    @Test
    @DisplayName("S-WIRE-05 exactly one role system message on the wire")
    fun S_WIRE_05_exactly_one_system_message() = runTest {
        val captured = captureOpenAiParams(provider = "OPENAI", modelId = "gpt-4o")
        captured.service.completeSingleTurn(
            sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello")),
            systemPrompt = "You are a helpful assistant.",
            contextSidecars = """State ledger JSON: {"k":1}""",
            availableTools = emptyList(),
            allowToolCall = false,
            onTextToken = {},
            onThinkingToken = {},
            options = GenerationOptions(reasoningBudget = 0),
        )
        val messages = captured.params.single().messages()
        val systemMessages = messages.filter { it.isSystem() }
        assertEquals(1, systemMessages.size)
        assertEquals("You are a helpful assistant.", systemMessages.single().asSystem().content().asText())
    }

    @Test
    @DisplayName("S-WIRE-08 trailing-index frozen sidecar attaches to last user message")
    fun S_WIRE_08_trailing_frozen_attaches_to_last_user() = runTest {
        val captured = captureOpenAiParams(provider = "XAI", modelId = "grok-4.3")
        val fixture = toolLoopFixture()
        captured.service.completeSingleTurn(
            sessionMessages = listOf(fixture.userMessage),
            systemPrompt = "You are a helpful assistant.",
            contextSidecars = """State ledger JSON: {"turn":1}""",
            availableTools = emptyList(),
            allowToolCall = true,
            onTextToken = {},
            onThinkingToken = {},
            options = GenerationOptions(reasoningBudget = 0),
        )
        captured.service.completeSingleTurn(
            sessionMessages = listOf(fixture.userMessage, fixture.assistantWithTool, fixture.toolResult),
            systemPrompt = "You are a helpful assistant.",
            contextSidecars = """State ledger JSON: {"turn":2}""",
            availableTools = emptyList(),
            allowToolCall = false,
            onTextToken = {},
            onThinkingToken = {},
            options = GenerationOptions(reasoningBudget = 0),
        )
        val messages = captured.params.last().messages()
        val lastUser = messages.last { it.isUser() }
        assertTrue(lastUser.asUser().content().asText().contains("[Sidecar-"))
    }

    @Test
    @DisplayName("S-WIRE-06 sidecar suffix appears after Metadata when metadata is present")
    fun S_WIRE_06_sidecar_after_metadata() = runTest {
        val captured = captureOpenAiParams(provider = "OPENAI", modelId = "gpt-4o")
        val userContentWithMetadata = "Hello\n\n[Metadata: Sent at Thursday, June 18, 2026 at 3:45 PM]"
        captured.service.completeSingleTurn(
            sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = userContentWithMetadata)),
            systemPrompt = "You are a helpful assistant.",
            contextSidecars = """State ledger JSON: {"k":1}""",
            availableTools = emptyList(),
            allowToolCall = false,
            onTextToken = {},
            onThinkingToken = {},
            options = GenerationOptions(reasoningBudget = 0),
        )
        val userText = captured.params.single().messages()
            .first { it.isUser() }.asUser().content().asText()
        val metadataIdx = userText.indexOf("[Metadata:")
        val sidecarIdx = userText.indexOf("[Sidecar-")
        assertTrue(metadataIdx in 0 until sidecarIdx, "Metadata must appear before Sidecar")
    }

    @Test
    @DisplayName("S-WIRE-07 image-bearing user message keeps sidecar in first text content part")
    fun S_WIRE_07_image_bearing_sidecar_in_first_text_part() = runTest {
        val captured = captureOpenAiParams(provider = "OPENAI", modelId = "gpt-4o")
        val imageFile = createTempPngFile()
        captured.service.completeSingleTurn(
            sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "Describe this image")),
            systemPrompt = "You are a helpful assistant.",
            contextSidecars = """State ledger JSON: {"k":1}""",
            availableTools = emptyList(),
            allowToolCall = false,
            onTextToken = {},
            onThinkingToken = {},
            options = GenerationOptions(reasoningBudget = 0, imageUris = listOf(imageFile.toURI().toString())),
        )
        val userMsg = captured.params.single().messages().first { it.isUser() }
        val parts = userMsg.asUser().content().asArrayOfContentParts()
        assertTrue(parts.first().isText(), "first part must be text")
        val firstText = parts.first().asText().text()
        assertTrue(firstText.contains("[Sidecar-"), "sidecar must be in first text part")
        assertTrue(firstText.contains("""State ledger JSON: {"k":1}"""))
        assertTrue(parts.any { it.isImageUrl() }, "an image part must be present")
        assertEquals(2, parts.size, "no extra text part added for sidecar")
    }

    private data class ToolLoopFixture(
        val userMessage: AgentLoopMessage,
        val assistantWithTool: AgentLoopMessage,
        val toolResult: AgentLoopMessage,
    )

    private fun toolLoopFixture(): ToolLoopFixture =
        ToolLoopFixture(
            userMessage = AgentLoopMessage(id = "u1", role = Role.USER, content = "Search for cats"),
            assistantWithTool = AgentLoopMessage(
                id = "a1",
                role = Role.ASSISTANT,
                content = "",
                metadataJson = """
                    {"raw_tool_calls":[{"id":"call_1","name":"web_search","arguments_json":"{\"query\":\"cats\"}"}]}
                """.trimIndent(),
            ),
            toolResult = AgentLoopMessage(
                id = "t1",
                role = Role.USER,
                content = """{"results":[]}""",
                metadataJson = """{"call_id":"call_1"}""",
            ),
        )

    private data class CapturedOpenAiParams(
        val service: BaseOpenAiSdkInferenceService,
        val params: MutableList<ChatCompletionCreateParams>,
    )

    private fun captureOpenAiParams(provider: String, modelId: String): CapturedOpenAiParams {
        val capturedParams = mutableListOf<ChatCompletionCreateParams>()
        val service = object : BaseOpenAiSdkInferenceService(
            client = mockk<OpenAIClient>(),
            modelId = modelId,
            provider = provider,
            modelType = ModelType.FAST,
            loggingPort = loggingPort,
            toolStreamGatePort = FakeToolStreamGatePort(),
        ) {
            override val tag: String = "SidecarUserEmbedWireTest"

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
                    assistantMessageText = "done",
                )
            }
        }
        return CapturedOpenAiParams(service = service, params = capturedParams)
    }

    private fun createTempPngFile(): File {
        val file = File.createTempFile("sidecar_test_image", ".png")
        val pngSignature =
            intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A).map { it.toByte() }.toByteArray()
        file.writeBytes(pngSignature + ByteArray(32))
        return file
    }
}