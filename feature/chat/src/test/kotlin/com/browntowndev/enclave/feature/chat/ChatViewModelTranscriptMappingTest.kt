package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.testing.FakeActiveChatTurnSnapshotPort
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.core.testing.FakeTtsPlaybackController
import com.browntowndev.enclave.core.testing.FakeViewModelErrorHandler
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.MessageTranscriptSegment
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ModelType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
@DisplayName("ChatViewModel transcript / thinking mapping")
class ChatViewModelTranscriptMappingTest {
    private lateinit var viewModel: ChatViewModel
    private val settingsUseCases: FakeChatSettingsUseCases = FakeChatSettingsUseCases()
    private val chatUseCases: FakeChatUseCases = FakeChatUseCases()
    private val shareChatUseCase: FakeShareChatUseCase = FakeShareChatUseCase()

    @BeforeEach
    fun setup() {
        viewModel =
            ChatViewModelTestSupport.createViewModel(
                settingsUseCases = settingsUseCases,
                chatUseCases = chatUseCases,
                savedStateHandle = SavedStateHandle(),
                cancelInferenceUseCase = FakeCancelInferenceUseCase(),
                inferenceLockManager = FakeInferenceLockManager(),
                modelDisplayNamesUseCase = FakeGetModelDisplayNameUseCase(),
                activeModelProvider = FakeActiveModelProvider(),
                toolExecutionEventPort = FakeToolExecutionEventPort(),
                errorHandler = FakeViewModelErrorHandler(),
                activeChatTurnSnapshotPort = FakeActiveChatTurnSnapshotPort(),
                playbackController = FakeTtsPlaybackController(),
                studioRepository = FakeStudioRepository(),
                shareChatUseCase = shareChatUseCase,
                chatRepository = FakeChatRepository(),
                exportArtifactDocumentsUseCase = FakeExportArtifactDocumentsUseCase(),
                stageImageAttachmentUseCase = FakeStageImageAttachmentUseCase(),
            )
    }

    @Test
    fun `mapToChatMessage derives segment thinking duration from timestamps when seconds null`() {
        val tStart = 1_000L
        val tEnd = 24_000L
        val message =
            assistantMessage(
                messageState = MessageState.COMPLETE,
                transcriptSegments =
                    listOf(
                        MessageTranscriptSegment.Thinking(
                            ordinal = 0,
                            text = "chunk a",
                            thinkingDurationSeconds = null,
                            thinkingStartTime = tStart,
                            thinkingEndTime = tEnd,
                        ),
                        MessageTranscriptSegment.AssistantText(ordinal = 1, text = "answer"),
                    ),
            )

        val ui = viewModel.mapToChatMessageForTesting(message)
        val thinking = ui.transcriptSegments.first() as TranscriptSegmentUi.ThinkingBlock
        assertEquals(23L, thinking.durationSeconds)
    }

    @Test
    fun `mapToChatMessage prefers explicit segment thinkingDurationSeconds over timestamps`() {
        val message =
            assistantMessage(
                messageState = MessageState.COMPLETE,
                transcriptSegments =
                    listOf(
                        MessageTranscriptSegment.Thinking(
                            ordinal = 0,
                            text = "x",
                            thinkingDurationSeconds = 99L,
                            thinkingStartTime = 0L,
                            thinkingEndTime = 5_000L,
                        ),
                    ),
            )

        val ui = viewModel.mapToChatMessageForTesting(message)
        val thinking = ui.transcriptSegments.first() as TranscriptSegmentUi.ThinkingBlock
        assertEquals(99L, thinking.durationSeconds)
    }

    @Test
    fun `mapToChatMessage derives segment duration from timestamps when stored seconds are zero`() {
        val message =
            assistantMessage(
                messageState = MessageState.COMPLETE,
                transcriptSegments =
                    listOf(
                        MessageTranscriptSegment.Thinking(
                            ordinal = 0,
                            text = "cot",
                            thinkingDurationSeconds = 0L,
                            thinkingStartTime = 10_000L,
                            thinkingEndTime = 45_000L,
                        ),
                        MessageTranscriptSegment.AssistantText(ordinal = 1, text = "answer"),
                    ),
            )

        val ui = viewModel.mapToChatMessageForTesting(message)
        val thinking = ui.transcriptSegments.first() as TranscriptSegmentUi.ThinkingBlock
        assertEquals(35L, thinking.durationSeconds)
    }

    @Test
    fun `mapToChatMessage splits message aggregate across thinking segments when all slice durations missing`() {
        val message =
            assistantMessage(
                messageState = MessageState.COMPLETE,
                thinkingRaw = "trace",
                thinkingDurationSeconds = 10L,
                thinkingStartTime = null,
                thinkingEndTime = null,
                transcriptSegments =
                    listOf(
                        MessageTranscriptSegment.Thinking(ordinal = 0, text = "t0"),
                        MessageTranscriptSegment.AssistantText(ordinal = 1, text = "a1"),
                        MessageTranscriptSegment.Thinking(ordinal = 2, text = "t2"),
                        MessageTranscriptSegment.Thinking(ordinal = 3, text = "t3"),
                    ),
            )

        val ui = viewModel.mapToChatMessageForTesting(message)
        val durations =
            ui.transcriptSegments.filterIsInstance<TranscriptSegmentUi.ThinkingBlock>().map { it.durationSeconds }
        assertEquals(listOf(4L, 3L, 3L), durations)
    }

    @Test
    fun `mapToChatMessage marks last thinking segment streaming when message is THINKING`() {
        val message =
            assistantMessage(
                messageState = MessageState.THINKING,
                thinkingRaw = "live",
                transcriptSegments =
                    listOf(
                        MessageTranscriptSegment.Thinking(ordinal = 0, text = "a"),
                        MessageTranscriptSegment.AssistantText(ordinal = 1, text = "mid"),
                        MessageTranscriptSegment.Thinking(ordinal = 2, text = "b"),
                    ),
            )

        val ui = viewModel.mapToChatMessageForTesting(message)
        val blocks = ui.transcriptSegments.filterIsInstance<TranscriptSegmentUi.ThinkingBlock>()
        assertEquals(2, blocks.size)
        assertTrue(blocks.none { it.ordinal == 0 && it.isStreaming })
        assertTrue(blocks.any { it.ordinal == 2 && it.isStreaming })
    }

    @Test
    fun `mapToChatMessage complete indicator uses computed duration when denormalized seconds are zero`() {
        val message =
            assistantMessage(
                messageState = MessageState.COMPLETE,
                thinkingRaw = "full trace",
                thinkingDurationSeconds = 0L,
                thinkingStartTime = 100L,
                thinkingEndTime = 8_100L,
                transcriptSegments = emptyList(),
            )

        val ui = viewModel.mapToChatMessageForTesting(message)
        val complete = ui.indicatorState as IndicatorState.Complete
        assertNotNull(complete.thinkingData)
        assertEquals(8L, complete.thinkingData!!.thinkingDurationSeconds)
    }

    @Test
    fun `mapToChatMessage complete indicator uses computed duration when denormalized seconds null`() {
        val message =
            assistantMessage(
                messageState = MessageState.COMPLETE,
                thinkingRaw = "full trace",
                thinkingDurationSeconds = null,
                thinkingStartTime = 100L,
                thinkingEndTime = 8_100L,
                transcriptSegments = emptyList(),
            )

        val ui = viewModel.mapToChatMessageForTesting(message)
        val complete = ui.indicatorState as IndicatorState.Complete
        assertNotNull(complete.thinkingData)
        assertEquals(8L, complete.thinkingData!!.thinkingDurationSeconds)
    }

    @Test
    fun `mapToChatMessage generating omits thinkingData when duration cannot be resolved`() {
        val message =
            assistantMessage(
                messageState = MessageState.GENERATING,
                thinkingRaw = "in progress",
                thinkingDurationSeconds = null,
                thinkingStartTime = 500L,
                thinkingEndTime = null,
                transcriptSegments = emptyList(),
            )

        val ui = viewModel.mapToChatMessageForTesting(message)
        val generating = ui.indicatorState as IndicatorState.Generating
        assertNull(generating.thinkingData)
    }

    @Test
    fun `mapToChatMessage exposes transcript segments for interleaved complete message`() {
        val message =
            assistantMessage(
                messageState = MessageState.COMPLETE,
                thinkingRaw = "stitched",
                thinkingDurationSeconds = 30L,
                transcriptSegments =
                    listOf(
                        MessageTranscriptSegment.Thinking(ordinal = 0, text = "t1", thinkingDurationSeconds = 12L),
                        MessageTranscriptSegment.AssistantText(ordinal = 1, text = "a1"),
                        MessageTranscriptSegment.Thinking(ordinal = 2, text = "t2", thinkingDurationSeconds = 11L),
                    ),
            )

        val ui = viewModel.mapToChatMessageForTesting(message)
        assertEquals(3, ui.transcriptSegments.size)
        assertTrue(ui.transcriptSegments[0] is TranscriptSegmentUi.ThinkingBlock)
        assertTrue(ui.transcriptSegments[1] is TranscriptSegmentUi.AnswerBlock)
        assertTrue(ui.transcriptSegments[2] is TranscriptSegmentUi.ThinkingBlock)
        val complete = ui.indicatorState as IndicatorState.Complete
        assertNotNull(complete.thinkingData)
    }

    private fun assistantMessage(
        messageState: MessageState,
        thinkingRaw: String? = null,
        thinkingDurationSeconds: Long? = null,
        thinkingStartTime: Long? = null,
        thinkingEndTime: Long? = null,
        transcriptSegments: List<MessageTranscriptSegment>,
    ): Message =
        Message(
            id = MessageId("m1"),
            chatId = ChatId("c1"),
            content = Content(text = "body"),
            role = Role.ASSISTANT,
            messageState = messageState,
            createdAt = 1L,
            thinkingRaw = thinkingRaw,
            thinkingDurationSeconds = thinkingDurationSeconds,
            thinkingStartTime = thinkingStartTime,
            thinkingEndTime = thinkingEndTime,
            modelType = ModelType.FAST,
            transcriptSegments = transcriptSegments,
        )
}
