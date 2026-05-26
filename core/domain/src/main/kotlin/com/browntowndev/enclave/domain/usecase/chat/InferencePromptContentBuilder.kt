package com.browntowndev.enclave.domain.usecase.chat

object InferencePromptContentBuilder {
    fun buildUserContent(
        prompt: String,
        volatileTurnContext: String?,
    ): String =
        buildString {
            val context = volatileTurnContext?.trim()?.takeIf { it.isNotEmpty() }
            if (context != null) {
                append(context)
                append("\n\n")
            }
            append(prompt)
        }
}
