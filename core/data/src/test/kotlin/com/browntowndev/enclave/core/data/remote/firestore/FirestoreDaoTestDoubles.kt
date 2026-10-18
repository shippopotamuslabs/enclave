package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.data.local.ChatDao
import com.browntowndev.enclave.core.data.local.ChatEntity
import com.browntowndev.enclave.core.data.local.ChatSummaryDao
import com.browntowndev.enclave.core.data.local.ChatSummaryEntity
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.FirestoreSyncPlaceholderDao
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.MessageInferenceTelemetryDao
import com.browntowndev.enclave.core.data.local.MessageInferenceTelemetryEntity
import com.browntowndev.enclave.core.data.local.MessageTranscriptSegmentDao
import com.browntowndev.enclave.core.data.local.MessageTranscriptSegmentEntity
import com.browntowndev.enclave.core.data.local.TelemetryEventDao
import com.browntowndev.enclave.core.data.local.TelemetryEventEntity
import com.browntowndev.enclave.core.data.local.dao.message.MessageCommandDao
import com.browntowndev.enclave.core.data.local.dao.message.MessageDao
import com.browntowndev.enclave.core.data.local.dao.message.MessageDaoPersist
import com.browntowndev.enclave.core.data.local.dao.message.MessageQueryDao
import com.browntowndev.enclave.core.data.local.dao.message.MessageSyncCommandDao
import com.browntowndev.enclave.core.data.local.dao.message.MessageWithFiles
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.kotlinFunction

/**
 * Hand-written DAO doubles for the Firestore sync test suites (ENC-28).
 *
 * Two shapes:
 *  - [recordingDao]: a dynamic-proxy recording fake for the Room DAO *interfaces*.
 *    Unscripted calls return the same relaxed defaults MockK provided (null for
 *    nullable/reference returns, empty collections, 0/false for primitives), and
 *    every invocation is recorded for state-based assertions.
 *  - Explicit in-memory subclasses for the Room DAO *abstract classes* (ChatDao,
 *    ChatSummaryDao, MessageTranscriptSegmentDao, MessageInferenceTelemetryDao,
 *    FirestoreSyncPlaceholderDao, TelemetryEventDao), which cannot be proxied.
 */

// ---------------------------------------------------------------------------
// Recording proxy fake for DAO interfaces
// ---------------------------------------------------------------------------

class RecordingDaoHandle<T : Any> @PublishedApi internal constructor(
    val dao: T,
    private val scripts: MutableMap<String, (List<Any?>) -> Any?>,
    val calls: MutableList<Call>,
) {
    data class Call(
        val method: String,
        val args: List<Any?>,
    )

    /** Script [method] to return [result] on every call. */
    fun script(
        method: String,
        result: Any?,
    ) {
        scripts[method] = { result }
    }

    /** Script [method] with a handler receiving the call arguments (excluding the continuation). */
    fun script(
        method: String,
        handler: (List<Any?>) -> Any?,
    ) {
        scripts[method] = handler
    }

    fun callsTo(method: String): List<Call> = calls.filter { it.method == method }

    fun countCalls(method: String): Int = callsTo(method).size
}

inline fun <reified T : Any> recordingDao(): RecordingDaoHandle<T> {
    val scripts = mutableMapOf<String, (List<Any?>) -> Any?>()
    val calls = mutableListOf<RecordingDaoHandle.Call>()
    val handler =
        InvocationHandler { proxy, method, rawArgs ->
            val args = rawArgs?.toList() ?: emptyList()
            if (method.declaringClass == Any::class.java) {
                return@InvocationHandler when (method.name) {
                    "toString" -> "RecordingDao(${T::class.simpleName})"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args.firstOrNull()
                    else -> null
                }
            }
            // Suspend functions receive a trailing Continuation that is not part of the
            // declared signature; drop it from the recorded arguments.
            val isSuspend = args.lastOrNull() is kotlin.coroutines.Continuation<*>
            val effectiveArgs = if (isSuspend) args.dropLast(1) else args
            // Key scripts and recorded calls by the KOTLIN function name, not the JVM
            // name: DAO methods taking value-class parameters (MessageId, ChatId, ...)
            // have mangled JVM names (e.g. "getMessageById-Y3LuFk0"), so a script
            // registered as "getMessageById" never matched and the call silently fell
            // through to the relaxed default. (ENC-28 cluster R: this is why the
            // Message/MessageFile sync-handler fixtures saw a null existing row.)
            val kotlinName = method.kotlinFunction?.name ?: method.name
            calls += RecordingDaoHandle.Call(kotlinName, effectiveArgs)
            scripts[kotlinName]?.let { return@InvocationHandler it(effectiveArgs) }
            relaxedDefault(method, isSuspend)
        }
    @Suppress("UNCHECKED_CAST")
    val dao =
        Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java),
            handler,
        ) as T
    return RecordingDaoHandle(dao, scripts, calls)
}

/** MockK-relaxed-style default for an unscripted DAO call. */
@PublishedApi
internal fun relaxedDefault(
    method: Method,
    isSuspend: Boolean,
): Any? {
    val classifier: KClass<*>? =
        if (isSuspend || method.returnType == Any::class.java) {
            method.kotlinFunction?.returnType?.classifier as? KClass<*>
        } else {
            method.returnType.kotlin
        }
    return when {
        classifier == null -> null
        classifier == Unit::class -> Unit
        classifier == Boolean::class -> false
        classifier == Int::class -> 0
        classifier == Long::class -> 0L
        classifier == Double::class -> 0.0
        classifier == Float::class -> 0f
        classifier == Short::class -> 0.toShort()
        classifier == Byte::class -> 0.toByte()
        classifier == Char::class -> 0.toChar()
        classifier == String::class -> ""
        classifier.isSubclassOf(List::class) -> emptyList<Any?>()
        classifier.isSubclassOf(Set::class) -> emptySet<Any?>()
        classifier.isSubclassOf(Map::class) -> emptyMap<Any?, Any?>()
        classifier.isSubclassOf(Flow::class) -> emptyFlow<Any?>()
        classifier.java.isInterface ->
            Proxy.newProxyInstance(
                classifier.java.classLoader,
                arrayOf(classifier.java),
            ) { _, nestedMethod, _ -> relaxedDefault(nestedMethod, isSuspend = false) }
        else -> null
    }
}

// ---------------------------------------------------------------------------
// ChatDao (abstract class) — in-memory implementation with recorded writes
// ---------------------------------------------------------------------------

class FakeChatDao : ChatDao() {
    val entities = linkedMapOf<ChatId, ChatEntity>()
    val upsertedChats = mutableListOf<ChatEntity>()
    val markSyncedCalls = mutableListOf<List<ChatId>>()

    /** Optional shared ordering log (e.g. "countActiveChats") for cross-collaborator assertions. */
    var events: MutableList<String>? = null

    fun seed(chat: ChatEntity) {
        entities[chat.id] = chat
    }

    override suspend fun upsert(chatEntity: ChatEntity): Long {
        upsertedChats += chatEntity
        entities[chatEntity.id] = chatEntity
        return 1L
    }

    override suspend fun insertIfAbsent(chatEntity: ChatEntity): Long {
        if (chatEntity.id in entities) return -1L
        entities[chatEntity.id] = chatEntity
        return 1L
    }

    override suspend fun update(chatEntity: ChatEntity): Int {
        if (chatEntity.id !in entities) return 0
        entities[chatEntity.id] = chatEntity
        return 1
    }

    override suspend fun delete(chatEntity: ChatEntity): Int =
        if (entities.remove(chatEntity.id) != null) 1 else 0

    override fun getAllChats(): Flow<List<ChatEntity>> =
        flowOf(entities.values.filter { !it.deleted })

    override suspend fun countActiveChats(): Int {
        events?.add("countActiveChats")
        return entities.values.count { !it.deleted }
    }

    override suspend fun getChatById(id: ChatId): ChatEntity? = entities[id]?.takeUnless { it.deleted }

    override suspend fun getChatsByIds(ids: List<ChatId>): List<ChatEntity> =
        ids.mapNotNull { entities[it] }.filter { !it.deleted }

    override suspend fun claimPending() {
        entities.replaceAll { _, entity ->
            if (entity.pendingSync == 1) entity.copy(pendingSync = 2) else entity
        }
    }

    override suspend fun getClaimedForSync(): List<ChatEntity> =
        entities.values.filter { it.pendingSync == 2 }

    override suspend fun markSynced(ids: List<ChatId>) {
        markSyncedCalls += ids
        ids.forEach { id -> entities[id]?.let { entities[id] = it.copy(pendingSync = 0) } }
    }

    override suspend fun updatePinStatus(
        chatId: ChatId,
        timestamp: Long,
    ): Int {
        val existing = entities[chatId] ?: return 0
        entities[chatId] = existing.copy(pinned = !existing.pinned, pendingSync = 1, updatedAt = timestamp)
        return 1
    }

    override suspend fun softDeleteById(
        chatId: ChatId,
        timestamp: Long,
    ): Int {
        val existing = entities[chatId] ?: return 0
        entities[chatId] = existing.copy(deleted = true, pendingSync = 1, updatedAt = timestamp)
        return 1
    }

    override suspend fun purgeDeleted(ids: List<ChatId>): Int {
        var removed = 0
        ids.forEach { id ->
            if (entities[id]?.deleted == true && entities.remove(id) != null) removed++
        }
        return removed
    }

    override suspend fun deleteOrphanedPlaceholders(): Int = 0

    override suspend fun updateName(
        chatId: ChatId,
        newName: String,
        timestamp: Long,
    ): Int {
        val existing = entities[chatId] ?: return 0
        entities[chatId] = existing.copy(name = newName, pendingSync = 1, updatedAt = timestamp)
        return 1
    }

    override fun searchChats(
        query: String,
        messageIds: List<MessageId>,
    ): Flow<List<ChatEntity>> = flowOf(emptyList())
}

// ---------------------------------------------------------------------------
// Remaining abstract-class DAOs — minimal in-memory subclasses (filler roles)
// ---------------------------------------------------------------------------

class FakeChatSummaryDao : ChatSummaryDao() {
    val entities = linkedMapOf<Long, ChatSummaryEntity>()
    val insertOrUpdateCalls = mutableListOf<ChatSummaryEntity>()

    override fun getSummaryForChat(chatId: ChatId): Flow<ChatSummaryEntity?> =
        flowOf(entities.values.firstOrNull { it.chatId == chatId })

    override suspend fun getSummaryForChatSync(chatId: ChatId): ChatSummaryEntity? =
        entities.values.firstOrNull { it.chatId == chatId && !it.deleted }

    override suspend fun insertOrUpdateSummary(summary: ChatSummaryEntity) {
        insertOrUpdateCalls += summary
        entities[summary.id] = summary
    }

    override suspend fun getByIdSync(id: Long): ChatSummaryEntity? = entities[id]

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<ChatSummaryEntity> = emptyList()

    override suspend fun markSynced(ids: List<Long>) = Unit

    override suspend fun softDeleteById(
        id: Long,
        timestamp: Long,
    ): Int = if (entities.remove(id) != null) 1 else 0

    override suspend fun deleteSummaryForChat(
        chatId: ChatId,
        timestamp: Long,
    ): Int {
        val removed = entities.values.filter { it.chatId == chatId }
        removed.forEach { entities.remove(it.id) }
        return removed.size
    }

    override suspend fun getSummaryByChatIdIncludingDeletedSync(chatId: ChatId): ChatSummaryEntity? =
        entities.values.firstOrNull { it.chatId == chatId }
}

class FakeMessageTranscriptSegmentDao : MessageTranscriptSegmentDao() {
    val entities = linkedMapOf<String, MessageTranscriptSegmentEntity>()
    val upsertCalls = mutableListOf<MessageTranscriptSegmentEntity>()

    override fun observeSegmentsForChat(chatId: ChatId): Flow<List<MessageTranscriptSegmentEntity>> =
        flowOf(entities.values.toList())

    override suspend fun getById(id: String): MessageTranscriptSegmentEntity? = entities[id]

    override suspend fun softDeleteAllActiveForMessage(
        messageId: MessageId,
        timestamp: Long,
    ) = Unit

    override suspend fun insertAll(segments: List<MessageTranscriptSegmentEntity>) {
        segments.forEach { entities[it.id] = it }
    }

    override suspend fun upsert(entity: MessageTranscriptSegmentEntity) {
        upsertCalls += entity
        entities[entity.id] = entity
    }

    override suspend fun softDeleteById(
        id: String,
        timestamp: Long,
    ) = Unit

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<MessageTranscriptSegmentEntity> = emptyList()

    override suspend fun markSynced(ids: List<String>) = Unit
}

class FakeMessageInferenceTelemetryDao : MessageInferenceTelemetryDao() {
    val entities = linkedMapOf<String, MessageInferenceTelemetryEntity>()
    val upsertCalls = mutableListOf<MessageInferenceTelemetryEntity>()

    override suspend fun insertAll(rows: List<MessageInferenceTelemetryEntity>) {
        rows.forEach { entities[it.id] = it }
    }

    override suspend fun upsert(entity: MessageInferenceTelemetryEntity) {
        upsertCalls += entity
        entities[entity.id] = entity
    }

    override suspend fun getById(id: String): MessageInferenceTelemetryEntity? = entities[id]

    override fun observeByMessageId(messageId: String): Flow<List<MessageInferenceTelemetryEntity>> =
        flowOf(entities.values.filter { it.messageId == messageId })

    override fun observeByChatId(chatId: String): Flow<List<MessageInferenceTelemetryEntity>> =
        flowOf(entities.values.filter { it.chatId == chatId })

    override fun observeAll(): Flow<List<MessageInferenceTelemetryEntity>> =
        flowOf(entities.values.toList())

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<MessageInferenceTelemetryEntity> = emptyList()

    override suspend fun markSynced(ids: List<String>) = Unit

    override suspend fun softDeleteById(
        id: String,
        timestamp: Long,
    ) = Unit

    override suspend fun softDeleteAllActiveForMessage(
        messageId: String,
        timestamp: Long,
    ) = Unit
}

class FakeFirestoreSyncPlaceholderDao : FirestoreSyncPlaceholderDao() {
    val insertedChats = mutableListOf<ChatEntity>()
    val insertedMessages = mutableListOf<MessageEntity>()

    override suspend fun insertChatIfAbsent(chatEntity: ChatEntity): Long {
        if (insertedChats.any { it.id == chatEntity.id }) return -1L
        insertedChats += chatEntity
        return 1L
    }

    override suspend fun insertMessageIfAbsent(messageEntity: MessageEntity): Long {
        if (insertedMessages.any { it.id == messageEntity.id }) return -1L
        insertedMessages += messageEntity
        return 1L
    }
}

class FakeTelemetryEventDao : TelemetryEventDao() {
    val insertedEvents = mutableListOf<TelemetryEventEntity>()

    override suspend fun insert(event: TelemetryEventEntity) {
        insertedEvents += event
    }

    override suspend fun getPendingUploads(limit: Int): List<TelemetryEventEntity> =
        insertedEvents.take(limit)

    override suspend fun markUploaded(ids: List<String>) = Unit

    override suspend fun evictOldest(count: Int) = Unit

    override suspend fun getCount(): Int = insertedEvents.size

    override suspend fun deleteUploaded() = Unit
}

// ---------------------------------------------------------------------------
// MessageDao bundle — the real delegating class over recording sub-DAO proxies
// ---------------------------------------------------------------------------

/**
 * A real [MessageDao] wired over recording proxies for its query/command
 * sub-DAOs, so handler tests can script reads (e.g. `getMessageById`) and
 * assert writes (e.g. `upsert`) without mocking the concrete class.
 */
class FakeMessageDaoBundle(
    database: EnclaveDatabase,
) {
    val query = recordingDao<MessageQueryDao>()
    val command = recordingDao<MessageCommandDao>()
    val syncCommand = recordingDao<MessageSyncCommandDao>()
    val persist = MessageDaoPersist(database, command.dao, syncCommand.dao)
    val messageDao: MessageDao = MessageDao(query.dao, command.dao, syncCommand.dao, persist)

    /** Every entity passed to the command DAO's `upsert`, in call order. */
    val upsertedMessages: List<MessageEntity>
        get() = command.callsTo("upsert").map { it.args[0] as MessageEntity }

    fun stubMessageById(message: MessageWithFiles?) {
        query.script("getMessageById") { message }
    }
}
