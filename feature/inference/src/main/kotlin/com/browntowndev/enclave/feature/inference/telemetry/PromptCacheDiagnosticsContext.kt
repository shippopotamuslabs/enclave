package com.browntowndev.enclave.feature.inference.telemetry

import com.browntowndev.enclave.domain.model.chat.ChatId

/**
 * Per-turn inputs for [PromptCachePrefixDiagnostics] logging on implicit-cache providers.
 */
data class PromptCacheDiagnosticsContext(
    val requestModelId: String,
    val chatId: ChatId?,
    val systemPrompt: String,
    val toolNames: List<String>,
    val frozenSidecarCount: Int,
    val liveSidecarPresent: Boolean,
    val sessionMessageCount: Int,
)
