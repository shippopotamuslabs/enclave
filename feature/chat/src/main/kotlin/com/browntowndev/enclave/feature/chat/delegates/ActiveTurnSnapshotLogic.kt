@file:Suppress("MatchingDeclarationName")

package com.browntowndev.enclave.feature.chat.delegates

import com.browntowndev.enclave.domain.model.chat.AccumulatedMessages
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnKey

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
