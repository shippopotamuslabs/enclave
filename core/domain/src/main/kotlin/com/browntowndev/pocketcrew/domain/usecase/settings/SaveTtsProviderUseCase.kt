package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.model.config.TtsProviderAsset
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import com.browntowndev.pocketcrew.domain.port.repository.TtsProviderRepositoryPort
import javax.inject.Inject

class SaveTtsProviderUseCase @Inject constructor(
    private val repository: TtsProviderRepositoryPort,
) {
    suspend operator fun invoke(draft: TtsProviderDraft): Result<TtsProviderId> {
        return Result.runCatching {
            repository.saveTtsProvider(
                asset = TtsProviderAsset(
                    id = draft.id,
                    displayName = draft.displayName,
                    provider = draft.provider,
                    voiceName = draft.voiceName,
                    baseUrl = draft.baseUrl,
                    credentialAlias = draft.credentialAlias,
                ),
                apiKey = draft.apiKey.takeIf { it.isNotBlank() },
            )
        }
    }
}
