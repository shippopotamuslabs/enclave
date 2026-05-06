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
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    @ApplicationScope private val externalScope: CoroutineScope,
    private val userIdProvider: () -> String?
) : CloudSyncPort {

    private val listenerRegistrations = mutableListOf<ListenerRegistration>()

    override fun startListening() {
        val userId = userIdProvider() ?: return

        val chatsListener = firestore.collection("users/$userId/chats")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                externalScope.launch {
                    for (dc in snapshots.documentChanges) {
                        val doc = dc.document
                        when (dc.type) {
                            DocumentChange.Type.ADDED -> {
                                val entity = chatEntityFromDoc(doc)
                                val existing = chatDao.getChatById(entity.id)
                                if (existing == null) {
                                    chatDao.insert(entity)
                                }
                            }
                            DocumentChange.Type.MODIFIED -> {
                                val entity = chatEntityFromDoc(doc)
                                chatDao.insert(entity)
                            }
                            DocumentChange.Type.REMOVED -> {
                                chatDao.deleteById(ChatId(doc.id))
                            }
                        }
                    }
                }
            }

        val messagesListener = firestore.collection("users/$userId/messages")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                externalScope.launch {
                    for (dc in snapshots.documentChanges) {
                        val doc = dc.document
                        when (dc.type) {
                            DocumentChange.Type.ADDED -> {
                                val entity = messageEntityFromDoc(doc)
                                val existing = messageDao.getMessageById(entity.id)
                                if (existing == null) {
                                    messageDao.insert(entity)
                                }
                            }
                            DocumentChange.Type.MODIFIED -> {
                                val entity = messageEntityFromDoc(doc)
                                messageDao.insert(entity)
                            }
                            DocumentChange.Type.REMOVED -> {
                                messageDao.deleteById(MessageId(doc.id))
                            }
                        }
                    }
                }
            }

        listenerRegistrations.add(chatsListener)
        listenerRegistrations.add(messagesListener)
    }

    override fun stopListening() {
        listenerRegistrations.forEach { it.remove() }
        listenerRegistrations.clear()
    }

    override suspend fun syncAllLocalDataToCloud(): Result<Unit> {
        val userId = userIdProvider()
            ?: return Result.failure(IllegalStateException("User not signed in"))
        return try {
            val chats = chatDao.getAllChats().first()
            chats.forEach { chatEntity ->
                val chatData = mapOf(
                    "name" to chatEntity.name,
                    "created" to chatEntity.created.time,
                    "lastModified" to chatEntity.lastModified.time,
                    "pinned" to chatEntity.pinned
                )
                firestore.collection("users/$userId/chats")
                    .document(chatEntity.id.value)
                    .set(chatData, SetOptions.merge())
                    .await()
            }

            val messages = messageDao.getAllMessages().first()
            messages.forEach { messageWithFiles ->
                val msg = messageWithFiles.message
                val msgData = mutableMapOf<String, Any?>(
                    "chatId" to msg.chatId.value,
                    "content" to msg.content,
                    "role" to msg.role.name,
                    "messageState" to msg.messageState.name
                )
                msg.createdAt?.let { msgData["createdAt"] = it }
                msg.modelType?.let { msgData["modelType"] = it.name }

                firestore.collection("users/$userId/messages")
                    .document(msg.id.value)
                    .set(msgData, SetOptions.merge())
                    .await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun chatEntityFromDoc(doc: com.google.firebase.firestore.QueryDocumentSnapshot): ChatEntity {
        return ChatEntity(
            id = ChatId(doc.id),
            name = doc.getString("name") ?: "Unnamed Chat",
            created = Date(doc.getLong("created") ?: System.currentTimeMillis()),
            lastModified = Date(doc.getLong("lastModified") ?: System.currentTimeMillis()),
            pinned = doc.getBoolean("pinned") ?: false
        )
    }

    private fun messageEntityFromDoc(doc: com.google.firebase.firestore.QueryDocumentSnapshot): MessageEntity {
        return MessageEntity(
            id = MessageId(doc.id),
            chatId = ChatId(doc.getString("chatId") ?: ""),
            content = doc.getString("content") ?: "",
            role = doc.getString("role")?.let { Role.valueOf(it) } ?: Role.USER,
            messageState = doc.getString("messageState")?.let { MessageState.valueOf(it) }
                ?: MessageState.COMPLETE,
            createdAt = doc.getLong("createdAt"),
            modelType = doc.getString("modelType")?.let { ModelType.valueOf(it) }
        )
    }
}
