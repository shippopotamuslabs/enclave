package com.browntowndev.enclave.domain.model.config

/**
 * Per-million-token USD rates captured from provider model discovery at BYOK setup time.
 */
data class ApiModelSavedPricing(
    val promptUsdPerMillion: Double? = null,
    val completionUsdPerMillion: Double? = null,
    val cachedPromptUsdPerMillion: Double? = null,
) {
    fun hasPromptAndCompletionRates(): Boolean =
        promptUsdPerMillion != null && completionUsdPerMillion != null
}
