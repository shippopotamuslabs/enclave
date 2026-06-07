package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.port.repository.LiveVoiceProviderRepositoryPort
import javax.inject.Inject

class SaveLiveVoiceProviderUseCase
    @Inject
    constructor(
        private val repository: LiveVoiceProviderRepositoryPort,
    ) {
        suspend operator fun invoke(draft: LiveVoiceProviderDraft): Result<LiveVoiceProviderId> =
            Result.runCatching {
                val finalCredentialAlias =
                    draft.sourceCredentialAlias?.takeIf { it.isNotBlank() } ?: draft.credentialAlias
                repository.saveLiveVoiceProvider(
                    asset =
                        LiveVoiceProviderAsset(
                            id = draft.id,
                            displayName = draft.displayName,
                            provider = draft.provider,
                            voiceName = draft.voiceName,
                            modelName = draft.modelName,
                            baseUrl = draft.baseUrl,
                            credentialAlias = finalCredentialAlias,
                        ),
                    apiKey = draft.apiKey.takeIf { it.isNotBlank() },
                )
            }
    }
