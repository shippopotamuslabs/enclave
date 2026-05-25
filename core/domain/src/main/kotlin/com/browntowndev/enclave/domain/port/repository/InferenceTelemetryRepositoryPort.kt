package com.browntowndev.enclave.domain.port.repository

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.telemetry.ChatTelemetrySummary
import com.browntowndev.enclave.domain.model.telemetry.MessageTelemetrySummary
import com.browntowndev.enclave.domain.model.telemetry.UsagePeriod
import com.browntowndev.enclave.domain.model.telemetry.UsageSpendSummary
import kotlinx.coroutines.flow.Flow

interface InferenceTelemetryRepositoryPort {
    fun observeMessageTelemetry(messageId: MessageId): Flow<MessageTelemetrySummary>

    fun observeChatTelemetry(chatId: ChatId): Flow<ChatTelemetrySummary>

    fun observeUsageSpendSummary(period: UsagePeriod): Flow<UsageSpendSummary>
}
