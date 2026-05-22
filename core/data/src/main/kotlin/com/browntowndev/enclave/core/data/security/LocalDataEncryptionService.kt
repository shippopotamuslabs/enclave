package com.browntowndev.enclave.core.data.security

interface LocalDataEncryptionService {
    fun encryptString(
        plaintext: String,
        aad: ByteArray,
    ): String

    fun decryptString(
        stored: String,
        aad: ByteArray,
    ): String

    fun isEncrypted(stored: String): Boolean

    fun isUnlocked(): Boolean = true

    fun encryptString(plaintext: String): String = encryptString(plaintext, ByteArray(0))

    fun decryptString(stored: String): String = decryptString(stored, ByteArray(0))

    fun encryptNullable(
        plaintext: String?,
        aad: ByteArray,
    ): String? = plaintext?.let { encryptString(it, aad) }

    fun decryptNullable(
        stored: String?,
        aad: ByteArray,
    ): String? = stored?.let { decryptString(it, aad) }

    fun encryptNullable(plaintext: String?): String? = plaintext?.let { encryptString(it) }

    fun decryptNullable(stored: String?): String? = stored?.let { decryptString(it) }

    object NoOp : LocalDataEncryptionService {
        override fun encryptString(
            plaintext: String,
            aad: ByteArray,
        ): String = plaintext

        override fun decryptString(
            stored: String,
            aad: ByteArray,
        ): String = stored

        override fun isEncrypted(stored: String): Boolean = false

        override fun isUnlocked(): Boolean = true
    }
}
