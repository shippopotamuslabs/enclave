package com.browntowndev.pocketcrew.domain.util

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArtifactContextCompressorTest {

    @Test
    fun `compress squashes append_blocks calls and retains last block text`() {
        val history = listOf(
            ChatMessage(Role.USER, "Start a report"),
            ChatMessage(Role.ASSISTANT, "call:start_artifact{\"title\":\"Report\"}"),
            ChatMessage(Role.USER, "{\"status\":\"success\",\"artifactId\":\"123\"}"),
            ChatMessage(Role.ASSISTANT, "call:append_blocks{\"artifactId\":\"123\",\"blocks\":[{\"type\":\"paragraph\",\"text\":\"Part 1\"}]}"),
            ChatMessage(Role.USER, "{\"status\":\"success\",\"totalBlocks\":1}"),
            ChatMessage(Role.ASSISTANT, "call:append_blocks{\"artifactId\":\"123\",\"blocks\":[{\"type\":\"paragraph\",\"text\":\"Part 2\"}]}"),
            ChatMessage(Role.USER, "{\"status\":\"success\",\"totalBlocks\":2}"),
            ChatMessage(Role.ASSISTANT, "Now I will continue...")
        )

        val compressed = ArtifactContextCompressor.compress(history)

        // Expected:
        // USER: Start a report
        // ASSISTANT: call:start_artifact...
        // USER: {"status":"success","artifactId":"123"}
        // SYSTEM: Summary with "Part 2"
        // ASSISTANT: Now I will continue...
        
        assertEquals(5, compressed.size)
        assertEquals(Role.USER, compressed[0].role)
        assertEquals(Role.ASSISTANT, compressed[1].role)
        assertEquals(Role.USER, compressed[2].role)
        assertEquals(Role.SYSTEM, compressed[3].role)
        assertEquals(Role.ASSISTANT, compressed[4].role)

        assertTrue(compressed[3].content.contains("generated 2 blocks"))
        assertTrue(compressed[3].content.contains("\"Part 2\""))
    }

    @Test
    fun `compress handles no append_blocks gracefully`() {
        val history = listOf(
            ChatMessage(Role.USER, "Hello"),
            ChatMessage(Role.ASSISTANT, "Hi")
        )
        val compressed = ArtifactContextCompressor.compress(history)
        assertEquals(history, compressed)
    }
}
