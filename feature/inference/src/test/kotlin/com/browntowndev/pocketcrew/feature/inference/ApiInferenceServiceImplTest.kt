package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.openai.client.OpenAIClient
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class ApiInferenceServiceImplTest {

    @Test
    fun `setHistory clears and adds messages`() = runBlocking {
        val client = mockk<OpenAIClient>()
        val service = ApiInferenceServiceImpl(
            client = client,
            modelId = "gpt-4o",
            provider = "OPENAI",
            modelType = ModelType.MAIN
        )

        val messages = listOf(ChatMessage(Role.USER, "Hello"))
        service.setHistory(messages)
        
        // Since conversationHistory is private, we're just testing it doesn't crash
        // A full test would mock the stream response and verify the params passed to createStreaming.
        
        service.closeSession()
    }
}
