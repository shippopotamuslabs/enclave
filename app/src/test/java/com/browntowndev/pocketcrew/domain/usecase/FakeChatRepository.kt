package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.ThinkingData
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.StepCompletionData

/**
 * Fake implementation of ChatRepository for testing.
 * Allows controlling chat creation and verifying method calls.
 */
class FakeChatRepository : ChatRepository {

    private val createdChats = mutableListOf<Chat>()
    private var nextChatId = 1L
    var shouldThrowOnCreateChat = false
    private val savedAssistantMessages = mutableListOf<Pair<Long, String>>()
    private val savedStepCompletions = mutableListOf<StepCompletionData>()

    override suspend fun createChat(chat: Chat): Long {
        if (shouldThrowOnCreateChat) {
            throw RuntimeException("Simulated error on createChat")
        }
        val chatWithId = chat.copy(id = nextChatId)
        createdChats.add(chatWithId)
        return nextChatId++
    }

    override suspend fun saveAssistantMessage(
        messageId: Long,
        content: String,
        thinkingData: ThinkingData?
    ) {
        savedAssistantMessages.add(messageId to content)
    }

    override suspend fun saveStepCompletion(
        messageId: Long,
        stepType: PipelineStep,
        stepOutput: String,
        thinkingDurationSeconds: Int,
        thinkingSteps: List<String>,
        modelType: ModelType
    ) {
        savedStepCompletions.add(
            StepCompletionData(
                stepOutput = stepOutput,
                thinkingDurationSeconds = thinkingDurationSeconds,
                totalDurationSeconds = thinkingDurationSeconds,
                thinkingSteps = thinkingSteps,
                stepType = stepType,
                modelType = modelType
            )
        )
    }

    override suspend fun getStepCompletionsForMessage(messageId: Long): List<StepCompletionData> {
        return savedStepCompletions.toList()
    }

    fun getCreatedChats(): List<Chat> = createdChats.toList()

    fun verifyChatCreated(times: Int) {
        org.junit.jupiter.api.Assertions.assertEquals(times, createdChats.size)
    }

    fun verifyChatName(expectedName: String) {
        org.junit.jupiter.api.Assertions.assertTrue(
            createdChats.any { it.name == expectedName },
            "No chat was created with name: $expectedName"
        )
    }

    fun reset() {
        createdChats.clear()
        nextChatId = 1L
        shouldThrowOnCreateChat = false
        savedAssistantMessages.clear()
        savedStepCompletions.clear()
    }
}

