package com.browntowndev.pocketcrew.core.data.security

import android.util.Base64
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoManagerImpl
    @Inject
    constructor() : CryptoManagerPort {
        private var inMemoryKey: SecretKeySpec? = null
            set(value) {
                field = value
                _isVaultUnlocked.value = value != null
            }

        private val _isVaultUnlocked = MutableStateFlow(false)
        override val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

        private val cachedKey: SecretKeySpec?
            get() = inMemoryKey

        override fun deriveKek(
            password: String,
            salt: ByteArray,
        ): Result<ByteArray> =
            try {
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val spec = PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
                val tmp = factory.generateSecret(spec)
                Result.success(tmp.encoded)
            } catch (e: Exception) {
                Result.failure(e)
            }

        override fun generateDek(size: Int): ByteArray = generateRandomKey(size)

        override fun wrapDek(
            dek: ByteArray,
            kek: ByteArray,
        ): Result<ByteArray> =
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val iv = ByteArray(IV_LENGTH)
                SecureRandom().nextBytes(iv)
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(TAG_LENGTH, iv))
                val ciphertext = cipher.doFinal(dek)
                val out = ByteArray(IV_LENGTH + ciphertext.size)
                System.arraycopy(iv, 0, out, 0, IV_LENGTH)
                System.arraycopy(ciphertext, 0, out, IV_LENGTH, ciphertext.size)
                Result.success(out)
            } catch (e: Exception) {
                Result.failure(e)
            }

        override fun unwrapDek(
            wrapped: ByteArray,
            kek: ByteArray,
        ): Result<ByteArray> {
            if (wrapped.size <= IV_LENGTH) {
                return Result.failure(IllegalArgumentException("Wrapped DEK too short"))
            }
            return try {
                val iv = ByteArray(IV_LENGTH)
                System.arraycopy(wrapped, 0, iv, 0, IV_LENGTH)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(TAG_LENGTH, iv))
                val dek = cipher.doFinal(wrapped, IV_LENGTH, wrapped.size - IV_LENGTH)
                Result.success(dek)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        override fun cacheDek(dek: ByteArray): Result<Unit> =
            try {
                inMemoryKey = SecretKeySpec(dek, "AES")
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }

        override fun getCachedDek(): ByteArray? = inMemoryKey?.encoded

        override fun clearKey() {
            inMemoryKey = null
        }

        override fun hasCachedKey(): Boolean = cachedKey != null

        override fun encrypt(plaintext: String, aad: ByteArray?): Result<ByteArray> = encryptBytes(plaintext.toByteArray(Charsets.UTF_8), aad)

        override fun encryptBytes(data: ByteArray, aad: ByteArray?): Result<ByteArray> {
            val key = cachedKey ?: return Result.failure(IllegalStateException("No DEK cached. Unlock the vault first."))

            return try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val iv = ByteArray(IV_LENGTH)
                SecureRandom().nextBytes(iv)

                val parameterSpec = GCMParameterSpec(TAG_LENGTH, iv)
                cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec)
                if (aad != null) cipher.updateAAD(aad)

                val ciphertext = cipher.doFinal(data)

                val result = ByteArray(IV_LENGTH + ciphertext.size)
                System.arraycopy(iv, 0, result, 0, IV_LENGTH)
                System.arraycopy(ciphertext, 0, result, IV_LENGTH, ciphertext.size)

                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        override fun decrypt(ciphertext: ByteArray, aad: ByteArray?): Result<String> = decryptBytes(ciphertext, aad).map { String(it, Charsets.UTF_8) }

        override fun decryptBytes(ciphertext: ByteArray, aad: ByteArray?): Result<ByteArray> {
            val key = cachedKey ?: return Result.failure(IllegalStateException("No DEK cached. Unlock the vault first."))

            if (ciphertext.size < IV_LENGTH) {
                return Result.failure(IllegalArgumentException("Ciphertext too short"))
            }

            return try {
                val cipher = Cipher.getInstance(TRANSFORMATION)

                val iv = ByteArray(IV_LENGTH)
                System.arraycopy(ciphertext, 0, iv, 0, IV_LENGTH)

                val parameterSpec = GCMParameterSpec(TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec)
                if (aad != null) cipher.updateAAD(aad)

                val plaintextBytes = cipher.doFinal(ciphertext, IV_LENGTH, ciphertext.size - IV_LENGTH)

                Result.success(plaintextBytes)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        override fun generateSalt(): ByteArray {
            val salt = ByteArray(SALT_LENGTH)
            SecureRandom().nextBytes(salt)
            return salt
        }

        override fun encodeBase64(data: ByteArray): String = Base64.encodeToString(data, Base64.NO_WRAP)

        override fun decodeBase64(base64: String): ByteArray = Base64.decode(base64, Base64.NO_WRAP)

        override fun generateRandomKey(size: Int): ByteArray {
            val key = ByteArray(size)
            SecureRandom().nextBytes(key)
            return key
        }

        override fun generateRandomIv(size: Int): ByteArray {
            val iv = ByteArray(size)
            SecureRandom().nextBytes(iv)
            return iv
        }

        override fun encryptWithKey(
            data: ByteArray,
            key: ByteArray,
            iv: ByteArray,
        ): Result<ByteArray> =
            try {
                val secretKey = SecretKeySpec(key, "AES")
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val parameterSpec = GCMParameterSpec(TAG_LENGTH, iv)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)

                val ciphertext = cipher.doFinal(data)
                Result.success(ciphertext)
            } catch (e: Exception) {
                Result.failure(e)
            }

        override fun decryptWithKey(
            ciphertext: ByteArray,
            key: ByteArray,
            iv: ByteArray,
        ): Result<ByteArray> =
            try {
                val secretKey = SecretKeySpec(key, "AES")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

                Result.success(cipher.doFinal(ciphertext))
            } catch (e: Exception) {
                Result.failure(e)
            }

        companion object {
            private const val TRANSFORMATION = "AES/GCM/NoPadding"
            private const val ITERATION_COUNT = 100000 // NIST recommended minimum for PBKDF2
            private const val KEY_LENGTH = 256
            private const val IV_LENGTH = 12 // 96 bits recommended for GCM
            private const val TAG_LENGTH = 128
            private const val SALT_LENGTH = 16
        }
    }
