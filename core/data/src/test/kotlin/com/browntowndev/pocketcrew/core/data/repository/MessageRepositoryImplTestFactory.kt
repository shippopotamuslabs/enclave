package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ChatSummaryDao
import com.browntowndev.pocketcrew.core.data.local.EmbeddingDao
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.local.MessageVisionAnalysisDao
import com.browntowndev.pocketcrew.core.data.repository.message.MessageEncryptionCoordinator
import com.browntowndev.pocketcrew.core.data.repository.message.MessagePersistenceCoordinator
import com.browntowndev.pocketcrew.core.data.repository.message.MessageReadCoordinator
import com.browntowndev.pocketcrew.core.data.repository.message.MessageVisionWriter
import com.browntowndev.pocketcrew.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.pocketcrew.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import io.mockk.mockk

internal fun createMessageRepositoryImpl(
    syncTrigger: CloudSyncTrigger = mockk(relaxed = true),
    messageDao: MessageDao = mockk(relaxed = true),
    messageVisionAnalysisDao: MessageVisionAnalysisDao = mockk(relaxed = true),
    chatSummaryDao: ChatSummaryDao = mockk(relaxed = true),
    embeddingDao: EmbeddingDao = mockk(relaxed = true),
    transactionProvider: TransactionProvider = mockk(relaxed = true),
    healer: SensitivePersistenceSelfHealer = testSensitiveSelfHealer(FakeLocalDataEncryptionService()),
    readRepairApplier: SensitiveRoomReadRepairApplier = mockk(relaxed = true),
): MessageRepositoryImpl {
    val encryption = MessageEncryptionCoordinator(healer)
    val persistence =
        MessagePersistenceCoordinator(
            syncTrigger = syncTrigger,
            messageDao = messageDao,
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
