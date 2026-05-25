package com.browntowndev.enclave.core.data.remote

import com.browntowndev.enclave.core.data.local.MessageInferenceTelemetryEntity
import com.google.firebase.firestore.DocumentSnapshot

internal fun messageInferenceTelemetryToFirestoreMap(
    local: MessageInferenceTelemetryEntity,
): Map<String, Any?> =
    mapOf(
        "id" to local.id,
        "messageId" to local.messageId,
        "chatId" to local.chatId,
        "sequenceIndex" to local.sequenceIndex,
        "providerKey" to local.providerKey,
        "providerLabel" to local.providerLabel,
        "modelId" to local.modelId,
        "modelLabel" to local.modelLabel,
        "sourceKind" to local.sourceKind,
        "requestStartedAtMs" to local.requestStartedAtMs,
        "firstOutputTokenAtMs" to local.firstOutputTokenAtMs,
        "completedAtMs" to local.completedAtMs,
        "inputTokens" to local.inputTokens,
        "outputTokens" to local.outputTokens,
        "reasoningTokens" to local.reasoningTokens,
        "cachedInputTokens" to local.cachedInputTokens,
        "cacheReadInputTokens" to local.cacheReadInputTokens,
        "cacheWriteInputTokens" to local.cacheWriteInputTokens,
        "cacheCreationInputTokens" to local.cacheCreationInputTokens,
        "costUsdDecimal" to local.costUsdDecimal,
        "costStatus" to local.costStatus,
        "costSource" to local.costSource,
        "appVersionName" to local.appVersionName,
        "appVersionCode" to local.appVersionCode,
        "telemetrySchemaVersion" to local.telemetrySchemaVersion,
        "updated_at" to local.updatedAt,
        "deleted" to local.deleted,
    )

@Suppress("ComplexCondition")
internal fun messageInferenceTelemetryFromFirestore(doc: DocumentSnapshot): MessageInferenceTelemetryEntity? {
    val messageId = doc.getString("messageId")
    val chatId = doc.getString("chatId")
    val providerKey = doc.getString("providerKey")
    val providerLabel = doc.getString("providerLabel")
    val modelId = doc.getString("modelId")
    val modelLabel = doc.getString("modelLabel")
    val sourceKind = doc.getString("sourceKind")
    val requestStartedAtMs = doc.getLong("requestStartedAtMs")
    if (
        messageId.isNullOrBlank() ||
        chatId.isNullOrBlank() ||
        providerKey.isNullOrBlank() ||
        providerLabel.isNullOrBlank() ||
        modelId.isNullOrBlank() ||
        modelLabel.isNullOrBlank() ||
        sourceKind.isNullOrBlank() ||
        requestStartedAtMs == null
    ) {
        return null
    }

    return MessageInferenceTelemetryEntity(
        id = doc.id,
        messageId = messageId,
        chatId = chatId,
        sequenceIndex = doc.getLong("sequenceIndex")?.toInt() ?: 0,
        providerKey = providerKey,
        providerLabel = providerLabel,
        modelId = modelId,
        modelLabel = modelLabel,
        sourceKind = sourceKind,
        requestStartedAtMs = requestStartedAtMs,
        firstOutputTokenAtMs = doc.getLong("firstOutputTokenAtMs"),
        completedAtMs = doc.getLong("completedAtMs"),
        inputTokens = doc.getLong("inputTokens")?.toInt(),
        outputTokens = doc.getLong("outputTokens")?.toInt(),
        reasoningTokens = doc.getLong("reasoningTokens")?.toInt(),
        cachedInputTokens = doc.getLong("cachedInputTokens")?.toInt(),
        cacheReadInputTokens = doc.getLong("cacheReadInputTokens")?.toInt(),
        cacheWriteInputTokens = doc.getLong("cacheWriteInputTokens")?.toInt(),
        cacheCreationInputTokens = doc.getLong("cacheCreationInputTokens")?.toInt(),
        costUsdDecimal = doc.getString("costUsdDecimal"),
        costStatus = doc.getString("costStatus") ?: "UNAVAILABLE",
        costSource = doc.getString("costSource") ?: "NOT_AVAILABLE",
        appVersionName = doc.getString("appVersionName") ?: "unknown",
        appVersionCode = doc.getLong("appVersionCode") ?: 0L,
        telemetrySchemaVersion = doc.getLong("telemetrySchemaVersion")?.toInt() ?: 1,
        pendingSync = 0,
        updatedAt = doc.getLong("updated_at") ?: 0L,
        deleted = doc.getBoolean("deleted") ?: false,
    )
}
