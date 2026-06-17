package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatMessage

data class LiteRtTelemetryContext(
    val history: List<ChatMessage>,
    val systemPrompt: String,
)
