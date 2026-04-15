package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Mode
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class PersistAccumulatedChatMessagesUseCaseTest {

    @Test
    fun `persist use case strips tool traces before storing content`() = runTest {
        val chatRepository = mockk<ChatRepository>(relaxed = true)
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
        val manager = ChatGenerationAccumulatorManager(
            mode = Mode.FAST,
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            defaultAssistantMessageId = MessageId("assistant"),
            chatRepository = chatRepository,
        )
        val persistUseCase = PersistAccumulatedChatMessagesUseCase(chatRepository)

        manager.reduce(
            MessageGenerationState.GeneratingText(
                """<tool_call>{"name":"tavily_web_search","arguments":{"query":"android"}}</tool_call>""",
                ModelType.FAST,
            )
        )
        manager.reduce(
            MessageGenerationState.GeneratingText(
                """<tool_result>{"answer":"value"}</tool_result>Final answer""",
                ModelType.FAST,
            )
        )
        manager.reduce(MessageGenerationState.Finished(ModelType.FAST))
        persistUseCase(manager)

        assertEquals("Final answer", contentSlot.captured)
        assertEquals(MessageState.COMPLETE, stateSlot.captured)
        assertFalse(contentSlot.captured.contains("tool_call"))
    }

    @Test
    fun `persist use case strips CDATA tool traces before storing content`() = runTest {
        val chatRepository = mockk<ChatRepository>(relaxed = true)
        val contentSlot = slot<String>()
        coEvery {
            chatRepository.persistAllMessageData(
                messageId = any(),
                modelType = any(),
                thinkingStartTime = any(),
                thinkingEndTime = any(),
                thinkingDuration = any(),
                thinkingRaw = any(),
                content = capture(contentSlot),
                messageState = any(),
                pipelineStep = any(),
            )
        } returns Unit
        val manager = ChatGenerationAccumulatorManager(
            mode = Mode.FAST,
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            defaultAssistantMessageId = MessageId("assistant"),
            chatRepository = chatRepository,
        )
        val persistUseCase = PersistAccumulatedChatMessagesUseCase(chatRepository)

        manager.reduce(
            MessageGenerationState.GeneratingText(
                """<![CDATA[<tool>{"name":"tavily_web_search","arguments":{"query":"android"}}</tool>]]>""",
                ModelType.FAST,
            )
        )
        manager.reduce(
            MessageGenerationState.GeneratingText(
                """<tool_result>{"answer":"value"}</tool_result>Final answer""",
                ModelType.FAST,
            )
        )
        manager.reduce(MessageGenerationState.Finished(ModelType.FAST))
        persistUseCase(manager)

        assertEquals("Final answer", contentSlot.captured)
        assertFalse(contentSlot.captured.contains("<![CDATA[<tool"))
    }

    @Test
    fun `persist use case keeps incomplete messages in processing state`() = runTest {
        val chatRepository = mockk<ChatRepository>(relaxed = true)
        val stateSlot = slot<MessageState>()
        coEvery {
            chatRepository.persistAllMessageData(
                messageId = any(),
                modelType = any(),
                thinkingStartTime = any(),
                thinkingEndTime = any(),
                thinkingDuration = any(),
                thinkingRaw = any(),
                content = any(),
                messageState = capture(stateSlot),
                pipelineStep = any(),
                tavilySources = any(),
            )
        } returns Unit
        val manager = ChatGenerationAccumulatorManager(
            mode = Mode.FAST,
            chatId = ChatId("chat"),
            userMessageId = MessageId("user"),
            defaultAssistantMessageId = MessageId("assistant"),
            chatRepository = chatRepository,
        )

        manager.reduce(MessageGenerationState.Processing(ModelType.FAST))
        PersistAccumulatedChatMessagesUseCase(chatRepository)(manager)

        coVerify(exactly = 1) { chatRepository.persistAllMessageData(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        assertEquals(MessageState.PROCESSING, stateSlot.captured)
    }
}
