package com.browntowndev.enclave.core.data.remote

import com.browntowndev.enclave.core.data.remote.firestore.ChatSyncDaos
import com.browntowndev.enclave.core.data.remote.firestore.ConfigSyncDaos
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreCloudPurgeClient
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreListenerCoordinator
import com.browntowndev.enclave.core.data.remote.firestore.FirestorePendingSyncEngine
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncAdapterRegistry
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncDaos
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncDependencies
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventApplier
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventDispatcher
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncEventFeedClient
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncHydrationRecovery
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncStateStore
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreVaultClient
import com.browntowndev.enclave.core.data.remote.firestore.StudioSyncDaos
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.data.security.LocalDataEncryptionService
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.core.data.security.StaticSensitiveFieldCatalog
import com.browntowndev.enclave.core.data.sync.EmbeddingRegenerationCoordinator
import com.browntowndev.enclave.domain.model.chat.Chat
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.security.VaultMetadata
import com.browntowndev.enclave.domain.model.sync.CloudSyncEventReplayResult
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.browntowndev.enclave.domain.qualifier.ApplicationScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore ↔ Room sync for opted-in entities.
 *
 * **Event feed:** Live sync is driven by a single ordered listener on
 * `users/{uid}/sync_events`. [syncRemoteChangesFromCloud] pages the feed before the live
 * listener attaches so initial hydration and live delivery share one code path.
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
        private val syncEventFeedClient = dependencies.syncEventFeedClient
        private val syncEventApplier = dependencies.syncEventApplier
        private val syncStateStore = dependencies.syncStateStore
        private val documentCache = dependencies.documentCache
        private val syncMutex = Mutex()
        private val eventApplyMutex = Mutex()
        private val listenerCoordinator =
            FirestoreListenerCoordinator(
                ctx = syncContext,
                syncEventFeedClient = syncEventFeedClient,
                syncEventApplier = syncEventApplier,
                syncStateStore = syncStateStore,
                eventApplyMutex = eventApplyMutex,
                externalScope = externalScope,
                cache = documentCache,
            )
        private val pendingSyncEngine =
            FirestorePendingSyncEngine(
                ctx = syncContext,
                adapters = adapterRegistry.adapters,
                syncStateStore = syncStateStore,
            )
        private val vaultClient = FirestoreVaultClient(syncContext)
        private val cloudPurgeClient =
            FirestoreCloudPurgeClient(
                ctx = syncContext,
                adapters = adapterRegistry.adapters,
                listenerCoordinator = listenerCoordinator,
                syncMutex = syncMutex,
            )
        private val hydrationRecovery =
            FirestoreSyncHydrationRecovery(
                chatDao = syncContext.daos.chatDao,
                syncStateStore = syncStateStore,
                loggingPort = syncContext.loggingPort,
            )

        override fun startListening() {
            listenerCoordinator.startListening()
        }

        override fun stopListening() {
            listenerCoordinator.stopListening()
        }

        override suspend fun syncRemoteChangesFromCloud(): Result<CloudSyncEventReplayResult> =
            runCatching {
                val userId = syncContext.userIdProvider() ?: throw IllegalStateException("Not signed in")
                if (!syncContext.cryptoManager.hasCachedKey()) {
                    throw IllegalStateException("Vault locked")
                }
                eventApplyMutex.withLock {
                    hydrationRecovery.resetStaleCursorIfLocalStoreEmpty(userId)
                    val lastApplied = syncStateStore.lastAppliedSequence(userId)
                    val page = syncEventFeedClient.fetchNextPage(userId, lastApplied)
                    try {
                        documentCache.prefetch(userId, page)
                        var applied = 0
                        var newLast = lastApplied
                        for (event in page) {
                            val result = syncEventApplier.apply(userId, event)
                            applied += result.appliedCount
                            if (result.lastAppliedSequence > newLast) newLast = result.lastAppliedSequence
                        }
                        // Persist the high-water mark so the next page query uses the correct
                        // sequence lower bound even when the dispatcher (e.g., a test double)
                        // does not update the state store itself.
                        if (newLast > lastApplied) {
                            syncStateStore.setLastAppliedSequence(userId, newLast)
                        }
                        syncContext.loggingPort.debug(
                            FirestoreSyncContext.TAG,
                            "syncRemoteChangesFromCloud: replay from sequence > $lastApplied, " +
                                "fetched=${page.size}, applied=$applied, lastApplied=$newLast",
                        )
                        CloudSyncEventReplayResult(
                            eventsApplied = applied,
                            lastAppliedSequence = newLast,
                            hasMore = page.size >= PAGE_SIZE_THRESHOLD,
                        )
                    } finally {
                        documentCache.clear()
                    }
                }
            }

        override suspend fun getRemainingEventsCount(): Result<Long> =
            runCatching {
                val userId = syncContext.userIdProvider() ?: throw IllegalStateException("Not signed in")
                hydrationRecovery.resetStaleCursorIfLocalStoreEmpty(userId)
                val lastApplied = syncStateStore.lastAppliedSequence(userId)
                val countQuery =
                    syncContext.firestore
                        .collection("users/$userId/sync_events")
                        .whereGreaterThan("sequence", lastApplied)
                        .count()
                val snapshot = countQuery.get(com.google.firebase.firestore.AggregateSource.SERVER).await()
                snapshot.count
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

        override suspend fun uploadVaultMetadata(metadata: VaultMetadata): Result<Unit> = vaultClient.uploadVaultMetadata(metadata)

        override suspend fun fetchVaultMetadata(): Result<VaultMetadata?> = vaultClient.fetchVaultMetadata()

        override suspend fun deleteUserAccount(): Result<Unit> {
            listenerCoordinator.detachAllListeners()
            return cloudPurgeClient.deleteUserAccount()
        }

        override suspend fun deleteAllChatsFromCloud(): Result<Unit> = cloudPurgeClient.deleteAllChatsFromCloud()

        override suspend fun deleteAllMediaFromCloud(): Result<Unit> = cloudPurgeClient.deleteAllMediaFromCloud()

        override suspend fun deleteAllMemoriesFromCloud(): Result<Unit> = cloudPurgeClient.deleteAllMemoriesFromCloud()

        override suspend fun deleteAllArtifactsFromCloud(): Result<Unit> = cloudPurgeClient.deleteAllArtifactsFromCloud()

        override fun enqueueEmbeddingRegeneration() {
            syncContext.embeddingCoordinator.enqueueFullRegeneration()
        }

        private companion object {
            const val PAGE_SIZE_THRESHOLD = 200
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
        internal val syncEventFeedClient: FirestoreSyncEventFeedClient,
        internal val syncEventApplier: FirestoreSyncEventDispatcher,
        internal val syncStateStore: FirestoreSyncStateStore,
        internal val documentCache: com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncDocumentCache,
    ) {
        @Inject
        @Suppress("LongParameterList")
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
            @ApplicationScope externalScope: CoroutineScope,
            database: com.browntowndev.enclave.core.data.local.EnclaveDatabase,
            preferencesDataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
        ) : this(
            firestore,
            chatSyncDaos,
            configSyncDaos,
            studioSyncDaos,
            apiKeyManager,
            cryptoManager,
            loggingPort,
            userIdProvider,
            embeddingCoordinator,
            localEncryption,
            externalScope,
            database,
            preferencesDataStore,
            FirestoreSyncStateStore(preferencesDataStore),
        )

        private constructor(
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
            externalScope: CoroutineScope,
            database: com.browntowndev.enclave.core.data.local.EnclaveDatabase,
            preferencesDataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
            sharedStateStore: FirestoreSyncStateStore,
            cache: com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncDocumentCache =
                com.browntowndev.enclave.core.data.remote.firestore
                    .FirestoreSyncDocumentCache(firestore),
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
            syncEventFeedClient =
                buildProductionFeedClient(firestore, externalScope, loggingPort),
            syncEventApplier =
                buildProductionApplier(
                    firestore = firestore,
                    chatSyncDaos = chatSyncDaos,
                    configSyncDaos = configSyncDaos,
                    studioSyncDaos = studioSyncDaos,
                    localEncryption = localEncryption,
                    cryptoManager = cryptoManager,
                    loggingPort = loggingPort,
                    apiKeyManager = apiKeyManager,
                    embeddingCoordinator = embeddingCoordinator,
                    userIdProvider = userIdProvider,
                    database = database,
                    stateStore = sharedStateStore,
                    cache = cache,
                ),
            syncStateStore = sharedStateStore,
            documentCache = cache,
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
            private fun buildProductionFeedClient(
                firestore: FirebaseFirestore,
                externalScope: CoroutineScope,
                loggingPort: LoggingPort,
            ): FirestoreSyncEventFeedClient =
                FirestoreSyncEventFeedClient(
                    gateway =
                        com.browntowndev.enclave.core.data.remote.firestore
                            .FirestoreBackedSyncEventQueryGateway(
                                firestore = firestore,
                                externalScope = externalScope,
                                loggingPort = loggingPort,
                            ),
                )

            @Suppress("LongParameterList")
            private fun buildProductionApplier(
                firestore: FirebaseFirestore,
                chatSyncDaos: ChatSyncDaos,
                configSyncDaos: ConfigSyncDaos,
                studioSyncDaos: StudioSyncDaos,
                localEncryption: LocalDataEncryptionService,
                cryptoManager: CryptoManagerPort,
                loggingPort: LoggingPort,
                apiKeyManager: ApiKeyManager,
                embeddingCoordinator: EmbeddingRegenerationCoordinator,
                userIdProvider: () -> String?,
                database: com.browntowndev.enclave.core.data.local.EnclaveDatabase,
                stateStore: FirestoreSyncStateStore,
                cache: com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncDocumentCache,
            ): FirestoreSyncEventApplier {
                val healer = SensitivePersistenceSelfHealer(localEncryption, loggingPort)
                val repairer =
                    FirestoreSensitiveFieldRepairer(
                        StaticSensitiveFieldCatalog(),
                        healer,
                    )
                val syncContext =
                    FirestoreSyncDependencies(
                        firestore = firestore,
                        daos = FirestoreSyncDaos.from(chatSyncDaos, configSyncDaos, studioSyncDaos),
                        localEncryption = localEncryption,
                        sensitiveSelfHealer = healer,
                        firestoreSensitiveFieldRepairer = repairer,
                        cryptoManager = cryptoManager,
                        loggingPort = loggingPort,
                        apiKeyManager = apiKeyManager,
                        embeddingCoordinator = embeddingCoordinator,
                        userIdProvider = userIdProvider,
                    ).toContext()
                val registry = FirestoreSyncAdapterRegistry(syncContext)
                val adapterLookup = registry.adapters.associateBy { it.collectionPath }
                val localStore =
                    com.browntowndev.enclave.core.data.remote.firestore
                        .RoomFirestoreSyncLocalStore(
                            database = database,
                            firestore = firestore,
                            userIdProvider = userIdProvider,
                            adapterLookup = adapterLookup,
                            repairer = repairer,
                            embeddingCoordinator = embeddingCoordinator,
                            loggingPort = loggingPort,
                            syncStateStore = stateStore,
                            pendingSyncLookup =
                                com.browntowndev.enclave.core.data.remote.firestore
                                    .FirestoreSyncPendingSyncLookup(database),
                            cache = cache,
                        )
                val sourceDocStore =
                    com.browntowndev.enclave.core.data.remote.firestore
                        .FirestoreBackedSyncSourceDocumentStore(firestore, cache)
                return FirestoreSyncEventApplier(
                    sourceDocuments = sourceDocStore,
                    localStore = localStore,
                    stateStore = stateStore,
                )
            }

            @Suppress("LongParameterList")
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
                syncEventFeedClient: FirestoreSyncEventFeedClient = FirestoreSyncEventFeedClient(),
                syncEventApplier: FirestoreSyncEventDispatcher = FirestoreSyncEventApplier(),
                syncStateStore: FirestoreSyncStateStore = FirestoreSyncStateStore(),
                documentCache: com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncDocumentCache =
                    com.browntowndev.enclave.core.data.remote.firestore
                        .FirestoreSyncDocumentCache(firestore),
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
                    syncEventFeedClient = syncEventFeedClient,
                    syncEventApplier = syncEventApplier,
                    syncStateStore = syncStateStore,
                    documentCache = documentCache,
                )
        }
    }
