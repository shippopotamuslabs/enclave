package com.browntowndev.enclave.feature.inference.appendonly

/**
 * Position-aware frozen sidecar for wire replay at original mapped insert index.
 * `markerId` is the UUID stamp frozen at live-ship time and replayed
 * byte-identically on every subsequent turn (INV-1).
 */
internal data class FrozenSidecarSnapshot(
    val text: String,
    val markerId: String,
    val mappedInsertIndex: Int,
)
