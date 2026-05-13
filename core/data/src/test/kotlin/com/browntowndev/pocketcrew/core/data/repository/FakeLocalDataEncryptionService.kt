package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.security.LocalDataEncryptionService

internal class FakeLocalDataEncryptionService : LocalDataEncryptionService {
    override fun encryptString(plaintext: String): String = "ENC:v1:$plaintext"

    override fun decryptString(stored: String): String = stored.removePrefix("ENC:v1:")

    override fun isEncrypted(stored: String): Boolean = stored.startsWith("ENC:v1:")
}
