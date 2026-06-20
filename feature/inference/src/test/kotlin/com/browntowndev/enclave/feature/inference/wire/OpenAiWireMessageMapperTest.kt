package com.browntowndev.enclave.feature.inference.wire

import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenAiWireMessageMapperTest {
    private val logger = NoopLoggingPort
    private val policy =
        object : OpenAiAssistantReplayPolicy {
            override fun normalizeContent(
                requestModelId: String,
                content: String,
                options: GenerationOptions,
                hasToolCalls: Boolean,
            ): String = content

            override fun applyContent(
                builder: ChatCompletionAssistantMessageParam.Builder,
                normalizedContent: String,
            ) {
                if (normalizedContent.isNotBlank()) builder.content(normalizedContent)
            }

            override fun shouldAttachReasoning(
                requestModelId: String,
                options: GenerationOptions,
                reasoningContent: String?,
            ): Boolean = reasoningContent != null
        }
    private val mapper = OpenAiWireMessageMapper(
        tag = "test",
        modelId = "model",
        loggingPort = logger,
        assistantPolicy = policy,
    )
    private val options = GenerationOptions(reasoningBudget = 0)

    @Test
    fun `systemPrompt emits system message`() {
        val param = mapper.systemPrompt("You are helpful", useCacheControl = false)
        assertTrue(param.isSystem())
        assertEquals("You are helpful", param.asSystem().content().asText())
    }

    @Test
    fun `userText emits user message`() {
        val param = mapper.userText("hi", imageUris = emptyList(), useCacheControl = false)
        assertTrue(param.isUser())
        assertEquals("hi", param.asUser().content().asText())
    }

    @Test
    fun `engineInternal emits system message with wire text`() {
        val param = mapper.engineInternal("<system_context>budget</system_context>")
        assertNotNull(param)
        assertTrue(param!!.isSystem())
    }

    @Test
    fun `compactSummary emits system message with conversation summary label`() {
        val param = mapper.compactSummary("the summary", useCacheControl = false)
        assertTrue(param.isSystem())
        assertEquals("[Conversation summary]\nthe summary", param.asSystem().content().asText())
    }

    @Test
    fun `toolResult emits tool message when not orphan`() {
        val param = mapper.toolResult("call_1", "{}", isOrphan = false, useCacheControl = false)
        assertTrue(param.isTool())
    }

    @Test
    fun `toolResult rewrites orphan as user message`() {
        val param = mapper.toolResult("call_orphan", "{}", isOrphan = true, useCacheControl = false)
        assertTrue(param.isUser())
        assertTrue(param.asUser().content().asText().startsWith("[Tool result]"))
    }

    @Test
    fun `assistantText drops tool calls without id and returns kept ids`() {
        val toolCalls = listOf(
            RawToolCallRef(id = "call_1", name = "web_search", argumentsJson = "{}"),
            RawToolCallRef(id = null, name = "orphan_tool", argumentsJson = "{}"),
        )
        val render = mapper.assistantText(
            content = "",
            toolCalls = toolCalls,
            reasoningContent = null,
            useCacheControl = false,
            requestModelId = "model",
            options = options,
            messageId = "a1",
        )
        assertNotNull(render)
        assertEquals(listOf("call_1"), render!!.toolCallIds)
    }

    @Test
    fun `assistantText returns null when content blank and all tool calls dropped`() {
        val toolCalls = listOf(RawToolCallRef(id = null, name = "orphan_tool", argumentsJson = "{}"))
        val render = mapper.assistantText(
            content = "  ",
            toolCalls = toolCalls,
            reasoningContent = null,
            useCacheControl = false,
            requestModelId = "model",
            options = options,
            messageId = "a1",
        )
        assertNull(render)
    }

    @Test
    fun `assistantText attaches reasoning content when policy permits`() {
        val render = mapper.assistantText(
            content = "answer",
            toolCalls = emptyList(),
            reasoningContent = "hidden chain",
            useCacheControl = false,
            requestModelId = "model",
            options = options,
            messageId = "a1",
        )!!
        assertTrue(render.param.isAssistant())
    }

    @Test
    fun `interface assistantText override errors`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            mapper.assistantText("x", emptyList(), null, useCacheControl = false)
        }
    }
}

private object NoopLoggingPort : LoggingPort {
    override fun debug(tag: String, message: String) {}
    override fun info(tag: String, message: String) {}
    override fun warning(tag: String, message: String) {}
    override fun error(tag: String, message: String, throwable: Throwable?) {}
}
