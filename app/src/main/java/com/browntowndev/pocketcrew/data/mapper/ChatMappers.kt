package com.browntowndev.pocketcrew.data.mapper

import com.browntowndev.pocketcrew.data.local.ChatEntity
import com.browntowndev.pocketcrew.data.local.MessageEntity
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.Message

/**
 * Mappers for converting between domain models and Room entities.
 * Uses requireNotNull() for null-checks as per contract requirements.
 */

/**
 * Converts ChatEntity to domain Chat model.
 */
fun ChatEntity.toDomain(): Chat = Chat(
    id = id,
    name = name,
    created = created,
    lastModified = lastModified,
    pinned = pinned
)

/**
 * Converts domain Chat model to ChatEntity.
 */
fun Chat.toEntity(): ChatEntity = ChatEntity(
    id = id,
    name = name,
    created = created,
    lastModified = lastModified,
    pinned = pinned
)

/**
 * Converts MessageEntity to domain Message model.
 */
fun MessageEntity.toDomain(): Message = Message(
    id = id,
    content = content,
    role = role,
    chatId = chatId
)

/**
 * Converts domain Message model to MessageEntity.
 */
fun Message.toEntity(): MessageEntity = MessageEntity(
    id = id,
    content = content,
    role = role,
    chatId = chatId
)
