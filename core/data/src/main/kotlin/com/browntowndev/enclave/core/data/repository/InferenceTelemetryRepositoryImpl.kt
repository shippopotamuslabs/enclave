package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.local.MessageInferenceTelemetryDao
import com.browntowndev.enclave.core.data.local.dao.message.MessageDao
import com.browntowndev.enclave.core.data.mapper.telemetry.toDomain
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.telemetry.ChatTelemetrySummary
import com.browntowndev.enclave.domain.model.telemetry.MessageTelemetrySummary
import com.browntowndev.enclave.domain.model.telemetry.TelemetrySummaryBuilder
import com.browntowndev.enclave.domain.model.telemetry.UsagePeriod
import com.browntowndev.enclave.domain.model.telemetry.UsageSpendSummary
import com.browntowndev.enclave.domain.port.repository.InferenceTelemetryRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject

class InferenceTelemetryRepositoryImpl
    @Inject
    constructor(
        private val dao: MessageInferenceTelemetryDao,
        private val messageDao: MessageDao,
        private val clock: Clock,
        private val zoneId: ZoneId,
    ) : InferenceTelemetryRepositoryPort {
        override fun observeMessageTelemetry(messageId: MessageId): Flow<MessageTelemetrySummary> =
            dao.observeByMessageId(messageId.value).map { entities ->
                TelemetrySummaryBuilder.buildMessageSummary(
                    messageId = messageId,
                    rows = entities.map { it.toDomain() },
                )
            }

        override fun observeChatTelemetry(chatId: ChatId): Flow<ChatTelemetrySummary> =
            combine(
                dao.observeByChatId(chatId.value),
                messageDao.getMessagesByChatIdFlow(chatId).map { messages ->
                    messages.count { it.message.role == Role.ASSISTANT }
                },
            ) { entities, assistantTurnCount ->
                TelemetrySummaryBuilder.buildChatSummary(
                    chatId = chatId,
                    rows = entities.map { it.toDomain() },
                    assistantTurnCount = assistantTurnCount,
                )
            }

        override fun observeUsageSpendSummary(period: UsagePeriod): Flow<UsageSpendSummary> =
            dao.observeAll().map { entities ->
                TelemetrySummaryBuilder.buildUsageSpendSummary(
                    period = period,
                    rows = entities.map { it.toDomain() },
                    now = clock.instant(),
                    zoneId = zoneId,
                )
            }
    }
