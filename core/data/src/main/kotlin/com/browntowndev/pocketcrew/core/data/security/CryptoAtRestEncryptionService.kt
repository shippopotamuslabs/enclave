package com.browntowndev.pocketcrew.core.data.security

import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts and decrypts strings at rest using the PCENC:v1:aes-gcm: envelope format.
 * No envelope version bump because the app is unreleased and legacy ciphertext is intentionally unsupported.
 */
@Singleton
class CryptoAtRestEncryptionService
    @Inject
    constructor(
        private val cryptoManager: CryptoManagerPort,
    ) : LocalDataEncryptionService {
        override fun encryptString(
            plaintext: String,
            aad: ByteArray,
        ): String {
            requireUnlocked()
            if (isEncrypted(plaintext)) return plaintext
            val ciphertext = cryptoManager.encrypt(plaintext, aad).getOrThrow()
            return "$ENVELOPE_PREFIX${cryptoManager.encodeBase64(ciphertext)}"
        }

        override fun decryptString(
            stored: String,
            aad: ByteArray,
        ): String {
            requireUnlocked()
            if (!isEncrypted(stored)) return stored
            val envelope = parseEnvelope(stored)
            val payload =
                when (envelope) {
                    is EnvelopeParse.Ok -> {
                        val combined = ByteArray(IV_LENGTH + envelope.ciphertext.size)
                        System.arraycopy(envelope.iv, 0, combined, 0, IV_LENGTH)
                        System.arraycopy(envelope.ciphertext, 0, combined, IV_LENGTH, envelope.ciphertext.size)
                        combined
                    }
                    is EnvelopeParse.Malformed -> throw MalformedEnvelopeException(envelope.reason)
                    EnvelopeParse.NotEnveloped -> return stored
                }
            return cryptoManager.decrypt(payload, aad).getOrThrow()
        }

        fun parseEnvelope(stored: String): EnvelopeParse {
            if (!stored.startsWith(ENVELOPE_PREFIX)) {
                return when {
                    stored.startsWith("PCENC:") -> EnvelopeParse.Malformed(MalformedEnvelopeReason.BAD_PREFIX)
                    else -> EnvelopeParse.NotEnveloped
                }
            }
            return parseEnvelopePayload(stored.substring(ENVELOPE_PREFIX.length))
        }

        private fun parseEnvelopePayload(payload: String): EnvelopeParse {
            // Size cap check on base64 string length to avoid allocating huge arrays
            if (payload.length.toLong() * 3L / 4L > ENVELOPE_MAX_DECODED_BYTES) {
                return EnvelopeParse.Malformed(MalformedEnvelopeReason.PAYLOAD_TOO_LARGE)
            }

            val decoded =
                try {
                    java.util.Base64
                        .getDecoder()
                        .decode(payload)
                } catch (_: IllegalArgumentException) {
                    return EnvelopeParse.Malformed(MalformedEnvelopeReason.INVALID_BASE64)
                }

            return when {
                decoded.size > ENVELOPE_MAX_DECODED_BYTES ->
                    EnvelopeParse.Malformed(MalformedEnvelopeReason.PAYLOAD_TOO_LARGE)
                decoded.size < MIN_PAYLOAD_BYTES ->
                    EnvelopeParse.Malformed(MalformedEnvelopeReason.PAYLOAD_TOO_SHORT)
                else -> {
                    val iv = decoded.copyOfRange(0, IV_LENGTH)
                    val ciphertext = decoded.copyOfRange(IV_LENGTH, decoded.size)
                    EnvelopeParse.Ok(iv, ciphertext)
                }
            }
        }

        override fun isEncrypted(stored: String): Boolean = parseEnvelope(stored) is EnvelopeParse.Ok

        override fun isUnlocked(): Boolean = cryptoManager.hasCachedKey()

        private fun requireUnlocked() {
            if (!cryptoManager.hasCachedKey()) throw EncryptionLockedException("Encryption session locked")
        }

        companion object {
            const val ENVELOPE_PREFIX = "PCENC:v1:aes-gcm:"
            const val ENVELOPE_MAX_DECODED_BYTES = 16777216
            private const val MIN_PAYLOAD_BYTES = 28
            private const val IV_LENGTH = 12
        }
    }
