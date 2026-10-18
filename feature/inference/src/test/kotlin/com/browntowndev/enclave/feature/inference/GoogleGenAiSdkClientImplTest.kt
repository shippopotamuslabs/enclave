@file:Suppress("ForbiddenMockKInTests") // MockK only for the raw google-genai SDK surface (Models/ResponseStream) that this Impl wraps.

package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.google.genai.Models
import com.google.genai.ResponseStream
import com.google.genai.types.Candidate
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.Part
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.stream.Stream

class GoogleGenAiSdkClientImplTest {
    @Test
    fun `generateContentStream emits events and returns result`() =
        runTest {
            val models = mockk<Models>(relaxed = true)
            val responseStream = mockk<ResponseStream<GenerateContentResponse>>(relaxed = true)

            every { models.generateContentStream(any<String>(), any<List<Content>>(), any<GenerateContentConfig>()) } returns responseStream
            every { responseStream.iterator() } returns
                Stream
                    .of(
                        thinkingResponse("Thinking..."),
                        textResponse("Hello!"),
                        functionCallResponse("my_tool", mapOf("arg" to "val")),
                    ).iterator()

            val sdkClient =
                GoogleGenAiSdkClientImpl(
                    modelsApiProvider = { models },
                    modelType = ModelType.FAST,
                    loggingPort = FakeLoggingPort(),
                )

            val events = mutableListOf<InferenceEvent>()
            val result =
                sdkClient.generateContentStream(
                    modelId = "test-model",
                    contents = emptyList<Content>(),
                    config = GenerateContentConfig.builder().build(),
                    emitEvent = { event: InferenceEvent -> events.add(event) },
                )

            assertTrue(events.any { it is InferenceEvent.Thinking && it.chunk == "Thinking..." })
            assertTrue(events.any { it is InferenceEvent.PartialResponse && it.chunk == "Hello!" })

            assertEquals("my_tool", result.functionCall?.name()?.get())
            assertTrue(result.emittedAny)
        }

    private fun textResponse(text: String): GenerateContentResponse =
        GenerateContentResponse
            .builder()
            .candidates(
                Candidate
                    .builder()
                    .content(
                        Content
                            .builder()
                            .role("model")
                            .parts(listOf(Part.fromText(text)))
                            .build(),
                    ).build(),
            ).build()

    private fun thinkingResponse(text: String): GenerateContentResponse =
        GenerateContentResponse
            .builder()
            .candidates(
                Candidate
                    .builder()
                    .content(
                        Content
                            .builder()
                            .role("model")
                            .parts(
                                listOf(
                                    Part
                                        .builder()
                                        .text(text)
                                        .thought(true)
                                        .build(),
                                ),
                            ).build(),
                    ).build(),
            ).build()

    private fun functionCallResponse(
        name: String,
        args: Map<String, Any>,
    ): GenerateContentResponse =
        GenerateContentResponse
            .builder()
            .candidates(
                Candidate
                    .builder()
                    .content(
                        Content
                            .builder()
                            .role("model")
                            .parts(
                                listOf(
                                    Part.fromFunctionCall(name, args),
                                ),
                            ).build(),
                    ).build(),
            ).build()
}
