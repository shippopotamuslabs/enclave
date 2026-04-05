package com.browntowndev.pocketcrew.domain.port.security

interface ApiKeyProviderPort {
    fun getApiKey(credentialAlias: String): String?
}
