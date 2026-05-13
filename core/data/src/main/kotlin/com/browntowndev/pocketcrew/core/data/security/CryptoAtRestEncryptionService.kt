package com.browntowndev.pocketcrew.core.data.security

import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoAtRestEncryptionService
    @Inject
    constructor(
        private val cryptoManager: CryptoManagerPort,
    ) : LocalDataEncryptionService {
        override fun encryptString(plaintext: String): String {
            requireUnlocked()
            if (isEncrypted(plaintext)) return plaintext
            val ciphertext = cryptoManager.encrypt(plaintext).getOrThrow()
            return "$ENVELOPE_PREFIX${cryptoManager.encodeBase64(ciphertext)}"
        }

        override fun decryptString(stored: String): String {
            requireUnlocked()
            if (!isEncrypted(stored)) return stored
            val payload = stored.removePrefix(ENVELOPE_PREFIX)
            val ciphertext = cryptoManager.decodeBase64(payload)
            return cryptoManager.decrypt(ciphertext).getOrThrow()
        }

        override fun isEncrypted(stored: String): Boolean = stored.startsWith(ENVELOPE_PREFIX)

        private fun requireUnlocked() {
            check(cryptoManager.hasCachedKey()) { "Encryption session locked" }
        }

        companion object {
            const val ENVELOPE_PREFIX = "PCENC:v1:aes-gcm:"
        }
    }
