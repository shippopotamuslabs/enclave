package com.browntowndev.enclave.feature.chat

import com.browntowndev.enclave.domain.model.chat.AccumulatedMessages
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageSnapshot
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.MessageTranscriptSegment
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.feature.chat.delegates.ChatUiStateReducer
import com.browntowndev.enclave.feature.chat.delegates.maybeAcknowledgeCompletedTurn
import com.browntowndev.enclave.feature.chat.delegates.resolveActiveTurnSnapshotObserveKey
import com.browntowndev.enclave.feature.chat.mapping.ChatMessageUiMapper
import com.browntowndev.enclave.feature.chat.mapping.PhotoAttachmentPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression harness for the second-turn streaming bug:
 * premature acknowledgeTurn at send → observe key null → UI stuck on PROCESSING.
 */
class ChatStreamingAckRegressionTest {
    private val chatId = ChatId("chat")
    private val previousAssistantId = MessageId("assistant-prev")
    private val newAssistantId = MessageId("assistant-new")
    private val newTurnKey = ActiveChatTurnKey(chatId, newAssistantId)
    private val reducer = ChatUiStateReducer(ChatMessageUiMapper())

    @Test
    fun `second turn send keeps observe key alive and streams engine loading and thinking`() {
        var requestedTurnKey: ActiveChatTurnKey? = null
        var acknowledgedTurnKeys = emptySet<ActiveChatTurnKey>()
        var activeTurnKey: ActiveChatTurnKey? = null
        var activeTurnSnapshot: AccumulatedMessages? = null
        var dbMessages = emptyList<Message>()

        fun incompleteKey(): ActiveChatTurnKey? =
            dbMessages
                .lastOrNull { message ->
                    message.role == Role.ASSISTANT && message.messageState != MessageState.COMPLETE
                }?.let { message -> ActiveChatTurnKey(chatId, message.id) }

        fun observeKey(): ActiveChatTurnKey? =
            resolveActiveTurnSnapshotObserveKey(
                requestedKey = requestedTurnKey,
                incompleteKey = incompleteKey(),
                acknowledgedKeys = acknowledgedTurnKeys,
                retainedKey = activeTurnKey,
            )

        fun reduceAndMaybeAck(): ChatUiStateReducer.ReduceResult {
            val result =
                reduce(
                    dbMessages = dbMessages,
                    activeTurnSnapshot = activeTurnSnapshot,
                    activeTurnKey = activeTurnKey,
                )
            result.acknowledgedTurnKey?.let { key ->
                maybeAcknowledgeCompletedTurn(
                    key = key,
                    dbMessages = dbMessages,
                    activeSnapshot = activeTurnSnapshot,
                    acknowledgedTurnKeys = acknowledgedTurnKeys,
                    onAcknowledge = { acknowledgedKey ->
                        acknowledgedTurnKeys = acknowledgedTurnKeys + acknowledgedKey
                        if (requestedTurnKey == acknowledgedKey) {
                            requestedTurnKey = null
                        }
                    },
                )
            }
            return result
        }

        fun assistantIndicator(messageId: MessageId = newAssistantId): IndicatorState? =
            reduceAndMaybeAck()
                .uiState.messages
                .last { message -> message.id == messageId }
                .indicatorState

        // sendMessage: un-ack and request the new turn before Room has caught up.
        acknowledgedTurnKeys = acknowledgedTurnKeys - newTurnKey
        requestedTurnKey = newTurnKey
        activeTurnKey = observeKey()
        activeTurnSnapshot = null

        assertEquals(newTurnKey, activeTurnKey)
        assertNull(reduceAndMaybeAck().acknowledgedTurnKey)
        assertTrue(acknowledgedTurnKeys.isEmpty())

        // Room catches up with previous COMPLETE turn + new PROCESSING placeholder.
        dbMessages =
            listOf(
                assistantMessage(
                    id = previousAssistantId,
                    content = "previous answer",
                    state = MessageState.COMPLETE,
                    createdAt = 1L,
                ),
                assistantMessage(
                    id = newAssistantId,
                    content = "",
                    state = MessageState.PROCESSING,
                    createdAt = 2L,
                ),
            )
        activeTurnKey = observeKey()

        assertEquals(newTurnKey, activeTurnKey)
        assertNull(reduceAndMaybeAck().acknowledgedTurnKey)
        assertTrue(acknowledgedTurnKeys.isEmpty())
        assertTrue(assistantIndicator() is IndicatorState.Processing)

        // Stale previous-turn snapshot must not trigger early ack for the new turn.
        activeTurnSnapshot =
            AccumulatedMessages(
                messages =
                    mapOf(
                        previousAssistantId to
                            messageSnapshot(
                                messageId = previousAssistantId,
                                content = "previous answer",
                                state = MessageState.COMPLETE,
                            ),
                    ),
            )
        assertNull(reduceAndMaybeAck().acknowledgedTurnKey)
        assertTrue(acknowledgedTurnKeys.isEmpty())
        assertEquals(newTurnKey, observeKey())

        // FGS publishes ENGINE_LOADING — must reach UI while observe key stays live.
        activeTurnSnapshot =
            AccumulatedMessages(
                messages =
                    mapOf(
                        newAssistantId to
                            messageSnapshot(
                                messageId = newAssistantId,
                                content = "",
                                state = MessageState.ENGINE_LOADING,
                            ),
                    ),
            )
        assertEquals(newTurnKey, observeKey())
        assertTrue(assistantIndicator() is IndicatorState.EngineLoading)

        // FGS publishes THINKING — must stream through reducer/mapper.
        activeTurnSnapshot =
            AccumulatedMessages(
                messages =
                    mapOf(
                        newAssistantId to
                            messageSnapshot(
                                messageId = newAssistantId,
                                content = "",
                                state = MessageState.THINKING,
                                thinkingRaw = "planning",
                                transcriptSegments =
                                    listOf(
                                        MessageTranscriptSegment.Thinking(
                                            ordinal = 0,
                                            text = "planning",
                                            thinkingStartTime = 1L,
                                        ),
                                    ),
                            ),
                    ),
            )
        assertEquals(newTurnKey, observeKey())
        assertTrue(assistantIndicator() is IndicatorState.Thinking)

        // Legitimate handoff only after both DB and snapshot are COMPLETE.
        dbMessages =
            listOf(
                assistantMessage(
                    id = previousAssistantId,
                    content = "previous answer",
                    state = MessageState.COMPLETE,
                    createdAt = 1L,
                ),
                assistantMessage(
                    id = newAssistantId,
                    content = "final answer",
                    state = MessageState.COMPLETE,
                    createdAt = 2L,
                ),
            )
        activeTurnSnapshot =
            AccumulatedMessages(
                messages =
                    mapOf(
                        newAssistantId to
                            messageSnapshot(
                                messageId = newAssistantId,
                                content = "final answer",
                                state = MessageState.COMPLETE,
                            ),
                    ),
            )

        val completed = reduceAndMaybeAck()
        assertEquals(newTurnKey, completed.acknowledgedTurnKey)
        assertEquals(setOf(newTurnKey), acknowledgedTurnKeys)
        assertNull(requestedTurnKey)
    }

    private fun reduce(
        dbMessages: List<Message>,
        activeTurnSnapshot: AccumulatedMessages?,
        activeTurnKey: ActiveChatTurnKey?,
    ): ChatUiStateReducer.ReduceResult =
        reducer.reduce(
            ChatUiStateReducer.ReduceParams(
                settings = SettingsData(),
                inputText = "",
                selectedMode = ChatModeUi.FAST,
                selectedImageUris = emptyList(),
                selectedFileName = null,
                speechState = SpeechState.Idle,
                attachmentPolicy = PhotoAttachmentPolicy(isEnabled = false, maxItems = 0, disabledReason = null),
                isGlobalInferenceBlocked = false,
                dbMessages = dbMessages,
                activeTurnKey = activeTurnKey,
                activeTurnSnapshot = activeTurnSnapshot,
                activeToolCallBanner = null,
                hasTtsProviderAssigned = false,
                isPlayingTts = false,
                studioMediaMap = emptyMap(),
                shareState = ShareState.Idle,
                isReadOnly = false,
                sharedMessages = emptyList(),
                artifactExportSheet = ArtifactExportSheetState.Hidden,
                chatId = chatId,
                modelDisplayNames = emptyMap(),
            ),
        )

    private fun assistantMessage(
        id: MessageId,
        content: String,
        state: MessageState,
        createdAt: Long,
    ): Message =
        Message(
            id = id,
            chatId = chatId,
            role = Role.ASSISTANT,
            content = Content(text = content),
            messageState = state,
            createdAt = createdAt,
        )

    private fun messageSnapshot(
        messageId: MessageId,
        content: String,
        state: MessageState,
        thinkingRaw: String = "",
        transcriptSegments: List<MessageTranscriptSegment> = emptyList(),
    ): MessageSnapshot =
        MessageSnapshot(
            messageId = messageId,
            modelType = ModelType.THINKING,
            content = content,
            thinkingRaw = thinkingRaw,
            messageState = state,
            isComplete = state == MessageState.COMPLETE,
            transcriptSegments = transcriptSegments,
        )
}
