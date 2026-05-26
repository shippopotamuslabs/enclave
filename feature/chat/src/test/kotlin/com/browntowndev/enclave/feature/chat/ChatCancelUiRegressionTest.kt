package com.browntowndev.enclave.feature.chat

import com.browntowndev.enclave.domain.model.chat.AccumulatedMessages
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageSnapshot
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.feature.chat.delegates.ChatUiStateReducer
import com.browntowndev.enclave.feature.chat.delegates.maybeAcknowledgeCompletedTurn
import com.browntowndev.enclave.feature.chat.mapping.ChatMessageUiMapper
import com.browntowndev.enclave.feature.chat.mapping.PhotoAttachmentPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression: user stop during thinking must exit generating UI and show completed actions
 * once the service publishes a terminal COMPLETE snapshot (even if Room still lags).
 */
class ChatCancelUiRegressionTest {
    private val chatId = ChatId("chat")
    private val assistantId = MessageId("assistant")
    private val turnKey = ActiveChatTurnKey(chatId, assistantId)
    private val reducer = ChatUiStateReducer(ChatMessageUiMapper())

    @Test
    fun `cancelled terminal snapshot clears generating before room catches up`() {
        var acknowledgedTurnKeys = emptySet<ActiveChatTurnKey>()
        var dbMessages =
            listOf(
                assistantMessage(
                    content = "",
                    state = MessageState.THINKING,
                    thinkingRaw = "planning",
                ),
            )
        var activeSnapshot =
            AccumulatedMessages(
                messages =
                    mapOf(
                        assistantId to
                            messageSnapshot(
                                content = "",
                                state = MessageState.THINKING,
                                thinkingRaw = "planning",
                            ),
                    ),
            )

        val thinking =
            reduce(
                dbMessages = dbMessages,
                activeSnapshot = activeSnapshot,
                activeTurnKey = turnKey,
            ).uiState
        assertTrue(thinking.isGenerating)
        assertTrue(thinking.messages.single().indicatorState is IndicatorState.Thinking)

        activeSnapshot =
            AccumulatedMessages(
                messages =
                    mapOf(
                        assistantId to
                            messageSnapshot(
                                content = "partial",
                                state = MessageState.COMPLETE,
                                thinkingRaw = "planning",
                            ),
                    ),
            )

        val cancelledBeforeRoom =
            reduce(
                dbMessages = dbMessages,
                activeSnapshot = activeSnapshot,
                activeTurnKey = turnKey,
            ).uiState
        assertFalse(cancelledBeforeRoom.isGenerating)
        assertTrue(cancelledBeforeRoom.messages.single().indicatorState is IndicatorState.Complete)

        dbMessages =
            listOf(
                assistantMessage(
                    content = "partial",
                    state = MessageState.COMPLETE,
                    thinkingRaw = "planning",
                ),
            )
        val result =
            reduce(
                dbMessages = dbMessages,
                activeSnapshot = activeSnapshot,
                activeTurnKey = turnKey,
            )
        maybeAcknowledgeCompletedTurn(
            key = turnKey,
            dbMessages = dbMessages,
            activeSnapshot = activeSnapshot,
            acknowledgedTurnKeys = acknowledgedTurnKeys,
            onAcknowledge = { acknowledgedTurnKeys = acknowledgedTurnKeys + it },
        )
        assertEquals(turnKey, result.acknowledgedTurnKey)
        assertFalse(result.uiState.isGenerating)
    }

    private fun reduce(
        dbMessages: List<Message>,
        activeSnapshot: AccumulatedMessages?,
        activeTurnKey: ActiveChatTurnKey?,
    ) = reducer.reduce(
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
            activeTurnSnapshot = activeSnapshot,
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
        content: String,
        state: MessageState,
        thinkingRaw: String = "",
    ): Message =
        Message(
            id = assistantId,
            chatId = chatId,
            role = Role.ASSISTANT,
            content = Content(text = content),
            messageState = state,
            thinkingRaw = thinkingRaw.ifBlank { null },
            createdAt = 1L,
            updatedAt = 100L,
        )

    private fun messageSnapshot(
        content: String,
        state: MessageState,
        thinkingRaw: String = "",
    ): MessageSnapshot =
        MessageSnapshot(
            messageId = assistantId,
            modelType = ModelType.THINKING,
            content = content,
            thinkingRaw = thinkingRaw,
            messageState = state,
            isComplete = state == MessageState.COMPLETE,
        )
}
