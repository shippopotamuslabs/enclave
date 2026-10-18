@file:Suppress("ForbiddenMockKInTests") // MockK only for the raw google-genai SDK Client handed through the owned provider port.

package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.feature.inference.media.GoogleMusicGenerationAdapter
import com.google.genai.Client
import com.google.genai.types.*
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleMusicGenerationAdapterTest {
    private val clientProvider = FakeGoogleGenAiClientProviderPort()
    private val client = mockk<Client>()

    @Test
    fun generateMusic_success_decodesAudioBytes() =
        runTest {
            val audioBytes = "audio-data".toByteArray()
            val adapter =
                object : GoogleMusicGenerationAdapter(clientProvider) {
                    override fun generateContent(
                        client: Client,
                        modelId: String,
                        prompt: String,
                    ): GenerateContentResponse =
                        GenerateContentResponse
                            .builder()
                            .candidates(
                                listOf(
                                    Candidate
                                        .builder()
                                        .content(
                                            Content
                                                .builder()
                                                .parts(
                                                    listOf(
                                                        Part
                                                            .builder()
                                                            .inlineData(
                                                                Blob
                                                                    .builder()
                                                                    .data(audioBytes)
                                                                    .mimeType("audio/mpeg")
                                                                    .build(),
                                                            ).build(),
                                                    ),
                                                ).build(),
                                        ).build(),
                                ),
                            ).build()
                }
            clientProvider.client = client

            val result =
                adapter.generateMusic(
                    prompt = "cheerful song",
                    apiKey = "key",
                    modelId = "lyria-3-clip-preview",
                    baseUrl = null,
                    settings = MusicGenerationSettings(duration = 30, tempo = 120),
                )

            assertTrue(result.isSuccess)
            assertArrayEquals(audioBytes, result.getOrThrow())
        }

    @Test
    fun generateMusic_proModel_usesConfiguredModelAndEnrichedPrompt() =
        runTest {
            val audioBytes = "audio-data".toByteArray()
            var capturedModelId: String? = null
            var capturedPrompt: String? = null
            val adapter =
                object : GoogleMusicGenerationAdapter(clientProvider) {
                    override fun generateContent(
                        client: Client,
                        modelId: String,
                        prompt: String,
                    ): GenerateContentResponse {
                        capturedModelId = modelId
                        capturedPrompt = prompt
                        return GenerateContentResponse
                            .builder()
                            .candidates(
                                listOf(
                                    Candidate
                                        .builder()
                                        .content(
                                            Content
                                                .builder()
                                                .parts(
                                                    listOf(
                                                        Part
                                                            .builder()
                                                            .inlineData(
                                                                Blob
                                                                    .builder()
                                                                    .data(audioBytes)
                                                                    .mimeType("audio/mpeg")
                                                                    .build(),
                                                            ).build(),
                                                    ),
                                                ).build(),
                                        ).build(),
                                ),
                            ).build()
                    }
                }
            clientProvider.client = client

            val result =
                adapter.generateMusic(
                    prompt = "cheerful song",
                    apiKey = "key",
                    modelId = "lyria-3-pro-preview",
                    baseUrl = null,
                    settings = MusicGenerationSettings(duration = 30, tempo = 120),
                )

            assertTrue(result.isSuccess)
            assertEquals("lyria-3-pro-preview", capturedModelId)
            assertEquals("Create a 30-second track at 120 BPM. cheerful song", capturedPrompt)
        }

    @Test
    fun generateMusic_noAudioData_returnsFailure() =
        runTest {
            val adapter =
                object : GoogleMusicGenerationAdapter(clientProvider) {
                    override fun generateContent(
                        client: Client,
                        modelId: String,
                        prompt: String,
                    ): GenerateContentResponse =
                        GenerateContentResponse
                            .builder()
                            .candidates(
                                listOf(
                                    Candidate
                                        .builder()
                                        .content(
                                            Content
                                                .builder()
                                                .parts(listOf(Part.builder().text("Thinking...").build()))
                                                .build(),
                                        ).build(),
                                ),
                            ).build()
                }
            clientProvider.client = client

            val result =
                adapter.generateMusic(
                    prompt = "cheerful song",
                    apiKey = "key",
                    modelId = "lyria-3-clip-preview",
                    baseUrl = null,
                    settings = MusicGenerationSettings(duration = 30, tempo = 120),
                )

            assertTrue(result.isFailure)
            assertEquals("No audio data returned from generation", result.exceptionOrNull()?.message)
        }
}
