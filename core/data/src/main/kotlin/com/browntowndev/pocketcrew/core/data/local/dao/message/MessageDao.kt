package com.browntowndev.pocketcrew.core.data.local.dao.message

import com.browntowndev.pocketcrew.core.data.local.MessageEntity
import com.browntowndev.pocketcrew.core.data.local.MessageFileEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageDao
    @Inject
    constructor(
        private val query: MessageQueryDao,
        private val command: MessageCommandDao,
        private val syncCommand: MessageSyncCommandDao,
        private val persist: MessageDaoPersist,
    ) : MessageQueryDao by query,
        MessageCommandDao by command,
        MessageSyncCommandDao by syncCommand {
        suspend fun persistAllMessageData(payload: MessagePersistPayload) = persist.persist(payload)

        // Explicit overrides so mockk can stub command paths on MessageDao mocks in unit tests.
        override suspend fun upsert(messageEntity: MessageEntity): Long = command.upsert(messageEntity)

        override suspend fun insertFiles(files: List<MessageFileEntity>) = command.insertFiles(files)
    }
