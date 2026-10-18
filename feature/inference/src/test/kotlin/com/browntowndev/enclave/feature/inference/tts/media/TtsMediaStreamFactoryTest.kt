package com.browntowndev.enclave.feature.inference.tts.media

import com.browntowndev.enclave.core.testing.FakeAutomaticErrorTrackingGate
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.feature.inference.AndroidLoggingAdapter
import com.browntowndev.enclave.feature.inference.FakeGoogleGenAiClientProviderPort
import com.browntowndev.enclave.feature.inference.FakeOpenAiClientProviderPort
import com.browntowndev.enclave.feature.inference.GoogleGenAiClientProviderPort
import com.browntowndev.enclave.feature.inference.OpenAiClientProviderPort
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TtsMediaStreamFactoryTest {
    private val httpClient: OkHttpClient = OkHttpClient()
    private val openAiClientProvider: OpenAiClientProviderPort = FakeOpenAiClientProviderPort()
    private val googleClientProvider: GoogleGenAiClientProviderPort = FakeGoogleGenAiClientProviderPort()
    private val logger: AndroidLoggingAdapter = AndroidLoggingAdapter(FakeAutomaticErrorTrackingGate())
    private lateinit var factory: TtsMediaStreamFactory

    @BeforeEach
    fun setUp() {
        factory =
            TtsMediaStreamFactory(
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
