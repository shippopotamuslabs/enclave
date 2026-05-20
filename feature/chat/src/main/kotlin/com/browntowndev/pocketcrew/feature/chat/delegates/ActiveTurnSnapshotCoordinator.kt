package com.browntowndev.pocketcrew.feature.chat.delegates

import com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.pocketcrew.domain.util.throttleLatest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns active-turn key resolution, live snapshot subscription, and snapshot bridge state.
 * - [retainedTurnKey]: scan-retained key kept until Room confirms COMPLETE after handoff.
 * - Observe key: used to subscribe to [ActiveChatTurnSnapshotPort]; requested turns are never
 *   suppressed by the acknowledged set.
 */
@OptIn(FlowPreview::class)
class ActiveTurnSnapshotCoordinator
    @Inject
    constructor(
        private val activeChatTurnSnapshotPort: ActiveChatTurnSnapshotPort,
    ) {
        private val _state =
            MutableStateFlow(
                ActiveTurnSnapshotState(
                    key = null,
                    snapshot = null,
                ),
            )
        val state: StateFlow<ActiveTurnSnapshotState> = _state.asStateFlow()

        private val _retainedTurnKey = MutableStateFlow<ActiveChatTurnKey?>(null)
        val retainedTurnKey: StateFlow<ActiveChatTurnKey?> = _retainedTurnKey.asStateFlow()

        private var observeKeyFlow: StateFlow<ActiveChatTurnKey?>? = null

        fun start(
            scope: CoroutineScope,
            chatIdFlow: Flow<ChatId?>,
            dbMessagesFlow: Flow<List<Message>>,
            requestedTurnKeyFlow: Flow<ActiveChatTurnKey?>,
            acknowledgedTurnKeysFlow: Flow<Set<ActiveChatTurnKey>>,
        ) {
            val turnCandidateFlow =
                combine(
                    chatIdFlow,
                    dbMessagesFlow,
                    requestedTurnKeyFlow,
                ) { chatId, messages, requestedTurnKey ->
                    RetainedTurnCandidate(
                        chatId = chatId,
                        messages = messages,
                        key = resolveTurnCandidateKey(chatId, messages, requestedTurnKey),
                    )
                }

            val retainedFlow =
                turnCandidateFlow
                    .combine(acknowledgedTurnKeysFlow) { candidate, acknowledgedKeys ->
                        candidate to acknowledgedKeys
                    }.scan(null as ActiveChatTurnKey?) { currentKey, (candidate, acknowledgedKeys) ->
                        advanceRetainedTurnKey(
                            currentKey = currentKey,
                            candidate = candidate,
                            acknowledgedKeys = acknowledgedKeys,
                        )
                    }.distinctUntilChanged()
                    .stateIn(scope, SharingStarted.Eagerly, null)

            retainedFlow
                .onEach { key -> _retainedTurnKey.value = key }
                .launchIn(scope)

            val observeKeys =
                combine(
                    dbMessagesFlow,
                    chatIdFlow,
                    requestedTurnKeyFlow,
                    acknowledgedTurnKeysFlow,
                    retainedFlow,
                ) { messages, chatId, requestedTurnKey, acknowledgedKeys, retainedKey ->
                    val requestedKey = requestedTurnKey?.takeIf { key -> key.chatId == chatId }
                    resolveActiveTurnSnapshotObserveKey(
                        requestedKey = requestedKey,
                        incompleteKey = incompleteAssistantTurnKey(chatId, messages),
                        acknowledgedKeys = acknowledgedKeys,
                        retainedKey = retainedKey,
                    )
                }.distinctUntilChanged()
                    .shareIn(scope, SharingStarted.Eagerly, replay = 1)

            observeKeyFlow = observeKeys.stateIn(scope, SharingStarted.Eagerly, null)

            scope.launch {
                observeKeys.collectLatest { key ->
                    if (key == null) {
                        _state.value =
                            ActiveTurnSnapshotState(
                                key = null,
                                snapshot = null,
                            )
                    } else {
                        _state.value =
                            ActiveTurnSnapshotState(
                                key = key,
                                snapshot = null,
                            )
                        activeChatTurnSnapshotPort
                            .observe(key)
                            .throttleLatest(16L)
                            .distinctUntilChanged()
                            .collect { snapshot ->
                                _state.value =
                                    ActiveTurnSnapshotState(
                                        key = key,
                                        snapshot = snapshot,
                                    )
                            }
                    }
                }
            }
        }

        fun currentObserveKey(): ActiveChatTurnKey? = observeKeyFlow?.value

        suspend fun clearTurn(key: ActiveChatTurnKey) {
            activeChatTurnSnapshotPort.clear(key)
        }

        suspend fun markWebSearchSourcesExtracted(
            key: ActiveChatTurnKey,
            urls: List<String>,
        ) {
            activeChatTurnSnapshotPort.markWebSearchSourcesExtracted(key, urls)
        }
    }

data class ActiveTurnSnapshotState(
    val key: ActiveChatTurnKey?,
    val snapshot: AccumulatedMessages?,
)

internal data class RetainedTurnCandidate(
    val chatId: ChatId?,
    val messages: List<Message>,
    val key: ActiveChatTurnKey?,
)

internal fun resolveTurnCandidateKey(
    chatId: ChatId?,
    messages: List<Message>,
    requestedTurnKey: ActiveChatTurnKey?,
): ActiveChatTurnKey? {
    val requestedKey = requestedTurnKey?.takeIf { key -> key.chatId == chatId }
    return requestedKey ?: incompleteAssistantTurnKey(chatId, messages)
}

internal fun incompleteAssistantTurnKey(
    chatId: ChatId?,
    messages: List<Message>,
): ActiveChatTurnKey? =
    chatId?.let { id ->
        messages
            .lastOrNull { message ->
                message.role == Role.ASSISTANT && message.messageState != MessageState.COMPLETE
            }?.let { message -> ActiveChatTurnKey(id, message.id) }
    }

internal fun advanceRetainedTurnKey(
    currentKey: ActiveChatTurnKey?,
    candidate: RetainedTurnCandidate,
    acknowledgedKeys: Set<ActiveChatTurnKey>,
): ActiveChatTurnKey? {
    val candidateKey = candidate.key?.takeUnless { key -> key in acknowledgedKeys }
    return when {
        currentKey != null && currentKey in acknowledgedKeys -> null
        candidateKey != null -> candidateKey
        currentKey != null &&
            candidate.chatId == currentKey.chatId &&
            candidate.messages.any { message ->
                message.id == currentKey.assistantMessageId &&
                    message.messageState == MessageState.COMPLETE
            } -> currentKey
        else -> null
    }
}

internal fun resolveActiveTurnSnapshotObserveKey(
    requestedKey: ActiveChatTurnKey?,
    incompleteKey: ActiveChatTurnKey?,
    acknowledgedKeys: Set<ActiveChatTurnKey>,
    retainedKey: ActiveChatTurnKey?,
): ActiveChatTurnKey? {
    val candidateKey =
        requestedKey ?: incompleteKey?.takeUnless { key -> key in acknowledgedKeys }
    val retainedSnapshotKey = retainedKey?.takeUnless { key -> key in acknowledgedKeys }
    return candidateKey ?: retainedSnapshotKey
}

internal fun maybeAcknowledgeCompletedTurn(
    key: ActiveChatTurnKey,
    dbMessages: List<Message>,
    activeSnapshot: AccumulatedMessages?,
    acknowledgedTurnKeys: Set<ActiveChatTurnKey>,
    onAcknowledge: (ActiveChatTurnKey) -> Unit,
) {
    if (key in acknowledgedTurnKeys) return
    val dbMessage = dbMessages.find { message -> message.id == key.assistantMessageId }
    val snapshotMessage = activeSnapshot?.messages?.get(key.assistantMessageId)
    if (dbMessage?.messageState == MessageState.COMPLETE &&
        snapshotMessage?.messageState == MessageState.COMPLETE
    ) {
        onAcknowledge(key)
    }
}
