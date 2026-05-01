package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.media.ImageGenerationSettings
import com.browntowndev.pocketcrew.feature.inference.media.GoogleMusicGenerationAdapter
import com.google.genai.Client
import com.google.genai.types.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Optional

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleMusicGenerationAdapterTest {
    private val clientProvider = mockk<GoogleGenAiClientProviderPort>()
    private val client = mockk<Client>()

    @Test
    fun generateMusic_success_decodesAudioBytes() = runTest {
        val audioBytes = "audio-data".toByteArray()
        val adapter = object : GoogleMusicGenerationAdapter(clientProvider) {
            override fun generateContent(
                client: Client,
                modelId: String,
                prompt: String
            ): GenerateContentResponse {
                return GenerateContentResponse.builder()
                    .candidates(listOf(
                        Candidate.builder()
                            .content(
                                Content.builder()
                                    .parts(listOf(
                                        Part.builder()
                                            .inlineData(
                                                Blob.builder()
                                                    .data(audioBytes)
                                                    .mimeType("audio/mpeg")
                                                    .build()
                                            )
                                            .build()
                                    ))
                                    .build()
                            )
                            .build()
                    ))
                    .build()
            }
        }
        every { clientProvider.getClient("key", null) } returns client

        val result = adapter.generateMusic(
            prompt = "cheerful song",
            apiKey = "key",
            modelId = "lyria-3-clip-preview",
            baseUrl = null,
            settings = ImageGenerationSettings(), // Dummy settings
        )

        assertTrue(result.isSuccess)
        assertArrayEquals(audioBytes, result.getOrThrow())
    }

    @Test
    fun generateMusic_noAudioData_returnsFailure() = runTest {
        val adapter = object : GoogleMusicGenerationAdapter(clientProvider) {
            override fun generateContent(
                client: Client,
                modelId: String,
                prompt: String
            ): GenerateContentResponse {
                return GenerateContentResponse.builder()
                    .candidates(listOf(
                        Candidate.builder()
                            .content(
                                Content.builder()
                                    .parts(listOf(Part.builder().text("Thinking...").build()))
                                    .build()
                            )
                            .build()
                    ))
                    .build()
            }
        }
        every { clientProvider.getClient("key", null) } returns client

        val result = adapter.generateMusic(
            prompt = "cheerful song",
            apiKey = "key",
            modelId = "lyria-3-clip-preview",
            baseUrl = null,
            settings = ImageGenerationSettings(),
        )

        assertTrue(result.isFailure)
        assertEquals("No audio data returned from generation", result.exceptionOrNull()?.message)
    }
}
