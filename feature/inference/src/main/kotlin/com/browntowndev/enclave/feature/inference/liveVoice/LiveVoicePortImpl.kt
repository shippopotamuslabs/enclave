package com.browntowndev.enclave.feature.inference.liveVoice

import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.media.LiveVoicePort
import com.browntowndev.enclave.domain.port.media.LiveVoiceSession
import javax.inject.Inject

class LiveVoicePortImpl
    @Inject
    constructor() : LiveVoicePort {
        override suspend fun openSession(
            asset: LiveVoiceProviderAsset,
            apiKey: String,
            tools: List<ToolDefinition>,
        ): Result<LiveVoiceSession> =
            when (asset.provider) {
                ApiProvider.OPENAI, ApiProvider.XAI ->
                    Result.success(OpenAiRealtimeSession(asset, apiKey, tools))

                ApiProvider.GOOGLE ->
                    Result.success(GeminiLiveSession(asset, apiKey, tools))

                else ->
                    Result.failure(
                        IllegalStateException(
                            "Live Voice not supported for provider: ${asset.provider.name}",
                        ),
                    )
            }
    }
