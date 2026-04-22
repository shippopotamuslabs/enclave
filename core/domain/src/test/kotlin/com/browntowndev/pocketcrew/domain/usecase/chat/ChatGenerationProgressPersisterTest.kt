package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Mode
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.ExtractedUrlTrackerPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChatGenerationProgressPersisterTest {

    @Test
    fun `applyState does not persist partial progress before terminal state`() = runTest {
        val chatRepository = mockChatRepository()
        val session = ChatGenerationProgressPersister(
            chatRepository = chatRepository,
            extractedUrlTracker = FakeExtractedUrlTracker(),
        ).startSession(
            mode = Mode.FAST,
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            assistantMessageId = MessageId("assistant"),
        )

        session.applyState(MessageGenerationState.GeneratingText("hello", ModelType.FAST))
        session.applyState(MessageGenerationState.GeneratingText(" world", ModelType.FAST))
        session.applyState(MessageGenerationState.GeneratingText("!", ModelType.FAST))

        coVerify(exactly = 0) {
            chatRepository.persistAllMessageData(
                messageId = MessageId("assistant"),
                modelType = any(),
                thinkingStartTime = any(),
                thinkingEndTime = any(),
                thinkingDuration = any(),
                thinkingRaw = any(),
                content = any(),
                messageState = any(),
                pipelineStep = any(),
                tavilySources = any(),
            )
        }
    }

    @Test
    fun `applyState always persists terminal state with complete content`() = runTest {
        val chatRepository = mockChatRepository()
        val contentSlot = slot<String>()
        val stateSlot = slot<MessageState>()
        coEvery {
            chatRepository.persistAllMessageData(
                messageId = any(),
                modelType = any(),
                thinkingStartTime = any(),
                thinkingEndTime = any(),
                thinkingDuration = any(),
                thinkingRaw = any(),
                content = capture(contentSlot),
                messageState = capture(stateSlot),
                pipelineStep = any(),
                tavilySources = any(),
            )
        } returns Unit
        val session = ChatGenerationProgressPersister(
            chatRepository = chatRepository,
            extractedUrlTracker = FakeExtractedUrlTracker(),
        ).startSession(
            mode = Mode.FAST,
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            assistantMessageId = MessageId("assistant"),
        )

        session.applyState(MessageGenerationState.GeneratingText("final answer", ModelType.FAST))
        session.applyState(MessageGenerationState.Finished(ModelType.FAST))

        assertEquals("final answer", contentSlot.captured)
        assertEquals(MessageState.COMPLETE, stateSlot.captured)
    }

    @Test
    fun `flush with cancelled partial progress does not persist truncated content`() = runTest {
        val chatRepository = mockChatRepository()
        val session = ChatGenerationProgressPersister(
            chatRepository = chatRepository,
            extractedUrlTracker = FakeExtractedUrlTracker(),
        ).startSession(
            mode = Mode.FAST,
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            assistantMessageId = MessageId("assistant"),
        )

        session.applyState(MessageGenerationState.GeneratingText("truncated", ModelType.FAST))
        session.flush(markIncompleteAsCancelled = true)

        coVerify(exactly = 0) {
            chatRepository.persistAllMessageData(
                messageId = MessageId("assistant"),
                modelType = any(),
                thinkingStartTime = any(),
                thinkingEndTime = any(),
                thinkingDuration = any(),
                thinkingRaw = any(),
                content = any(),
                messageState = any(),
                pipelineStep = any(),
                tavilySources = any(),
            )
        }
    }

    @Test
    fun `flush without cancellation does not persist incomplete progress`() = runTest {
        val chatRepository = mockChatRepository()
        val session = ChatGenerationProgressPersister(
            chatRepository = chatRepository,
            extractedUrlTracker = FakeExtractedUrlTracker(),
        ).startSession(
            mode = Mode.FAST,
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            assistantMessageId = MessageId("assistant"),
        )

        session.applyState(MessageGenerationState.GeneratingText("still streaming", ModelType.FAST))
        session.flush(markIncompleteAsCancelled = false)

        coVerify(exactly = 0) {
            chatRepository.persistAllMessageData(
                messageId = MessageId("assistant"),
                modelType = any(),
                thinkingStartTime = any(),
                thinkingEndTime = any(),
                thinkingDuration = any(),
                thinkingRaw = any(),
                content = any(),
                messageState = any(),
                pipelineStep = any(),
                tavilySources = any(),
            )
        }
    }

    private fun mockChatRepository(): ChatRepository = mockk(relaxed = true)

    private class FakeExtractedUrlTracker : ExtractedUrlTrackerPort {
        override val urls: Set<String> = emptySet()
        override fun add(url: String) = Unit
        override fun clear() = Unit
    }
}
