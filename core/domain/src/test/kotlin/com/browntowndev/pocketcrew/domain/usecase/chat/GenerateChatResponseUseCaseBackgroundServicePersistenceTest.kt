package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Mode
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.pocketcrew.domain.port.inference.ChatInferenceExecutorPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.PipelineExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.ExtractedUrlTrackerPort
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GenerateChatResponseUseCaseBackgroundServicePersistenceTest {

    @Test
    fun `direct inference publishes accumulated snapshots to active turn port`() = runTest {
        val userMessageId = MessageId("user")
        val assistantMessageId = MessageId("assistant")
        val chatId = ChatId("chat")
        val activeChatTurnSnapshotPort = RecordingActiveChatTurnSnapshotPort()
        val chatInferenceExecutor = completedFastExecutor("final chunk")
        val useCase = createUseCase(
            userMessageId = userMessageId,
            chatId = chatId,
            chatInferenceExecutor = chatInferenceExecutor,
            activeChatTurnSnapshotPort = activeChatTurnSnapshotPort,
        )

        val emissions = useCase(
            prompt = "hello",
            userMessageId = userMessageId,
            assistantMessageId = assistantMessageId,
            chatId = chatId,
            mode = Mode.FAST,
            backgroundInferenceEnabled = false,
        ).toList()

        val key = ActiveChatTurnKey(
            chatId = chatId,
            assistantMessageId = assistantMessageId,
        )
        val publishedSnapshots = activeChatTurnSnapshotPort.published.map { it.second }

        assertEquals(emissions, publishedSnapshots)
        assertTrue(activeChatTurnSnapshotPort.published.all { it.first == key })
        assertEquals(
            "final chunk",
            publishedSnapshots.last().messages.getValue(assistantMessageId).content,
        )
        assertTrue(publishedSnapshots.last().messages.getValue(assistantMessageId).isComplete)
    }

    @Test
    fun `background inference does not publish accumulated snapshots from use case`() = runTest {
        val userMessageId = MessageId("user")
        val assistantMessageId = MessageId("assistant")
        val chatId = ChatId("chat")
        val activeChatTurnSnapshotPort = RecordingActiveChatTurnSnapshotPort()
        val chatInferenceExecutor = completedFastExecutor("background chunk")
        val useCase = createUseCase(
            userMessageId = userMessageId,
            chatId = chatId,
            chatInferenceExecutor = chatInferenceExecutor,
            activeChatTurnSnapshotPort = activeChatTurnSnapshotPort,
        )

        useCase(
            prompt = "hello",
            userMessageId = userMessageId,
            assistantMessageId = assistantMessageId,
            chatId = chatId,
            mode = Mode.FAST,
            backgroundInferenceEnabled = true,
        ).toList()

        assertTrue(activeChatTurnSnapshotPort.published.isEmpty())
    }

    @Test
    fun `background service cancellation before terminal state does not persist truncated assistant message`() = runTest {
        val userMessageId = MessageId("user")
        val assistantMessageId = MessageId("assistant")
        val chatId = ChatId("chat")
        val chatRepository = mockk<ChatRepository>(relaxed = true)
        val messageRepository = mockk<MessageRepository> {
            coEvery { getMessageById(userMessageId) } returns Message(
                id = userMessageId,
                chatId = chatId,
                content = Content(text = "hello"),
                role = Role.USER,
            )
        }
        val chatInferenceExecutor = mockk<ChatInferenceExecutorPort> {
            every { stop() } returns Unit
            every {
                execute(
                    prompt = any(),
                    userMessageId = any(),
                    assistantMessageId = any(),
                    chatId = any(),
                    userHasImage = any(),
                    modelType = any(),
                    backgroundInferenceEnabled = any(),
                )
            } returns flow {
                emit(MessageGenerationState.GeneratingText("partial", ModelType.FAST))
                awaitCancellation()
            }
        }
        val useCase = GenerateChatResponseUseCase(
            pipelineExecutor = mockPipelineExecutor(),
            chatRepository = chatRepository,
            messageRepository = messageRepository,
            loggingPort = mockk<LoggingPort>(relaxed = true),
            activeModelProvider = mockk<ActiveModelProviderPort>(),
            extractedUrlTracker = noOpExtractedUrlTracker(),
            chatInferenceExecutor = chatInferenceExecutor,
        )

        useCase(
            prompt = "hello",
            userMessageId = userMessageId,
            assistantMessageId = assistantMessageId,
            chatId = chatId,
            mode = Mode.FAST,
            backgroundInferenceEnabled = true,
        ).first()

        coVerify(exactly = 0) {
            chatRepository.persistAllMessageData(
                messageId = assistantMessageId,
                modelType = any(),
                thinkingStartTime = any(),
                thinkingEndTime = any(),
                thinkingDuration = any(),
                thinkingRaw = any(),
                content = any(),
                messageState = MessageState.COMPLETE,
                pipelineStep = any(),
                tavilySources = any(),
            )
        }
    }

    private fun createUseCase(
        userMessageId: MessageId,
        chatId: ChatId,
        chatInferenceExecutor: ChatInferenceExecutorPort,
        activeChatTurnSnapshotPort: ActiveChatTurnSnapshotPort,
        chatRepository: ChatRepository = mockk(relaxed = true),
    ): GenerateChatResponseUseCase {
        val messageRepository = mockk<MessageRepository> {
            coEvery { getMessageById(userMessageId) } returns Message(
                id = userMessageId,
                chatId = chatId,
                content = Content(text = "hello"),
                role = Role.USER,
            )
        }
        return GenerateChatResponseUseCase(
            pipelineExecutor = mockPipelineExecutor(),
            chatRepository = chatRepository,
            messageRepository = messageRepository,
            loggingPort = mockk<LoggingPort>(relaxed = true),
            activeModelProvider = mockk<ActiveModelProviderPort>(),
            extractedUrlTracker = noOpExtractedUrlTracker(),
            chatInferenceExecutor = chatInferenceExecutor,
            activeChatTurnSnapshotPort = activeChatTurnSnapshotPort,
        )
    }

    private fun completedFastExecutor(text: String): ChatInferenceExecutorPort {
        return mockk {
            every { stop() } returns Unit
            every {
                execute(
                    prompt = any(),
                    userMessageId = any(),
                    assistantMessageId = any(),
                    chatId = any(),
                    userHasImage = any(),
                    modelType = any(),
                    backgroundInferenceEnabled = any(),
                )
            } returns flow {
                emit(MessageGenerationState.GeneratingText(text, ModelType.FAST))
                emit(MessageGenerationState.Finished(ModelType.FAST))
            }
        }
    }

    private fun mockPipelineExecutor(): PipelineExecutorPort = mockk {
        every { executePipeline(any(), any()) } returns emptyFlow()
        coEvery { stopPipeline(any()) } returns Unit
        coEvery { resumeFromState(any(), any(), any(), any()) } returns emptyFlow()
    }

    private fun noOpExtractedUrlTracker(): ExtractedUrlTrackerPort = object : ExtractedUrlTrackerPort {
        override val urls: Set<String> get() = emptySet()
        override fun add(url: String) = Unit
        override fun clear() = Unit
    }

    private class RecordingActiveChatTurnSnapshotPort : ActiveChatTurnSnapshotPort {
        val published = mutableListOf<Pair<ActiveChatTurnKey, AccumulatedMessages>>()

        override fun observe(key: ActiveChatTurnKey): Flow<AccumulatedMessages?> {
            return flowOf(null)
        }

        override suspend fun publish(
            key: ActiveChatTurnKey,
            snapshot: AccumulatedMessages,
        ) {
            published += key to snapshot
        }

        override suspend fun markSourcesExtracted(
            key: ActiveChatTurnKey,
            urls: List<String>,
        ) = Unit

        override suspend fun acknowledgeHandoff(key: ActiveChatTurnKey) = Unit

        override suspend fun clear(key: ActiveChatTurnKey) = Unit
    }
}
