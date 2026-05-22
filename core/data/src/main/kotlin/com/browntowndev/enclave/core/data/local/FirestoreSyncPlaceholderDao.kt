package com.browntowndev.enclave.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Transaction

/**
 * Atomic placeholder inserts for inbound Firestore child documents (web search sources,
 * message files, etc.) that may arrive before their parent chat/message rows exist locally.
 */
@Dao
abstract class FirestoreSyncPlaceholderDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertChatIfAbsent(chatEntity: ChatEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertMessageIfAbsent(messageEntity: MessageEntity): Long

    @Transaction
    open suspend fun insertPlaceholderChatAndMessage(
        chatEntity: ChatEntity,
        messageEntity: MessageEntity,
    ) {
        insertChatIfAbsent(chatEntity)
        insertMessageIfAbsent(messageEntity)
    }
}
