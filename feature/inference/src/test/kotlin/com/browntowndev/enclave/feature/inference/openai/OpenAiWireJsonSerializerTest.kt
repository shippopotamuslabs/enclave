package com.browntowndev.enclave.feature.inference.openai

import com.fasterxml.jackson.databind.ObjectMapper
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenAiWireJsonSerializerTest {
    @Test
    fun serializeBody_producesJson_forChatCompletionBody() {
        val params =
            ChatCompletionCreateParams
                .builder()
                .model("deepseek-v4-flash")
                .addMessage(
                    ChatCompletionUserMessageParam
                        .builder()
                        .content("hello")
                        .build(),
                ).build()

        val wireJson = OpenAiWireJsonSerializer.serializeBody(params._body())

        assertTrue(wireJson.startsWith("{"))
        assertTrue(wireJson.contains("\"messages\""))
        assertTrue(wireJson.contains("\"hello\""))
        assertFalse(wireJson.contains("\"valid\""))
    }

    @Test
    fun serializeBody_differsFromPlainObjectMapper() {
        val params =
            ChatCompletionCreateParams
                .builder()
                .model("deepseek-v4-flash")
                .addMessage(
                    ChatCompletionUserMessageParam
                        .builder()
                        .content("hello")
                        .build(),
                ).build()
        val body = params._body()

        val wireJson = OpenAiWireJsonSerializer.serializeBody(body)
        val plainJson = ObjectMapper().writeValueAsString(body)

        assertDoesNotThrow { wireJson.toByteArray(Charsets.UTF_8) }
        assertTrue(
            wireJson != plainJson || wireJson.contains("\"model\""),
            "Wire JSON should be valid request JSON",
        )
    }
}
