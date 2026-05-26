package com.browntowndev.enclave.core.data.inference

import com.browntowndev.enclave.core.data.local.MessageCommittedInferencePromptDao
import com.browntowndev.enclave.core.data.local.MessageCommittedInferencePromptEntity
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.port.inference.CommittedInferencePromptPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.qualifier.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomCommittedInferencePromptStore @Inject constructor(
    private val dao: MessageCommittedInferencePromptDao,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val logger: LoggingPort,
) : CommittedInferencePromptPort {

    private val cache = ConcurrentHashMap<MessageId, String>()

    override fun commit(userMessageId: MessageId, content: String): Unit {
        cache[userMessageId] = content
        scope.launch(Dispatchers.IO) {
            try {
                dao.insertOrUpdate(
                    MessageCommittedInferencePromptEntity(
                        messageId = userMessageId,
                        inferenceContent = content,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            } catch (e: Exception) {
                logger.error(
                    "RoomCommittedInferencePromptStore",
                    "Failed to commit prompt for message: $userMessageId",
                    e,
                )
            }
        }
    }

    override fun get(userMessageId: MessageId): String? {
        val cached = cache[userMessageId]
        if (cached != null) {
            return cached
        }
        return try {
            runBlocking(Dispatchers.IO) {
                val entity = dao.getByMessageId(userMessageId)
                if (entity != null) {
                    cache[userMessageId] = entity.inferenceContent
                    entity.inferenceContent
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            logger.error(
                "RoomCommittedInferencePromptStore",
                "Failed to get prompt for message: $userMessageId",
                e,
            )
            null
        }
    }

    override fun clear(userMessageId: MessageId): Unit {
        cache.remove(userMessageId)
        scope.launch(Dispatchers.IO) {
            try {
                dao.deleteByMessageId(userMessageId)
            } catch (e: Exception) {
                logger.error(
                    "RoomCommittedInferencePromptStore",
                    "Failed to clear prompt for message: $userMessageId",
                    e,
                )
            }
        }
    }
}
