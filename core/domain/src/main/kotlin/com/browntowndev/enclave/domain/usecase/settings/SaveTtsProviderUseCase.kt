package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.config.TtsProviderAsset
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ApiProviderModelPolicy
import com.browntowndev.enclave.domain.port.repository.TtsProviderRepositoryPort
import javax.inject.Inject

class SaveTtsProviderUseCase
    @Inject
    constructor(
        private val repository: TtsProviderRepositoryPort,
    ) {
        suspend operator fun invoke(draft: TtsProviderDraft): Result<TtsProviderId> =
            Result.runCatching {
                if (draft.provider == ApiProvider.FAL) {
                    val modelName = draft.modelName?.trim().orEmpty()
                    require(modelName.isNotBlank()) {
                        "fal.ai TTS requires a model ID."
                    }
                    require(ApiProviderModelPolicy.isFalTtsModelCandidate(modelName)) {
                        "Model '$modelName' is not a supported fal.ai TTS endpoint."
                    }
                }
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
