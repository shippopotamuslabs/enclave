package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.config.TtsProviderAsset
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.port.repository.TtsProviderRepositoryPort
import javax.inject.Inject

class SaveTtsProviderUseCase
    @Inject
    constructor(
        private val repository: TtsProviderRepositoryPort,
    ) {
        suspend operator fun invoke(draft: TtsProviderDraft): Result<TtsProviderId> =
            Result.runCatching {
                val finalCredentialAlias = draft.sourceCredentialAlias?.takeIf { it.isNotBlank() } ?: draft.credentialAlias
                repository.saveTtsProvider(
                    asset =
                        TtsProviderAsset(
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
