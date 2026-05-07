package com.browntowndev.pocketcrew.core.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Base64

import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting

@Singleton
class CryptoManagerImpl @VisibleForTesting internal constructor(
    private val prefsProvider: () -> SharedPreferences
) : CryptoManagerPort {

    @Inject constructor(
        @ApplicationContext context: Context
    ) : this({
        EncryptedSharedPreferences.create(
            context,
            "crypto_manager_prefs",
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    })

    private val prefs: SharedPreferences by lazy { prefsProvider() }

    private var inMemoryKey: SecretKeySpec? = null

    private val cachedKey: SecretKeySpec?
        get() = inMemoryKey

    override fun deriveAndCacheKey(password: String, salt: ByteArray): Result<Unit> {
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
            val tmp = factory.generateSecret(spec)
            inMemoryKey = SecretKeySpec(tmp.encoded, "AES")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun clearKey() {
        inMemoryKey = null
    }

    override fun hasCachedKey(): Boolean {
        return cachedKey != null
    }

    override fun encrypt(plaintext: String): Result<ByteArray> {
        val key = cachedKey ?: return Result.failure(IllegalStateException("No key cached. Call deriveAndCacheKey first."))
        
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = ByteArray(IV_LENGTH)
            SecureRandom().nextBytes(iv)
            
            val parameterSpec = GCMParameterSpec(TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec)
            
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            
            // Prepend IV to ciphertext
            val result = ByteArray(IV_LENGTH + ciphertext.size)
            System.arraycopy(iv, 0, result, 0, IV_LENGTH)
            System.arraycopy(ciphertext, 0, result, IV_LENGTH, ciphertext.size)
            
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun decrypt(ciphertext: ByteArray): Result<String> {
        val key = cachedKey ?: return Result.failure(IllegalStateException("No key cached. Call deriveAndCacheKey first."))
        
        if (ciphertext.size < IV_LENGTH) {
            return Result.failure(IllegalArgumentException("Ciphertext too short"))
        }

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            
            // Extract IV
            val iv = ByteArray(IV_LENGTH)
            System.arraycopy(ciphertext, 0, iv, 0, IV_LENGTH)
            
            val parameterSpec = GCMParameterSpec(TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec)
            
            // Decrypt the rest
            val plaintextBytes = cipher.doFinal(ciphertext, IV_LENGTH, ciphertext.size - IV_LENGTH)
            
            Result.success(String(plaintextBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }

    companion object {
        private const val PREF_CACHED_KEY = "cached_derived_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ITERATION_COUNT = 100000 // NIST recommended minimum for PBKDF2
        private const val KEY_LENGTH = 256
        private const val IV_LENGTH = 12 // 96 bits recommended for GCM
        private const val TAG_LENGTH = 128
        private const val SALT_LENGTH = 16
    }
}
