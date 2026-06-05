package com.browntowndev.enclave.domain.model.download

/**
 * How strictly on-disk pack members are verified.
 *
 * [SIZE_ON_DISK] is for UI and scheduling (Settings, re-download gating).
 * [SHA256_INTEGRITY] is for post-download integrity checks only.
 */
enum class ModelPackVerificationDepth {
    SIZE_ON_DISK,
    SHA256_INTEGRITY,
}
