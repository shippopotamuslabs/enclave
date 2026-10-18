package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.feature.inference.appendonly.FrozenSidecarSnapshot
import com.browntowndev.enclave.feature.inference.appendonly.ResolvedSidecar
import com.openai.models.responses.ResponseInputImage
import com.openai.models.responses.ResponseInputItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

class SidecarResponsesEmbedTest {
    private val loggingPort = FakeLoggingPort()

    private fun mapSession(messages: List<AgentLoopMessage>): List<ResponseWireSegment> =
        AgentLoopOpenAiResponseMapper.mapSession(
            sessionMessages = messages,
            imageUris = emptyList(),
            imageTargetIndex = -1,
            loggingPort = loggingPort,
            tag = "test",
            modelId = "gpt-4o",
        ).segments

    @Test
    @DisplayName("S-RESP-01 Responses path emits no sidecar SystemText segments")
    fun S_RESP_01_no_sidecar_system_text() {
        val mappedSegments = mapSession(listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello")))
        val segments = OpenAiCompatibleResponseSidecarPlacement.buildSegmentsWithSidecars(
            mappedSegments = mappedSegments,
            frozenSidecars = emptyList(),
            preRetrievalSidecar = null,
            preRetrievalInsertIndex = null,
            currentSidecar = ResolvedSidecar(text = """State ledger JSON: {"k":1}""", markerId = "11111111-2222-3333-4444-555555555555"),
            tailInsertIndex = 0,
            prependedSystemPrompt = "You are a helpful assistant.",
        )
        val sidecarSystemTexts = segments.filterIsInstance<ResponseWireSegment.SystemText>()
            .map { it.text }
            .filter { it.startsWith("State ledger JSON:") || it.startsWith("Pre-retrieval context:") || it.startsWith("[Sidecar-") }
        assertTrue(sidecarSystemTexts.isEmpty(), "no sidecar SystemText segments must be emitted")
    }

    @Test
    @DisplayName("S-RESP-02 Responses path embeds sidecar as suffix in user Input content")
    fun S_RESP_02_sidecar_in_user_input_content() {
        val mappedSegments = mapSession(listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello")))
        val segments = OpenAiCompatibleResponseSidecarPlacement.buildSegmentsWithSidecars(
            mappedSegments = mappedSegments,
            frozenSidecars = emptyList(),
            preRetrievalSidecar = null,
            preRetrievalInsertIndex = null,
            currentSidecar = ResolvedSidecar(text = "ledger", markerId = "11111111-2222-3333-4444-555555555555"),
            tailInsertIndex = 0,
            prependedSystemPrompt = null,
        )
        val userInputs = segments.filterIsInstance<ResponseWireSegment.Input>()
        assertTrue(userInputs.isNotEmpty())
        val tailInputContent = userInputs.last().item.toString()
        assertTrue(tailInputContent.contains("[Sidecar-"), "user input content must contain sidecar marker")
        assertTrue(tailInputContent.contains("ledger"), "user input content must contain sidecar body")
    }

    @Test
    @DisplayName("S-RESP-03 Responses path keeps stable system prompt SystemText segment")
    fun S_RESP_03_keeps_stable_system_prompt() {
        val mappedSegments = mapSession(listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello")))
        val segments = OpenAiCompatibleResponseSidecarPlacement.buildSegmentsWithSidecars(
            mappedSegments = mappedSegments,
            frozenSidecars = emptyList(),
            preRetrievalSidecar = null,
            preRetrievalInsertIndex = null,
            currentSidecar = ResolvedSidecar(text = "ledger", markerId = "11111111-2222-3333-4444-555555555555"),
            tailInsertIndex = 0,
            prependedSystemPrompt = "You are a helpful assistant.",
        )
        val systemTexts = segments.filterIsInstance<ResponseWireSegment.SystemText>()
        assertEquals(1, systemTexts.size)
        assertEquals("You are a helpful assistant.", systemTexts.single().text)
    }

    @Test
    @DisplayName("S-RESP-04 Responses image-bearing user input keeps sidecar in first text content part")
    fun S_RESP_04_image_bearing_sidecar_in_first_text_part() {
        val imageFile = File.createTempFile("sidecar_resp_image", ".png")
        val pngSignature =
            intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A).map { it.toByte() }.toByteArray()
        imageFile.writeBytes(pngSignature + ByteArray(32))
        val mappedSegments = AgentLoopOpenAiResponseMapper.mapSession(
            sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "Describe this")),
            imageUris = listOf(imageFile.toURI().toString()),
            imageTargetIndex = 0,
            loggingPort = loggingPort,
            tag = "test",
            modelId = "gpt-4o",
        ).segments
        val segments = OpenAiCompatibleResponseSidecarPlacement.buildSegmentsWithSidecars(
            mappedSegments = mappedSegments,
            frozenSidecars = emptyList(),
            preRetrievalSidecar = null,
            preRetrievalInsertIndex = null,
            currentSidecar = ResolvedSidecar(text = "ledger", markerId = "11111111-2222-3333-4444-555555555555"),
            tailInsertIndex = 0,
            prependedSystemPrompt = null,
        )
        val tailInput = segments.filterIsInstance<ResponseWireSegment.Input>().last()
        assertTrue(tailInput.item.isMessage())
        val msg = tailInput.item.asMessage()
        val parts = msg.content()
        assertTrue(parts.first().isInputText())
        assertTrue(parts.first().asInputText().text().contains("[Sidecar-"))
        assertTrue(parts.first().asInputText().text().contains("ledger"))
        assertTrue(parts.any { it.isInputImage() }, "image part must be preserved")
        assertEquals(2, parts.size, "should have exactly one text part and one image part")
    }

    @Test
    @DisplayName("S-RESP-05 Responses image-only user input gets sidecar text and keeps image")
    fun S_RESP_05_image_only_user_input_gets_sidecar_text_and_keeps_image() {
        val imageUrl = "data:image/png;base64,iVBORw0KGgo="
        val mappedSegments =
            listOf(
                ResponseWireSegment.Input(
                    ResponseInputItem.ofMessage(
                        ResponseInputItem.Message
                            .builder()
                            .role(ResponseInputItem.Message.Role.of("user"))
                            .addContent(
                                ResponseInputImage
                                    .builder()
                                    .detail(ResponseInputImage.Detail.AUTO)
                                    .imageUrl(imageUrl)
                                    .build(),
                            ).build(),
                    ),
                ),
            )
        val segments = OpenAiCompatibleResponseSidecarPlacement.buildSegmentsWithSidecars(
            mappedSegments = mappedSegments,
            frozenSidecars = emptyList(),
            preRetrievalSidecar = null,
            preRetrievalInsertIndex = null,
            currentSidecar =
                ResolvedSidecar(
                    text = "ledger",
                    markerId = "11111111-2222-3333-4444-555555555555",
                ),
            tailInsertIndex = 0,
            prependedSystemPrompt = null,
        )

        val tailInput = segments.filterIsInstance<ResponseWireSegment.Input>().single()
        val parts = tailInput.item.asMessage().content()
        assertEquals(2, parts.size, "sidecar text should be added without dropping the image")
        assertTrue(parts.first().isInputText())
        assertTrue(parts.first().asInputText().text().contains("[Sidecar-"))
        assertTrue(parts.first().asInputText().text().contains("ledger"))
        assertEquals(1, parts.count { it.isInputImage() })
        assertTrue(parts.single { it.isInputImage() }.asInputImage().toString().contains(imageUrl))
    }

    @Test
    @DisplayName("S-RESP-06 Responses sidecars anchor to user input when tail is tool result")
    fun S_RESP_06_sidecars_anchor_to_user_input_when_tail_is_tool_result() {
        val mappedSegments =
            AgentLoopOpenAiResponseMapper.mapSession(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Find Kotlin docs"),
                        AgentLoopMessage(
                            id = "a1",
                            role = Role.ASSISTANT,
                            content = "",
                            metadataJson =
                                """{"raw_tool_calls":[{"id":"call_1","name":"web_search","arguments_json":"{}"}]}""",
                        ),
                        AgentLoopMessage(
                            id = "tool-result-1",
                            role = Role.ASSISTANT,
                            content = """{"result":"ok"}""",
                            metadataJson = """{"call_id":"call_1"}""",
                        ),
                    ),
                imageUris = emptyList(),
                imageTargetIndex = -1,
                loggingPort = loggingPort,
                tag = "test",
                modelId = "gpt-4o",
            ).segments
        val segments = OpenAiCompatibleResponseSidecarPlacement.buildSegmentsWithSidecars(
            mappedSegments = mappedSegments,
            frozenSidecars =
                listOf(
                    FrozenSidecarSnapshot(
                        text = """State ledger JSON: {"turn":1}""",
                        markerId = "11111111-2222-3333-4444-555555555555",
                        mappedInsertIndex = mappedSegments.lastIndex,
                    ),
                ),
            preRetrievalSidecar = null,
            preRetrievalInsertIndex = null,
            currentSidecar =
                ResolvedSidecar(
                    text = """State ledger JSON: {"turn":2}""",
                    markerId = "66666666-7777-8888-9999-000000000000",
                ),
            tailInsertIndex = mappedSegments.lastIndex,
            prependedSystemPrompt = null,
        )

        val inputItems = segments.filterIsInstance<ResponseWireSegment.Input>().map { it.item }
        val userText =
            inputItems
                .first { it.isMessage() && it.asMessage().role().toString().contains("user") }
                .asMessage()
                .content()
                .first { it.isInputText() }
                .asInputText()
                .text()
        assertTrue(userText.contains("""State ledger JSON: {"turn":1}"""))
        assertTrue(userText.contains("""State ledger JSON: {"turn":2}"""))
        assertTrue(inputItems.any { it.isFunctionCallOutput() }, "tool result replay must remain intact")
    }
}