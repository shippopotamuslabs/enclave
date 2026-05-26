package com.browntowndev.enclave.feature.inference.appendonly

/**
 * Provider-agnostic normalized message used for append-only digest and log sync.
 * Excludes volatile sidecar SYSTEM messages.
 */
internal data class ProviderNormalizedMessage(
    val role: String,
    val content: String?,
    val toolCalls: String? = null,
    val toolCallId: String? = null,
    val name: String? = null,
    val id: String? = null,
)
