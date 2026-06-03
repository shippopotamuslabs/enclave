package com.browntowndev.enclave.feature.inference.tts

import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.feature.inference.GoogleGenAiClientProviderPort
import com.browntowndev.enclave.feature.inference.OpenAiClientProviderPort
import com.browntowndev.enclave.feature.inference.fal.FalService
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TtsServiceFactoryTest {
    private val factory =
        TtsServiceFactory(
            httpClient = OkHttpClient(),
            openAiClientProvider = mockk<OpenAiClientProviderPort>(),
            googleClientProvider = mockk<GoogleGenAiClientProviderPort>(),
            logger = mockk<LoggingPort>(relaxed = true),
            falService = mockk<FalService>(),
        )

    @Test
    fun create_falProvider_returnsFalTtsService() {
        assertTrue(factory.create(ApiProvider.FAL, "key", null) is FalTtsService)
    }

    @Test
    fun createStreaming_falProvider_returnsNullForBatchFallback() {
        assertNull(factory.createStreaming(ApiProvider.FAL, "key", null))
    }
}
