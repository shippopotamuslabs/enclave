package com.browntowndev.pocketcrew.core.data.local.dao.message

import androidx.room.Embedded
import androidx.room.Relation
import com.browntowndev.pocketcrew.core.data.local.MessageEntity
import com.browntowndev.pocketcrew.core.data.local.MessageFileEntity

data class MessageWithFiles(
    @Embedded val message: MessageEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "message_id",
    )
    val files: List<MessageFileEntity>,
)
