package com.browntowndev.enclave.feature.inference.anthropic

internal object AnthropicSdkBaseUrl {
    private const val VERSION_SEGMENT = "/v1"

    fun normalize(baseUrl: String?): String? {
        val trimmed = baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: return null
        return if (trimmed.endsWith(VERSION_SEGMENT, ignoreCase = true)) {
            trimmed.removeSuffix(trimmed.takeLast(VERSION_SEGMENT.length))
        } else {
            trimmed
        }
    }
}
