package com.browntowndev.enclave.core.data.mapper.telemetry

import com.browntowndev.enclave.core.data.local.MessageInferenceTelemetryEntity
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.telemetry.TelemetryClientBuild
import com.browntowndev.enclave.domain.model.telemetry.TelemetryCostSource
import com.browntowndev.enclave.domain.model.telemetry.TelemetryCostStatus
import com.browntowndev.enclave.domain.model.telemetry.TelemetryProviderKey
import com.browntowndev.enclave.domain.model.telemetry.TelemetrySourceKind
import com.browntowndev.enclave.domain.model.telemetry.TurnTelemetry
import com.browntowndev.enclave.domain.model.telemetry.TurnTelemetryId
import java.math.BigDecimal

fun TurnTelemetry.toEntity(): MessageInferenceTelemetryEntity =
    MessageInferenceTelemetryEntity(
        id = id.value,
        messageId = messageId.value,
        chatId = chatId.value,
        sequenceIndex = sequenceIndex,
        providerKey = providerKey.name,
        providerLabel = providerLabel,
        modelId = modelId,
        modelLabel = modelLabel,
        sourceKind = sourceKind.name,
        requestStartedAtMs = requestStartedAtMs,
        firstOutputTokenAtMs = firstOutputTokenAtMs,
        completedAtMs = completedAtMs,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        reasoningTokens = reasoningTokens,
        cachedInputTokens = cachedInputTokens,
        cacheReadInputTokens = cacheReadInputTokens,
        cacheWriteInputTokens = cacheWriteInputTokens,
        cacheCreationInputTokens = cacheCreationInputTokens,
        costUsdDecimal = costUsd?.toPlainString(),
        costStatus = costStatus.name,
        costSource = costSource.name,
        appVersionName = clientBuild.appVersionName,
        appVersionCode = clientBuild.appVersionCode,
        telemetrySchemaVersion = clientBuild.telemetrySchemaVersion,
        pendingSync = 1,
    )

fun MessageInferenceTelemetryEntity.toDomain(): TurnTelemetry =
    TurnTelemetry(
        id = TurnTelemetryId(id),
        messageId = MessageId(messageId),
        chatId = ChatId(chatId),
        sequenceIndex = sequenceIndex,
        providerKey = TelemetryProviderKey.valueOf(providerKey),
        providerLabel = providerLabel,
        modelId = modelId,
        modelLabel = modelLabel,
        sourceKind = TelemetrySourceKind.valueOf(sourceKind),
        requestStartedAtMs = requestStartedAtMs,
        firstOutputTokenAtMs = firstOutputTokenAtMs,
        completedAtMs = completedAtMs,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        reasoningTokens = reasoningTokens,
        cachedInputTokens = cachedInputTokens,
        cacheReadInputTokens = cacheReadInputTokens,
        cacheWriteInputTokens = cacheWriteInputTokens,
        cacheCreationInputTokens = cacheCreationInputTokens,
        costUsd = costUsdDecimal?.let { BigDecimal(it) },
        costStatus = TelemetryCostStatus.valueOf(costStatus),
        costSource = TelemetryCostSource.valueOf(costSource),
        clientBuild =
            TelemetryClientBuild(
                appVersionName = appVersionName,
                appVersionCode = appVersionCode,
                telemetrySchemaVersion = telemetrySchemaVersion,
            ),
    )

fun MessageInferenceTelemetryEntity.toFirestoreFields(): Map<String, Any?> =
    mapOf(
        "id" to id,
        "messageId" to messageId,
        "chatId" to chatId,
        "sequenceIndex" to sequenceIndex,
        "providerKey" to providerKey,
        "providerLabel" to providerLabel,
        "modelId" to modelId,
        "modelLabel" to modelLabel,
        "sourceKind" to sourceKind,
        "requestStartedAtMs" to requestStartedAtMs,
        "firstOutputTokenAtMs" to firstOutputTokenAtMs,
        "completedAtMs" to completedAtMs,
        "inputTokens" to inputTokens,
        "outputTokens" to outputTokens,
        "reasoningTokens" to reasoningTokens,
        "cachedInputTokens" to cachedInputTokens,
        "cacheReadInputTokens" to cacheReadInputTokens,
        "cacheWriteInputTokens" to cacheWriteInputTokens,
        "cacheCreationInputTokens" to cacheCreationInputTokens,
        "costUsdDecimal" to costUsdDecimal,
        "costStatus" to costStatus,
        "costSource" to costSource,
        "appVersionName" to appVersionName,
        "appVersionCode" to appVersionCode,
        "telemetrySchemaVersion" to telemetrySchemaVersion,
    )

/**
 * Merges optional token/timing fields from [existing] when this entity (typically a remote doc) omits them.
 * Used for equal-timestamp Firestore sync so null remote columns cannot clobber local values.
 *
 * Field coverage must stay aligned with [com.browntowndev.enclave.domain.model.telemetry.TurnTelemetry.coalesceTokenFieldsWith].
 */
fun MessageInferenceTelemetryEntity.coalesceTokenFieldsWith(
    existing: MessageInferenceTelemetryEntity,
): MessageInferenceTelemetryEntity =
    copy(
        pendingSync = 0,
        firstOutputTokenAtMs = firstOutputTokenAtMs ?: existing.firstOutputTokenAtMs,
        completedAtMs = completedAtMs ?: existing.completedAtMs,
        inputTokens = inputTokens ?: existing.inputTokens,
        outputTokens = outputTokens ?: existing.outputTokens,
        reasoningTokens = reasoningTokens ?: existing.reasoningTokens,
        cachedInputTokens = cachedInputTokens ?: existing.cachedInputTokens,
        cacheReadInputTokens = cacheReadInputTokens ?: existing.cacheReadInputTokens,
        cacheWriteInputTokens = cacheWriteInputTokens ?: existing.cacheWriteInputTokens,
        cacheCreationInputTokens = cacheCreationInputTokens ?: existing.cacheCreationInputTokens,
        costUsdDecimal = costUsdDecimal ?: existing.costUsdDecimal,
        updatedAt = updatedAt,
    )
