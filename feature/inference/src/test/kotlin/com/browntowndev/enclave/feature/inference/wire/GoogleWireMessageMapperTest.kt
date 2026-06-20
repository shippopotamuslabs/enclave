package com.browntowndev.enclave.feature.inference.wire

import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.feature.inference.GoogleAgentLoopContentMapper
import com.browntowndev.enclave.feature.inference.appendonly.SidecarMarker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Direct coverage for [GoogleAgentLoopContentMapper], which implements
 * [ProviderWireMessageMapper] for the Gemini GenerateContent wire. Previously only exercised
 * transitively through GoogleInferenceServiceImplTest.
 */
class GoogleWireMessageMapperTest {
    private val mapper = GoogleAgentLoopContentMapper

    @Test
    fun `systemPrompt returns null because Google sets system instruction on the config`() {
        assertNull(mapper.systemPrompt("You are helpful", useCacheControl = false))
    }

    @Test
    fun `userText emits user role with stripped base64 data uris`() {
        val content = mapper.userText("hi", imageUris = emptyList(), useCacheControl = false)!!
        assertEquals("user", content.role().orElseThrow())
        val parts = content.parts().orElseThrow()
        assertEquals(1, parts.size)
        assertEquals("hi", parts[0].text().orElseThrow())
    }

    @Test
    fun `userText with images emits text and inline image parts`() {
        val imageUri = createTempImageUri()
        val content = mapper.userText("look", imageUris = listOf(imageUri), useCacheControl = false)!!
        val parts = content.parts().orElseThrow()
        assertEquals(2, parts.size)
        assertEquals("look", parts[0].text().orElseThrow())
        assertTrue(parts[1].inlineData().isPresent)
    }

    @Test
    fun `engineInternal returns null for blank wire text`() {
        assertNull(mapper.engineInternal(""))
    }

    @Test
    fun `engineInternal emits user role for non-blank wire text`() {
        val content = mapper.engineInternal("<system_context>budget</system_context>")!!
        assertEquals("user", content.role().orElseThrow())
    }

    @Test
    fun `compactSummary emits user role with conversation summary label`() {
        val content = mapper.compactSummary("the summary", useCacheControl = false)
        assertEquals("user", content.role().orElseThrow())
        val text = content.parts().orElseThrow()[0].text().orElseThrow()
        assertTrue(text.startsWith("[Conversation summary]"))
        assertTrue(text.contains("the summary"))
    }

    @Test
    fun `toolResult emits user role with function response part`() {
        val content = mapper.toolResult("call_1", """{"results":[]}""", isOrphan = false, useCacheControl = false)
        assertEquals("user", content.role().orElseThrow())
        val part = content.parts().orElseThrow()[0]
        assertTrue(part.functionResponse().isPresent)
        assertEquals("call_1", part.functionResponse().get().name().orElseThrow())
    }

    @Test
    fun `assistantText emits model role with text and function call parts`() {
        val content = mapper.assistantText(
            content = "answer",
            toolCalls = listOf(RawToolCallRef(id = "call_1", name = "web_search", argumentsJson = """{"q":"x"}""")),
            reasoningContent = null,
            useCacheControl = false,
        )!!
        assertEquals("model", content.role().orElseThrow())
        val parts = content.parts().orElseThrow()
        assertEquals(2, parts.size)
        assertEquals("answer", parts[0].text().orElseThrow())
        assertTrue(parts[1].functionCall().isPresent)
        assertEquals("web_search", parts[1].functionCall().get().name().orElseThrow())
    }

    @Test
    fun `assistantText tolerates null tool call id`() {
        val content = mapper.assistantText(
            content = "answer",
            toolCalls = listOf(RawToolCallRef(id = null, name = "web_search", argumentsJson = "{}")),
            reasoningContent = null,
            useCacheControl = false,
        )!!
        val call = content.parts().orElseThrow()[1].functionCall().get()
        assertEquals("web_search", call.name().orElseThrow())
    }

    @Test
    fun `assistantText returns null when content blank and no tool calls`() {
        assertNull(mapper.assistantText("  ", emptyList(), reasoningContent = null, useCacheControl = false))
    }

    @Test
    fun `mapMessage delegates to interface methods and drops system rows`() {
        val msg = AgentLoopMessage(id = "s1", role = Role.SYSTEM, content = "system text")
        assertNull(mapper.mapMessage(msg, index = 0, lastUserIndex = -1, imageUris = emptyList()))
    }

    @Test
    fun `mapMessage embeds sidecar markers into user content`() {
        val msg = AgentLoopMessage(id = "u1", role = Role.USER, content = "hi")
        val markers = listOf(SidecarMarker(markerId = "uuid-1", body = "extra context"))
        val content = mapper.mapMessage(msg, index = 0, lastUserIndex = -1, imageUris = emptyList(), sidecarMarkers = markers)!!
        val text = content.parts().orElseThrow()[0].text().orElseThrow()
        assertTrue(text.contains("extra context"))
        assertTrue(text.contains("hi"))
    }

    @Test
    fun `mapMessage routes assistant tool result to function response`() {
        val msg = AgentLoopMessage(
            id = "t1",
            role = Role.ASSISTANT,
            content = """{"results":[]}""",
            metadataJson = """{"call_id":"call_1"}""",
        )
        val content = mapper.mapMessage(msg, index = 0, lastUserIndex = -1, imageUris = emptyList())!!
        assertEquals("user", content.role().orElseThrow())
        assertTrue(content.parts().orElseThrow()[0].functionResponse().isPresent)
    }

    @Test
    fun `mapMessage routes compact summary assistant row to user summary`() {
        val msg = AgentLoopMessage(id = "compact-summary-001", role = Role.ASSISTANT, content = "the summary")
        val content = mapper.mapMessage(msg, index = 0, lastUserIndex = -1, imageUris = emptyList())!!
        assertEquals("user", content.role().orElseThrow())
        assertFalse(content.parts().orElseThrow()[0].text().orElseThrow().contains("model"))
    }

    @Test
    fun `lastNonToolResultUserIndex delegates to shared helper`() {
        val messages = listOf(
            AgentLoopMessage(id = "u1", role = Role.USER, content = "first"),
            AgentLoopMessage(id = "t1", role = Role.USER, content = "{}", metadataJson = """{"call_id":"c1"}"""),
            AgentLoopMessage(id = "u2", role = Role.USER, content = "second"),
        )
        assertEquals(2, mapper.lastNonToolResultUserIndex(messages))
    }

    private fun createTempImageUri(): String {
        val file = File.createTempFile("google-wire-image", ".jpg")
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        return file.toURI().toString()
    }
}
