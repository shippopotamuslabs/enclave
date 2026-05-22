package com.browntowndev.enclave.domain.usecase.byok

import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.port.repository.ApiModelRepositoryPort
import javax.inject.Inject

interface SaveApiCredentialsUseCase {
    suspend operator fun invoke(
        credentials: ApiCredentials,
        apiKey: String,
        sourceCredentialAlias: String? = null,
    ): ApiCredentialsId
}

class SaveApiCredentialsUseCaseImpl
    @Inject
    constructor(
        private val apiModelRepository: ApiModelRepositoryPort,
    ) : SaveApiCredentialsUseCase {
        override suspend fun invoke(
            credentials: ApiCredentials,
            apiKey: String,
            sourceCredentialAlias: String?,
        ): ApiCredentialsId =
            apiModelRepository.saveCredentials(
                credentials = credentials,
                apiKey = apiKey,
                sourceCredentialAlias = sourceCredentialAlias,
            )
    }
