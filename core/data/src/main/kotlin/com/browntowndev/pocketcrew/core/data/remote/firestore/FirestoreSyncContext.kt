package com.browntowndev.pocketcrew.core.data.remote.firestore

import com.browntowndev.pocketcrew.core.data.local.ChatEntity
import com.browntowndev.pocketcrew.core.data.local.MessageEntity
import com.browntowndev.pocketcrew.core.data.remote.FirestoreSensitiveFieldRepairer
import com.browntowndev.pocketcrew.core.data.security.AadContext
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import com.browntowndev.pocketcrew.core.data.security.LocalDataEncryptionService
import com.browntowndev.pocketcrew.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.pocketcrew.core.data.sync.EmbeddingRegenerationCoordinator
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.atomic.AtomicBoolean

internal class FirestoreSyncContext(
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
    val purgeInProgress: AtomicBoolean,
) {
    companion object {
        const val TAG = "FirestoreDataSource"
        const val FIRESTORE_DELETE_PAGE_SIZE = 400L
    }

    suspend fun ensurePlaceholderMessage(
        messageId: MessageId,
        chatId: ChatId,
    ) {
        if (purgeInProgress.get()) return
        if (chatId.value.isBlank()) return
        if (messageId.value.isBlank()) return
        val chat = daos.chatDao.getChatById(chatId)
        if (chat == null) {
            daos.chatDao.insertIfAbsent(
                ChatEntity(
                    id = chatId,
                    name =
                        localEncryption.encryptString(
                            "Syncing...",
                            AadContext("ChatEntity", "name", chatId.value).toBytes(),
                        ),
                    created = java.util.Date(0),
                    lastModified = java.util.Date(0),
                    pinned = false,
                    updatedAt = 0L,
                    deleted = false,
                    pendingSync = 0,
                ),
            )
        }

        daos.messageDao.insertIfAbsent(
            MessageEntity(
                id = messageId,
                chatId = chatId,
                content =
                    localEncryption.encryptString(
                        "Syncing...",
                        AadContext("MessageEntity", "content", messageId.value).toBytes(),
                    ),
                role = Role.USER,
                messageState = MessageState.COMPLETE,
                createdAt = 0L,
                pendingSync = 0,
            ),
        )
    }

    suspend fun cleanupOrphanedPlaceholderChats() {
        if (purgeInProgress.get()) return
        val removed = daos.chatDao.deleteOrphanedPlaceholders()
        if (removed > 0) {
            loggingPort.debug(TAG, "Removed $removed orphaned placeholder chat(s)")
        }
    }
}
