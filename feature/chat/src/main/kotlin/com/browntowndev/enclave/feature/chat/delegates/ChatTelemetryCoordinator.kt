package com.browntowndev.enclave.feature.chat.delegates

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.telemetry.TelemetrySummaryBuilder
import com.browntowndev.enclave.domain.model.telemetry.TurnTelemetry
import com.browntowndev.enclave.domain.usecase.telemetry.ObserveChatTelemetryUseCase
import com.browntowndev.enclave.domain.usecase.telemetry.ObserveMessageTelemetryUseCase
import com.browntowndev.enclave.feature.chat.stats.ChatStatsSheetUiState
import com.browntowndev.enclave.feature.chat.stats.ChatTelemetryUiMapper
import com.browntowndev.enclave.feature.chat.stats.ResponseStatsSheetUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class ChatTelemetryCoordinator
    @Inject
    constructor(
        private val observeMessageTelemetryUseCase: ObserveMessageTelemetryUseCase,
        private val observeChatTelemetryUseCase: ObserveChatTelemetryUseCase,
        private val mapper: ChatTelemetryUiMapper,
    ) {
        private val _responseStatsSheet = MutableStateFlow(ResponseStatsSheetUiState())
        val responseStatsSheet: StateFlow<ResponseStatsSheetUiState> = _responseStatsSheet.asStateFlow()

        private val _chatStatsSheet = MutableStateFlow(ChatStatsSheetUiState())
        val chatStatsSheet: StateFlow<ChatStatsSheetUiState> = _chatStatsSheet.asStateFlow()

        private var responseStatsJob: Job? = null
        private var chatStatsJob: Job? = null

        fun onResponseStatsClick(
            messageId: MessageId,
            inMemoryTelemetryRows: List<TurnTelemetry> = emptyList(),
            scope: CoroutineScope,
        ) {
            responseStatsJob?.cancel()
            responseStatsJob =
                scope.launch {
                    observeMessageTelemetryUseCase(messageId).collect { persisted ->
                        val summary =
                            if (persisted.requestCount > 0) {
                                persisted
                            } else if (inMemoryTelemetryRows.isNotEmpty()) {
                                TelemetrySummaryBuilder.buildMessageSummary(
                                    messageId = messageId,
                                    rows = inMemoryTelemetryRows,
                                )
                            } else {
                                persisted
                            }
                        _responseStatsSheet.value =
                            mapper.mapResponseStats(summary).copy(
                                isVisible = true,
                                messageId = messageId,
                            )
                    }
                }
        }

        fun onChatStatsClick(
            chatId: ChatId,
            scope: CoroutineScope,
        ) {
            chatStatsJob?.cancel()
            chatStatsJob =
                scope.launch {
                    observeChatTelemetryUseCase(chatId).collect { summary ->
                        _chatStatsSheet.value = mapper.mapChatStats(summary).copy(isVisible = true)
                    }
                }
        }

        fun dismissResponseStats() {
            responseStatsJob?.cancel()
            _responseStatsSheet.update { it.copy(isVisible = false) }
        }

        fun dismissChatStats() {
            chatStatsJob?.cancel()
            _chatStatsSheet.update { it.copy(isVisible = false) }
        }
    }
