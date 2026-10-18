package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

open class SubmitPromptUseCase
    @Inject
    constructor(
        private val createUserMessageUseCase: CreateUserMessageUseCase,
        private val createAssistantPlaceholderUseCase: CreateAssistantPlaceholderUseCase,
        private val rollbackPromptSubmissionUseCase: RollbackPromptSubmissionUseCase,
        private val cloudSyncPort: CloudSyncPort,
        private val generateChatTitleUseCase: GenerateChatTitleUseCase,
    ) {
        open suspend operator fun invoke(request: SubmitPromptRequest): UserPromptResult {
            val wasNewChat = request.message.chatId == null
            var userResult: UserPromptResult? = null
            var titleJob: Job? = null
            try {
                userResult =
                    createUserMessageUseCase(
                        message = request.message,
                        scheduleCloudSync = false,
                    )
                createAssistantPlaceholderUseCase(
                    chatId = userResult.chatId,
                    userMessageCreatedAtMillis = userResult.userMessageCreatedAtMillis,
                    assistantMessageId = request.assistantMessageId,
                    scheduleCloudSync = false,
                )
                if (wasNewChat && request.message.content.text.isNotBlank()) {
                    val chatId = userResult.chatId
                    val prompt = request.message.content.text
                    titleJob =
                        CoroutineScope(coroutineContext).launch {
                            generateChatTitleUseCase(chatId, prompt)
                        }
                }
                cloudSyncPort.triggerSync()
                return userResult
            } catch (e: CancellationException) {
                titleJob?.cancel()
                userResult?.let { persisted ->
                    rollbackPromptSubmissionUseCase(
                        chatId = persisted.chatId,
                        userMessageId = persisted.userMessageId,
                        assistantMessageId = request.assistantMessageId,
                        wasNewChat = wasNewChat,
                    )
                }
                cloudSyncPort.triggerSync()
                throw e
            } catch (e: Exception) {
                titleJob?.cancel()
                userResult?.let { persisted ->
                    rollbackPromptSubmissionUseCase(
                        chatId = persisted.chatId,
                        userMessageId = persisted.userMessageId,
                        assistantMessageId = request.assistantMessageId,
                        wasNewChat = wasNewChat,
                    )
                }
                cloudSyncPort.triggerSync()
                throw e
            }
        }
    }
