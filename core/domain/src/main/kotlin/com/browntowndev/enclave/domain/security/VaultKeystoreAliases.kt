package com.browntowndev.enclave.domain.security

/**
 * Single source of truth for AndroidKeyStore aliases used by the BiometricPort to wrap the
 * Data Encryption Key. Lives in the pure-Kotlin domain so that any layer (presentation, data,
 * or other use cases) can reference the alias without depending on the Android-specific port
 * implementation.
 */
object VaultKeystoreAliases {
    const val VAULT_DEK_SECURE_KEY: String = "vault_dek"
}
