package com.browntowndev.enclave.domain.usecase.telemetry

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.telemetry.ChatTelemetrySummary
import com.browntowndev.enclave.domain.port.repository.InferenceTelemetryRepositoryPort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveChatTelemetryUseCase
    @Inject
    constructor(
        private val repository: InferenceTelemetryRepositoryPort,
    ) {
        operator fun invoke(chatId: ChatId): Flow<ChatTelemetrySummary> =
            repository.observeChatTelemetry(chatId)
    }
