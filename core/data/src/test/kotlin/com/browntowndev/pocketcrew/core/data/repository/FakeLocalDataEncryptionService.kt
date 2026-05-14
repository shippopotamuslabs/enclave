package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.security.CryptoAtRestEncryptionService
import com.browntowndev.pocketcrew.core.data.security.LocalDataEncryptionService
import com.browntowndev.pocketcrew.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import io.mockk.mockk

internal class FakeLocalDataEncryptionService : LocalDataEncryptionService {
    var lastEncryptAad: ByteArray? = null
    var lastDecryptAad: ByteArray? = null

    override fun encryptString(plaintext: String): String =
        "${CryptoAtRestEncryptionService.ENVELOPE_PREFIX}$plaintext"

    override fun decryptString(stored: String): String =
        stored.removePrefix(CryptoAtRestEncryptionService.ENVELOPE_PREFIX)

    override fun isEncrypted(stored: String): Boolean =
        stored.startsWith(CryptoAtRestEncryptionService.ENVELOPE_PREFIX)

    fun encryptString(plaintext: String, aad: ByteArray): String {
        lastEncryptAad = aad
        return encryptString(plaintext)
    }

    fun decryptString(stored: String, aad: ByteArray): String {
        lastDecryptAad = aad
        return decryptString(stored)
    }
}

internal fun testSensitiveSelfHealer(
    localEncryption: LocalDataEncryptionService = FakeLocalDataEncryptionService(),
    loggingPort: LoggingPort = mockk(relaxed = true),
): SensitivePersistenceSelfHealer = SensitivePersistenceSelfHealer(localEncryption, loggingPort)
