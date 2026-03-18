package com.browntowndev.pocketcrew.domain.usecase.chat

import javax.inject.Inject

class ChatUseCasesImpl @Inject constructor(
    override val processPromptUseCase: CreateUserMessageUseCase,
    override val generateChatResponseUseCase: GenerateChatResponseUseCase,
    override val getChatUseCase: GetChatUseCase
) : ChatUseCases
