package com.browntowndev.enclave.feature.chat.stats

import com.browntowndev.enclave.domain.model.chat.MessageId

data class ResponseMetricRowUi(
    val label: String,
    val value: String,
)

data class ResponseStatsSheetUiState(
    val isVisible: Boolean = false,
    val messageId: MessageId? = null,
    val title: String = "",
    val providerLabel: String = "",
    val modelLabel: String = "",
    val metricRows: List<ResponseMetricRowUi> = emptyList(),
    val availabilityMessage: String? = null,
)

data class SummaryMetricRowUi(
    val label: String,
    val value: String,
)

data class ProviderBreakdownRowUi(
    val label: String,
    val costLabel: String,
    val turnCount: Int,
    val requestCount: Int? = null,
)

data class ModelBreakdownRowUi(
    val label: String,
    val costLabel: String,
    val outputTokens: Int,
)

data class ChatStatsSheetUiState(
    val isVisible: Boolean = false,
    val title: String = "",
    val summaryRows: List<SummaryMetricRowUi> = emptyList(),
    val providerBreakdownRows: List<ProviderBreakdownRowUi> = emptyList(),
    val modelBreakdownRows: List<ModelBreakdownRowUi> = emptyList(),
)
