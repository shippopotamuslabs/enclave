package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResponsesInputMessageSupportTest {
    @Test
    fun `textInputItem uses easy assistant message instead of input_text content`() {
        val item = ResponsesInputMessageSupport.textInputItem(Role.ASSISTANT, "Hi there!")

        assertTrue(item.isEasyInputMessage())
        assertFalse(item.toString().contains("input_text"))
    }

    @Test
    fun `mapToResponseParams serializes assistant history without input_text content type`() {
        val params =
            OpenAiRequestMapper.mapToResponseParams(
                modelId = "gpt-5.4-nano",
                prompt = "How are you?",
                history =
                    listOf(
                        ChatMessage(Role.USER, "Hello"),
                        ChatMessage(Role.ASSISTANT, "Hi there!"),
                    ),
                options = GenerationOptions(reasoningBudget = 0),
            )

        val inputItems = params.input().orElseThrow().asResponse()
        val assistantItem =
            inputItems.firstOrNull { item ->
                item.isEasyInputMessage() &&
                    item.asEasyInputMessage().role() == com.openai.models.responses.EasyInputMessage.Role.ASSISTANT
            }

        assertTrue(assistantItem != null)
        assertTrue(assistantItem!!.asEasyInputMessage().content().toString().contains("Hi there!"))
        assertFalse(assistantItem.toString().contains("input_text"))
    }
}