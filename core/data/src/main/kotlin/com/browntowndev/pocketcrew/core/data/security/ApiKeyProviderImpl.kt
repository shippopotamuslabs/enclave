package com.browntowndev.pocketcrew.core.data.security

import com.browntowndev.pocketcrew.domain.port.security.ApiKeyProviderPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyProviderImpl @Inject constructor(
    private val apiKeyManager: ApiKeyManager
) : ApiKeyProviderPort {
    override suspend fun getApiKey(credentialAlias: String): String? {
        return apiKeyManager.get(credentialAlias)
    }

    override fun saveApiKey(credentialAlias: String, apiKey: String) {
        apiKeyManager.save(credentialAlias, apiKey)
    }
}
