package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import com.google.genai.types.Content
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

class GoogleInferenceServiceImplSidecarWireTest {
    private val loggingPort = FakeLoggingPort()

    @Test
    @DisplayName("S-GOOG-01 live tail suffix wire shape in tail Content text part")
    fun S_GOOG_01_live_tail_suffix_wire_shape_in_tail_content_text_part() =
        runTest {
            val harness = createHarness()
            runTurn1SingleUser(harness.service)

            val tailText = tailFirstTextPart(harness.contents)
            val uuid1 = extractSidecarUuid(tailText)

            assertTrue(UUID_REGEX.matches(uuid1))
            assertEquals("Hello Turn 1\n\n[Sidecar-$uuid1: Volatile Sidecar 1]", tailText)
            assertTrue(tailText.endsWith(": Volatile Sidecar 1]"))
            assertTrue(tailText.indexOf("[Sidecar-") > 0)
        }

    @Test
    @DisplayName("S-GOOG-02 frozen replay byte-identity in text part across turns")
    fun S_GOOG_02_frozen_replay_byte_identity_in_text_part_across_turns() =
        runTest {
            val harness = createHarness()
            runTurn1SingleUser(harness.service)
            val turn1Contents = harness.contents.toList()
            val turn1TailText = tailFirstTextPart(turn1Contents)
            val uuid1 = extractSidecarUuid(turn1TailText)

            runTurn2DistinctSidecars(harness.service)
            val turn2Contents = harness.contents.toList()
            val u1Text = firstTextPart(findContentStartingWith(turn2Contents, "Hello Turn 1"))

            assertEquals(turn1TailText, u1Text)
            assertEquals("Hello Turn 1\n\n[Sidecar-$uuid1: Volatile Sidecar 1]", u1Text)
        }

    @Test
    @DisplayName("S-GOOG-03 deduped turn omits live suffix frozen anchor replays unchanged")
    fun S_GOOG_03_deduped_turn_omits_live_suffix_frozen_anchor_replays_unchanged() =
        runTest {
            val harness = createHarness()
            runTurn1SingleUser(harness.service)
            val uuid1 = extractSidecarUuid(tailFirstTextPart(harness.contents))

            runTurn2DedupedSidecars(harness.service)
            val turn2Contents = harness.contents.toList()
            val u1Text = firstTextPart(findContentStartingWith(turn2Contents, "Hello Turn 1"))
            val tailText = tailFirstTextPart(turn2Contents)

            assertEquals("Hello Turn 1\n\n[Sidecar-$uuid1: Volatile Sidecar 1]", u1Text)
            assertEquals("Hello Turn 2", tailText)
            assertFalse(tailText.contains("[Sidecar-"))
        }

    @Test
    @DisplayName("S-GOOG-04 image-bearing user turn suffix in leading text part")
    fun S_GOOG_04_image_bearing_user_turn_suffix_in_leading_text_part() =
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

            val parts = harness.contents.last().parts().get()
            assertEquals(2, parts.size)

            val text = parts[0].text().get()
            val uuid = extractSidecarUuid(text)
            assertTrue(UUID_REGEX.matches(uuid))
            assertEquals("Describe this\n\n[Sidecar-$uuid: Volatile Sidecar 1]", text)
            assertTrue(parts[1].inlineData().isPresent)
        }

    @Test
    @DisplayName("S-GOOG-05 live to frozen markerId continuity uuid equality across turns")
    fun S_GOOG_05_live_to_frozen_markerId_continuity() =
        runTest {
            val harness = createHarness()
            runTurn1SingleUser(harness.service)
            val uuid1 = extractSidecarUuid(tailFirstTextPart(harness.contents))

            val frozenAfterTurn1 = harness.service.appendOnlyContextForTests()!!.frozenSidecarsForWireReplay()
            assertEquals(1, frozenAfterTurn1.size)
            assertEquals(uuid1, frozenAfterTurn1.single().markerId)

            runTurn2DistinctSidecars(harness.service)
            val turn2Contents = harness.contents.toList()
            val frozenUuidTurn2 =
                extractSidecarUuid(
                    firstTextPart(findContentStartingWith(turn2Contents, "Hello Turn 1")),
                )

            assertEquals(uuid1, frozenUuidTurn2)
        }

    @Test
    @DisplayName("S-GOOG-06 mutation-defense suffix-after-content and uuid continuity")
    fun S_GOOG_06_mutation_defense_suffix_after_content_and_uuid_continuity() =
        runTest {
            val harness = createHarness()
            runTurn1SingleUser(harness.service)
            val turn1TailText = tailFirstTextPart(harness.contents)
            val uuid1 = extractSidecarUuid(turn1TailText)

            assertFalse(turn1TailText.startsWith("Volatile Sidecar 1"))
            assertTrue(turn1TailText.startsWith("Hello Turn 1"))
            assertTrue(turn1TailText.indexOf("[Sidecar-") > 0)

            runTurn2DistinctSidecars(harness.service)
            val turn2Contents = harness.contents.toList()
            val frozenUuidTurn2 =
                extractSidecarUuid(
                    firstTextPart(findContentStartingWith(turn2Contents, "Hello Turn 1")),
                )

            assertEquals(uuid1, frozenUuidTurn2)
        }

    private fun createHarness(): GoogleSidecarHarness {
        val sdkClient = FakeGoogleGenAiSdkClient()
        sdkClient.streamResult =
            GoogleSdkResult(
                emittedAny = true,
                functionCall = null,
                assistantContent = null,
                finishReason = "STOP",
            )

        val service =
            GoogleInferenceServiceImpl(
                sdkClient = sdkClient,
                modelId = "gemini-2.5-flash",
                modelType = ModelType.MAIN,
                loggingPort = loggingPort,
                toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                toolStreamGatePort = FakeToolStreamGatePort(),
            )
        return GoogleSidecarHarness(service, sdkClient)
    }

    private suspend fun runTurn1SingleUser(service: GoogleInferenceServiceImpl) {
        service.completeSingleTurn(
            sessionMessages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello Turn 1"),
                ),
            systemPrompt = "System Instruction",
            contextSidecars = "Volatile Sidecar 1",
            availableTools = emptyList(),
            allowToolCall = false,
            onTextToken = {},
            onThinkingToken = {},
            options = GenerationOptions(reasoningBudget = 0),
        )
    }

    private suspend fun runTurn2DistinctSidecars(service: GoogleInferenceServiceImpl) {
        service.completeSingleTurn(
            sessionMessages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello Turn 1"),
                    AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Reply Turn 1"),
                    AgentLoopMessage(id = "u2", role = Role.USER, content = "Hello Turn 2"),
                ),
            systemPrompt = "System Instruction",
            contextSidecars = "Volatile Sidecar 2",
            availableTools = emptyList(),
            allowToolCall = false,
            onTextToken = {},
            onThinkingToken = {},
            options = GenerationOptions(reasoningBudget = 0),
        )
    }

    private suspend fun runTurn2DedupedSidecars(service: GoogleInferenceServiceImpl) {
        service.completeSingleTurn(
            sessionMessages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello Turn 1"),
                    AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Reply Turn 1"),
                    AgentLoopMessage(id = "u2", role = Role.USER, content = "Hello Turn 2"),
                ),
            systemPrompt = "System Instruction",
            contextSidecars = "Volatile Sidecar 1",
            availableTools = emptyList(),
            allowToolCall = false,
            onTextToken = {},
            onThinkingToken = {},
            options = GenerationOptions(reasoningBudget = 0),
        )
    }

    private fun tailFirstTextPart(contents: List<Content>): String =
        contents.last().parts().get()[0].text().get()

    private fun firstTextPart(content: Content): String =
        content.parts().get().first { it.text().isPresent }.text().get()

    private fun findContentStartingWith(contents: List<Content>, prefix: String): Content =
        contents.first { content ->
            content.parts().get().any { part ->
                part.text().orElse("").startsWith(prefix)
            }
        }

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

    private data class GoogleSidecarHarness(
        val service: GoogleInferenceServiceImpl,
        val sdkClient: FakeGoogleGenAiSdkClient,
    ) {
        val contents: List<Content> get() = sdkClient.lastGenerateContents!!
    }

    private companion object {
        private val SIDEcar_UUID_EXTRACT = Regex("\\[Sidecar-([0-9a-f-]{36}): ")
        private val UUID_REGEX =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    }
}
