package com.browntowndev.enclave.feature.inference.tts

import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.feature.inference.FakeFalService
import com.browntowndev.enclave.feature.inference.FakeGoogleGenAiClientProviderPort
import com.browntowndev.enclave.feature.inference.FakeOpenAiClientProviderPort
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TtsServiceFactoryTest {
    private val factory =
        TtsServiceFactory(
            httpClient = OkHttpClient(),
            openAiClientProvider = FakeOpenAiClientProviderPort(),
            googleClientProvider = FakeGoogleGenAiClientProviderPort(),
            logger = FakeLoggingPort(),
            falService = FakeFalService(),
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
