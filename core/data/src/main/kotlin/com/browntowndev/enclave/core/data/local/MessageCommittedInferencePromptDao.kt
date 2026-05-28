package com.browntowndev.enclave.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.browntowndev.enclave.domain.model.chat.MessageId

@Dao
interface MessageCommittedInferencePromptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(entity: MessageCommittedInferencePromptEntity): Unit

    @Query("SELECT * FROM message_committed_inference_prompt WHERE message_id = :messageId")
    fun getByMessageId(messageId: MessageId): MessageCommittedInferencePromptEntity?

    @Query("DELETE FROM message_committed_inference_prompt WHERE message_id = :messageId")
    fun deleteByMessageId(messageId: MessageId): Unit
}
