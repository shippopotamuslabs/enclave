package com.browntowndev.pocketcrew.feature.chat

import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageSnapshot
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.MessageTranscriptSegment
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.pocketcrew.domain.port.media.SpeechState
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.feature.chat.delegates.ChatUiStateReducer
import com.browntowndev.pocketcrew.feature.chat.mapping.ChatMessageUiMapper
import com.browntowndev.pocketcrew.feature.chat.mapping.PhotoAttachmentPolicy
import com.browntowndev.pocketcrew.feature.inference.ActiveChatTurnStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelLiveSnapshotIntegrationTest {
    private val chatId = ChatId("live-snapshot-chat")
    private val assistantId = MessageId("assistant-live")
    private val turnKey = ActiveChatTurnKey(chatId, assistantId)

    private fun processingAssistant() =
        Message(
            id = assistantId,
            chatId = chatId,
            role = Role.ASSISTANT,
            content = Content(text = ""),
            messageState = MessageState.PROCESSING,
            createdAt = 1L,
        )

    private fun thinkingSnapshot(text: String = "planning") =
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

    @Test
    fun `active chat turn store replays latest snapshot to new collectors`() =
        runTest {
            val store = ActiveChatTurnStore()
            store.publish(turnKey, thinkingSnapshot())

            val collected = mutableListOf<AccumulatedMessages?>()
            val job = launch { store.observe(turnKey).collect { collected.add(it) } }
            advanceUntilIdle()

            assertEquals(1, collected.size)
            assertEquals(MessageState.THINKING, collected.single()?.messages?.get(assistantId)?.messageState)

            job.cancel()
        }

    @Test
    fun `flatMapLatest observe wiring projects thinking snapshot`() =
        runTest {
            val store = ActiveChatTurnStore()
            store.publish(turnKey, thinkingSnapshot())
            val reducer = ChatUiStateReducer(ChatMessageUiMapper())

            val dbMessages = MutableStateFlow(listOf(processingAssistant()))
            val observeKeyFlow =
                dbMessages.map { messages ->
                    messages
                        .lastOrNull { message ->
                            message.role == Role.ASSISTANT && message.messageState != MessageState.COMPLETE
                        }?.let { message -> ActiveChatTurnKey(chatId, message.id) }
                }.distinctUntilChanged()

            val indicators = mutableListOf<IndicatorState?>()
            val job =
                launch {
                    observeKeyFlow
                        .flatMapLatest { key ->
                            val snapshots =
                                if (key == null) {
                                    flowOf(null)
                                } else {
                                    store.observe(key)
                                }
                            combine(dbMessages, snapshots) { messages, snapshot ->
                                reducer
                                    .reduce(
                                        ChatUiStateReducer.ReduceParams(
                                            settings = SettingsData(),
                                            inputText = "",
                                            selectedMode = ChatModeUi.FAST,
                                            selectedImageUris = emptyList(),
                                            selectedFileName = null,
                                            speechState = SpeechState.Idle,
                                            attachmentPolicy = PhotoAttachmentPolicy(false, 0, null),
                                            isGlobalInferenceBlocked = false,
                                            dbMessages = messages,
                                            activeTurnKey = key,
                                            activeTurnSnapshot = snapshot,
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
                                    ).uiState.messages.single()
                                    .indicatorState
                            }
                        }.collect { indicators.add(it) }
                }

            advanceUntilIdle()
            assertTrue(indicators.last() is IndicatorState.Thinking, "history=$indicators")
            job.cancel()
        }

    @Test
    fun `combine reduce path projects thinking snapshot over processing db row`() =
        runTest {
            val reducer = ChatUiStateReducer(ChatMessageUiMapper())
            val dbMessages = MutableStateFlow(listOf(processingAssistant()))
            val snapshots = MutableStateFlow<AccumulatedMessages?>(null)

            val indicators = mutableListOf<IndicatorState?>()
            val job =
                launch {
                    combine(dbMessages, snapshots) { messages, snapshot ->
                        reducer
                            .reduce(
                                ChatUiStateReducer.ReduceParams(
                                    settings = SettingsData(),
                                    inputText = "",
                                    selectedMode = ChatModeUi.FAST,
                                    selectedImageUris = emptyList(),
                                    selectedFileName = null,
                                    speechState = SpeechState.Idle,
                                    attachmentPolicy = PhotoAttachmentPolicy(false, 0, null),
                                    isGlobalInferenceBlocked = false,
                                    dbMessages = messages,
                                    activeTurnKey = turnKey,
                                    activeTurnSnapshot = snapshot,
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
                            ).uiState.messages.single()
                            .indicatorState
                    }.collect { indicators.add(it) }
                }

            advanceUntilIdle()
            snapshots.value = thinkingSnapshot()
            advanceUntilIdle()

            assertTrue(indicators.last() is IndicatorState.Thinking, "history=$indicators")
            job.cancel()
        }
}
