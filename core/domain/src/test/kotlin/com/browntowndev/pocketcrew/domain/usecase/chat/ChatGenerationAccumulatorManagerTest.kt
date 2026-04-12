package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Mode
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.port.inference.InferenceBusyException
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatGenerationAccumulatorManagerTest {

    @Test
    fun `non crew mode accumulates streaming state onto default assistant message`() = runTest {
        val manager = ChatGenerationAccumulatorManager(
            mode = Mode.FAST,
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            defaultAssistantMessageId = MessageId("assistant"),
            chatRepository = mockk(relaxed = true),
        )

        manager.reduce(MessageGenerationState.Processing(ModelType.FAST))
        manager.reduce(MessageGenerationState.ThinkingLive("thinking", ModelType.FAST))
        val result = manager.reduce(MessageGenerationState.GeneratingText("answer", ModelType.FAST))
        manager.reduce(MessageGenerationState.Finished(ModelType.FAST))

        val snapshot = result.messages.getValue(MessageId("assistant"))
        assertEquals(MessageState.GENERATING, snapshot.messageState)
        assertEquals("thinking", snapshot.thinkingRaw)
        assertEquals("answer", snapshot.content)
        assertEquals(PipelineStep.FINAL, snapshot.pipelineStep)
        assertTrue(manager.messages.getValue(MessageId("assistant")).isComplete)
    }

    @Test
    fun `crew mode creates distinct assistant messages per model step`() = runTest {
        val chatRepository = mockk<ChatRepository>()
        coEvery {
            chatRepository.createAssistantMessage(
                chatId = ChatId("chat"),
                userMessageId = MessageId("user"),
                modelType = ModelType.MAIN,
                pipelineStep = PipelineStep.SYNTHESIS,
            )
        } returns MessageId("main-assistant")
        coEvery {
            chatRepository.createAssistantMessage(
                chatId = ChatId("chat"),
                userMessageId = MessageId("user"),
                modelType = ModelType.FINAL_SYNTHESIS,
                pipelineStep = PipelineStep.FINAL,
            )
        } returns MessageId("final-assistant")

        val manager = ChatGenerationAccumulatorManager(
            mode = Mode.CREW,
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            defaultAssistantMessageId = MessageId("unused"),
            chatRepository = chatRepository,
        )

        val first = manager.reduce(MessageGenerationState.Processing(ModelType.MAIN))
        val second = manager.reduce(MessageGenerationState.Processing(ModelType.FINAL_SYNTHESIS))

        assertTrue(first.messages.containsKey(MessageId("main-assistant")))
        assertTrue(second.messages.containsKey(MessageId("final-assistant")))
        coVerify(exactly = 1) {
            chatRepository.createAssistantMessage(
                chatId = ChatId("chat"),
                userMessageId = MessageId("user"),
                modelType = ModelType.MAIN,
                pipelineStep = PipelineStep.SYNTHESIS,
            )
        }
        coVerify(exactly = 1) {
            chatRepository.createAssistantMessage(
                chatId = ChatId("chat"),
                userMessageId = MessageId("user"),
                modelType = ModelType.FINAL_SYNTHESIS,
                pipelineStep = PipelineStep.FINAL,
            )
        }
    }

    @Test
    fun `failed busy state writes user facing lock message`() = runTest {
        val manager = ChatGenerationAccumulatorManager(
            mode = Mode.FAST,
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            defaultAssistantMessageId = MessageId("assistant"),
            chatRepository = mockk(relaxed = true),
        )

        val result = manager.reduce(
            MessageGenerationState.Failed(
                error = InferenceBusyException(),
                modelType = ModelType.FAST,
            )
        )

        val snapshot = result.messages.getValue(MessageId("assistant"))
        assertTrue(snapshot.content.contains("Another message is in progress"))
        assertEquals(MessageState.COMPLETE, snapshot.messageState)
    }
}
