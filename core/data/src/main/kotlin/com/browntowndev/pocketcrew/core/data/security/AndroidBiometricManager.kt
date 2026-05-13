@file:Suppress(
    "MaxLineLength",
    "MagicNumber",
    "UnusedPrivateProperty",
    "UnusedParameter",
    "TooManyFunctions",
    "ComplexCondition",
    "LargeClass",
    "CyclomaticComplexMethod",
    "SwallowedException",
    "InstanceOfCheckForException",
    "ThrowsCount",
    "NestedBlockDepth",
    "ReturnCount",
    "LoopWithTooManyJumpStatements",
    "FunctionNaming",
    "LongMethod",
    "MatchingDeclarationName",
    "UnusedPrivateMember",
    "LongParameterList",
)

package com.browntowndev.pocketcrew.core.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.security.BiometricPort
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidBiometricManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val loggingPort: LoggingPort,
    ) : BiometricPort {
        private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        private val sharedPrefs = context.getSharedPreferences("biometric_prefs", Context.MODE_PRIVATE)

        companion object {
            private const val TAG = "BiometricManager"
            private const val KEY_ALIAS = "vault_password_key"
            private const val TRANSFORMATION = "AES/GCM/NoPadding"
        }

        override fun isBiometricAvailable(): Boolean {
            val biometricManager = BiometricManager.from(context)
            val canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            loggingPort.debug(TAG, "isBiometricAvailable: canAuthenticate result=$canAuth")
            return canAuth == BiometricManager.BIOMETRIC_SUCCESS
        }

        override suspend fun authenticate(
            title: String,
            subtitle: String,
        ): Result<Unit> = Result.failure(UnsupportedOperationException("Use UI-layer prompt for raw authentication"))

        override suspend fun storeSecureValue(
            key: String,
            value: String,
        ): Result<Unit> =
            Result.failure(
                UnsupportedOperationException(
                    "Use UI-layer prompt for raw authentication via getEncryptCipher and storeSecureValueWithCipher",
                ),
            )

        fun getEncryptCipher(key: String): Cipher? {
            loggingPort.debug(TAG, "getEncryptCipher: key=$key")
            return try {
                deleteKey()
                val secretKey = createKey(requireAuth = true)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                cipher
            } catch (e: Exception) {
                loggingPort.error(TAG, "getEncryptCipher failed", e)
                null
            }
        }

        suspend fun storeSecureValueWithCipher(
            key: String,
            value: String,
            cipher: Cipher,
        ): Result<Unit> = storeSecureBytesWithCipher(key, value.toByteArray(Charsets.UTF_8), cipher)

        suspend fun storeSecureBytesWithCipher(
            key: String,
            value: ByteArray,
            cipher: Cipher,
        ): Result<Unit> {
            loggingPort.debug(TAG, "storeSecureBytesWithCipher: key=$key bytes=${value.size}")
            return try {
                val encryptedBytes = cipher.doFinal(value)
                val iv = cipher.iv

                sharedPrefs
                    .edit()
                    .putString("${key}_data", Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
                    .putString("${key}_iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                    .apply()

                loggingPort.info(TAG, "storeSecureBytesWithCipher: Successfully stored $key")
                Result.success(Unit)
            } catch (e: Exception) {
                loggingPort.error(TAG, "storeSecureBytesWithCipher failed", e)
                Result.failure(e)
            }
        }

        override suspend fun getSecureValue(
            key: String,
            promptTitle: String,
            promptSubtitle: String,
        ): Result<String?> = Result.failure(UnsupportedOperationException("getSecureValue requires Activity. Call via UI layer."))

        override fun hasSecureValue(key: String): Boolean {
            val exists = sharedPrefs.contains("${key}_data")
            loggingPort.debug(TAG, "hasSecureValue: key=$key exists=$exists")
            return exists
        }

        override fun clearSecureValue(key: String) {
            loggingPort.info(TAG, "clearSecureValue: key=$key")
            sharedPrefs
                .edit()
                .remove("${key}_data")
                .remove("${key}_iv")
                .apply()
        }

        private fun deleteKey() {
            if (keyStore.containsAlias(KEY_ALIAS)) {
                loggingPort.debug(TAG, "deleteKey: Removing existing key alias $KEY_ALIAS")
                keyStore.deleteEntry(KEY_ALIAS)
            }
        }

        private fun createKey(requireAuth: Boolean): SecretKey {
            loggingPort.debug(TAG, "createKey: requireAuth=$requireAuth")
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec =
                KeyGenParameterSpec
                    .Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(requireAuth)
                    .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                    .build()

            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }

        /**
         * [BiometricPort] implementation: cipher for decrypt flow after IV is loaded from prefs.
         */
        fun getDecryptCipher(key: String): Cipher? {
            loggingPort.debug(TAG, "getDecryptCipher: key=$key")
            val ivBase64 = sharedPrefs.getString("${key}_iv", null) ?: return null
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)

            val secretKey = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            if (secretKey == null) {
                loggingPort.error(TAG, "getDecryptCipher: Key not found in KeyStore")
                return null
            }

            return try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
                cipher
            } catch (e: Exception) {
                loggingPort.error(TAG, "getDecryptCipher: Failed to initialize cipher", e)
                null
            }
        }

        fun decryptWithCipher(
            key: String,
            cipher: Cipher,
        ): String? = decryptBytesWithCipher(key, cipher)?.let { String(it, Charsets.UTF_8) }

        fun decryptBytesWithCipher(
            key: String,
            cipher: Cipher,
        ): ByteArray? {
            loggingPort.debug(TAG, "decryptBytesWithCipher: key=$key")
            return try {
                val dataBase64 = sharedPrefs.getString("${key}_data", null) ?: return null
                val encryptedBytes = Base64.decode(dataBase64, Base64.NO_WRAP)
                cipher.doFinal(encryptedBytes)
            } catch (e: Exception) {
                loggingPort.error(TAG, "decryptBytesWithCipher: Failed to decrypt", e)
                null
            }
        }
    }
