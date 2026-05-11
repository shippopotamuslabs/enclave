package com.browntowndev.pocketcrew.domain.port.security

interface ApiKeyProviderPort {
    suspend fun getApiKey(credentialAlias: String): String?
    fun saveApiKey(credentialAlias: String, apiKey: String)
}
