package com.browntowndev.pocketcrew.domain.usecase.security

import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.browntowndev.pocketcrew.domain.port.security.ApiKeyProviderPort
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class UnlockVaultUseCase @Inject constructor(
    private val cryptoManager: CryptoManagerPort,
    private val apiModelRepository: ApiModelRepositoryPort,
    private val apiKeyProvider: ApiKeyProviderPort
) {
    suspend operator fun invoke(password: String, salt: ByteArray): Result<Unit> {
        val deriveResult = cryptoManager.deriveAndCacheKey(password, salt)
        if (deriveResult.isFailure) return deriveResult

        return try {
            // Re-populate ApiKeyManager with decrypted keys from Room
            val credentials = apiModelRepository.getAllCredentials()
            credentials.forEach { cred ->
                cred.encryptedApiKey?.let { encryptedKey ->
                    val decryptedResult = cryptoManager.decrypt(encryptedKey)
                    decryptedResult.getOrNull()?.let { plaintext ->
                        apiKeyProvider.saveApiKey(cred.credentialAlias, plaintext)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
