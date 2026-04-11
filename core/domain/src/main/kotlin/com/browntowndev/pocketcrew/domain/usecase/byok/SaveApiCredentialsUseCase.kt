package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import javax.inject.Inject

interface SaveApiCredentialsUseCase {
    suspend operator fun invoke(
        credentials: ApiCredentials,
        apiKey: String,
        sourceCredentialAlias: String? = null
    ): ApiCredentialsId
}

class SaveApiCredentialsUseCaseImpl @Inject constructor(
    private val apiModelRepository: ApiModelRepositoryPort,
) : SaveApiCredentialsUseCase {
    override suspend fun invoke(
        credentials: ApiCredentials,
        apiKey: String,
        sourceCredentialAlias: String?
    ): ApiCredentialsId {
        return apiModelRepository.saveCredentials(
            credentials = credentials,
            apiKey = apiKey,
            sourceCredentialAlias = sourceCredentialAlias,
        )
    }
}
