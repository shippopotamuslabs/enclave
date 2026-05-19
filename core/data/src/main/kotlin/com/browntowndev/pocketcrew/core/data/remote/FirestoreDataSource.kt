package com.browntowndev.pocketcrew.core.data.remote

import com.browntowndev.pocketcrew.core.data.remote.firestore.ChatSyncDaos
import com.browntowndev.pocketcrew.core.data.remote.firestore.ConfigSyncDaos
import com.browntowndev.pocketcrew.core.data.remote.firestore.FirestoreCloudPurgeClient
import com.browntowndev.pocketcrew.core.data.remote.firestore.FirestoreListenerCoordinator
import com.browntowndev.pocketcrew.core.data.remote.firestore.FirestorePendingSyncEngine
import com.browntowndev.pocketcrew.core.data.remote.firestore.FirestoreSyncAdapterRegistry
import com.browntowndev.pocketcrew.core.data.remote.firestore.FirestoreSyncDependencies
import com.browntowndev.pocketcrew.core.data.remote.firestore.FirestoreSyncDaos
import com.browntowndev.pocketcrew.core.data.remote.firestore.FirestoreVaultClient
import com.browntowndev.pocketcrew.core.data.remote.firestore.StudioSyncDaos
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import com.browntowndev.pocketcrew.core.data.security.LocalDataEncryptionService
import com.browntowndev.pocketcrew.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.pocketcrew.core.data.security.StaticSensitiveFieldCatalog
import com.browntowndev.pocketcrew.core.data.sync.EmbeddingRegenerationCoordinator
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.security.VaultMetadata
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.browntowndev.pocketcrew.domain.qualifier.ApplicationScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore ↔ Room sync for opted-in entities.
 *
 * **Transcript segments:** Synced under `message_transcript_segments` with encrypted `text`,
 * mirroring the same pending_sync / tombstone pattern as [WebSearchSourceEntity].
 */
@Singleton
class FirestoreDataSource
    @Inject
    constructor(
        dependencies: FirestoreSyncDependenciesFacade,
        @param:ApplicationScope private val externalScope: CoroutineScope,
    ) : CloudSyncPort {
        private val syncDependencies = dependencies.toSyncDependencies()
        private val syncContext = syncDependencies.toContext()
        private val adapterRegistry = FirestoreSyncAdapterRegistry(syncContext)
        private val syncMutex = Mutex()
        private val listenerCoordinator =
            FirestoreListenerCoordinator(
                ctx = syncContext,
                adapters = adapterRegistry.adapters,
                externalScope = externalScope,
            )
        private val pendingSyncEngine =
            FirestorePendingSyncEngine(
                ctx = syncContext,
                adapters = adapterRegistry.adapters,
            )
        private val vaultClient = FirestoreVaultClient(syncContext)
        private val cloudPurgeClient =
            FirestoreCloudPurgeClient(
                ctx = syncContext,
                adapters = adapterRegistry.adapters,
                listenerCoordinator = listenerCoordinator,
                syncMutex = syncMutex,
            )

        override fun startListening() {
            listenerCoordinator.startListening()
        }

        override fun stopListening() {
            listenerCoordinator.stopListening()
        }

        override suspend fun syncAllLocalDataToCloud(): Result<Unit> =
            syncMutex.withLock {
                val userId = syncContext.userIdProvider() ?: return Result.failure(IllegalStateException("Not signed in"))
                if (!syncContext.cryptoManager.hasCachedKey()) {
                    return Result.failure(IllegalStateException("Vault locked"))
                }
                pendingSyncEngine.syncAllLocalDataToCloud(userId)
            }

        override suspend fun syncEntitiesToCloud(
            chats: List<Chat>,
            messages: List<Message>,
        ): Result<Unit> =
            syncMutex.withLock {
                val userId = syncContext.userIdProvider() ?: return Result.failure(IllegalStateException("Not signed in"))
                if (!syncContext.cryptoManager.hasCachedKey()) {
                    return Result.failure(IllegalStateException("Vault locked"))
                }
                pendingSyncEngine.syncEntitiesToCloud(userId, chats, messages)
            }

        override suspend fun syncPendingChanges(): Result<Unit> =
            syncMutex.withLock {
                val userId = syncContext.userIdProvider() ?: return Result.failure(IllegalStateException("Not signed in"))
                if (!syncContext.cryptoManager.hasCachedKey()) {
                    return Result.failure(IllegalStateException("Vault locked"))
                }
                pendingSyncEngine.syncPendingChanges(userId)
            }

        override suspend fun updateVaultSalt(salt: String): Result<Unit> = vaultClient.updateVaultSalt(salt)

        override suspend fun getVaultSalt(): Result<String?> = vaultClient.getVaultSalt()

        override suspend fun updateVaultVerificationData(ciphertext: String): Result<Unit> =
            vaultClient.updateVaultVerificationData(ciphertext)

        override suspend fun getVaultVerificationData(): Result<String?> = vaultClient.getVaultVerificationData()

        override suspend fun updateWrappedDek(wrappedDek: String?): Result<Unit> = vaultClient.updateWrappedDek(wrappedDek)

        override suspend fun getWrappedDek(): Result<String?> = vaultClient.getWrappedDek()

        override suspend fun uploadVaultMetadata(metadata: VaultMetadata): Result<Unit> =
            vaultClient.uploadVaultMetadata(metadata)

        override suspend fun fetchVaultMetadata(): Result<VaultMetadata?> = vaultClient.fetchVaultMetadata()

        override suspend fun deleteUserAccount(): Result<Unit> = cloudPurgeClient.deleteUserAccount()

        override suspend fun deleteAllChatsFromCloud(): Result<Unit> = cloudPurgeClient.deleteAllChatsFromCloud()

        override suspend fun deleteAllMediaFromCloud(): Result<Unit> = cloudPurgeClient.deleteAllMediaFromCloud()

        override suspend fun deleteAllMemoriesFromCloud(): Result<Unit> = cloudPurgeClient.deleteAllMemoriesFromCloud()

        override suspend fun deleteAllArtifactsFromCloud(): Result<Unit> = cloudPurgeClient.deleteAllArtifactsFromCloud()

        override fun enqueueEmbeddingRegeneration() {
            syncContext.embeddingCoordinator.enqueueFullRegeneration()
        }
    }

/**
 * Hilt-friendly facade that bundles Firestore sync dependencies to keep [FirestoreDataSource]'s
 * constructor within Detekt's parameter limits.
 */
@Singleton
class FirestoreSyncDependenciesFacade
    private constructor(
        private val firestore: FirebaseFirestore,
        private val chatSyncDaos: ChatSyncDaos,
        private val configSyncDaos: ConfigSyncDaos,
        private val studioSyncDaos: StudioSyncDaos,
        private val apiKeyManager: ApiKeyManager,
        private val cryptoManager: CryptoManagerPort,
        private val loggingPort: LoggingPort,
        private val userIdProvider: () -> String?,
        private val embeddingCoordinator: EmbeddingRegenerationCoordinator,
        private val localEncryption: LocalDataEncryptionService,
        private val sensitiveSelfHealer: SensitivePersistenceSelfHealer,
        private val purgeInProgress: AtomicBoolean,
    ) {
        @Inject
        constructor(
            firestore: FirebaseFirestore,
            chatSyncDaos: ChatSyncDaos,
            configSyncDaos: ConfigSyncDaos,
            studioSyncDaos: StudioSyncDaos,
            apiKeyManager: ApiKeyManager,
            cryptoManager: CryptoManagerPort,
            loggingPort: LoggingPort,
            userIdProvider: () -> String?,
            embeddingCoordinator: EmbeddingRegenerationCoordinator,
            localEncryption: LocalDataEncryptionService,
        ) : this(
            firestore = firestore,
            chatSyncDaos = chatSyncDaos,
            configSyncDaos = configSyncDaos,
            studioSyncDaos = studioSyncDaos,
            apiKeyManager = apiKeyManager,
            cryptoManager = cryptoManager,
            loggingPort = loggingPort,
            userIdProvider = userIdProvider,
            embeddingCoordinator = embeddingCoordinator,
            localEncryption = localEncryption,
            sensitiveSelfHealer = SensitivePersistenceSelfHealer(localEncryption, loggingPort),
            purgeInProgress = AtomicBoolean(false),
        )

        internal fun toSyncDependencies(): FirestoreSyncDependencies =
            FirestoreSyncDependencies(
                firestore = firestore,
                daos = FirestoreSyncDaos.from(chatSyncDaos, configSyncDaos, studioSyncDaos),
                localEncryption = localEncryption,
                sensitiveSelfHealer = sensitiveSelfHealer,
                firestoreSensitiveFieldRepairer =
                    FirestoreSensitiveFieldRepairer(
                        StaticSensitiveFieldCatalog(),
                        sensitiveSelfHealer,
                    ),
                cryptoManager = cryptoManager,
                loggingPort = loggingPort,
                apiKeyManager = apiKeyManager,
                embeddingCoordinator = embeddingCoordinator,
                userIdProvider = userIdProvider,
                purgeInProgress = purgeInProgress,
            )

        internal companion object {
            fun forTests(
                firestore: FirebaseFirestore,
                chatSyncDaos: ChatSyncDaos,
                configSyncDaos: ConfigSyncDaos,
                studioSyncDaos: StudioSyncDaos,
                apiKeyManager: ApiKeyManager,
                cryptoManager: CryptoManagerPort,
                loggingPort: LoggingPort,
                userIdProvider: () -> String?,
                embeddingCoordinator: EmbeddingRegenerationCoordinator,
                localEncryption: LocalDataEncryptionService,
                sensitiveSelfHealer: SensitivePersistenceSelfHealer,
                purgeInProgress: AtomicBoolean,
            ): FirestoreSyncDependenciesFacade =
                FirestoreSyncDependenciesFacade(
                    firestore = firestore,
                    chatSyncDaos = chatSyncDaos,
                    configSyncDaos = configSyncDaos,
                    studioSyncDaos = studioSyncDaos,
                    apiKeyManager = apiKeyManager,
                    cryptoManager = cryptoManager,
                    loggingPort = loggingPort,
                    userIdProvider = userIdProvider,
                    embeddingCoordinator = embeddingCoordinator,
                    localEncryption = localEncryption,
                    sensitiveSelfHealer = sensitiveSelfHealer,
                    purgeInProgress = purgeInProgress,
                )
        }
    }
