package com.browntowndev.enclave.feature.inference.appendonly

/**
 * Position-aware frozen sidecar for wire replay at original mapped insert index.
 */
internal data class FrozenSidecarSnapshot(
    val text: String,
    val mappedInsertIndex: Int,
)
