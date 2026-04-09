package com.browntowndev.pocketcrew.core.data.local

import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import java.security.MessageDigest

internal fun buildApiCredentialsIdentitySignature(
    provider: ApiProvider,
    modelId: String,
    baseUrl: String?,
    apiKey: String,
): String = buildApiCredentialsIdentitySignature(
    provider = provider.name,
    modelId = modelId,
    baseUrl = baseUrl,
    apiKey = apiKey,
)

internal fun buildApiCredentialsIdentitySignature(
    provider: String,
    modelId: String,
    baseUrl: String?,
    apiKey: String,
): String {
    val normalizedBaseUrl = baseUrl.normalizedBaseUrl()
    val input = buildString {
        append(provider.trim())
        append('|')
        append(modelId.trim())
        append('|')
        append(normalizedBaseUrl)
        append('|')
        append(apiKey)
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

internal fun String?.normalizedBaseUrl(): String = this?.trim().orEmpty()
