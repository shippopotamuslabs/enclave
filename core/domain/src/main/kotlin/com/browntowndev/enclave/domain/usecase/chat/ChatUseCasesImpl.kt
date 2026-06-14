package com.browntowndev.enclave.domain.usecase.chat

import javax.inject.Inject

class ChatUseCasesImpl
    @Inject
    constructor(
        override val createAssistantPlaceholderUseCase: CreateAssistantPlaceholderUseCase,
        override val submitPromptUseCase: SubmitPromptUseCase,
        override val mergeDbMessagesWithOptimisticUseCase: MergeDbMessagesWithOptimisticUseCase,
        override val generateChatResponseUseCase: GenerateChatResponseUseCase,
        override val generateChatTitleUseCase: GenerateChatTitleUseCase,
        override val getChatUseCase: GetChatUseCase,
        override val mergeMessagesUseCase: MergeMessagesUseCase,
        override val listenToSpeechUseCase: ListenToSpeechUseCase,
        override val processFileAttachmentUseCase: ProcessFileAttachmentUseCase,
    ) : ChatUseCases
