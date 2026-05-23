package com.browntowndev.enclave.core.data.local

import com.browntowndev.enclave.domain.model.inference.ApiProvider
import java.security.MessageDigest

internal fun buildApiCredentialsIdentitySignature(
    provider: String,
    baseUrl: String?,
    apiKey: String,
): String {
    val normalizedBaseUrl = baseUrl.normalizedBaseUrl()
    val input =
        buildString {
            append(provider.trim())
            append('|')
            append(normalizedBaseUrl)
            append('|')
            append(apiKey)
        }
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

internal fun buildApiCredentialsIdentitySignature(
    provider: ApiProvider,
    baseUrl: String?,
    apiKey: String,
): String =
    buildApiCredentialsIdentitySignature(
        provider = provider.name,
        baseUrl = baseUrl,
        apiKey = apiKey,
    )

internal fun String?.normalizedBaseUrl(): String = this?.trim().orEmpty()
