package com.browntowndev.pocketcrew.core.data.remote.firestore

import com.browntowndev.pocketcrew.core.data.remote.FirestoreSensitiveFieldRepairer
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import com.browntowndev.pocketcrew.core.data.security.LocalDataEncryptionService
import com.browntowndev.pocketcrew.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.pocketcrew.core.data.sync.EmbeddingRegenerationCoordinator
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
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
