package com.browntowndev.pocketcrew.core.data.mapper.message

import com.browntowndev.pocketcrew.core.data.local.WebSearchSourceEntity
import com.browntowndev.pocketcrew.domain.model.chat.WebSearchSource

private const val SYNC_STATE_PENDING = 1

fun WebSearchSourceEntity.toDomain(): WebSearchSource =
    WebSearchSource(
        id = id,
        messageId = messageId,
        title = title,
        url = url,
        content = content,
        score = score,
        extracted = extracted,
    )

fun WebSearchSource.toEntity(): WebSearchSourceEntity =
    WebSearchSourceEntity(
        id = id,
        messageId = messageId,
        title = title,
        url = url,
        content = content,
        score = score,
        extracted = extracted,
        pendingSync = SYNC_STATE_PENDING,
    )
