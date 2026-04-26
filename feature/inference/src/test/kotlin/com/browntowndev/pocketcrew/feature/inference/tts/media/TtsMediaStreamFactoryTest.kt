package com.browntowndev.pocketcrew.feature.inference.tts.media

import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.port.media.TtsMediaStreamFactoryPort
import com.browntowndev.pocketcrew.feature.inference.AndroidLoggingAdapter
import com.browntowndev.pocketcrew.feature.inference.GoogleGenAiClientProviderPort
import com.browntowndev.pocketcrew.feature.inference.OpenAiClientProviderPort
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TtsMediaStreamFactoryTest {

    private val httpClient: OkHttpClient = OkHttpClient()
    private val openAiClientProvider: OpenAiClientProviderPort = mockk()
    private val googleClientProvider: GoogleGenAiClientProviderPort = mockk()
    private val logger: AndroidLoggingAdapter = mockk(relaxed = true)
    private lateinit var factory: TtsMediaStreamFactory

    @BeforeEach
    fun setUp() {
        factory = TtsMediaStreamFactory(
            httpClient = httpClient,
            openAiClientProvider = openAiClientProvider,
            googleClientProvider = googleClientProvider,
            logger = logger,
        )
    }

    @Test
    fun `create returns service for OPENAI provider`() {
        val service = factory.create(ApiProvider.OPENAI, "sk-test", null)
        assertNotNull(service)
        assertTrue(service is OpenAiMediaStreamService)
    }

    @Test
    fun `create returns service for XAI provider`() {
        val service = factory.create(ApiProvider.XAI, "xai-test", null)
        assertNotNull(service)
        assertTrue(service is XAiMediaStreamService)
    }

    @Test
    fun `create returns service for GOOGLE provider`() {
        val service = factory.create(ApiProvider.GOOGLE, "google-test", null)
        assertNotNull(service)
        assertTrue(service is GoogleMediaStreamService)
    }

    @Test
    fun `create returns null for unsupported provider`() {
        val service = factory.create(ApiProvider.ANTHROPIC, "key", null)
        assertNull(service)
    }

    @Test
    fun `create returns null for OPENROUTER provider`() {
        val service = factory.create(ApiProvider.OPENROUTER, "key", null)
        assertNull(service)
    }
}