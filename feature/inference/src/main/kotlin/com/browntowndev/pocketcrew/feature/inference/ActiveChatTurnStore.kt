package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.pocketcrew.domain.model.artifact.mergeArtifactGenerationRequests
import com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.model.chat.MessageSnapshot
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
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
                val mergedMessages = current?.messages?.toMutableMap() ?: mutableMapOf()
                snapshot.messages.forEach { (id, newSnapshot) ->
                    val existingSnapshot = current?.messages?.get(id)
                    mergedMessages[id] = if (existingSnapshot == null) {
                        newSnapshot
                    } else {
                        existingSnapshot.mergeWith(newSnapshot)
                    }
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

    override suspend fun markWebSearchSourcesExtracted(
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
                            webSearchSources = snapshot.webSearchSources.map { source ->
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
        val entry = entryFor(key)
        entry.mutex.withLock {
            entry.flow.update { current ->
                val activeSnapshot = current ?: emptySnapshot(assistantMessageId)
                activeSnapshot.copy(
                    messages = activeSnapshot.messages.mapValues { (id, snapshot) ->
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

    override suspend fun attachMedia(
        key: ActiveChatTurnKey,
        assistantMessageId: com.browntowndev.pocketcrew.domain.model.chat.MessageId,
        mediaUris: List<String>,
        mediaIds: List<String>,
        mediaTypes: List<String>,
        prompts: List<String>,
    ) {
        val entry = entryFor(key)
        entry.mutex.withLock {
            entry.flow.update { current ->
                val activeSnapshot = current ?: emptySnapshot(assistantMessageId)
                activeSnapshot.copy(
                    messages = activeSnapshot.messages.mapValues { (id, snapshot) ->
                        if (id == assistantMessageId) {
                            snapshot.copy(
                                generatedMediaUris = (snapshot.generatedMediaUris + mediaUris).distinct(),
                                generatedMediaAssetIds = (snapshot.generatedMediaAssetIds + mediaIds).distinct(),
                                generatedMediaTypes = (snapshot.generatedMediaTypes + mediaTypes).distinct(),
                                generatedMediaPrompts = (snapshot.generatedMediaPrompts + prompts).distinct(),
                            )
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

    private fun emptySnapshot(
        assistantMessageId: com.browntowndev.pocketcrew.domain.model.chat.MessageId,
    ): AccumulatedMessages =
        AccumulatedMessages(
            messages = mapOf(
                assistantMessageId to MessageSnapshot(
                    messageId = assistantMessageId,
                    modelType = ModelType.FAST,
                    content = "",
                    thinkingRaw = "",
                ),
            ),
        )

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

private fun MessageSnapshot.mergeWith(incoming: MessageSnapshot): MessageSnapshot {
    val mergedArtifacts = artifacts.mergeArtifacts(incoming.artifacts)
    val mergedMediaUris = generatedMediaUris.mergeDistinct(incoming.generatedMediaUris)
    val mergedMediaAssetIds = generatedMediaAssetIds.mergeDistinct(incoming.generatedMediaAssetIds)
    val mergedMediaTypes = generatedMediaTypes.mergeDistinct(incoming.generatedMediaTypes)
    val mergedMediaPrompts = generatedMediaPrompts.mergeDistinct(incoming.generatedMediaPrompts)

    return when {
        incoming.matchesWith(
            other = this,
            artifacts = mergedArtifacts,
            generatedMediaUris = mergedMediaUris,
            generatedMediaAssetIds = mergedMediaAssetIds,
            generatedMediaTypes = mergedMediaTypes,
            generatedMediaPrompts = mergedMediaPrompts,
        ) -> this
        incoming.hasSameListReferences(
            artifacts = mergedArtifacts,
            generatedMediaUris = mergedMediaUris,
            generatedMediaAssetIds = mergedMediaAssetIds,
            generatedMediaTypes = mergedMediaTypes,
            generatedMediaPrompts = mergedMediaPrompts,
        ) -> incoming
        else -> incoming.copy(
            artifacts = mergedArtifacts,
            generatedMediaUris = mergedMediaUris,
            generatedMediaAssetIds = mergedMediaAssetIds,
            generatedMediaTypes = mergedMediaTypes,
            generatedMediaPrompts = mergedMediaPrompts,
        )
    }
}

private fun List<ArtifactGenerationRequest>.mergeArtifacts(
    incoming: List<ArtifactGenerationRequest>,
): List<ArtifactGenerationRequest> {
    if (incoming.isEmpty()) return this
    val merged = mergeArtifactGenerationRequests(this, incoming)
    return when {
        merged == this -> this
        merged == incoming -> incoming
        else -> merged
    }
}

private fun List<String>.mergeDistinct(incoming: List<String>): List<String> {
    if (incoming.isEmpty()) return this
    if (isEmpty()) {
        val distinctIncoming = incoming.distinct()
        return if (distinctIncoming == incoming) incoming else distinctIncoming
    }

    val additions = incoming.filterNot { value -> value in this }
    return if (additions.isEmpty()) this else this + additions
}

private fun MessageSnapshot.matchesWith(
    other: MessageSnapshot,
    artifacts: List<ArtifactGenerationRequest>,
    generatedMediaUris: List<String>,
    generatedMediaAssetIds: List<String>,
    generatedMediaTypes: List<String>,
    generatedMediaPrompts: List<String>,
): Boolean {
    return messageId == other.messageId &&
        modelType == other.modelType &&
        content == other.content &&
        thinkingRaw == other.thinkingRaw &&
        thinkingDurationSeconds == other.thinkingDurationSeconds &&
        thinkingStartTime == other.thinkingStartTime &&
        thinkingEndTime == other.thinkingEndTime &&
        isComplete == other.isComplete &&
        messageState == other.messageState &&
        pipelineStep == other.pipelineStep &&
        webSearchSources == other.webSearchSources &&
        artifacts == other.artifacts &&
        generatedMediaUris == other.generatedMediaUris &&
        generatedMediaAssetIds == other.generatedMediaAssetIds &&
        generatedMediaTypes == other.generatedMediaTypes &&
        generatedMediaPrompts == other.generatedMediaPrompts &&
        transcriptSegments == other.transcriptSegments
}

private fun MessageSnapshot.hasSameListReferences(
    artifacts: List<ArtifactGenerationRequest>,
    generatedMediaUris: List<String>,
    generatedMediaAssetIds: List<String>,
    generatedMediaTypes: List<String>,
    generatedMediaPrompts: List<String>,
): Boolean {
    return this.artifacts === artifacts &&
        this.generatedMediaUris === generatedMediaUris &&
        this.generatedMediaAssetIds === generatedMediaAssetIds &&
        this.generatedMediaTypes === generatedMediaTypes &&
        this.generatedMediaPrompts === generatedMediaPrompts
}
