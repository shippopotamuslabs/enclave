package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.TtsAudioChunk
import com.browntowndev.enclave.domain.port.inference.StreamingTtsServicePort
import com.browntowndev.enclave.domain.port.inference.TtsServiceFactoryPort
import com.browntowndev.enclave.domain.port.inference.TtsServicePort
import com.browntowndev.enclave.domain.port.media.AudioPlayerPort
import com.browntowndev.enclave.domain.port.media.StreamingAudioConfig
import com.browntowndev.enclave.domain.port.media.StreamingAudioPlayerPort
import com.browntowndev.enclave.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.enclave.domain.port.media.TtsPlaybackStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Recording [TtsServicePort] fake (ENC-28): returns [synthesizeResult] for every call. */
class FakeTtsService : TtsServicePort {
    data class SynthesizeCall(
        val text: String,
        val voice: String,
        val modelId: String?,
    )

    val synthesizeCalls = mutableListOf<SynthesizeCall>()
    var synthesizeResult: Result<ByteArray> = Result.success(ByteArray(0))

    override suspend fun synthesizeSpeech(
        text: String,
        voice: String,
        modelId: String?,
    ): Result<ByteArray> {
        synthesizeCalls += SynthesizeCall(text, voice, modelId)
        return synthesizeResult
    }
}

/** Recording [StreamingTtsServicePort] fake (ENC-28): returns the [chunks] flow for every call. */
class FakeStreamingTtsService : StreamingTtsServicePort {
    data class SynthesizeStreamingCall(
        val text: String,
        val voice: String,
        val modelId: String?,
    )

    val synthesizeStreamingCalls = mutableListOf<SynthesizeStreamingCall>()
    var chunks: Flow<TtsAudioChunk> = emptyFlow()

    override fun synthesizeSpeechStreaming(
        text: String,
        voice: String,
        modelId: String?,
    ): Flow<TtsAudioChunk> {
        synthesizeStreamingCalls += SynthesizeStreamingCall(text, voice, modelId)
        return chunks
    }
}

/** Recording [TtsServiceFactoryPort] fake (ENC-28): hands out the configured services. */
class FakeTtsServiceFactory : TtsServiceFactoryPort {
    data class CreateCall(
        val provider: ApiProvider,
        val apiKey: String,
        val baseUrl: String?,
    )

    val createCalls = mutableListOf<CreateCall>()
    val createStreamingCalls = mutableListOf<CreateCall>()

    var batchService: TtsServicePort = FakeTtsService()
    var streamingService: StreamingTtsServicePort? = null

    override fun create(
        provider: ApiProvider,
        apiKey: String,
        baseUrl: String?,
    ): TtsServicePort {
        createCalls += CreateCall(provider, apiKey, baseUrl)
        return batchService
    }

    override fun createStreaming(
        provider: ApiProvider,
        apiKey: String,
        baseUrl: String?,
    ): StreamingTtsServicePort? {
        createStreamingCalls += CreateCall(provider, apiKey, baseUrl)
        return streamingService
    }
}

/** Recording [AudioPlayerPort] fake (ENC-28). */
class FakeAudioPlayer : AudioPlayerPort {
    val playAudioCalls = mutableListOf<ByteArray>()

    var stopCallCount = 0
        private set

    override suspend fun playAudio(audioBytes: ByteArray) {
        playAudioCalls += audioBytes
    }

    override fun stop() {
        stopCallCount++
    }
}

/** Recording [StreamingAudioPlayerPort] fake (ENC-28). */
class FakeStreamingAudioPlayer : StreamingAudioPlayerPort {
    val initializeCalls = mutableListOf<StreamingAudioConfig>()
    val enqueuedChunks = mutableListOf<ByteArray>()

    var startPlaybackCallCount = 0
        private set

    var stopCallCount = 0
        private set

    private var initialized = false

    override suspend fun initialize(config: StreamingAudioConfig) {
        initializeCalls += config
        initialized = true
    }

    override suspend fun enqueueChunk(audioChunk: ByteArray) {
        enqueuedChunks += audioChunk
    }

    override fun startPlayback() {
        startPlaybackCallCount++
    }

    override fun stop() {
        stopCallCount++
        initialized = false
    }

    override fun isInitialized(): Boolean = initialized
}
