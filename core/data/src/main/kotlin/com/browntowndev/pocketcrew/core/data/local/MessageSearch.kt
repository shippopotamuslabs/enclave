package com.browntowndev.pocketcrew.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import com.browntowndev.pocketcrew.domain.model.chat.MessageId

@Entity(tableName = "message_search")
@Fts4(contentEntity = MessageEntity::class, tokenizer = FtsOptions.TOKENIZER_PORTER)
data class MessageSearch(
    @ColumnInfo(name = "id")
    val id: MessageId,
    @ColumnInfo(name = "content")
    val content: String
)
