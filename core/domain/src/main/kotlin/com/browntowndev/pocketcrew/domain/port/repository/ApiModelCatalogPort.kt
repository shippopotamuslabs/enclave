package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider

interface ApiModelCatalogPort {
    suspend fun fetchModels(
        provider: ApiProvider,
        apiKey: String,
        baseUrl: String? = null
    ): List<String>
}
