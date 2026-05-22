package com.browntowndev.enclave.core.data.mapper.message

import com.browntowndev.enclave.core.data.local.MessageFileEntity
import com.browntowndev.enclave.domain.model.artifact.MimeType
import com.browntowndev.enclave.domain.model.chat.FileAttachment

private const val SYNC_STATE_NONE = 0

fun MessageFileEntity.toDomain(): FileAttachment =
    FileAttachment(
        name = name,
        content = content,
    )

fun FileAttachment.toEntity(
    id: String,
    messageId: String,
): MessageFileEntity =
    MessageFileEntity(
        id = id,
        messageId = messageId,
        name = name,
        content = content,
        mimeType = MimeType.PLAIN_TEXT,
        syncStatus = "PENDING_UPLOAD",
        pendingSync = SYNC_STATE_NONE,
    )
