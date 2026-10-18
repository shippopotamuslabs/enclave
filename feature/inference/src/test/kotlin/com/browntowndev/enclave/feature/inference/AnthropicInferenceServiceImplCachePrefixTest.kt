// MockK is used only for Anthropic Java SDK types (com.anthropic.*), a third-party SDK boundary.
@file:Suppress("ForbiddenMockKInTests")

package com.browntowndev.enclave.feature.inference

import com.anthropic.client.AnthropicClient
import com.anthropic.core.http.StreamResponse
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.RawMessageStreamEvent
import com.anthropic.services.blocking.MessageService
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import java.util.stream.Stream

class AnthropicInferenceServiceImplCachePrefixTest {
    private val loggingPort = FakeLoggingPort()

    @Test
    @DisplayName("S-ANTH-01 live tail suffix wire shape Turn 1")
    fun S_ANTH_01_live_tail_suffix_wire_shape_turn_1() =
        runTest {
            val harness = createHarness()
            runTurn1(harness.service)

            val params1 = harness.capturedParams.single()
            val u1Text = userText(params1, 0)
            val uuid1 = extractSidecarUuid(u1Text)

            assertTrue(UUID_REGEX.matches(uuid1))
            assertEquals("Hello Turn 1\n\n[Sidecar-$uuid1: Volatile Sidecar 1]", u1Text)
            assertTrue(u1Text.startsWith("Hello Turn 1"))
            assertTrue(u1Text.endsWith(": Volatile Sidecar 1]"))
            assertTrue(u1Text.indexOf("[Sidecar-") > 0)
            assertEquals("Reply Turn 1", userText(params1, 1))
        }

    @Test
    @DisplayName("S-ANTH-02 frozen replay byte-identity and live to frozen markerId continuity Turn 2")
    fun S_ANTH_02_frozen_replay_byte_identity_and_markerId_continuity() =
        runTest {
            val harness = createHarness()
            runTurn1(harness.service)
            val params1 = harness.capturedParams[0]
            val turn1U1Text = userText(params1, 0)
            val uuid1 = extractSidecarUuid(turn1U1Text)

            runTurn2DistinctSidecars(harness.service)
            val params2 = harness.capturedParams[1]

            assertEquals(turn1U1Text, userText(params2, 0))
            assertEquals("Hello Turn 1\n\n[Sidecar-$uuid1: Volatile Sidecar 1]", userText(params2, 0))
        }

    @Test
    @DisplayName("S-ANTH-03 metadata-before-sidecar ordering")
    fun S_ANTH_03_metadata_before_sidecar_ordering() =
        runTest {
            val harness = createHarness()
            harness.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(
                            id = "u1",
                            role = Role.USER,
                            content = "Hello Turn 1\n\n[Metadata: Sent at noon]",
                        ),
                    ),
                systemPrompt = "System Instruction",
                contextSidecars = "Volatile Sidecar 1",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val params = harness.capturedParams.single()
            val u1Text = userText(params, 0)
            val uuid = extractSidecarUuid(u1Text)

            assertTrue(UUID_REGEX.matches(uuid))
            assertEquals(
                "Hello Turn 1\n\n[Metadata: Sent at noon]\n\n[Sidecar-$uuid: Volatile Sidecar 1]",
                u1Text,
            )
            assertTrue(u1Text.indexOf("[Metadata:") >= 0)
            assertTrue(u1Text.indexOf("[Metadata:") < u1Text.indexOf("[Sidecar-"))
            assertTrue(u1Text.endsWith(": Volatile Sidecar 1]"))
        }

    @Test
    @DisplayName("S-ANTH-04 Turn-2 live tail gets a new distinct uuid")
    fun S_ANTH_04_turn_2_live_tail_gets_new_distinct_uuid() =
        runTest {
            val harness = createHarness()
            runTurn1(harness.service)
            val uuid1 = extractSidecarUuid(userText(harness.capturedParams[0], 0))

            runTurn2DistinctSidecars(harness.service)
            val params2 = harness.capturedParams[1]
            val u2Text = userText(params2, 2)
            val uuid2 = extractSidecarUuid(u2Text)

            assertTrue(UUID_REGEX.matches(uuid2))
            assertNotEquals(uuid1, uuid2)
            assertEquals("Hello Turn 2\n\n[Sidecar-$uuid2: Volatile Sidecar 2]", u2Text)
            assertEquals("Reply Turn 2", userText(params2, 3))
        }

    @Test
    @DisplayName("S-ANTH-05 cache-breakpoint placement unchanged")
    fun S_ANTH_05_cache_breakpoint_placement_unchanged() =
        runTest {
            val harness = createHarness()
            runTurn1(harness.service)
            runTurn2DistinctSidecars(harness.service)

            val params1 = harness.capturedParams[0]
            val params2 = harness.capturedParams[1]

            assertTrue(
                params1.system().get().asTextBlockParams().single().cacheControl().isPresent,
            )
            assertTrue(
                params2.system().get().asTextBlockParams().single().cacheControl().isPresent,
            )

            assertTrue(textBlock(params1, 0).cacheControl().isPresent)
            assertFalse(textBlock(params1, 1).cacheControl().isPresent)

            assertFalse(textBlock(params2, 0).cacheControl().isPresent)
            assertFalse(textBlock(params2, 1).cacheControl().isPresent)
            assertTrue(textBlock(params2, 2).cacheControl().isPresent)
            assertFalse(textBlock(params2, 3).cacheControl().isPresent)
        }

    @Test
    @DisplayName("S-ANTH-06 image-bearing user turn suffix in trailing text block")
    fun S_ANTH_06_image_bearing_user_turn_suffix_in_trailing_text_block() =
        runTest {
            val harness = createHarness()
            harness.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Describe this"),
                    ),
                systemPrompt = "System Instruction",
                contextSidecars = "Volatile Sidecar 1",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        imageUris = listOf(createTempImageUri()),
                    ),
            )

            val blocks = userBlocks(harness.capturedParams.single(), 0)
            assertEquals(2, blocks.size)
            assertTrue(blocks[0].isImage())
            assertTrue(blocks[1].isText())

            val text = blocks[1].asText().text()
            val uuid = extractSidecarUuid(text)
            assertTrue(UUID_REGEX.matches(uuid))
            assertEquals("Describe this\n\n[Sidecar-$uuid: Volatile Sidecar 1]", text)
        }

    @Test
    @DisplayName("S-ANTH-07 deduped turn omits live suffix frozen anchor replays unchanged")
    fun S_ANTH_07_deduped_turn_omits_live_suffix_frozen_anchor_replays_unchanged() =
        runTest {
            val harness = createHarness()
            runTurn1(harness.service)
            val uuid1 = extractSidecarUuid(userText(harness.capturedParams[0], 0))

            runTurn2DedupedSidecars(harness.service)
            val params2 = harness.capturedParams[1]

            assertEquals(
                "Hello Turn 1\n\n[Sidecar-$uuid1: Volatile Sidecar 1]",
                userText(params2, 0),
            )
            assertEquals("Hello Turn 2", userText(params2, 2))
            assertFalse(userText(params2, 2).contains("[Sidecar-"))
        }

    @Test
    @DisplayName("S-ANTH-08 mutation-defense suffix-after-content and uuid continuity")
    fun S_ANTH_08_mutation_defense_suffix_after_content_and_uuid_continuity() =
        runTest {
            val harness = createHarness()
            runTurn1(harness.service)
            val params1 = harness.capturedParams[0]
            val turn1U1Text = userText(params1, 0)
            val uuid1 = extractSidecarUuid(turn1U1Text)

            assertFalse(turn1U1Text.startsWith("Volatile Sidecar 1"))
            assertTrue(turn1U1Text.startsWith("Hello Turn 1"))
            assertTrue(turn1U1Text.indexOf("[Sidecar-") > 0)

            runTurn2DistinctSidecars(harness.service)
            val params2 = harness.capturedParams[1]
            val frozenUuidTurn2 = extractSidecarUuid(userText(params2, 0))

            assertEquals(uuid1, frozenUuidTurn2)
        }

    private fun createHarness(): AnthropicHarness {
        val client = mockk<AnthropicClient>()
        val messageService = mockk<MessageService>()
        val streamResponse = mockk<StreamResponse<RawMessageStreamEvent>>()
        val capturedParams = mutableListOf<MessageCreateParams>()

        every { client.messages() } returns messageService
        every { messageService.createStreaming(any<MessageCreateParams>()) } answers {
            capturedParams.add(firstArg())
            streamResponse
        }
        every { streamResponse.stream() } answers {
            Stream.of(anthropicMessageStopEvent())
        }
        every { streamResponse.close() } returns Unit

        val service =
            AnthropicInferenceServiceImpl(
                client = client,
                modelId = "claude-3-5-sonnet-20241022",
                modelType = ModelType.MAIN,
                loggingPort = loggingPort,
                toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                toolStreamGatePort = FakeToolStreamGatePort(),
            )
        return AnthropicHarness(service, capturedParams)
    }

    private suspend fun runTurn1(service: AnthropicInferenceServiceImpl) {
        service.completeSingleTurn(
            sessionMessages = turn1Messages(),
            systemPrompt = "System Instruction",
            contextSidecars = "Volatile Sidecar 1",
            availableTools = emptyList(),
            allowToolCall = false,
            onTextToken = {},
            onThinkingToken = {},
            options = GenerationOptions(reasoningBudget = 0),
        )
    }

    private suspend fun runTurn2DistinctSidecars(service: AnthropicInferenceServiceImpl) {
        service.completeSingleTurn(
            sessionMessages = turn2Messages(),
            systemPrompt = "System Instruction",
            contextSidecars = "Volatile Sidecar 2",
            availableTools = emptyList(),
            allowToolCall = false,
            onTextToken = {},
            onThinkingToken = {},
            options = GenerationOptions(reasoningBudget = 0),
        )
    }

    private suspend fun runTurn2DedupedSidecars(service: AnthropicInferenceServiceImpl) {
        service.completeSingleTurn(
            sessionMessages = turn2Messages(),
            systemPrompt = "System Instruction",
            contextSidecars = "Volatile Sidecar 1",
            availableTools = emptyList(),
            allowToolCall = false,
            onTextToken = {},
            onThinkingToken = {},
            options = GenerationOptions(reasoningBudget = 0),
        )
    }

    private fun turn1Messages(): List<AgentLoopMessage> =
        listOf(
            AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello Turn 1"),
            AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Reply Turn 1"),
        )

    private fun turn2Messages(): List<AgentLoopMessage> =
        listOf(
            AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello Turn 1"),
            AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Reply Turn 1"),
            AgentLoopMessage(id = "u2", role = Role.USER, content = "Hello Turn 2"),
            AgentLoopMessage(id = "a2", role = Role.ASSISTANT, content = "Reply Turn 2"),
        )

    private fun userText(params: MessageCreateParams, index: Int): String =
        textBlock(params, index).text()

    private fun textBlock(params: MessageCreateParams, index: Int) =
        params.messages()[index]
            .content()
            .asBlockParams()
            .single()
            .asText()

    private fun userBlocks(params: MessageCreateParams, index: Int): List<ContentBlockParam> =
        params.messages()[index].content().asBlockParams()

    private fun extractSidecarUuid(text: String): String {
        val match =
            requireNotNull(SIDEcar_UUID_EXTRACT.find(text)) {
                "Expected [Sidecar-<uuid>: ...] suffix in text but got: $text"
            }
        return match.groupValues[1]
    }

    private fun createTempImageUri(): String {
        val file = File.createTempFile("sidecar-image", ".jpg")
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        return file.toURI().toString()
    }

    private data class AnthropicHarness(
        val service: AnthropicInferenceServiceImpl,
        val capturedParams: MutableList<MessageCreateParams>,
    )

    private companion object {
        private val SIDEcar_UUID_EXTRACT = Regex("\\[Sidecar-([0-9a-f-]{36}): ")
        private val UUID_REGEX =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    }
}
