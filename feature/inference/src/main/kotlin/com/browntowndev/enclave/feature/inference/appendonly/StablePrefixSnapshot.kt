package com.browntowndev.enclave.feature.inference.appendonly

internal data class StablePrefixSnapshot(
    val systemPrompt: String,
    val toolsFingerprint: String,
    val version: Int,
)
