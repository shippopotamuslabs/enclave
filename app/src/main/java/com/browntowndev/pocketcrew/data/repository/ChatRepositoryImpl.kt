package com.browntowndev.pocketcrew.data.repository

import com.browntowndev.pocketcrew.data.local.ChatDao
import com.browntowndev.pocketcrew.data.local.MessageDao
import com.browntowndev.pocketcrew.data.local.StepCompletionEntity
import com.browntowndev.pocketcrew.data.mapper.toEntity
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.ThinkingData
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.StepCompletionData
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room implementation of ChatRepository.
 *
 * This implementation handles saving chats to the chat table.
 * Transaction management is intentionally NOT handled here - it is
 * delegated to the use case layer via TransactionProvider.
 *
 * @param chatDao The DAO for chat operations
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao
) : ChatRepository {

    /**
     * Creates a new chat in the database.
     *
     * @param chat The chat to create
     * @return The ID of the newly created chat
     */
    override suspend fun createChat(chat: Chat): Long {
        val entity = chat.toEntity()
        return chatDao.insert(entity)
    }

    /**
     * Saves an assistant message to the database.
     *
     * @param messageId The ID of the message
     * @param content The content of the message
     * @param thinkingData Optional thinking data for reasoning models
     */
    override suspend fun saveAssistantMessage(
        messageId: Long,
        content: String,
        thinkingData: ThinkingData?
    ) {
        messageDao.updateMessageContent(
            id = messageId,
            content = content,
            thinkingDuration = thinkingData?.thinkingDurationSeconds,
            thinkingSteps = thinkingData?.steps?.joinToString("\n"),
            thinkingRaw = thinkingData?.rawFullThought
        )
    }

    /**
     * Saves a single step completion for Crew mode.
     */
    override suspend fun saveStepCompletion(
        messageId: Long,
        stepType: PipelineStep,
        stepOutput: String,
        thinkingDurationSeconds: Int,
        thinkingSteps: List<String>,
        modelType: ModelType
    ) {
        val entity = StepCompletionEntity(
            messageId = messageId,
            stepType = stepType,
            stepOutput = stepOutput,
            durationSeconds = thinkingDurationSeconds,
            thinkingSteps = JSONArray(thinkingSteps).toString(),
            modelType = modelType
        )
        messageDao.insertStepCompletion(entity)
    }

    /**
     * Loads all step completions for a message.
     */
    override suspend fun getStepCompletionsForMessage(messageId: Long): List<StepCompletionData> {
        val entities = messageDao.getStepCompletionsForMessage(messageId)
        return entities.map { entity ->
            val stepsArray = try {
                JSONArray(entity.thinkingSteps).let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }
            } catch (e: Exception) {
                emptyList()
            }
            StepCompletionData(
                stepOutput = entity.stepOutput,
                thinkingDurationSeconds = entity.durationSeconds,
                totalDurationSeconds = entity.durationSeconds,
                thinkingSteps = stepsArray,
                stepType = entity.stepType,
                modelType = entity.modelType
            )
        }
    }
}
