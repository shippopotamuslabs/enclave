package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenAiRequestMapperTest {

    @Test
    fun `mapToResponseParams correctly maps model, prompt, and history`() {
        val history = listOf(
            ChatMessage(Role.SYSTEM, "You are a helpful assistant."),
            ChatMessage(Role.USER, "Hello"),
            ChatMessage(Role.ASSISTANT, "Hi there!")
        )
        val options = GenerationOptions(
            reasoningBudget = 0,
            temperature = 0.7f,
            maxTokens = 100
        )

        val params = OpenAiRequestMapper.mapToResponseParams(
            modelId = "gpt-4o",
            prompt = "How are you?",
            history = history,
            options = options
        )

        assertTrue(params.model().get().toString().contains("gpt-4o"))
        assertTrue(params.instructions().get().contains("You are a helpful assistant."))
        
        val inputString = params.input().get().asText()
        assertTrue(inputString.contains("USER: Hello"))
        assertTrue(inputString.contains("ASSISTANT: Hi there!"))
        assertTrue(inputString.contains("USER: How are you?"))
    }
}
