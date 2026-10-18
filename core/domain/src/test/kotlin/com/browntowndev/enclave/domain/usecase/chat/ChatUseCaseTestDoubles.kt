package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.core.testing.FakeTransactionProvider
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.port.inference.ChatTitleGeneratorPort

/** Recording [CreateUserMessageUseCase] fake: returns the canned [result] and records every message. */
internal class FakeCreateUserMessageUseCase : CreateUserMessageUseCase(
    FakeTransactionProvider(),
    FakeMessageRepository(),
    FakeChatRepository(),
) {
    val calls = mutableListOf<Message>()
    var result: UserPromptResult? = null

    override suspend fun invoke(
        message: Message,
        scheduleCloudSync: Boolean,
    ): UserPromptResult {
        calls += message
        return result ?: error("FakeCreateUserMessageUseCase.result not set")
    }
}

/** Recording [GenerateChatTitleUseCase] fake: records (chatId, prompt) pairs, generates nothing. */
internal class FakeGenerateChatTitleUseCase : GenerateChatTitleUseCase(
    ChatTitleGeneratorPort.Dummy,
    FakeChatRepository(),
    FakeLoggingPort(),
) {
    val calls = mutableListOf<Pair<ChatId, String>>()

    override suspend fun invoke(
        chatId: ChatId,
        prompt: String,
    ) {
        calls += chatId to prompt
    }
}

/** Recording [CreateAssistantPlaceholderUseCase] fake: records calls, optionally throws [failure]. */
internal class FakeCreateAssistantPlaceholderUseCase : CreateAssistantPlaceholderUseCase(
    FakeMessageRepository(),
    FakeChatRepository(),
) {
    data class PlaceholderCall(
        val chatId: ChatId,
        val userMessageCreatedAtMillis: Long,
        val assistantMessageId: MessageId,
        val scheduleCloudSync: Boolean,
    )

    val calls = mutableListOf<PlaceholderCall>()
    var failure: Throwable? = null

    override suspend fun invoke(
        chatId: ChatId,
        userMessageCreatedAtMillis: Long,
        assistantMessageId: MessageId,
        scheduleCloudSync: Boolean,
    ): AssistantPlaceholderResult {
        calls += PlaceholderCall(chatId, userMessageCreatedAtMillis, assistantMessageId, scheduleCloudSync)
        failure?.let { throw it }
        return AssistantPlaceholderResult(assistantMessageId = assistantMessageId)
    }
}

/** Recording [RollbackPromptSubmissionUseCase] fake: records calls, rolls nothing back. */
internal class FakeRollbackPromptSubmissionUseCase : RollbackPromptSubmissionUseCase(
    FakeMessageRepository(),
    FakeChatRepository(),
    FakeTransactionProvider(),
) {
    data class RollbackCall(
        val chatId: ChatId,
        val userMessageId: MessageId,
        val assistantMessageId: MessageId,
        val wasNewChat: Boolean,
    )

    val calls = mutableListOf<RollbackCall>()

    override suspend fun invoke(
        chatId: ChatId,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        wasNewChat: Boolean,
    ) {
        calls += RollbackCall(chatId, userMessageId, assistantMessageId, wasNewChat)
    }
}
