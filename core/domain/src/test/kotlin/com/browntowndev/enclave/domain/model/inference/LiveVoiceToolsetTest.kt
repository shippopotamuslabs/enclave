package com.browntowndev.enclave.domain.model.inference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LiveVoiceToolsetTest {
    private val expectedNames =
        setOf(
            "web_search",
            "web_extract",
            "search_chat_history",
            "search_chat",
            "get_message_context",
            "manage_memories",
            "search_x_recent",
            "x_get_thread",
            "x_trending",
            "x_user_tweets",
            "x_mention_search",
        )

    private val excludedNames =
        setOf(
            "start_artifact",
            "append_blocks",
            "search_current_artifact",
            "generate_image",
            "generate_video",
            "generate_music",
            "tool_discovery_tool",
            "attached_image_inspect",
            "read_context_reference",
        )

    @Test
    @DisplayName("LV-TOOL-001")
    fun lvTool001_whitelistContainsExactlyElevenSearchTools() {
        val names = LiveVoiceToolset.tools.map { it.name }.toSet()

        assertEquals(11, LiveVoiceToolset.tools.size)
        assertEquals(expectedNames, names)
        excludedNames.forEach { excluded ->
            assertFalse(names.contains(excluded), "Whitelist must not contain $excluded")
        }
    }

    @Test
    @DisplayName("LV-TOOL-MUT-001")
    fun lvToolMut001_whitelistExcludesArtifactTools() {
        val names = LiveVoiceToolset.tools.map { it.name }.toSet()

        assertFalse(names.contains("start_artifact"))
        assertFalse(names.contains("append_blocks"))
        assertFalse(names.contains("search_current_artifact"))
    }
}