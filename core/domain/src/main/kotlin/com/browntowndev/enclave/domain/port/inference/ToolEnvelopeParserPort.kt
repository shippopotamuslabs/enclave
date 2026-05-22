package com.browntowndev.enclave.domain.port.inference

import com.browntowndev.enclave.domain.model.inference.ToolEnvelopeBatch
import com.browntowndev.enclave.domain.model.inference.ToolEnvelopeField

/**
 * Pure-domain port exposing the canonical tool-call parser implemented in
 * Rust `agent_core`. The `:core:agent` module provides the UniFFI-backed
 * adapter; tests provide a Fake.
 */
interface ToolEnvelopeParserPort {
    fun parse(raw: String): ToolEnvelopeBatch

    fun coerceProviderArguments(
        toolName: String,
        argsJson: String,
    ): ToolEnvelopeField

    fun hasLocalToolContract(systemPrompt: String?): Boolean

    fun buildToolResultMessage(resultJson: String): String
}
