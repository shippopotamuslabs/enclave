package com.browntowndev.enclave.domain.model.inference

/**
 * Domain models for Rust-owned streaming tool-marker holdback (TOOL-PARSE-002).
 */
data class ToolStreamGateHandle(
    val nativeHandle: Any,
)

data class ToolStreamGatePushResult(
    val emittedPrefix: String,
)

data class ToolStreamGateFinish(
    val trailingFlush: String,
    val hasToolMarker: Boolean,
)
