package com.browntowndev.pocketcrew.core.data.remote

import com.browntowndev.pocketcrew.core.data.local.ChatDao
import com.browntowndev.pocketcrew.core.data.local.ChatEntity
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.local.MessageEntity
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import com.browntowndev.pocketcrew.domain.qualifier.ApplicationScope
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot

import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort

@Singleton
class FirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val cryptoManager: CryptoManagerPort,
    private val loggingPort: LoggingPort,
    @ApplicationScope private val externalScope: CoroutineScope,
    private val userIdProvider: () -> String?
) : CloudSyncPort {

    companion object {
        private const val TAG = "FirestoreDataSource"
    }

    private val listenerRegistrations = mutableListOf<ListenerRegistration>()

    override fun startListening() {
        if (listenerRegistrations.isNotEmpty()) {
            loggingPort.debug(TAG, "startListening: Already listening, skipping")
            return
        }
        loggingPort.debug(TAG, "startListening: Attempting to start Firestore listeners")
        try {
            val userId = userIdProvider()
            if (userId == null) {
                loggingPort.warning(TAG, "startListening: userId is null, skipping")
                return
            }

            loggingPort.debug(TAG, "startListening: Starting listeners for UID: $userId")
            val chatsListener = firestore.collection("users/$userId/chats")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        loggingPort.error(TAG, "Chats listener error", e)
                        return@addSnapshotListener
                    }
                    if (snapshots == null) {
                        loggingPort.warning(TAG, "Chats snapshot is null")
                        return@addSnapshotListener
                    }

                    loggingPort.debug(TAG, "Chats snapshot received: ${snapshots.size()} documents, ${snapshots.documentChanges.size} changes")
                    externalScope.launch {
                        try {
                            for (dc in snapshots.documentChanges) {
                                val doc = dc.document
                                when (dc.type) {
                                    DocumentChange.Type.ADDED -> {
                                        val entity = chatEntityFromDoc(doc)
                                        loggingPort.debug(TAG, "Chat ADDED: ${entity.id.value}")
                                        val existing = chatDao.getChatById(entity.id)
                                        // If it doesn't exist OR it's our temporary placeholder (identified by Epoch 0 timestamp)
                                        if (existing == null || existing.created.time == 0L) {
                                            chatDao.insert(entity)
                                        } else {
                                            loggingPort.debug(TAG, "Chat ${entity.id.value} already exists locally, skipping insert")
                                        }
                                    }
                                    DocumentChange.Type.MODIFIED -> {
                                        val entity = chatEntityFromDoc(doc)
                                        loggingPort.debug(TAG, "Chat MODIFIED: ${entity.id.value}")
                                        chatDao.insert(entity)
                                    }
                                    DocumentChange.Type.REMOVED -> {
                                        loggingPort.debug(TAG, "Chat REMOVED: ${doc.id}")
                                        chatDao.deleteById(ChatId(doc.id))
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            loggingPort.error(TAG, "Error processing chats snapshot", e)
                        }
                    }
                }

            val messagesListener = firestore.collection("users/$userId/messages")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        loggingPort.error(TAG, "Messages listener error", e)
                        return@addSnapshotListener
                    }
                    if (snapshots == null) {
                        loggingPort.warning(TAG, "Messages snapshot is null")
                        return@addSnapshotListener
                    }

                    loggingPort.debug(TAG, "Messages snapshot received: ${snapshots.size()} documents, ${snapshots.documentChanges.size} changes")
                    externalScope.launch {
                        try {
                            for (dc in snapshots.documentChanges) {
                                val doc = dc.document
                                when (dc.type) {
                                    DocumentChange.Type.ADDED -> {
                                        val entity = messageEntityFromDoc(doc)
                                        loggingPort.debug(TAG, "Message ADDED: ${entity.id.value}")
                                        
                                        // Synchronize: insert placeholder chat to avoid SQLiteConstraintException
                                        val chatExists = chatDao.getChatById(entity.chatId) != null
                                        if (!chatExists) {
                                            loggingPort.debug(TAG, "Inserting placeholder chat for message ${entity.id.value}")
                                            chatDao.insert(
                                                ChatEntity(
                                                    id = entity.chatId,
                                                    name = "Syncing...",
                                                    created = Date(0), // Sentinel value to identify placeholders
                                                    lastModified = Date(0),
                                                    pinned = false
                                                )
                                            )
                                        }

                                        val existing = messageDao.getMessageById(entity.id)
                                        if (existing == null) {
                                            messageDao.insert(entity)
                                        } else {
                                            loggingPort.debug(TAG, "Message ${entity.id.value} already exists locally, skipping insert")
                                        }
                                    }
                                    DocumentChange.Type.MODIFIED -> {
                                        val entity = messageEntityFromDoc(doc)
                                        loggingPort.debug(TAG, "Message MODIFIED: ${entity.id.value}")
                                        messageDao.insert(entity)
                                    }
                                    DocumentChange.Type.REMOVED -> {
                                        loggingPort.debug(TAG, "Message REMOVED: ${doc.id}")
                                        messageDao.deleteById(MessageId(doc.id))
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            loggingPort.error(TAG, "Error processing messages snapshot", e)
                        }
                    }
                }

            listenerRegistrations.add(chatsListener)
            listenerRegistrations.add(messagesListener)
            loggingPort.debug(TAG, "startListening: Listeners started successfully")
        } catch (e: Exception) {
            loggingPort.error(TAG, "Failed to start listening", e)
        }
    }

    override fun stopListening() {
        listenerRegistrations.forEach { it.remove() }
        listenerRegistrations.clear()
    }

    override suspend fun syncAllLocalDataToCloud(): Result<Unit> {
        loggingPort.debug(TAG, "!!! syncAllLocalDataToCloud: Starting initial sync !!!")
        val userId = userIdProvider()
        if (userId == null) {
            val error = "syncAllLocalDataToCloud: User not signed in"
            loggingPort.error(TAG, error)
            return Result.failure(IllegalStateException(error))
        }

        if (!cryptoManager.hasCachedKey()) {
            val error = "syncAllLocalDataToCloud: Vault is locked. Aborting sync."
            loggingPort.error(TAG, error)
            return Result.failure(IllegalStateException(error))
        }

        return try {
            val chats = chatDao.getAllChats().first()
            loggingPort.debug(TAG, "syncAllLocalDataToCloud: Found ${chats.size} chats to sync")
            
            withTimeout(15000L) {
                chats.forEach { chatEntity ->
                    loggingPort.debug(TAG, "syncAllLocalDataToCloud: Pushing chat ${chatEntity.id.value}")
                    val encryptedName = cryptoManager.encrypt(chatEntity.name).getOrThrow()
                    val chatData = mapOf(
                        "name" to Blob.fromBytes(encryptedName),
                        "created" to chatEntity.created.time,
                        "lastModified" to chatEntity.lastModified.time,
                        "pinned" to chatEntity.pinned
                    )
                    firestore.collection("users/$userId/chats")
                        .document(chatEntity.id.value)
                        .set(chatData, SetOptions.merge())
                        .await()
                    loggingPort.debug(TAG, "syncAllLocalDataToCloud: Successfully pushed chat ${chatEntity.id.value}")
                }

                val messages = messageDao.getAllMessages().first()
                loggingPort.debug(TAG, "syncAllLocalDataToCloud: Found ${messages.size} messages to sync")
                messages.forEach { messageWithFiles ->
                    val msg = messageWithFiles.message
                    loggingPort.debug(TAG, "syncAllLocalDataToCloud: Pushing message ${msg.id.value}")
                    val encryptedContent = cryptoManager.encrypt(msg.content).getOrThrow()
                    val msgData = mutableMapOf<String, Any?>(
                        "chatId" to msg.chatId.value,
                        "content" to Blob.fromBytes(encryptedContent),
                        "role" to msg.role.name,
                        "messageState" to msg.messageState.name
                    )
                    msg.createdAt?.let { msgData["createdAt"] = it }
                    msg.modelType?.let { msgData["modelType"] = it.name }

                    firestore.collection("users/$userId/messages")
                        .document(msg.id.value)
                        .set(msgData, SetOptions.merge())
                        .await()
                    loggingPort.debug(TAG, "syncAllLocalDataToCloud: Successfully pushed message ${msg.id.value}")
                }
            }

            loggingPort.debug(TAG, "syncAllLocalDataToCloud: Initial sync completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            loggingPort.error(TAG, "syncAllLocalDataToCloud: Sync failed", e)
            Result.failure(e)
        }
    }

    override suspend fun syncEntitiesToCloud(
        chats: List<Chat>,
        messages: List<Message>
    ): Result<Unit> {
        val userId = userIdProvider()
        if (userId == null) {
            loggingPort.error(TAG, "syncEntitiesToCloud: No user ID available, aborting sync")
            return Result.failure(IllegalStateException("User not signed in"))
        }
        
        if (!cryptoManager.hasCachedKey()) {
            loggingPort.error(TAG, "syncEntitiesToCloud: Vault is locked, aborting sync")
            return Result.failure(IllegalStateException("Vault is locked"))
        }

        return try {
            val allEntities = mutableListOf<Pair<String, Map<String, Any?>>>()
            
            chats.forEach { chat ->
                loggingPort.debug(TAG, "syncEntitiesToCloud: Preparing chat for batch: ${chat.id.value}")
                val encryptedName = cryptoManager.encrypt(chat.name).getOrThrow()
                val chatData = mapOf(
                    "name" to Blob.fromBytes(encryptedName),
                    "created" to chat.created.time,
                    "lastModified" to chat.lastModified.time,
                    "pinned" to chat.pinned
                )
                allEntities.add("users/$userId/chats/${chat.id.value}" to chatData)
            }

            messages.forEach { msg ->
                loggingPort.debug(TAG, "syncEntitiesToCloud: Adding message to batch: ${msg.id.value}")
                val encryptedContent = cryptoManager.encrypt(msg.content.text).getOrThrow()
                val msgData = mutableMapOf<String, Any?>(
                    "chatId" to msg.chatId.value,
                    "content" to Blob.fromBytes(encryptedContent),
                    "role" to msg.role.name,
                    "messageState" to msg.messageState.name
                )
                msg.createdAt?.let { msgData["createdAt"] = it }
                msg.modelType?.let { msgData["modelType"] = it.name }
                allEntities.add("users/$userId/messages/${msg.id.value}" to msgData)
            }

            if (allEntities.isEmpty()) {
                loggingPort.debug(TAG, "syncEntitiesToCloud: No entities to sync")
                return Result.success(Unit)
            }

            loggingPort.debug(TAG, "syncEntitiesToCloud: Executing batches for ${allEntities.size} items")
            // Firestore batches have a limit of 500 operations
            allEntities.chunked(500).forEach { batchItems ->
                firestore.runBatch { batch ->
                    batchItems.forEach { (path, data) ->
                        loggingPort.debug(TAG, "syncEntitiesToCloud: Batch set document: $path")
                        batch.set(firestore.document(path), data, SetOptions.merge())
                    }
                }.await()
            }
            
            loggingPort.debug(TAG, "syncEntitiesToCloud: Sync completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            loggingPort.error(TAG, "syncEntitiesToCloud: Sync failed", e)
            Result.failure(e)
        }
    }

    override suspend fun updateVaultSalt(salt: String): Result<Unit> {
        val userId = userIdProvider() ?: return Result.failure(IllegalStateException("User not signed in"))
        return try {
            firestore.document("users/$userId")
                .set(mapOf("vaultSalt" to salt), SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getVaultSalt(): Result<String?> {
        val userId = userIdProvider() ?: return Result.failure(IllegalStateException("User not signed in"))
        return try {
            val snapshot = firestore.document("users/$userId").get().await()
            if (snapshot.exists()) {
                Result.success(snapshot.getString("vaultSalt"))
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateVaultVerificationData(ciphertext: String): Result<Unit> {
        val userId = userIdProvider() ?: return Result.failure(IllegalStateException("User not signed in"))
        return try {
            firestore.document("users/$userId")
                .set(mapOf("vaultVerification" to ciphertext), SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getVaultVerificationData(): Result<String?> {
        val userId = userIdProvider() ?: return Result.failure(IllegalStateException("User not signed in"))
        return try {
            val snapshot = firestore.document("users/$userId").get().await()
            if (snapshot.exists()) {
                Result.success(snapshot.getString("vaultVerification"))
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun chatEntityFromDoc(doc: DocumentSnapshot): ChatEntity {
        val nameBlob = doc.getBlob("name")
        val name = nameBlob?.let {
            cryptoManager.decrypt(it.toBytes()).getOrDefault("Decryption failed")
        } ?: "Unnamed Chat"

        return ChatEntity(
            id = ChatId(doc.id),
            name = name,
            created = Date(doc.getLong("created") ?: System.currentTimeMillis()),
            lastModified = Date(doc.getLong("lastModified") ?: System.currentTimeMillis()),
            pinned = doc.getBoolean("pinned") ?: false
        )
    }

    private fun messageEntityFromDoc(doc: DocumentSnapshot): MessageEntity {
        val contentBlob = doc.getBlob("content")
        val content = contentBlob?.let {
            cryptoManager.decrypt(it.toBytes()).getOrDefault("Decryption failed")
        } ?: ""

        return MessageEntity(
            id = MessageId(doc.id),
            chatId = ChatId(doc.getString("chatId") ?: ""),
            content = content,
            role = doc.getString("role")?.let { Role.valueOf(it) } ?: Role.USER,
            messageState = doc.getString("messageState")?.let { MessageState.valueOf(it) }
                ?: MessageState.COMPLETE,
            createdAt = doc.getLong("createdAt"),
            modelType = doc.getString("modelType")?.let { ModelType.valueOf(it) }
        )
    }
}
