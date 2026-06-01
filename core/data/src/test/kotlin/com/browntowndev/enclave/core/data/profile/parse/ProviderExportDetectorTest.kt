package com.browntowndev.enclave.core.data.profile.parse

import com.browntowndev.enclave.domain.model.profile.ConversationProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProviderExportDetectorTest {
    private val detector = ProviderExportDetector(
        parsers = listOf(OpenAiExportParser(), AnthropicExportParser(), GoogleExportParser(), XaiExportParser()),
        fallback = GenericFallbackParser(),
    )

    @Test
    fun `PP-10 detects OpenAI conversations json by mapping key`() {
        val preview = """[{"id":"conv1","title":"t","mapping":{"n":{"id":"n","parent":null,"children":[]}},"current_node":"n"}]"""
        val parser = detector.detect("conversations.json", preview)
        assertEquals(ConversationProvider.OPENAI, parser?.provider)
    }

    @Test
    fun `PP-11 detects Anthropic conversations json by chat_messages key`() {
        val preview = """[{"uuid":"abc","name":"test","chat_messages":[{"sender":"human","text":"hi"}]}]"""
        val parser = detector.detect("conversations.json", preview)
        assertEquals(ConversationProvider.ANTHROPIC, parser?.provider)
    }

    @Test
    fun `PP-12 falls back to generic parser for unknown JSON array`() {
        val preview = """[{"id":"1","title":"chat","messages":[{"role":"user","content":"hello"}]}]"""
        val parser = detector.detect("data.json", preview)
        assertEquals(ConversationProvider.UNKNOWN, parser?.provider)
    }

    @Test
    fun `PP-13 returns null for non-JSON entry`() {
        val parser = detector.detect("readme.txt", "This is a readme file.")
        assertNull(parser)
    }
}
