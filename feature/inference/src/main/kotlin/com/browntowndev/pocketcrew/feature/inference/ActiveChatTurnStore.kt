package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages
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
                // Merge current artifacts into the new snapshot if they exist
                val mergedMessages = snapshot.messages.mapValues { (id, newSnapshot) ->
                    val existingArtifacts = current?.messages?.get(id)?.artifacts ?: emptyList()
                    newSnapshot.copy(artifacts = (newSnapshot.artifacts + existingArtifacts).distinct())
                }
                snapshot.copy(messages = mergedMessages)
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

    override suspend fun attachArtifact(
        key: ActiveChatTurnKey,
        assistantMessageId: com.browntowndev.pocketcrew.domain.model.chat.MessageId,
        artifact: com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest,
    ) {
        val entry = entries[key] ?: return
        entry.mutex.withLock {
            entry.flow.update { current ->
                current?.copy(
                    messages = current.messages.mapValues { (id, snapshot) ->
                        if (id == assistantMessageId) {
                            snapshot.copy(artifacts = snapshot.artifacts + artifact)
                        } else {
                            snapshot
                        }
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

    override suspend fun getSnapshot(key: ActiveChatTurnKey): AccumulatedMessages? {
        return entries[key]?.flow?.value
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

private fun AccumulatedMessages?.isTerminalSnapshot(): Boolean {
    return this != null && messages.isNotEmpty() && messages.values.all { snapshot ->
        snapshot.messageState == MessageState.COMPLETE
    }
}
