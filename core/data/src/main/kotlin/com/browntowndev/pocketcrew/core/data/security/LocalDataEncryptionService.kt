package com.browntowndev.pocketcrew.core.data.security

interface LocalDataEncryptionService {
    fun encryptString(plaintext: String): String

    fun decryptString(stored: String): String

    fun isEncrypted(stored: String): Boolean

    fun isUnlocked(): Boolean = true

    fun encryptNullable(plaintext: String?): String? = plaintext?.let(::encryptString)

    fun decryptNullable(stored: String?): String? = stored?.let(::decryptString)

    object NoOp : LocalDataEncryptionService {
        override fun encryptString(plaintext: String): String = plaintext

        override fun decryptString(stored: String): String = stored

        override fun isEncrypted(stored: String): Boolean = false

        override fun isUnlocked(): Boolean = true
    }
}
