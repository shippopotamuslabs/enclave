package com.browntowndev.enclave.domain.usecase.telemetry

import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.telemetry.MessageTelemetrySummary
import com.browntowndev.enclave.domain.port.repository.InferenceTelemetryRepositoryPort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveMessageTelemetryUseCase
    @Inject
    constructor(
        private val repository: InferenceTelemetryRepositoryPort,
    ) {
        operator fun invoke(messageId: MessageId): Flow<MessageTelemetrySummary> =
            repository.observeMessageTelemetry(messageId)
    }
