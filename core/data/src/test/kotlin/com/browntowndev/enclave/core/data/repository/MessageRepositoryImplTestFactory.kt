package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.ChatEntity
import com.browntowndev.enclave.core.data.local.ChatSummaryDao
import com.browntowndev.enclave.core.data.local.EmbeddingDao
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.MessageDao
import com.browntowndev.enclave.core.data.local.MessageFileDao
import com.browntowndev.enclave.core.data.local.MessageVisionAnalysisDao
import com.browntowndev.enclave.core.data.local.messageDao
import com.browntowndev.enclave.core.data.repository.message.MessageEncryptionCoordinator
import com.browntowndev.enclave.core.data.repository.message.MessagePersistenceCoordinator
import com.browntowndev.enclave.core.data.repository.message.MessageReadCoordinator
import com.browntowndev.enclave.core.data.repository.message.MessageVisionWriter
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.enclave.core.data.sync.CloudSyncTrigger
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeTransactionProvider
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.port.repository.TransactionProvider
import java.util.Date

/**
 * Test factory for [MessageRepositoryImpl] (ENC-28, MockK-free): every
 * collaborator is a real implementation or a hand-written fake.
 *
 * The [createMessageRepositoryImpl] overload taking an [EnclaveDatabase]
 * wires the split DAOs, the Room transaction provider, and the read-repair
 * applier from that database, so persistence and repair assertions observe
 * the same database the test seeded.
 *
 * The DAO-wise overload keeps the historical call shape for tests that
 * bring their own DAOs (e.g. ChatHistoryRehydratorIntegrationTest). Its
 * defaults are fakes, except the DAO / applier defaults, which come from a
 * shared scratch in-memory database: collaborators sourced from it act as
 * discard sinks (their writes land in the scratch database, never in the
 * caller's), so a caller that asserts on persistence or on read repairs
 * must pass the real collaborators explicitly.
 */
internal fun createInMemoryEnclaveDatabase(): EnclaveDatabase =
    Room
        .inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            EnclaveDatabase::class.java,
        ).allowMainThreadQueries()
        .build()

/** Shared scratch database backing the DAO-wise overload's default collaborators. */
private val scratchDatabase: EnclaveDatabase by lazy { createInMemoryEnclaveDatabase() }

internal fun createMessageRepositoryImpl(
    database: EnclaveDatabase,
    syncTrigger: CloudSyncTrigger = RecordingCloudSyncTrigger(),
    healer: SensitivePersistenceSelfHealer = testSensitiveSelfHealer(FakeLocalDataEncryptionService(), FakeLoggingPort()),
    readRepairApplier: SensitiveRoomReadRepairApplier = database.realReadRepairApplier(),
): MessageRepositoryImpl =
    createMessageRepositoryImpl(
        syncTrigger = syncTrigger,
        messageDao = database.messageDao(),
        messageVisionAnalysisDao = database.messageVisionAnalysisDao(),
        chatSummaryDao = database.chatSummaryDao(),
        embeddingDao = database.embeddingDao(),
        transactionProvider = RoomTransactionProvider(database),
        healer = healer,
        readRepairApplier = readRepairApplier,
        messageFileDao = database.messageFileDao(),
    )

internal fun createMessageRepositoryImpl(
    syncTrigger: CloudSyncTrigger = RecordingCloudSyncTrigger(),
    messageDao: MessageDao = scratchDatabase.messageDao(),
    messageVisionAnalysisDao: MessageVisionAnalysisDao = scratchDatabase.messageVisionAnalysisDao(),
    chatSummaryDao: ChatSummaryDao = scratchDatabase.chatSummaryDao(),
    embeddingDao: EmbeddingDao = scratchDatabase.embeddingDao(),
    transactionProvider: TransactionProvider = FakeTransactionProvider(),
    healer: SensitivePersistenceSelfHealer = testSensitiveSelfHealer(FakeLocalDataEncryptionService(), FakeLoggingPort()),
    readRepairApplier: SensitiveRoomReadRepairApplier = scratchDatabase.realReadRepairApplier(),
    messageFileDao: MessageFileDao = scratchDatabase.messageFileDao(),
): MessageRepositoryImpl {
    val encryption = MessageEncryptionCoordinator(healer)
    val persistence =
        MessagePersistenceCoordinator(
            syncTrigger = syncTrigger,
            messageDao = messageDao,
            messageFileDao = messageFileDao,
            embeddingDao = embeddingDao,
            chatSummaryDao = chatSummaryDao,
            transactionProvider = transactionProvider,
            healer = healer,
        )
    val reads =
        MessageReadCoordinator(
            messageDao = messageDao,
            chatSummaryDao = chatSummaryDao,
            encryption = encryption,
            persistence = persistence,
            healer = healer,
            readRepairApplier = readRepairApplier,
        )
    val vision =
        MessageVisionWriter(
            syncTrigger = syncTrigger,
            messageVisionAnalysisDao = messageVisionAnalysisDao,
            healer = healer,
            readRepairApplier = readRepairApplier,
        )
    return MessageRepositoryImpl(
        persistence = persistence,
        reads = reads,
        vision = vision,
    )
}

/** Seeds the parent chat row that message rows foreign-key to. */
internal suspend fun EnclaveDatabase.seedChat(chatId: ChatId) {
    chatDao().upsert(
        ChatEntity(
            id = chatId,
            name = "chat-$chatId",
            created = Date(0L),
            lastModified = Date(0L),
            pinned = false,
        ),
    )
}
