package com.browntowndev.enclave.feature.inference.fal

import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.media.ModelGenerationOutputSchema
import com.browntowndev.enclave.domain.port.repository.ApiModelCatalogPort
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FalOutputSchemaResolver
    @Inject
    constructor(
        private val apiModelCatalog: ApiModelCatalogPort,
    ) {
        private val cache = ConcurrentHashMap<String, ModelGenerationOutputSchema?>()

        suspend fun resolve(
            modelId: String,
            apiKey: String,
            baseUrl: String?,
        ): ModelGenerationOutputSchema? {
            val trimmedModelId = modelId.trim()
            if (trimmedModelId.isEmpty()) return null
            val cacheKey = "$trimmedModelId|${baseUrl.orEmpty().trim()}"
            return cache.getOrPut(cacheKey) {
                runCatching {
                    apiModelCatalog
                        .fetchModel(
                            provider = ApiProvider.FAL,
                            apiKey = apiKey,
                            modelId = trimmedModelId,
                            baseUrl = baseUrl,
                        )?.outputSchema
                }.getOrNull()
            }
        }
    }
