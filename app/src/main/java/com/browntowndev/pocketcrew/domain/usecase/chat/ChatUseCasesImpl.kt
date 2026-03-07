package com.browntowndev.pocketcrew.domain.usecase.chat

import javax.inject.Inject

class ChatUseCasesImpl @Inject constructor(
    override val processPrompt: CreateUserMessageUseCase,
    override val generateChatResponseUseCase: GenerateChatResponseUseCase
) : ChatUseCases
