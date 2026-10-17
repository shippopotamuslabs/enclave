package com.browntowndev.enclave.domain.usecase.security

import com.browntowndev.enclave.domain.port.security.BiometricCipherPort
import com.browntowndev.enclave.domain.security.VaultKeystoreAliases
import javax.crypto.Cipher
import javax.inject.Inject

/** Encrypt cipher for enrolling the vault DEK behind biometrics. Alias stays in domain. */
class GetBiometricEncryptCipherUseCase
    @Inject
    constructor(
        private val biometricCipherPort: BiometricCipherPort,
    ) {
        operator fun invoke(): Cipher? = biometricCipherPort.getEncryptCipher(VaultKeystoreAliases.VAULT_DEK_SECURE_KEY)
    }

/** Decrypt cipher for biometric vault unlock, or null when no usable keystore key exists. */
class GetBiometricDecryptCipherUseCase
    @Inject
    constructor(
        private val biometricCipherPort: BiometricCipherPort,
    ) {
        operator fun invoke(): Cipher? = biometricCipherPort.getDecryptCipher(VaultKeystoreAliases.VAULT_DEK_SECURE_KEY)
    }

/** Persists [dek] behind the biometric-bound keystore key using an authenticated [cipher]. */
class StoreBiometricDekUseCase
    @Inject
    constructor(
        private val biometricCipherPort: BiometricCipherPort,
    ) {
        suspend operator fun invoke(
            dek: ByteArray,
            cipher: Cipher,
        ): Result<Unit> =
            biometricCipherPort.storeSecureBytesWithCipher(
                VaultKeystoreAliases.VAULT_DEK_SECURE_KEY,
                dek,
                cipher,
            )
    }

/** Decrypts the biometric-wrapped vault DEK using an authenticated [cipher]. */
class DecryptBiometricDekUseCase
    @Inject
    constructor(
        private val biometricCipherPort: BiometricCipherPort,
    ) {
        operator fun invoke(cipher: Cipher): ByteArray? =
            biometricCipherPort.decryptBytesWithCipher(
                VaultKeystoreAliases.VAULT_DEK_SECURE_KEY,
                cipher,
            )
    }
