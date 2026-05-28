package com.browntowndev.enclave.domain.telemetry

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
import java.time.Instant

object TelemetryTestFixtures {
    val chat1 = ChatId("chat-1")
    val chatOther = ChatId("chat-other")
    val mOpenAi1 = MessageId("m-openai-1")
    val mAnthropic1 = MessageId("m-anthropic-1")
    val mLitert1 = MessageId("m-litert-1")
    val mOpenRouter1 = MessageId("m-openrouter-1")
    val mLegacy1 = MessageId("m-legacy-1")
    val mAgent1 = MessageId("m-agent-1")

    private val clientBuild =
        TelemetryClientBuild(
            appVersionName = "1.4.0",
            appVersionCode = 140L,
            telemetrySchemaVersion = 1,
        )

    private fun instant(iso: String): Long = Instant.parse(iso).toEpochMilli()

    val telOpenAi1: TurnTelemetry =
        TurnTelemetry(
            id = TurnTelemetryId("tel-openai-1"),
            messageId = mOpenAi1,
            chatId = chat1,
            sequenceIndex = 0,
            providerKey = TelemetryProviderKey.OPENAI,
            providerLabel = "OpenAI",
            modelId = "gpt-4.1",
            modelLabel = "gpt-4.1",
            sourceKind = TelemetrySourceKind.REMOTE,
            requestStartedAtMs = instant("2026-05-10T13:59:25Z"),
            firstOutputTokenAtMs = instant("2026-05-10T13:59:25.4Z"),
            completedAtMs = instant("2026-05-10T13:59:29.4Z"),
            inputTokens = 1000,
            outputTokens = 200,
            reasoningTokens = 80,
            cachedInputTokens = null,
            cacheReadInputTokens = 400,
            cacheWriteInputTokens = 0,
            cacheCreationInputTokens = 0,
            costUsd = BigDecimal("0.002500"),
            costStatus = TelemetryCostStatus.EXACT,
            costSource = TelemetryCostSource.PROVIDER,
            clientBuild = clientBuild,
        )

    val telAnthropic1: TurnTelemetry =
        TurnTelemetry(
            id = TurnTelemetryId("tel-anthropic-1"),
            messageId = mAnthropic1,
            chatId = chat1,
            sequenceIndex = 0,
            providerKey = TelemetryProviderKey.ANTHROPIC,
            providerLabel = "Anthropic",
            modelId = "claude-sonnet-4-20250514",
            modelLabel = "claude-sonnet-4-20250514",
            sourceKind = TelemetrySourceKind.REMOTE,
            requestStartedAtMs = instant("2026-05-10T14:01:02Z"),
            firstOutputTokenAtMs = instant("2026-05-10T14:01:02.6Z"),
            completedAtMs = instant("2026-05-10T14:01:04.6Z"),
            inputTokens = 800,
            outputTokens = 100,
            reasoningTokens = 50,
            cachedInputTokens = null,
            cacheReadInputTokens = 0,
            cacheWriteInputTokens = 0,
            cacheCreationInputTokens = 300,
            costUsd = BigDecimal("0.003000"),
            costStatus = TelemetryCostStatus.ESTIMATED,
            costSource = TelemetryCostSource.SAVED_MODEL_PRICING,
            clientBuild = clientBuild,
        )

    val telLitert1: TurnTelemetry =
        TurnTelemetry(
            id = TurnTelemetryId("tel-litert-1"),
            messageId = mLitert1,
            chatId = chat1,
            sequenceIndex = 0,
            providerKey = TelemetryProviderKey.LITERT,
            providerLabel = "LiteRT",
            modelId = "DeepSeek-R1-0528-Qwen3-8B",
            modelLabel = "DeepSeek-R1-0528-Qwen3-8B",
            sourceKind = TelemetrySourceKind.LOCAL,
            requestStartedAtMs = instant("2026-05-10T14:03:42Z"),
            firstOutputTokenAtMs = instant("2026-05-10T14:03:42.2Z"),
            completedAtMs = instant("2026-05-10T14:03:44.2Z"),
            inputTokens = null,
            outputTokens = 60,
            reasoningTokens = null,
            cachedInputTokens = null,
            cacheReadInputTokens = null,
            cacheWriteInputTokens = null,
            cacheCreationInputTokens = null,
            costUsd = null,
            costStatus = TelemetryCostStatus.UNAVAILABLE,
            costSource = TelemetryCostSource.NOT_AVAILABLE,
            clientBuild = clientBuild,
        )

    val telOpenRouter1: TurnTelemetry =
        TurnTelemetry(
            id = TurnTelemetryId("tel-openrouter-1"),
            messageId = mOpenRouter1,
            chatId = chatOther,
            sequenceIndex = 0,
            providerKey = TelemetryProviderKey.OPENROUTER,
            providerLabel = "OpenRouter",
            modelId = "openrouter/deepseek-r1",
            modelLabel = "openrouter/deepseek-r1",
            sourceKind = TelemetrySourceKind.REMOTE,
            requestStartedAtMs = instant("2026-04-14T09:29:33Z"),
            firstOutputTokenAtMs = instant("2026-04-14T09:29:33.3Z"),
            completedAtMs = instant("2026-04-14T09:30:03Z"),
            inputTokens = 500,
            outputTokens = 80,
            reasoningTokens = 20,
            cachedInputTokens = null,
            cacheReadInputTokens = 100,
            cacheWriteInputTokens = 20,
            cacheCreationInputTokens = 0,
            costUsd = BigDecimal("0.001200"),
            costStatus = TelemetryCostStatus.EXACT,
            costSource = TelemetryCostSource.PROVIDER,
            clientBuild =
                clientBuild.copy(
                    appVersionName = "1.3.0",
                    appVersionCode = 130L,
                ),
        )

    val telAgent1: TurnTelemetry =
        TurnTelemetry(
            id = TurnTelemetryId("tel-agent-1"),
            messageId = mAgent1,
            chatId = chat1,
            sequenceIndex = 0,
            providerKey = TelemetryProviderKey.OPENAI,
            providerLabel = "OpenAI",
            modelId = "gpt-4.1",
            modelLabel = "gpt-4.1",
            sourceKind = TelemetrySourceKind.REMOTE,
            requestStartedAtMs = instant("2026-05-10T15:00:00Z"),
            firstOutputTokenAtMs = instant("2026-05-10T15:00:00.3Z"),
            completedAtMs = instant("2026-05-10T15:00:02.3Z"),
            inputTokens = null,
            outputTokens = 40,
            reasoningTokens = null,
            cachedInputTokens = null,
            cacheReadInputTokens = null,
            cacheWriteInputTokens = null,
            cacheCreationInputTokens = null,
            costUsd = BigDecimal("0.001000"),
            costStatus = TelemetryCostStatus.EXACT,
            costSource = TelemetryCostSource.PROVIDER,
            clientBuild = clientBuild,
        )

    val telAgent2: TurnTelemetry =
        telAgent1.copy(
            id = TurnTelemetryId("tel-agent-2"),
            sequenceIndex = 1,
            requestStartedAtMs = instant("2026-05-10T15:00:03Z"),
            firstOutputTokenAtMs = instant("2026-05-10T15:00:03.5Z"),
            completedAtMs = instant("2026-05-10T15:00:05.5Z"),
            outputTokens = 60,
            costUsd = BigDecimal("0.002000"),
        )

    val chat1Rows = listOf(telOpenAi1, telAnthropic1, telLitert1)

    val lifetimeRows = listOf(telOpenAi1, telAnthropic1, telLitert1, telOpenRouter1)
}
