package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.port.repository.ApiModelCatalogResult
import com.browntowndev.enclave.domain.usecase.byok.FetchApiProviderModelDetailUseCase
import com.browntowndev.enclave.domain.usecase.byok.FetchApiProviderModelsUseCase

/** Recording [FetchApiProviderModelsUseCase] fake (ENC-28). Returns [result] verbatim. */
class FakeFetchApiProviderModelsUseCase(
    var result: ApiModelCatalogResult = ApiModelCatalogResult(models = emptyList()),
) : FetchApiProviderModelsUseCase {
    data class Call(
        val provider: ApiProvider,
        val currentApiKey: String,
        val credentialAlias: String?,
        val baseUrl: String?,
    )

    val calls = mutableListOf<Call>()

    override suspend fun invoke(
        provider: ApiProvider,
        currentApiKey: String,
        credentialAlias: String?,
        baseUrl: String?,
    ): ApiModelCatalogResult {
        calls += Call(provider, currentApiKey, credentialAlias, baseUrl)
        return result
    }
}

/** Recording [FetchApiProviderModelDetailUseCase] fake (ENC-28). Returns [detail] verbatim. */
class FakeFetchApiProviderModelDetailUseCase(
    var detail: DiscoveredApiModel? = null,
) : FetchApiProviderModelDetailUseCase {
    data class Call(
        val provider: ApiProvider,
        val modelId: String,
        val currentApiKey: String,
        val credentialAlias: String?,
        val baseUrl: String?,
    )

    val calls = mutableListOf<Call>()

    override suspend fun invoke(
        provider: ApiProvider,
        modelId: String,
        currentApiKey: String,
        credentialAlias: String?,
        baseUrl: String?,
    ): DiscoveredApiModel? {
        calls += Call(provider, modelId, currentApiKey, credentialAlias, baseUrl)
        return detail
    }
}
