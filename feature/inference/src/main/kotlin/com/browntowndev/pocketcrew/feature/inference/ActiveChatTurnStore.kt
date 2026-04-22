package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.model.chat.MessageSnapshot
import com.browntowndev.pocketcrew.domain.model.chat.TavilySource
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnSnapshotPort
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ActiveChatTurnStore @Inject constructor() : ActiveChatTurnSnapshotPort {
    private val entries = ConcurrentHashMap<ActiveChatTurnKey, Entry>()
    private val cleanupJobs = ConcurrentHashMap<ActiveChatTurnKey, Job>()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun observe(key: ActiveChatTurnKey): Flow<AccumulatedMessages?> {
        return entryFor(key).flow
    }

    override suspend fun publish(
        key: ActiveChatTurnKey,
        snapshot: AccumulatedMessages,
    ) {
        val entry = entryFor(key)
        entry.mutex.withLock {
            entry.flow.update { current ->
                mergeAccumulatedMessages(
                    current = current,
                    incoming = snapshot,
                )
            }
            if (entry.flow.value.isTerminalSnapshot()) {
                scheduleTerminalCleanup(key, entry)
            } else {
                cleanupJobs.remove(key)?.cancel()
            }
        }
    }

    override suspend fun markSourcesExtracted(
        key: ActiveChatTurnKey,
        urls: List<String>,
    ) {
        if (urls.isEmpty()) return

        val entry = entries[key] ?: return
        entry.mutex.withLock {
            entry.flow.update { current ->
                current?.copy(
                    messages = current.messages.mapValues { (_, snapshot) ->
                        snapshot.copy(
                            tavilySources = snapshot.tavilySources.map { source ->
                                if (source.url in urls) {
                                    source.copy(extracted = true)
                                } else {
                                    source
                                }
                            },
                        )
                    },
                )
            }
        }
    }

    override suspend fun acknowledgeHandoff(key: ActiveChatTurnKey) {
        retire(key)
    }

    override suspend fun clear(key: ActiveChatTurnKey) {
        retire(key)
    }

    internal fun snapshotValue(key: ActiveChatTurnKey): AccumulatedMessages? {
        return entries[key]?.flow?.value
    }

    private fun entryFor(key: ActiveChatTurnKey): Entry {
        return entries.computeIfAbsent(key) { Entry() }
    }

    private suspend fun retire(key: ActiveChatTurnKey) {
        cleanupJobs.remove(key)?.cancel()
        val entry = entries[key] ?: return
        entry.mutex.withLock {
            entry.flow.update { null }
            entries.remove(key, entry)
        }
    }

    private fun scheduleTerminalCleanup(
        key: ActiveChatTurnKey,
        entry: Entry,
    ) {
        cleanupJobs.remove(key)?.cancel()
        cleanupJobs[key] = cleanupScope.launch {
            delay(TERMINAL_CLEANUP_DELAY_MS)
            entry.mutex.withLock {
                if (entries[key] === entry && entry.flow.value.isTerminalSnapshot()) {
                    entry.flow.update { null }
                    entries.remove(key, entry)
                }
            }
            cleanupJobs.remove(key, coroutineContext[Job])
        }
    }

    private data class Entry(
        val flow: MutableStateFlow<AccumulatedMessages?> = MutableStateFlow(null),
        val mutex: Mutex = Mutex(),
    )

    private companion object {
        private const val TERMINAL_CLEANUP_DELAY_MS = 5 * 60 * 1000L
    }
}

private fun mergeAccumulatedMessages(
    current: AccumulatedMessages?,
    incoming: AccumulatedMessages,
): AccumulatedMessages {
    if (current == null) return incoming

    val mergedMessages = current.messages.toMutableMap()
    incoming.messages.forEach { (messageId, incomingSnapshot) ->
        val currentSnapshot = mergedMessages[messageId]
        mergedMessages[messageId] = if (currentSnapshot == null) {
            incomingSnapshot
        } else {
            mergeSnapshot(
                current = currentSnapshot,
                incoming = incomingSnapshot,
            )
        }
    }

    return AccumulatedMessages(messages = mergedMessages)
}

private fun mergeSnapshot(
    current: MessageSnapshot,
    incoming: MessageSnapshot,
): MessageSnapshot {
    val mergedSources = mergeSources(
        current = current.tavilySources,
        incoming = incoming.tavilySources,
    )

    if (current.messageState == MessageState.COMPLETE) {
        return current.copy(tavilySources = mergedSources)
    }

    if (!incoming.extendsTextFrom(current)) {
        return if (incoming.messageState == MessageState.COMPLETE) {
            current.copy(
                isComplete = true,
                messageState = MessageState.COMPLETE,
                thinkingDurationSeconds = maxOf(
                    current.thinkingDurationSeconds,
                    incoming.thinkingDurationSeconds,
                ),
                thinkingEndTime = incoming.thinkingEndTime.takeIf { endTime -> endTime != 0L }
                    ?: current.thinkingEndTime,
                tavilySources = mergedSources,
            )
        } else {
            current.copy(tavilySources = mergedSources)
        }
    }

    return incoming.copy(tavilySources = mergedSources)
}

private fun MessageSnapshot.extendsTextFrom(current: MessageSnapshot): Boolean {
    return content.startsWith(current.content) &&
        thinkingRaw.startsWith(current.thinkingRaw)
}

private fun mergeSources(
    current: List<TavilySource>,
    incoming: List<TavilySource>,
): List<TavilySource> {
    val currentByUrl = current.associateBy { source -> source.url }
    val incomingUrls = incoming.mapTo(mutableSetOf()) { source -> source.url }
    val mergedIncoming = incoming.map { source ->
        if (currentByUrl[source.url]?.extracted == true && !source.extracted) {
            source.copy(extracted = true)
        } else {
            source
        }
    }
    return mergedIncoming + current.filter { source -> source.url !in incomingUrls }
}

private fun AccumulatedMessages?.isTerminalSnapshot(): Boolean {
    return this != null && messages.isNotEmpty() && messages.values.all { snapshot ->
        snapshot.messageState == MessageState.COMPLETE
    }
}
