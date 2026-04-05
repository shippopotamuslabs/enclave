package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.openai.client.OpenAIClient
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApiInferenceServiceImplTest {

    @Test
    fun `setHistory clears and adds messages`() = runBlocking {
        val client = mockk<OpenAIClient>()
        val service = ApiInferenceServiceImpl(
            client = client,
            modelId = "gpt-4o",
            provider = "OPENAI",
            modelType = ModelType.MAIN,
            loggingPort = mockk<LoggingPort>(relaxed = true)
        )

        val messages = listOf(ChatMessage(Role.USER, "Hello"))
        service.setHistory(messages)
        
        // Since conversationHistory is private, we're just testing it doesn't crash
        // A full test would mock the stream response and verify the params passed to createStreaming.

        service.closeSession()
    }

    @Test
    fun `mergeSystemPrompt prepends configured prompt when history lacks system message`() {
        val service = ApiInferenceServiceImpl(
            client = mockk(),
            modelId = "gpt-4o",
            provider = "OPENAI",
            modelType = ModelType.MAIN,
            loggingPort = mockk<LoggingPort>(relaxed = true)
        )

        val merged = service.mergeSystemPrompt(
            history = listOf(ChatMessage(Role.USER, "hello")),
            systemPrompt = "You are Grok."
        )

        assertEquals(
            listOf(
                ChatMessage(Role.SYSTEM, "You are Grok."),
                ChatMessage(Role.USER, "hello")
            ),
            merged
        )
    }

    @Test
    fun `mergeSystemPrompt ignores blank prompt`() {
        val service = ApiInferenceServiceImpl(
            client = mockk(),
            modelId = "gpt-4o",
            provider = "OPENAI",
            modelType = ModelType.MAIN,
            loggingPort = mockk<LoggingPort>(relaxed = true)
        )

        val history = listOf(ChatMessage(Role.USER, "hello"))
        val merged = service.mergeSystemPrompt(history, "   ")

        assertEquals(history, merged)
    }
}
