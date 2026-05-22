package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.feature.inference.media.GoogleImageGenerationAdapter
import com.google.common.collect.ImmutableList
import com.google.genai.Client
import com.google.genai.types.*
import io.mockk.*
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
    private val clientProvider = mockk<GoogleGenAiClientProviderPort>()

    @Test
    fun testMockkMatch() {
        val mock = mockk<GoogleGenAiClientProviderPort>()
        val client = mockk<Client>()
        every { mock.getClient(any(), any(), any(), any()) } returns client

        assertNotNull(mock.getClient("key", null, emptyMap(), "v1alpha"))
    }

    @Test
    fun generateImage_noReference_callsGenerateContent() =
        runTest {
            val promptSlot = slot<String>()
            val contentsSlot = slot<List<Content>>()
            val configSlot = slot<GenerateContentConfig>()
            val mockProvider = mockk<GoogleGenAiClientProviderPort>()
            val mockClient = mockk<Client>()

            val adapter =
                object : GoogleImageGenerationAdapter(mockProvider) {
                    override fun generateContent(
                        client: Client,
                        modelId: String,
                        contents: List<Content>,
                        config: GenerateContentConfig,
                    ): GenerateContentResponse {
                        promptSlot.captured = modelId
                        contentsSlot.captured = contents
                        configSlot.captured = config

                        val blob = mockk<Blob>()
                        every { blob.data() } returns java.util.Optional.of("modern".toByteArray())

                        val part = mockk<Part>()
                        every { part.inlineData() } returns java.util.Optional.of(blob)

                        val response = mockk<GenerateContentResponse>()
                        every { response.parts() } returns ImmutableList.of(part)

                        return response
                    }
                }

            every { mockProvider.getClient(any(), any(), any(), any()) } returns mockClient

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
            assertEquals("gemini-3.1-flash-image-preview", promptSlot.captured)
            assertEquals(1, contentsSlot.captured.size)
            assertEquals(
                1,
                contentsSlot.captured[0]
                    .parts()
                    .get()
                    .size,
            )
            assertEquals(
                "modern prompt",
                contentsSlot.captured[0]
                    .parts()
                    .get()[0]
                    .text()
                    .get(),
            )
            assertEquals(listOf("IMAGE"), configSlot.captured.responseModalities().get())
            assertEquals(2, configSlot.captured.candidateCount().get())
        }

    @Test
    fun generateImage_withReference_callsGenerateContent() =
        runTest {
            val contentsSlot = slot<List<Content>>()
            val mockProvider = mockk<GoogleGenAiClientProviderPort>()
            val mockClient = mockk<Client>()

            val adapter =
                object : GoogleImageGenerationAdapter(mockProvider) {
                    override fun generateContent(
                        client: Client,
                        modelId: String,
                        contents: List<Content>,
                        config: GenerateContentConfig,
                    ): GenerateContentResponse {
                        contentsSlot.captured = contents

                        val blob = mockk<Blob>()
                        every { blob.data() } returns java.util.Optional.of("edited".toByteArray())

                        val part = mockk<Part>()
                        every { part.inlineData() } returns java.util.Optional.of(blob)

                        val response = mockk<GenerateContentResponse>()
                        every { response.parts() } returns ImmutableList.of(part)

                        return response
                    }
                }

            every { mockProvider.getClient(any(), any(), any(), any()) } returns mockClient

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
            assertEquals(1, contentsSlot.captured.size)
            val parts = contentsSlot.captured[0].parts().get()
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
