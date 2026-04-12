package com.browntowndev.pocketcrew.domain.usecase
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageVisionAnalysis
import com.browntowndev.pocketcrew.domain.model.chat.ResolvedImageTarget
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import org.junit.jupiter.api.Assertions


/**
 * Fake implementation of MessageRepository for testing.
 * Allows controlling message saving and verifying method calls.
 */
class FakeMessageRepository : MessageRepository {

    private val savedMessages = mutableListOf<Message>()
    private var getMessageByIdResult: Message? = null
    private var getMessagesForChatResult: List<Message> = emptyList()
    private val visionAnalyses = mutableListOf<MessageVisionAnalysis>()
    private var nextMessageId = 1

    private var resolvedImageTarget: ResolvedImageTarget? = null

    // Methods to simulate errors
    var shouldThrowOnSaveMessage = false

    override suspend fun saveMessage(message: Message): MessageId {
        if (shouldThrowOnSaveMessage) throw RuntimeException("Simulated error on saveMessage")
        savedMessages.add(message)
        val id = MessageId((nextMessageId++).toString())
        return id
    }

    override suspend fun getMessageById(id: MessageId): Message? {
        return getMessageByIdResult
    }

    override suspend fun getMessagesForChat(chatId: ChatId): List<Message> {
        return getMessagesForChatResult
    }

    override suspend fun saveVisionAnalysis(
        userMessageId: MessageId,
        imageUri: String,
        promptText: String,
        analysisText: String,
        modelType: ModelType,
    ) {
        visionAnalyses.removeAll { it.userMessageId == userMessageId && it.imageUri == imageUri }
        visionAnalyses += MessageVisionAnalysis(
            id = "${userMessageId.value}:$imageUri",
            userMessageId = userMessageId,
            imageUri = imageUri,
            promptText = promptText,
            analysisText = analysisText,
            modelType = modelType,
            createdAt = 0L,
            updatedAt = 0L,
        )
    }

    override suspend fun getVisionAnalysesForMessages(
        userMessageIds: List<MessageId>
    ): Map<MessageId, List<MessageVisionAnalysis>> =
        visionAnalyses.filter { it.userMessageId in userMessageIds }.groupBy { it.userMessageId }

    override suspend fun resolveLatestImageBearingUserMessage(
        chatId: ChatId,
        currentUserMessageId: MessageId,
    ): ResolvedImageTarget? = resolvedImageTarget

    fun setMessagesForChat(messages: List<Message>) {
        getMessagesForChatResult = messages
    }

    fun getSavedMessages(): List<Message> = savedMessages.toList()

    fun setVisionAnalyses(analyses: List<MessageVisionAnalysis>) {
        visionAnalyses.clear()
        visionAnalyses.addAll(analyses)
    }

    fun verifySaveMessageCalled(times: Int) {
        Assertions.assertEquals(times, savedMessages.size)
    }

    fun verifyMessageSaved(message: Message) {
        Assertions.assertTrue(
            savedMessages.any { it.id == message.id && it.content == message.content && it.role == message.role },
            "Message was not saved: $message"
        )
    }

    fun setResolvedImageTarget(target: ResolvedImageTarget?) {
        resolvedImageTarget = target
    }

    fun reset() {
        savedMessages.clear()
        shouldThrowOnSaveMessage = false
        getMessageByIdResult = null
        getMessagesForChatResult = emptyList()
        visionAnalyses.clear()
        resolvedImageTarget = null
    }
}
