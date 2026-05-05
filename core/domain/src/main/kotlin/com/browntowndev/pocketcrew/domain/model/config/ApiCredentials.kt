package com.browntowndev.pocketcrew.domain.model.config

import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider

data class ApiCredentials(
    val id: ApiCredentialsId,
    val displayName: String,
    val provider: ApiProvider,
    val modelId: String,
    val baseUrl: String? = null,
    val isMultimodal: Boolean = false,
    val credentialAlias: String,
    val encryptedApiKey: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ApiCredentials

        if (id != other.id) return false
        if (displayName != other.displayName) return false
        if (provider != other.provider) return false
        if (modelId != other.modelId) return false
        if (baseUrl != other.baseUrl) return false
        if (isMultimodal != other.isMultimodal) return false
        if (credentialAlias != other.credentialAlias) return false
        if (encryptedApiKey != null) {
            if (other.encryptedApiKey == null) return false
            if (!encryptedApiKey.contentEquals(other.encryptedApiKey)) return false
        } else if (other.encryptedApiKey != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + provider.hashCode()
        result = 31 * result + modelId.hashCode()
        result = 31 * result + (baseUrl?.hashCode() ?: 0)
        result = 31 * result + isMultimodal.hashCode()
        result = 31 * result + credentialAlias.hashCode()
        result = 31 * result + (encryptedApiKey?.contentHashCode() ?: 0)
        return result
    }
}