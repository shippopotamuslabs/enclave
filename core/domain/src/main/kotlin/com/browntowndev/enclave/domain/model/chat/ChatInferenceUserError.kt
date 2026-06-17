package com.browntowndev.enclave.domain.model.chat

enum class ChatInferenceErrorCategory {
    GENERIC,
    PROMPT_TOO_LARGE,
    BUSY,
    DECRYPT_FAILURE,
    RATE_LIMITED,
    AUTH_FAILURE,
    ACCESS_DENIED,
    ENDPOINT_NOT_FOUND,
    PROVIDER_UNAVAILABLE,
    TRANSPORT_FAILURE,
    INVALID_REQUEST,
    AGENT_LIMIT,
    TOKEN_LIMIT,
    AGENT_INTERNAL,
}

data class ChatInferenceUserError(
    val title: String,
    val detail: String? = null,
    val hint: String? = null,
    val category: ChatInferenceErrorCategory = ChatInferenceErrorCategory.GENERIC,
) {
    fun formatForMessage(): String =
        listOfNotNull(
            title.takeIf { it.isNotBlank() },
            detail?.takeIf { it.isNotBlank() },
            hint?.takeIf { it.isNotBlank() },
        ).joinToString(separator = "\n\n")
}
