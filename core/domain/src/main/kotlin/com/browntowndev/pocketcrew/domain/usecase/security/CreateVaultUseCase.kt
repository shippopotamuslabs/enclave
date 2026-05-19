package com.browntowndev.pocketcrew.domain.usecase.security

import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import javax.inject.Inject

/**
 * First-time vault setup. Derives a transient KEK from [password] + [salt], generates a fresh
 * random Data Encryption Key (DEK), wraps the DEK with the KEK, caches the DEK in [CryptoManagerPort]
 * (so subsequent encrypt/decrypt calls work immediately), and returns the wrapped DEK blob so the
 * orchestrator can persist it to local DataStore + Firestore.
 *
 * Returned blob layout: `iv (12 bytes) || aesGcm(KEK, dek)` — same as [CryptoManagerPort.wrapDek].
 */
class CreateVaultUseCase
    @Inject
    constructor(
        private val cryptoManager: CryptoManagerPort,
    ) {
        suspend operator fun invoke(
            password: String,
            salt: ByteArray,
        ): Result<ByteArray> {
            val kek = cryptoManager.deriveKek(password, salt).getOrElse { return Result.failure(it) }
            val dek = cryptoManager.generateDek()
            val wrapped = cryptoManager.wrapDek(dek, kek).getOrElse { return Result.failure(it) }
            cryptoManager.cacheDek(dek).getOrElse { return Result.failure(it) }
            return Result.success(wrapped)
        }
    }
