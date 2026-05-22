package com.browntowndev.enclave.core.data.security

enum class MalformedEnvelopeReason { BAD_PREFIX, INVALID_BASE64, PAYLOAD_TOO_SHORT, PAYLOAD_TOO_LARGE }

sealed class EnvelopeParse {
    data class Ok(
        val iv: ByteArray,
        val ciphertext: ByteArray,
    ) : EnvelopeParse()

    data class Malformed(
        val reason: MalformedEnvelopeReason,
    ) : EnvelopeParse()

    data object NotEnveloped : EnvelopeParse()
}

class MalformedEnvelopeException(
    val reason: MalformedEnvelopeReason,
) : Exception("Malformed envelope: ${reason.name}")

class DecryptFailedException(
    message: String,
    cause: Throwable?,
) : Exception(message, cause)

class EncryptionLockedException(
    message: String,
) : IllegalStateException(message)
