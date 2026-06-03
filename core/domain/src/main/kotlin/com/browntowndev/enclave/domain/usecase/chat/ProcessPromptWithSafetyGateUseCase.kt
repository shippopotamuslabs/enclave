package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.usecase.inference.ModeratePromptUseCase
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ProcessPromptWithSafetyGateUseCase
    @Inject
    constructor(
        private val createUserMessageUseCase: CreateUserMessageUseCase,
        private val createAssistantPlaceholderUseCase: CreateAssistantPlaceholderUseCase,
        private val moderatePromptUseCase: ModeratePromptUseCase,
        private val rollbackPromptSubmissionUseCase: RollbackPromptSubmissionUseCase,
        private val cloudSyncPort: CloudSyncPort,
        private val generateChatTitleUseCase: GenerateChatTitleUseCase,
    ) {
        suspend operator fun invoke(request: PromptSafetyGateRequest): PromptSafetyGateResult {
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
                if (request.shouldModerate) {
                    val moderationResult = moderatePromptUseCase(request.promptTextForModeration)
                    if (moderationResult.blocked) {
                        rollbackPromptSubmissionUseCase(
                            chatId = userResult.chatId,
                            userMessageId = userResult.userMessageId,
                            assistantMessageId = request.assistantMessageId,
                            wasNewChat = wasNewChat,
                        )
                        cloudSyncPort.triggerSync()
                        return PromptSafetyGateResult.Blocked(moderationResult)
                    }
                }
                if (wasNewChat && request.promptTextForModeration.isNotBlank()) {
                    val chatId = userResult.chatId
                    val prompt = request.promptTextForModeration
                    titleJob =
                        CoroutineScope(coroutineContext).launch {
                            generateChatTitleUseCase(chatId, prompt)
                        }
                }
                cloudSyncPort.triggerSync()
                return PromptSafetyGateResult.Allowed(
                    userResult = userResult,
                    assistantMessageId = request.assistantMessageId,
                    wasNewChat = wasNewChat,
                )
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

