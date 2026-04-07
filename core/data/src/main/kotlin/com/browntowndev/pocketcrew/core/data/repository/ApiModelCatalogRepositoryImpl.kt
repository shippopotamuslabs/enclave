package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.openai.OpenAiClientProvider
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelCatalogPort
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ApiModelCatalogRepositoryImpl @Inject constructor(
    private val openAiClientProvider: OpenAiClientProvider,
) : ApiModelCatalogPort {
    override suspend fun fetchModels(
        provider: ApiProvider,
        apiKey: String,
        baseUrl: String?
    ): List<String> = withContext(Dispatchers.IO) {
        require(
            provider == ApiProvider.OPENAI ||
                provider == ApiProvider.OPENROUTER ||
                provider == ApiProvider.XAI
        ) {
            "Model discovery is currently supported for OpenAI, OpenRouter, and xAI."
        }
        val resolvedBaseUrl = baseUrl?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl()
        val client = openAiClientProvider.getClient(
            apiKey = apiKey,
            baseUrl = resolvedBaseUrl,
        )

        client.models()
            .list()
            .data()
            .map { it.id() }
            .distinct()
            .sorted()
    }
}
