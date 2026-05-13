package com.browntowndev.pocketcrew.domain.usecase.security

import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.security.ApiKeyProviderPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import javax.inject.Inject

/**
 * Unlocks an existing vault by deriving a transient KEK from [password] + [salt], unwrapping the
 * Data Encryption Key (DEK) from [wrappedDek], caching the DEK in [CryptoManagerPort], and
 * re-populating the API key cache so providers can fetch their plaintext keys.
 *
 * - Wrong password → `unwrapDek` fails (typically with `AEADBadTagException`) → `Result.failure`.
 * - On failure, no key is cached and no side effects are persisted.
 */
class UnlockVaultUseCase @Inject constructor(
    private val cryptoManager: CryptoManagerPort,
    private val apiModelRepository: ApiModelRepositoryPort,
    private val apiKeyProvider: ApiKeyProviderPort,
) {
    suspend operator fun invoke(
        password: String,
        salt: ByteArray,
        wrappedDek: ByteArray,
    ): Result<Unit> {
        val kek = cryptoManager.deriveKek(password, salt).getOrElse {
            cryptoManager.clearKey()
            return Result.failure(it)
        }
        val dek = cryptoManager.unwrapDek(wrappedDek, kek).getOrElse {
            cryptoManager.clearKey()
            return Result.failure(it)
        }
        cryptoManager.cacheDek(dek).getOrElse {
            cryptoManager.clearKey()
            return Result.failure(it)
        }

        return try {
            val credentials = apiModelRepository.getAllCredentials()
            credentials.forEach { cred ->
                cred.encryptedApiKey?.let { encryptedKey ->
                    cryptoManager.decrypt(encryptedKey).getOrNull()?.let { plaintext ->
                        apiKeyProvider.saveApiKey(cred.credentialAlias, plaintext)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            cryptoManager.clearKey()
            Result.failure(e)
        }
    }
}
