package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.inference.LiveVoiceToolset
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.media.LiveVoicePort
import com.browntowndev.enclave.domain.port.security.ApiKeyProviderPort
import com.browntowndev.enclave.domain.usecase.settings.ResolveAssignedModelSelectionUseCase
import javax.inject.Inject

class LiveVoiceSessionUseCase
    @Inject
    constructor(
        private val resolveAssignedModelSelectionUseCase: ResolveAssignedModelSelectionUseCase,
        private val liveVoicePort: LiveVoicePort,
        private val apiKeyProvider: ApiKeyProviderPort,
    ) {
        suspend operator fun invoke(): Result<LiveVoiceSessionOpenResult> {
            val selection =
                resolveAssignedModelSelectionUseCase(ModelType.LIVE_VOICE)
                    ?: return Result.failure(IllegalStateException("No Live Voice provider assigned."))

            val asset =
                selection.liveVoiceAsset
                    ?: return Result.failure(IllegalStateException("Assigned Live Voice selection is invalid."))

            val apiKey =
                apiKeyProvider.getApiKey(asset.credentialAlias)
                    ?: return Result.failure(
                        IllegalStateException("API key not found for Live Voice provider: ${asset.displayName}"),
                    )

            return liveVoicePort
                .openSession(asset, apiKey, tools = LiveVoiceToolset.tools)
                .map { session ->
                    LiveVoiceSessionOpenResult(session = session, asset = asset)
                }
        }
    }
