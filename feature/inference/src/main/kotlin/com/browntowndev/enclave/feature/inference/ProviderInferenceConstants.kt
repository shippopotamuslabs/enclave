package com.browntowndev.enclave.feature.inference

internal object ProviderInferenceConstants {
    const val XAI_CONVERSATION_ID_HEADER = "x-grok-conv-id"
    const val SYNTHETIC_API_ERROR_PREFIX = "Error: API Error"

    const val ANTHROPIC_THINKING_BUDGET_MEDIUM = 1536L
    const val ANTHROPIC_THINKING_BUDGET_XHIGH = 3072L
}
