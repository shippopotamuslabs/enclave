package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.usecase.byok.FetchApiProviderModelsUseCase
import javax.inject.Inject

class TestApiProviderConnectionUseCase
    @Inject
    constructor(
        private val fetchApiProviderModelsUseCase: FetchApiProviderModelsUseCase,
    ) {
        suspend operator fun invoke(
            provider: ApiProvider,
            currentApiKey: String,
            credentialAlias: String?,
            baseUrl: String?,
        ): Result<Unit> =
            runCatching {
                fetchApiProviderModelsUseCase(
                    provider = provider,
                    currentApiKey = currentApiKey,
                    credentialAlias = credentialAlias,
                    baseUrl = baseUrl,
                )
            }.map { }
    }
