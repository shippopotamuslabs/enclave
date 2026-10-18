@file:Suppress("ForbiddenMockKInTests") // MockK only for the raw google-genai SDK Client handed through the owned provider port.

package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.feature.inference.media.GoogleImageGenerationAdapter
import com.google.genai.Client
import com.google.genai.types.*
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleImageGenerationAdapterTest {
    private val clientProvider = FakeGoogleGenAiClientProviderPort()

    @Test
    fun testMockkMatch() {
        val provider = FakeGoogleGenAiClientProviderPort()
        val client = mockk<Client>()
        provider.client = client

        assertNotNull(provider.getClient("key", null, emptyMap(), "v1alpha"))
    }

    @Test
    fun generateImage_noReference_callsGenerateContent() =
        runTest {
            var capturedModelId: String? = null
            var capturedContents: List<Content>? = null
            var capturedConfig: GenerateContentConfig? = null
            val mockProvider = FakeGoogleGenAiClientProviderPort()
            val mockClient = mockk<Client>()

            val adapter =
                object : GoogleImageGenerationAdapter(mockProvider) {
                    override fun generateContent(
                        client: Client,
                        modelId: String,
                        contents: List<Content>,
                        config: GenerateContentConfig,
                    ): GenerateContentResponse {
                        capturedModelId = modelId
                        capturedContents = contents
                        capturedConfig = config

                        val part =
                            Part
                                .builder()
                                .inlineData(
                                    Blob
                                        .builder()
                                        .data("modern".toByteArray())
                                        .build(),
                                ).build()

                        return GenerateContentResponse
                            .builder()
                            .candidates(
                                listOf(
                                    Candidate
                                        .builder()
                                        .content(
                                            Content
                                                .builder()
                                                .parts(listOf(part))
                                                .build(),
                                        ).build(),
                                ),
                            ).build()
                    }
                }

            mockProvider.client = mockClient

            val result =
                adapter.generateImage(
                    prompt = "modern prompt",
                    apiKey = "key",
                    modelId = "gemini-3.1-flash-image-preview",
                    baseUrl = null,
                    settings = ImageGenerationSettings(generationCount = 2),
                    referenceImage = null,
                )

            assertTrue(result.isSuccess)
            assertEquals("modern", String(result.getOrThrow()[0]))
            assertEquals("gemini-3.1-flash-image-preview", capturedModelId)
            assertEquals(1, capturedContents!!.size)
            assertEquals(
                1,
                capturedContents!![0]
                    .parts()
                    .get()
                    .size,
            )
            assertEquals(
                "modern prompt",
                capturedContents!![0]
                    .parts()
                    .get()[0]
                    .text()
                    .get(),
            )
            assertEquals(listOf("IMAGE"), capturedConfig!!.responseModalities().get())
            assertEquals(2, capturedConfig!!.candidateCount().get())
        }

    @Test
    fun generateImage_withReference_callsGenerateContent() =
        runTest {
            var capturedContents: List<Content>? = null
            val mockProvider = FakeGoogleGenAiClientProviderPort()
            val mockClient = mockk<Client>()

            val adapter =
                object : GoogleImageGenerationAdapter(mockProvider) {
                    override fun generateContent(
                        client: Client,
                        modelId: String,
                        contents: List<Content>,
                        config: GenerateContentConfig,
                    ): GenerateContentResponse {
                        capturedContents = contents

                        val part =
                            Part
                                .builder()
                                .inlineData(
                                    Blob
                                        .builder()
                                        .data("edited".toByteArray())
                                        .build(),
                                ).build()

                        return GenerateContentResponse
                            .builder()
                            .candidates(
                                listOf(
                                    Candidate
                                        .builder()
                                        .content(
                                            Content
                                                .builder()
                                                .parts(listOf(part))
                                                .build(),
                                        ).build(),
                                ),
                            ).build()
                    }
                }

            mockProvider.client = mockClient

            val refBytes = "reference".toByteArray()
            val result =
                adapter.generateImage(
                    prompt = "edit prompt",
                    apiKey = "key",
                    modelId = "gemini-3.1-flash-image-preview",
                    baseUrl = null,
                    settings = ImageGenerationSettings(generationCount = 3),
                    referenceImage = refBytes,
                )

            assertTrue(result.isSuccess)
            assertEquals("edited", String(result.getOrThrow()[0]))
            assertEquals(1, capturedContents!!.size)
            val parts = capturedContents!![0].parts().get()
            assertEquals(2, parts.size)
            assertEquals("edit prompt", parts[0].text().get())
            assertEquals(
                "image/png",
                parts[1]
                    .inlineData()
                    .get()
                    .mimeType()
                    .get(),
            )
            assertNotNull(
                parts[1]
                    .inlineData()
                    .get()
                    .data()
                    .get(),
            )
        }
}
