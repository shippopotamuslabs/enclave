package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.config.UtilityModelId
import com.browntowndev.enclave.domain.model.config.UtilityModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.DownloadWorkRequest
import com.browntowndev.enclave.domain.model.download.ModelPack
import com.browntowndev.enclave.domain.model.download.ModelPackCompleteness
import com.browntowndev.enclave.domain.model.telemetry.ChatTelemetrySummary
import com.browntowndev.enclave.domain.model.telemetry.MessageTelemetrySummary
import com.browntowndev.enclave.domain.model.telemetry.UsagePeriod
import com.browntowndev.enclave.domain.model.telemetry.UsageSpendSummary
import com.browntowndev.enclave.domain.port.download.DownloadWorkSchedulerPort
import com.browntowndev.enclave.domain.port.download.ModelPackVerifierPort
import com.browntowndev.enclave.domain.port.repository.InferenceTelemetryRepositoryPort
import com.browntowndev.enclave.domain.port.repository.LiveVoiceProviderRepositoryPort
import com.browntowndev.enclave.domain.port.repository.UtilityModelRepositoryPort
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory [LiveVoiceProviderRepositoryPort] fake (ENC-28), mirroring the TTS fake. */
class FakeLiveVoiceProviderRepository(
    initial: List<LiveVoiceProviderAsset> = emptyList(),
) : LiveVoiceProviderRepositoryPort {
    private val state = MutableStateFlow(initial)

    val savedCalls = mutableListOf<Pair<LiveVoiceProviderAsset, String?>>()
    val deletedIds = mutableListOf<LiveVoiceProviderId>()

    /** When set, [saveLiveVoiceProvider] returns this id instead of the asset's own id (ENC-28). */
    var saveResultId: LiveVoiceProviderId? = null

    /** Number of [getLiveVoiceProviders] calls so far (ENC-28). */
    var getLiveVoiceProvidersCallCount = 0
        private set

    fun emit(providers: List<LiveVoiceProviderAsset>) {
        state.value = providers
    }

    override fun getLiveVoiceProviders(): Flow<List<LiveVoiceProviderAsset>> {
        getLiveVoiceProvidersCallCount++
        return state.asStateFlow()
    }

    override suspend fun getLiveVoiceProvidersSync(): List<LiveVoiceProviderAsset> = state.value

    override suspend fun getLiveVoiceProvider(id: LiveVoiceProviderId): LiveVoiceProviderAsset? =
        state.value.firstOrNull { it.id == id }

    override suspend fun saveLiveVoiceProvider(asset: LiveVoiceProviderAsset, apiKey: String?): LiveVoiceProviderId {
        savedCalls += asset to apiKey
        state.value = state.value.filterNot { it.id == asset.id } + asset
        return saveResultId ?: asset.id
    }

    override suspend fun deleteLiveVoiceProvider(id: LiveVoiceProviderId) {
        deletedIds += id
        state.value = state.value.filterNot { it.id == id }
    }
}

/** Recording [DownloadWorkSchedulerPort] fake (ENC-28). */
class FakeDownloadWorkSchedulerPort : DownloadWorkSchedulerPort {
    val enqueued = mutableListOf<DownloadWorkRequest>()
    var cancelCount = 0
    var cleanupCount = 0

    override fun enqueue(request: DownloadWorkRequest) {
        enqueued += request
    }

    override fun cancel() {
        cancelCount++
    }

    override suspend fun cleanupTempFiles() {
        cleanupCount++
    }
}

/** Minimal [ModelPackVerifierPort] fake (ENC-28): reports packs complete unless told otherwise. */
class FakeModelPackVerifierPort : ModelPackVerifierPort {
    var completeness: ModelPackCompleteness =
        ModelPackCompleteness(packId = "", installedCount = 0, totalCount = 0, isComplete = true, members = emptyList())

    /** Per-pack completeness overrides, keyed by packId; falls back to [completeness] (ENC-28). */
    val completenessByPackId = mutableMapOf<String, ModelPackCompleteness>()

    override suspend fun verifyPack(
        pack: ModelPack,
        depth: com.browntowndev.enclave.domain.model.download.ModelPackVerificationDepth,
    ): ModelPackCompleteness = completenessByPackId[pack.packId] ?: completeness

    override suspend fun verifyPackById(
        catalog: com.browntowndev.enclave.domain.model.download.ParsedModelCatalog,
        packId: String,
        depth: com.browntowndev.enclave.domain.model.download.ModelPackVerificationDepth,
    ): ModelPackCompleteness? = completenessByPackId[packId] ?: completeness
}

/** Flow-backed [InferenceTelemetryRepositoryPort] fake (ENC-28). */
class FakeInferenceTelemetryRepositoryPort : InferenceTelemetryRepositoryPort {
    var usageSpendSummary: UsageSpendSummary? = null

    override fun observeMessageTelemetry(messageId: MessageId): Flow<MessageTelemetrySummary> =
        MutableStateFlow(
            MessageTelemetrySummary(
                messageId = messageId,
                requestCount = 0,
                providerLabel = null,
                modelLabel = null,
                totalCostUsd = null,
                costStatus = com.browntowndev.enclave.domain.model.telemetry.TelemetryCostStatus.UNAVAILABLE,
                averageTtftMs = null,
                averageOutputTokensPerSecond = null,
                totalInputTokens = null,
                totalInputTokensNormalized = null,
                billingInputTokensNormalized = null,
                totalOutputTokens = null,
                totalReasoningTokens = null,
                peakCacheReadInputTokens = null,
                totalCacheReadInputTokens = null,
                peakCacheWriteInputTokens = null,
                totalCacheWriteInputTokens = null,
                cacheHitRatePercent = null,
                requestRows = emptyList(),
            ),
        )

    override fun observeChatTelemetry(chatId: ChatId): Flow<ChatTelemetrySummary> =
        MutableStateFlow(
            ChatTelemetrySummary(
                chatId = chatId,
                turnCount = 0,
                telemetryBackedTurnCount = 0,
                missingTelemetryTurnCount = 0,
                totalCostUsd = java.math.BigDecimal.ZERO,
                averageTtftMs = null,
                averageOutputTokensPerSecond = null,
                totalInputTokens = 0,
                peakInputTokensNormalized = 0,
                totalInputTokensNormalized = 0,
                totalOutputTokens = 0,
                totalReasoningTokens = 0,
                peakCacheReadInputTokens = 0,
                totalCacheReadInputTokens = 0,
                peakCacheWriteInputTokens = 0,
                totalCacheWriteInputTokens = 0,
                cacheHitRatePercent = null,
                providerBreakdown = emptyList(),
                modelBreakdown = emptyList(),
            ),
        )

    override fun observeUsageSpendSummary(period: UsagePeriod): Flow<UsageSpendSummary> =
        MutableStateFlow(
            usageSpendSummary
                ?: UsageSpendSummary(
                    period = period,
                    periodStartUtcMs = null,
                    periodEndUtcMs = null,
                    totalCostUsd = java.math.BigDecimal.ZERO,
                    telemetryBackedTurnCount = 0,
                    missingCostTurnCount = 0,
                    providerBreakdown = emptyList(),
                ),
        )
}
