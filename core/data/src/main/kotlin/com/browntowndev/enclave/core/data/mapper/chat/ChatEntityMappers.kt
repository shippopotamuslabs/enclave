package com.browntowndev.enclave.core.data.mapper.chat

import com.browntowndev.enclave.core.data.local.ChatEntity
import com.browntowndev.enclave.domain.model.chat.Chat

private const val SYNC_STATE_PENDING = 1
private const val UNINITIALIZED_TIME = 0L

fun ChatEntity.toDomain(): Chat =
    Chat(
        id = id,
        name = name,
        created = created,
        lastModified = lastModified,
        pinned = pinned,
        isSyncing = created.time == UNINITIALIZED_TIME,
    )

fun Chat.toEntity(): ChatEntity =
    ChatEntity(
        id = id,
        name = name,
        created = created,
        lastModified = lastModified,
        pinned = pinned,
        pendingSync = SYNC_STATE_PENDING,
    )
