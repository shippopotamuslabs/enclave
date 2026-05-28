package com.browntowndev.enclave.feature.chat.delegates

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
import com.browntowndev.enclave.feature.chat.IndicatorState
import com.browntowndev.enclave.feature.chat.TranscriptSegmentUi
import com.browntowndev.enclave.feature.chat.mapping.ChatMessageUiMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatUiStateReducerLiveSnapshotTest {
    private val chatId = ChatId("chat")
    private val assistantId = MessageId("assistant")
    private val turnKey = ActiveChatTurnKey(chatId, assistantId)
    private val reducer = ChatUiStateReducer(ChatMessageUiMapper())

    @Test
    fun `reduce shows thinking transcript when live snapshot arrives after processing db row`() {
        val dbMessages =
            listOf(
                Message(
                    id = assistantId,
                    chatId = chatId,
                    role = Role.ASSISTANT,
                    content = Content(text = ""),
                    messageState = MessageState.PROCESSING,
                    createdAt = 1L,
                ),
            )
        val liveSnapshot =
            AccumulatedMessages(
                messages =
                    mapOf(
                        assistantId to
                            MessageSnapshot(
                                messageId = assistantId,
                                modelType = ModelType.THINKING,
                                content = "",
                                thinkingRaw = "planning",
                                messageState = MessageState.THINKING,
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

        val initial =
            reduce(
                dbMessages = dbMessages,
                activeTurnSnapshot = null,
                activeTurnKey = turnKey,
            )
        val initialAssistant = initial.uiState.messages.single()
        assertTrue(initialAssistant.indicatorState is IndicatorState.Processing)

        val streaming =
            reduce(
                dbMessages = dbMessages,
                activeTurnSnapshot = liveSnapshot,
                activeTurnKey = turnKey,
            )
        val streamingAssistant = streaming.uiState.messages.single()
        assertTrue(streamingAssistant.indicatorState is IndicatorState.Thinking)
        assertEquals(1, streamingAssistant.transcriptSegments.size)
        assertTrue(streamingAssistant.transcriptSegments.single() is TranscriptSegmentUi.ThinkingBlock)
    }

    @Test
    fun `projector cache does not pin processing db row after live snapshot updates`() {
        val dbMessages =
            listOf(
                Message(
                    id = assistantId,
                    chatId = chatId,
                    role = Role.ASSISTANT,
                    content = Content(text = ""),
                    messageState = MessageState.PROCESSING,
                    createdAt = 1L,
                ),
            )
        val firstSnapshot = thinkingSnapshot(text = "hello")
        val secondSnapshot = thinkingSnapshot(text = "hello world")

        val first =
            reduce(
                dbMessages = dbMessages,
                activeTurnSnapshot = firstSnapshot,
                activeTurnKey = turnKey,
            )
        val second =
            reduce(
                dbMessages = dbMessages,
                activeTurnSnapshot = secondSnapshot,
                activeTurnKey = turnKey,
            )

        val firstBlock =
            first.uiState.messages
                .single()
                .transcriptSegments
                .single() as TranscriptSegmentUi.ThinkingBlock
        val secondBlock =
            second.uiState.messages
                .single()
                .transcriptSegments
                .single() as TranscriptSegmentUi.ThinkingBlock
        assertEquals("hello", firstBlock.text)
        assertEquals("hello world", secondBlock.text)
        assertTrue(
            first.uiState.messages
                .single()
                .indicatorState is IndicatorState.Thinking,
        )
        assertTrue(
            second.uiState.messages
                .single()
                .indicatorState is IndicatorState.Thinking,
        )
    }

    @Test
    fun `projector memo does not suppress live snapshot after null snapshot warm-up`() {
        val dbMessages =
            listOf(
                Message(
                    id = assistantId,
                    chatId = chatId,
                    role = Role.ASSISTANT,
                    content = Content(text = ""),
                    messageState = MessageState.PROCESSING,
                    createdAt = 1L,
                ),
            )
        val liveSnapshot = thinkingSnapshot(text = "streaming")

        reduce(dbMessages = dbMessages, activeTurnSnapshot = null, activeTurnKey = turnKey)
        val streaming =
            reduce(
                dbMessages = dbMessages,
                activeTurnSnapshot = liveSnapshot,
                activeTurnKey = turnKey,
            )

        assertTrue(
            streaming.uiState.messages
                .single()
                .indicatorState is IndicatorState.Thinking,
        )
    }

    @Test
    fun `reduce reuses stable chat message references when snapshot is unchanged`() {
        val dbMessages =
            listOf(
                Message(
                    id = assistantId,
                    chatId = chatId,
                    role = Role.ASSISTANT,
                    content = Content(text = ""),
                    messageState = MessageState.PROCESSING,
                    createdAt = 1L,
                ),
            )
        val liveSnapshot = thinkingSnapshot(text = "stable")

        val first =
            reduce(
                dbMessages = dbMessages,
                activeTurnSnapshot = liveSnapshot,
                activeTurnKey = turnKey,
            )
        val second =
            reduce(
                dbMessages = dbMessages,
                activeTurnSnapshot = liveSnapshot,
                activeTurnKey = turnKey,
            )

        assertSame(first.uiState.messages.single(), second.uiState.messages.single())
    }

    private fun thinkingSnapshot(text: String): AccumulatedMessages =
        AccumulatedMessages(
            messages =
                mapOf(
                    assistantId to
                        MessageSnapshot(
                            messageId = assistantId,
                            modelType = ModelType.THINKING,
                            content = "",
                            thinkingRaw = text,
                            messageState = MessageState.THINKING,
                            transcriptSegments =
                                listOf(
                                    MessageTranscriptSegment.Thinking(
                                        ordinal = 0,
                                        text = text,
                                        thinkingStartTime = 1L,
                                    ),
                                ),
                        ),
                ),
        )

    private fun reduce(
        dbMessages: List<Message>,
        activeTurnSnapshot: AccumulatedMessages?,
        activeTurnKey: ActiveChatTurnKey?,
    ) = reducer.reduce(
        ChatUiStateReducer.ReduceParams(
            settings = SettingsData(),
            inputText = "",
            selectedMode = com.browntowndev.enclave.feature.chat.ChatModeUi.FAST,
            selectedImageUris = emptyList(),
            selectedFileName = null,
            speechState = SpeechState.Idle,
            attachmentPolicy =
                com.browntowndev.enclave.feature.chat.mapping.PhotoAttachmentPolicy(
                    isEnabled = false,
                    maxItems = 0,
                    disabledReason = null,
                ),
            isGlobalInferenceBlocked = false,
            dbMessages = dbMessages,
            activeTurnKey = activeTurnKey,
            activeTurnSnapshot = activeTurnSnapshot,
            activeToolCallBanner = null,
            hasTtsProviderAssigned = false,
            isPlayingTts = false,
            studioMediaMap = emptyMap(),
            shareState = com.browntowndev.enclave.feature.chat.ShareState.Idle,
            isReadOnly = false,
            sharedMessages = emptyList(),
            artifactExportSheet = com.browntowndev.enclave.feature.chat.ArtifactExportSheetState.Hidden,
            chatId = chatId,
            modelDisplayNames = emptyMap(),
        ),
    )
}
