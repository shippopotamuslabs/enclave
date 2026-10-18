package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.security.CryptoAtRestEncryptionService
import com.browntowndev.enclave.core.data.security.LocalDataEncryptionService
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort

internal class FakeLocalDataEncryptionService : LocalDataEncryptionService {
    var lastEncryptAad: ByteArray? = null
    var lastDecryptAad: ByteArray? = null
    var forceDecryptFailure: Boolean = false

    override fun encryptString(
        plaintext: String,
        aad: ByteArray,
    ): String {
        lastEncryptAad = aad
        return "${CryptoAtRestEncryptionService.ENVELOPE_PREFIX}$plaintext"
    }

    override fun decryptString(
        stored: String,
        aad: ByteArray,
    ): String {
        if (forceDecryptFailure) {
            throw javax.crypto.AEADBadTagException()
        }
        lastDecryptAad = aad
        return stored.removePrefix(CryptoAtRestEncryptionService.ENVELOPE_PREFIX)
    }

    override fun isEncrypted(stored: String): Boolean = stored.startsWith(CryptoAtRestEncryptionService.ENVELOPE_PREFIX)
}

internal fun testSensitiveSelfHealer(
    localEncryption: LocalDataEncryptionService = FakeLocalDataEncryptionService(),
    loggingPort: LoggingPort = FakeLoggingPort(),
): SensitivePersistenceSelfHealer = SensitivePersistenceSelfHealer(localEncryption, loggingPort)
