package com.browntowndev.enclave.core.data.local.dao.message

import androidx.room.Embedded
import androidx.room.Relation
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.MessageFileEntity

data class MessageWithFiles(
    @Embedded val message: MessageEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "message_id",
    )
    val files: List<MessageFileEntity>,
)
