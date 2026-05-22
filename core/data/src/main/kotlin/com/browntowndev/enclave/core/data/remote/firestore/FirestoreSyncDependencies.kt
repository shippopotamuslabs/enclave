package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.data.remote.FirestoreSensitiveFieldRepairer
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.data.security.LocalDataEncryptionService
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.core.data.sync.EmbeddingRegenerationCoordinator
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.atomic.AtomicBoolean

internal data class FirestoreSyncDependencies(
    val firestore: FirebaseFirestore,
    val daos: FirestoreSyncDaos,
    val localEncryption: LocalDataEncryptionService,
    val sensitiveSelfHealer: SensitivePersistenceSelfHealer,
    val firestoreSensitiveFieldRepairer: FirestoreSensitiveFieldRepairer,
    val cryptoManager: CryptoManagerPort,
    val loggingPort: LoggingPort,
    val apiKeyManager: ApiKeyManager,
    val embeddingCoordinator: EmbeddingRegenerationCoordinator,
    val userIdProvider: () -> String?,
    val purgeInProgress: AtomicBoolean = AtomicBoolean(false),
) {
    fun toContext(): FirestoreSyncContext =
        FirestoreSyncContext(
            firestore = firestore,
            daos = daos,
            localEncryption = localEncryption,
            sensitiveSelfHealer = sensitiveSelfHealer,
            firestoreSensitiveFieldRepairer = firestoreSensitiveFieldRepairer,
            cryptoManager = cryptoManager,
            loggingPort = loggingPort,
            apiKeyManager = apiKeyManager,
            embeddingCoordinator = embeddingCoordinator,
            userIdProvider = userIdProvider,
            purgeInProgress = purgeInProgress,
        )
}
