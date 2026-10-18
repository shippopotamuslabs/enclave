package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.enclave.domain.model.chat.AccumulatedMessages
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnSnapshotPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [ActiveChatTurnSnapshotPort] fake (ENC-28) recording every publish in order. */
class FakeActiveChatTurnSnapshotPort : ActiveChatTurnSnapshotPort {
    private val snapshots = MutableStateFlow<Map<ActiveChatTurnKey, AccumulatedMessages>>(emptyMap())

    val publishes = mutableListOf<Pair<ActiveChatTurnKey, AccumulatedMessages>>()
    val clearedKeys = mutableListOf<ActiveChatTurnKey>()

    /** When set, [observe] returns this flow instead of the StateFlow-backed snapshots (ENC-28). */
    var observeFlowOverride: Flow<AccumulatedMessages?>? = null

    override fun observe(key: ActiveChatTurnKey): Flow<AccumulatedMessages?> =
        observeFlowOverride ?: snapshots.map { it[key] }

    override suspend fun publish(key: ActiveChatTurnKey, snapshot: AccumulatedMessages) {
        publishes += key to snapshot
        snapshots.value = snapshots.value + (key to snapshot)
    }

    override suspend fun markWebSearchSourcesExtracted(key: ActiveChatTurnKey, urls: List<String>) = Unit

    override suspend fun attachArtifact(
        key: ActiveChatTurnKey,
        assistantMessageId: MessageId,
        artifact: ArtifactGenerationRequest,
    ) = Unit

    override suspend fun attachMedia(
        key: ActiveChatTurnKey,
        assistantMessageId: MessageId,
        mediaUris: List<String>,
        mediaIds: List<String>,
        mediaTypes: List<String>,
        prompts: List<String>,
    ) = Unit

    override suspend fun acknowledgeHandoff(key: ActiveChatTurnKey) = Unit

    override suspend fun clear(key: ActiveChatTurnKey) {
        clearedKeys += key
        snapshots.value = snapshots.value - key
    }

    override suspend fun getSnapshot(key: ActiveChatTurnKey): AccumulatedMessages? = snapshots.value[key]
}
