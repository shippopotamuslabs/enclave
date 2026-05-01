package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.VideoGenerationSettings
import com.browntowndev.pocketcrew.feature.inference.media.GoogleVideoGenerationAdapter
import com.browntowndev.pocketcrew.feature.inference.GoogleGenAiClientProviderPort
import com.google.genai.Client
import com.google.genai.types.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleVideoGenerationAdapterTest {
    private val clientProvider = mockk<GoogleGenAiClientProviderPort>(relaxed = true)
    private val okHttpClient = mockk<OkHttpClient>(relaxed = true)

    @Test
    fun testAdapterCreation() {
        val adapter = GoogleVideoGenerationAdapter(okHttpClient, clientProvider, 0L)
        assertNotNull(adapter)
    }

    @Test
    fun generateVideo_success_callsSdkGenerateVideos() = runTest {
        val configSlot = slot<GenerateVideosConfig>()
        val mockClient = spyk(Client.builder().apiKey("key").build())
        
        val video = mockk<Video>()
        every { video.uri() } returns java.util.Optional.of("https://video-uri")
        
        val genVideo = mockk<GeneratedVideo>()
        every { genVideo.video() } returns java.util.Optional.of(video)

        val adapter = object : GoogleVideoGenerationAdapter(okHttpClient, clientProvider, 0L) {
            override fun generateVideos(
                client: Client,
                modelId: String,
                source: GenerateVideosSource,
                config: GenerateVideosConfig
            ): GenerateVideosOperation {
                configSlot.captured = config
                
                val finishedOp = mockk<GenerateVideosOperation>()
                every { finishedOp.name() } returns java.util.Optional.of("operation-name")
                every { finishedOp.done() } returns java.util.Optional.of(true)
                every { finishedOp.response() } returns java.util.Optional.of(
                    GenerateVideosResponse.builder()
                        .generatedVideos(listOf(genVideo))
                        .build()
                )
                every { finishedOp.error() } returns java.util.Optional.empty()
                
                return finishedOp
            }
            
            override suspend fun downloadVideo(url: String, apiKey: String): ByteArray {
                return "video-data".toByteArray()
            }
        }
        every { clientProvider.getClient(any(), any(), any(), any()) } returns mockClient
        
        val mockModels = mockk<com.google.genai.Models>(relaxed = true)
        val modelsField = mockClient.javaClass.getDeclaredField("models")
        modelsField.isAccessible = true
        modelsField.set(mockClient, mockModels)
        
        val opsField = mockClient.javaClass.getDeclaredField("operations")
        opsField.isAccessible = true
        val mockOps = mockk<com.google.genai.Operations>(relaxed = true)
        opsField.set(mockClient, mockOps)
        
        // Ensure getVideosOperation returns the finished operation when polled
        every { mockOps.getVideosOperation(any(), any()) } answers { firstArg() }

        val result = adapter.generateVideo(
            prompt = "cinematic sunset",
            apiKey = "key",
            modelId = "veo-3.1-generate-preview",
            baseUrl = null,
            settings = VideoGenerationSettings(
                aspectRatio = AspectRatio.SIXTEEN_NINE,
                videoDuration = 10,
                videoResolution = "1080p"
            ),
        )

        assertTrue(result.isSuccess)
        assertArrayEquals("video-data".toByteArray(), result.getOrThrow())
        assertEquals("16:9", configSlot.captured.aspectRatio().get())
        assertEquals(10, configSlot.captured.durationSeconds().get())
    }
}
