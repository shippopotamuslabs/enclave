# FEAT-inference-telemetry-stats

## Feature Summary
Add model/provider telemetry presentation in three surfaces without violating Clean Architecture:

1. Each assistant response gets a stats action that opens per-response telemetry; live responses show the fields already known and render unfinished fields as `Not available`.
2. The chat top bar replaces the direct new-chat icon with a `MoreVert` menu containing `New chat` and `View chat stats`.
3. Settings gains a `Usage & Spend` entry that opens lifetime/current-month provider spend and usage summaries.
4. Maintainers can aggregate synced telemetry across users to detect provider/model regressions after app releases, such as a provider cache-hit-rate drop after an update.

The feature only works end-to-end if telemetry survives rehydrate and can be aggregated across messages. The current codebase has no persisted telemetry contract in `InferenceEvent`, `MessageGenerationState`, `MessageSnapshot`, `Message`, or Room. Because of that, the preferred implementation requires explicit developer authorization for a new persisted telemetry schema.

## Required Decision Gate
Implementation may not start until the developer explicitly authorizes a schema change for durable telemetry storage.

Reason:
- Response-level stats must remain visible after Room rehydrate.
- Whole-chat stats must aggregate historical assistant turns, not only the active snapshot.
- Settings lifetime/current-month spend requires querying historical telemetry across chats.
- A no-schema approach can only cover the in-flight turn via `ActiveChatTurnSnapshotPort`, which does not satisfy the requested feature set.

Preferred storage decision:
- Add one child table and synced child collection: `message_inference_telemetry`.
- Store one telemetry row per model request that contributes to an assistant message.
- Aggregate from the row set by `message_id`, `chat_id`, provider, model, and time period; do not introduce a second aggregate table.

Rejected fallback:
- In-memory only or snapshot-only telemetry.
- Reason: fails response reload, chat totals after app restart, and settings usage/spend.

## File Structure

### Production Files

| Action | File | Responsibility |
|---|---|---|
| Modify | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/chat/Message.kt` | Keep message content/state unchanged except for any compile-required constructor compatibility; telemetry remains a separate read model and is not embedded in `Message`. |
| Modify | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/chat/ChatTurnSnapshots.kt` | Carry live telemetry through `MessageSnapshot` so in-flight sheets can render before DB handoff. |
| Modify | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/chat/MessageGenerationState.kt` | Add a terminal-safe telemetry update state emitted before `Finished`. |
| Modify | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/inference/AgentLoopModels.kt` | Extend `AgentModelTurnResult` with normalized per-turn telemetry captured by provider adapters. |
| Modify | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/port/inference/LlmInferencePort.kt` | Add `InferenceEvent.TelemetryCaptured`. |
| Modify | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/port/repository/ChatRepository.kt` | Extend atomic message persistence contract to accept telemetry for the same assistant turn. |
| Modify | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/DirectChatInferenceExecutor.kt` | Bridge `InferenceEvent.TelemetryCaptured` into `MessageGenerationState.TelemetryCaptured`. |
| Modify | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/ChatGenerationAccumulatorManager.kt` | Accumulate telemetry per assistant message and expose it in snapshots and final persistence. |
| Modify | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/PersistAccumulatedChatMessagesUseCase.kt` | Persist telemetry atomically with message content/state. |
| Create | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/telemetry/TurnTelemetry.kt` | Unified per-turn telemetry model and enums for provider, cost source, and availability. |
| Create | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/telemetry/MessageTelemetrySummary.kt` | Per-assistant-response aggregate over one or more model-request telemetry rows. |
| Create | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/telemetry/ChatTelemetrySummary.kt` | Whole-chat aggregates and provider/model breakdowns. |
| Create | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/telemetry/UsageSpendSummary.kt` | Settings-level totals and provider spend rows for a selected period. |
| Create | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/telemetry/UsagePeriod.kt` | `LIFETIME` and `CURRENT_MONTH` selection contract. |
| Create | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/telemetry/TelemetryClientBuild.kt` | App version and telemetry schema version captured with every telemetry row. |
| Create | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/port/repository/InferenceTelemetryRepositoryPort.kt` | Read-side telemetry queries for message, chat, and settings surfaces. |
| Create | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/telemetry/ObserveMessageTelemetryUseCase.kt` | ViewModel-facing message detail observer. |
| Create | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/telemetry/ObserveChatTelemetryUseCase.kt` | ViewModel-facing chat summary observer. |
| Create | `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/telemetry/ObserveUsageSpendSummaryUseCase.kt` | Settings observer for lifetime/current-month totals. |
| Create | `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/local/MessageInferenceTelemetryEntity.kt` | Room row keyed by `id` with a `message_id` FK plus provider/model/cost/token/timing fields. |
| Create | `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/local/MessageInferenceTelemetryDao.kt` | Detail and aggregate queries by message, chat, provider, model, and period. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/local/EnclaveDatabase.kt` | Register the telemetry entity and DAO; bump the schema version. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/local/EnclaveDatabaseMigrations.kt` | Document the version bump and the destructive-migration implication explicitly. |
| Create | `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/mapper/telemetry/MessageInferenceTelemetryMappers.kt` | Map domain telemetry records to Room/Firestore shapes and back. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/repository/ChatRepositoryImpl.kt` | Persist telemetry in the same transaction as assistant message content/state. |
| Create | `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/repository/InferenceTelemetryRepositoryImpl.kt` | Implement the read-side aggregate repository port. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/DataModule.kt` | Bind the new repository and provide the DAO. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/local/CleanupDao.kt` | Include telemetry rows in local cleanup flows if cascade coverage is incomplete. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/firestore/FirestoreSyncDependencyGraph.kt` | Register telemetry as a message child entity for sync ordering. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/firestore/FirestoreSyncPendingSyncLookup.kt` | Expose telemetry pending-sync state to the sync engine. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/firestore/ChatSyncDaos.kt` | Inject the telemetry DAO into Firestore sync context wiring. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/firestore/FirestoreSyncAdapterRegistry.kt` | Register the telemetry sync handler. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/FirestoreSensitiveFieldRefMapping.kt` | Register the new synced collection path if sensitive-field mapping requires an explicit collection owner entry. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/firestore/FirestoreCloudPurgeClient.kt` | Purge telemetry collection data during cloud wipe flows. |
| Create | `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/firestore/handlers/MessageInferenceTelemetrySyncHandler.kt` | Sync telemetry rows to and from Firestore. |
| Create | `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/firestore/mappers/MessageInferenceTelemetryFirestoreMapper.kt` | Map Firestore docs to the telemetry entity. |
| Modify | `functions/src/index.ts` | Export telemetry rollup Cloud Function entry points. |
| Create | `functions/src/telemetry/providerDailyRollup.ts` | Scheduled/admin aggregation of raw telemetry docs into provider/model/app-version daily rollups. |
| Create | `functions/src/telemetry/providerDailyRollup.test.ts` | Unit tests for cache-hit-rate and release-version rollup math. |
| Create | `functions/src/telemetry/providerDailyRollupTypes.ts` | Shared TypeScript shapes for raw telemetry docs and rollup docs. |
| Create | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/telemetry/ProviderTelemetryNormalizer.kt` | Normalize provider-specific usage/cost fields into the shared domain telemetry model. |
| Create | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/telemetry/ProviderCostEstimator.kt` | Versioned local estimation for providers that do not return final request cost. |
| Create | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/telemetry/ProviderPricingCatalog.kt` | Versioned pricing table used by the estimator. |
| Modify | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/BaseOpenAiSdkInferenceService.kt` | Capture OpenAI/xAI/OpenRouter-compatible timings and normalized usage/cost output. |
| Modify | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/openai/StreamState.kt` | Retain final OpenAI Responses usage/cost/cache fields until stream completion. |
| Modify | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/openai/OpenAiResponseStreamHandler.kt` | Populate `StreamedOpenAiResponse` telemetry payload from final stream events. |
| Modify | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/AnthropicInferenceServiceImpl.kt` | Capture Anthropic cache read/create fields, token counts, and estimated cost. |
| Modify | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/OpenRouterInferenceServiceImpl.kt` | Capture OpenRouter exact cost, cache read/write fields, and generation audit data. |
| Modify | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/GoogleInferenceServiceImpl.kt` | Emit timing and token telemetry; cost stays unavailable unless the SDK exposes it. |
| Modify | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/RustLoopInferenceService.kt` | Bridge inner `AgentModelTurnResult.telemetry` to outward `InferenceEvent.TelemetryCaptured`. |
| Modify | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/LiteRtInferenceServiceImpl.kt` | Measure request start, first visible token, completion time, output token count, and tokens/sec client-side. |
| Modify | `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/LlamaInferenceServiceImpl.kt` | Measure the same local telemetry for llama.cpp-backed generations. |
| Modify | `feature/chat/src/main/kotlin/com/browntowndev/enclave/feature/chat/ChatModels.kt` | Add UI-state contracts for response sheet and chat sheet data. |
| Modify | `feature/chat/src/main/kotlin/com/browntowndev/enclave/feature/chat/mapping/ChatMessageUiMapper.kt` | Preserve message mapping without loading telemetry; the response stats sheet observes telemetry by selected `messageId`. |
| Modify | `feature/chat/src/main/kotlin/com/browntowndev/enclave/feature/chat/components/assistantresponse/AssistantResponse.kt` | Thread a stats action callback to the final message action row. |
| Modify | `feature/chat/src/main/kotlin/com/browntowndev/enclave/feature/chat/components/assistantresponse/AssistantResponseFinalMessage.kt` | Pass response telemetry callback into the action row. |
| Modify | `feature/chat/src/main/kotlin/com/browntowndev/enclave/feature/chat/components/assistantresponse/AssistantResponseActionRow.kt` | Add the response stats icon and click handling. |
| Modify | `feature/chat/src/main/kotlin/com/browntowndev/enclave/feature/chat/components/messagelist/MessageList.kt` | Thread stats callbacks down to assistant rows. |
| Modify | `feature/chat/src/main/kotlin/com/browntowndev/enclave/feature/chat/components/messagelist/MessageListItem.kt` | Thread stats callbacks down to assistant rows. |
| Modify | `feature/chat/src/main/kotlin/com/browntowndev/enclave/feature/chat/ChatTopBar.kt` | Replace the direct new-chat icon with `MoreVert` and a popup menu. |
| Modify | `feature/chat/src/main/kotlin/com/browntowndev/enclave/feature/chat/ChatScreen.kt` | Host the response stats and chat stats bottom sheets. |
| Modify | `feature/chat/src/main/kotlin/com/browntowndev/enclave/feature/chat/ChatRoute.kt` | Wire new callbacks from `ChatViewModel` to `ChatScreen`. |
| Modify | `feature/chat/src/main/kotlin/com/browntowndev/enclave/feature/chat/ChatViewModel.kt` | Observe telemetry use cases, hold selected sheet state, and expose mapped UI models. |
| Create | `feature/chat/src/main/kotlin/com/browntowndev/enclave/feature/chat/stats/ChatTelemetryUiModels.kt` | Chat feature presentation models for response detail rows and chat summary sections. |
| Create | `feature/chat/src/main/kotlin/com/browntowndev/enclave/feature/chat/stats/ChatTelemetryUiMapper.kt` | Map domain summaries to stable chat UI models. |
| Create | `feature/chat/src/main/kotlin/com/browntowndev/enclave/feature/chat/stats/ResponseStatsBottomSheet.kt` | Bottom sheet for per-response telemetry detail. |
| Create | `feature/chat/src/main/kotlin/com/browntowndev/enclave/feature/chat/stats/ChatStatsBottomSheet.kt` | Bottom sheet for whole-chat aggregates and provider/model breakdown. |
| Modify | `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/SettingsModels.kt` | Add settings presentation models for usage/spend sheet state and period selection. |
| Modify | `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/SettingsUiStateFactory.kt` | Combine telemetry summary flow into `SettingsUiState`. |
| Modify | `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/SettingsViewModel.kt` | Manage sheet visibility and period changes; expose usage/spend UI state. |
| Modify | `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/SettingsRoute.kt` | Wire usage/spend callbacks from the ViewModel to the screen. |
| Modify | `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/SettingsScreen.kt` | Add the `Usage & Spend` row and host the bottom sheet. |
| Create | `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/usage/UsageSpendUiMapper.kt` | Map domain summaries to settings UI rows. |
| Create | `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/usage/UsageSpendBottomSheet.kt` | Render lifetime/current-month totals and provider breakdown rows. |

### Test Files

| Action | File | Responsibility |
|---|---|---|
| Create | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/telemetry/ObserveMessageTelemetryUseCaseTest.kt` | Message-detail summary shape and zero-state handling. |
| Create | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/telemetry/ObserveChatTelemetryUseCaseTest.kt` | Chat aggregate math and breakdown ordering. |
| Create | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/telemetry/ObserveUsageSpendSummaryUseCaseTest.kt` | Lifetime/current-month filtering and provider totals. |
| Create | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/chat/DirectChatInferenceExecutorTelemetryTest.kt` | `InferenceEvent.TelemetryCaptured` to `MessageGenerationState.TelemetryCaptured` bridging. |
| Create | `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/chat/GenerateChatResponseUseCaseTelemetryPersistenceTest.kt` | Active snapshot + final persistence handoff for telemetry. |
| Create | `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/local/MessageInferenceTelemetryDaoTest.kt` | DAO detail/aggregate query correctness. |
| Create | `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/repository/ChatRepositoryImplTelemetryPersistTest.kt` | Atomic assistant-message plus telemetry persistence. |
| Create | `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/repository/InferenceTelemetryRepositoryImplTest.kt` | Domain summary mapping, period filtering, and missing-cost handling. |
| Create | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/OpenAiTelemetryCaptureTest.kt` | OpenAI/xAI/OpenRouter-compatible usage normalization and exact cost capture. |
| Create | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/AnthropicTelemetryCaptureTest.kt` | Anthropic cache field capture and estimated-cost labeling. |
| Create | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/LocalInferenceTelemetryTest.kt` | LiteRT/llama local TTFT and output speed measurement. |
| Create | `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/RustLoopInferenceTelemetryBridgeTest.kt` | Inner `AgentModelTurnResult.telemetry` surfaces as `InferenceEvent.TelemetryCaptured`. |
| Create | `feature/chat/src/test/kotlin/com/browntowndev/enclave/feature/chat/ChatViewModelTelemetryTest.kt` | Response/chat sheet selection and mapped UI state. |
| Create | `feature/chat/src/test/kotlin/com/browntowndev/enclave/feature/chat/components/assistantresponse/AssistantResponseActionRowTelemetryTest.kt` | Stats icon visibility and click semantics for assistant responses. |
| Create | `feature/chat/src/test/kotlin/com/browntowndev/enclave/feature/chat/ChatTopBarMenuTest.kt` | `MoreVert` menu contents and action dispatch order. |
| Create | `feature/settings/src/test/kotlin/com/browntowndev/enclave/feature/settings/SettingsUsageSpendUiStateFactoryTest.kt` | Usage/spend sheet state composition and period switching. |
| Create | `feature/settings/src/test/kotlin/com/browntowndev/enclave/feature/settings/SettingsUsageSpendScreenTest.kt` | `Usage & Spend` row and bottom sheet rendering. |

## API Contracts And Interfaces

### Domain Telemetry Model

`TurnTelemetry` is the single normalized contract that every provider writes and every UI surface reads. It represents one model request, not necessarily one visible assistant response.

Required fields:
- `id: TurnTelemetryId`
- `messageId: MessageId`
- `chatId: ChatId`
- `sequenceIndex: Int`
- `providerKey: TelemetryProviderKey`
- `providerLabel: String`
- `modelId: String`
- `modelLabel: String`
- `sourceKind: TelemetrySourceKind`
- `requestStartedAtMs: Long`
- `firstOutputTokenAtMs: Long?`
- `completedAtMs: Long?`
- `inputTokens: Int?`
- `outputTokens: Int?`
- `reasoningTokens: Int?`
- `cachedInputTokens: Int?`
- `cacheReadInputTokens: Int?`
- `cacheWriteInputTokens: Int?`
- `cacheCreationInputTokens: Int?`
- `costUsd: BigDecimal?`
- `costStatus: TelemetryCostStatus`
- `costSource: TelemetryCostSource`
- `clientBuild: TelemetryClientBuild`

Derived values computed in domain/data read models, not stored as separate authoritative fields:
- `ttftMs = firstOutputTokenAtMs - requestStartedAtMs`
- `generationDurationMs = completedAtMs - firstOutputTokenAtMs`
- `outputTokensPerSecond = outputTokens / generationDurationSeconds`
- `cacheHitRate = totalCacheReadInputTokens / totalInputTokens`
- `response-level metrics = aggregate rows grouped by messageId`

Availability rules:
- Missing numeric provider fields remain `null`; the UI renders `Not available`.
- Missing cost fields render as `Unknown` in cost and spend breakdown rows. They must not render as `$0.000000`.
- Local providers set `costUsd = null`, `costStatus = UNAVAILABLE`, `costSource = NOT_AVAILABLE`.
- OpenAI and Anthropic may set `costStatus = ESTIMATED` when cost comes from the local pricing catalog rather than the provider response.
- xAI and OpenRouter use `EXACT` whenever the provider returns final request cost.
- Cost aggregation uses `BigDecimal` arithmetic internally. Formatting to fixed 6-decimal USD strings happens only in UI mappers.

`TelemetryClientBuild`
- `appVersionName: String`
- `appVersionCode: Long`
- `telemetrySchemaVersion: Int`

Client build rules:
- Every locally generated telemetry row stores the app version and telemetry schema version active at request time.
- Synced Firestore telemetry docs include these fields so server-side rollups can group by release.
- If a row is restored from older clients without build fields, rollup code groups it under `unknown` version labels rather than dropping it.

### Message-Level Summary Contract

`MessageTelemetrySummary`
- `messageId: MessageId`
- `requestCount: Int`
- `providerLabel: String?`
- `modelLabel: String?`
- `totalCostUsd: BigDecimal?`
- `costStatus: TelemetryCostStatus`
- `averageTtftMs: Long?`
- `averageOutputTokensPerSecond: Double?`
- `totalInputTokens: Int?`
- `totalOutputTokens: Int?`
- `totalReasoningTokens: Int?`
- `totalCacheReadInputTokens: Int?`
- `totalCacheWriteInputTokens: Int?`
- `cacheHitRatePercent: Double?`
- `requestRows: List<TurnTelemetry>`

Message aggregate rules:
- `requestCount` is the number of telemetry rows for the message.
- Cost sums only rows with `costUsd != null`; if every row has missing cost, `totalCostUsd = null` and the UI renders `Unknown`.
- `costStatus` is `EXACT` only when every cost-bearing row is exact, `ESTIMATED` when at least one cost-bearing row is estimated, and `UNAVAILABLE` when no row has cost.
- Provider/model labels use the single label when all rows agree. If rows differ, the UI label is `Multiple providers` or `Multiple models`.
- Response speed uses weighted throughput across rows that have both output tokens and generation duration.

### Chat-Level Summary Contract

`ChatTelemetrySummary`
- `chatId: ChatId`
- `turnCount: Int`
- `telemetryBackedTurnCount: Int`
- `missingTelemetryTurnCount: Int`
- `totalCostUsd: BigDecimal`
- `averageTtftMs: Long?`
- `averageOutputTokensPerSecond: Double?`
- `totalInputTokens: Int`
- `totalOutputTokens: Int`
- `totalReasoningTokens: Int`
- `totalCacheReadInputTokens: Int`
- `totalCacheWriteInputTokens: Int`
- `cacheHitRatePercent: Double?`
- `providerBreakdown: List<ProviderTelemetryBreakdown>`
- `modelBreakdown: List<ModelTelemetryBreakdown>`

Aggregate rules:
- `totalCostUsd` sums only rows with `costUsd != null`.
- `missingTelemetryTurnCount` counts assistant turns with no telemetry row.
- Provider/model `turnCount` counts distinct assistant message IDs, not telemetry rows.
- Provider/model `requestCount` counts telemetry rows.
- `averageTtftMs` averages only telemetry rows with non-null TTFT.
- `averageOutputTokensPerSecond` uses weighted throughput: `sum(outputTokens) / sum(generationDurationSeconds)` across rows that have both values.
- `cacheHitRatePercent` uses `sum(cacheReadInputTokens) / sum(inputTokens) * 100` across rows with non-null input tokens.
- Provider/model breakdown rows with no cost-bearing telemetry use cost label `Unknown`, not `$0.000000`.

### Settings-Level Summary Contract

`UsageSpendSummary`
- `period: UsagePeriod`
- `periodStartUtcMs: Long?`
- `periodEndUtcMs: Long?`
- `totalCostUsd: BigDecimal`
- `telemetryBackedTurnCount: Int`
- `missingCostTurnCount: Int`
- `providerBreakdown: List<ProviderUsageSpendRow>`

`UsagePeriod`
- `LIFETIME`
- `CURRENT_MONTH`

Period rules:
- `LIFETIME` includes every persisted telemetry row.
- `CURRENT_MONTH` includes rows whose `completedAtMs` falls between the first and last millisecond of the device's current calendar month.
- Month boundaries are computed from an injected clock and injected device `ZoneId`; tests must fix both current instant and zone.

### Fleet Analytics Rollup Contract

Raw `message_inference_telemetry` docs are not queried live for dashboards. Cloud Functions aggregates them into daily provider rollup docs.

Rollup document path:
- `users/{uid}/telemetry_rollups/provider_daily/{yyyyMMdd_provider_model_appVersionCode}`

Rollup fields:
- `dateUtc: String`
- `providerKey: String`
- `providerLabel: String`
- `modelId: String`
- `modelLabel: String`
- `appVersionName: String`
- `appVersionCode: Long?`
- `telemetrySchemaVersion: Int?`
- `requestCount: Long`
- `turnCount: Long`
- `inputTokens: Long`
- `outputTokens: Long`
- `reasoningTokens: Long`
- `cacheReadInputTokens: Long`
- `cacheWriteInputTokens: Long`
- `cacheCreationInputTokens: Long`
- `costUsdDecimal: String`
- `ttftSampleCount: Long`
- `ttftMsTotal: Long`
- `generationDurationMsTotal: Long`

Derived dashboard values:
- `cacheHitRatePercent = cacheReadInputTokens / inputTokens * 100`
- `averageTtftMs = ttftMsTotal / ttftSampleCount`
- `weightedOutputTokensPerSecond = outputTokens / (generationDurationMsTotal / 1000)`

Rollup rules:
- Rollups are admin/server-only outputs, not user-facing UI state.
- Rollups group by UTC date, provider, model, app version, and telemetry schema version.
- Missing cost contributes no cost and increments request/turn/token counts normally.
- Missing app version groups under `appVersionName = "unknown"` and `appVersionCode = null`.
- A cache-hit regression investigation compares rollups before and after an app release by `providerKey`, `modelId`, and `appVersionCode`.

### Repository Ports

Read side:
- `InferenceTelemetryRepositoryPort.observeMessageTelemetry(messageId: MessageId): Flow<MessageTelemetrySummary>`
- `InferenceTelemetryRepositoryPort.observeChatTelemetry(chatId: ChatId): Flow<ChatTelemetrySummary>`
- `InferenceTelemetryRepositoryPort.observeUsageSpendSummary(period: UsagePeriod): Flow<UsageSpendSummary>`

Write side:
- `ChatRepository.persistAllMessageData(..., telemetry: List<TurnTelemetry>)`

### UI Presentation Contracts

Chat feature:
- `ResponseStatsSheetUiState`
  - `isVisible: Boolean`
  - `messageId: MessageId?`
  - `title: String`
  - `providerLabel: String`
  - `modelLabel: String`
  - `metricRows: List<ResponseMetricRowUi>`
  - `availabilityMessage: String?`
- `ChatStatsSheetUiState`
  - `isVisible: Boolean`
  - `summaryRows: List<SummaryMetricRowUi>`
  - `providerBreakdownRows: List<ProviderBreakdownRowUi>`
  - `modelBreakdownRows: List<ModelBreakdownRowUi>`

Settings feature:
- `UsageSpendSheetUiState`
  - `isVisible: Boolean`
  - `selectedPeriod: UsagePeriodUi`
  - `totalCostLabel: String`
  - `providerRows: List<UsageSpendProviderRowUi>`
  - `emptyMessage: String?`

Formatting rules:
- Known cost displays use fixed 6-decimal USD strings such as `$0.002500`; unknown cost displays as `Unknown`.
- TTFT displays as whole milliseconds such as `400 ms`.
- Output speed displays as one decimal place such as `45.0 tok/s`.
- Any unavailable field displays exactly `Not available`.

## Data Model And Schema Changes

Preferred schema:
- New Room entity and synced Firestore child collection: `message_inference_telemetry`
- Primary key: `id`
- Foreign key: `message_id -> message.id`
- Indexed fields: `message_id`, `chat_id`, `provider_key`, `model_id`, `completed_at_ms`

Stored columns:
- `id`
- `message_id`
- `chat_id`
- `sequence_index`
- `provider_key`
- `provider_label`
- `model_id`
- `model_label`
- `source_kind`
- `request_started_at_ms`
- `first_output_token_at_ms`
- `completed_at_ms`
- `input_tokens`
- `output_tokens`
- `reasoning_tokens`
- `cached_input_tokens`
- `cache_read_input_tokens`
- `cache_write_input_tokens`
- `cache_creation_input_tokens`
- `cost_usd_decimal`
- `cost_status`
- `cost_source`
- `app_version_name`
- `app_version_code`
- `telemetry_schema_version`
- `pending_sync`
- `updated_at`
- `deleted`

No new transient UI columns are introduced. The telemetry row is durable domain data, not UI state. Provider/model/cost/token/timing telemetry is acceptable as plaintext synced metadata; it is not registered as sensitive content unless a later security review changes that policy.

Room/upgrade implications:
- `EnclaveDatabase` must bump from version `1`.
- The app currently relies on `fallbackToDestructiveMigration`. If maintainers do not accept destructive migration for telemetry, implementation must stop for a separate migration decision before any schema code lands.

Sync decision:
- Preferred plan syncs telemetry like other message child data so stats survive device changes and cloud restore.
- If maintainers explicitly waive sync and accept local-device-only stats, scenarios `TEL-010` and `TEL-011` must be rewritten before implementation begins.

## Ordered Implementation Tasks

1. Authorize the durable telemetry schema and destructive-migration posture.
   - Files: none in code yet; this is the hard gate.
   - Outcome: explicit approval to add `message_inference_telemetry` and accept the Room version decision.
   - Scenarios: `TEL-010`, `TEL-011`

2. Add the shared domain telemetry contract and generation event path.
   - Files: `TurnTelemetry.kt`, `MessageTelemetrySummary.kt`, `ChatTelemetrySummary.kt`, `UsageSpendSummary.kt`, `UsagePeriod.kt`, `Message.kt`, `ChatTurnSnapshots.kt`, `MessageGenerationState.kt`, `AgentLoopModels.kt`, `LlmInferencePort.kt`, `DirectChatInferenceExecutor.kt`, `ChatGenerationAccumulatorManager.kt`, `PersistAccumulatedChatMessagesUseCase.kt`, `ChatRepository.kt`
   - Outcome: provider adapters can emit normalized per-request telemetry, the accumulator can hold multiple telemetry rows for one assistant message, and message persistence accepts those rows atomically.
   - Scenarios: `TEL-001`, `TEL-002`, `TEL-003`, `TEL-005`, `TEL-009`, `TEL-010`, `TEL-013`

3. Capture provider and local-engine telemetry in `feature/inference`.
   - Files: `ProviderTelemetryNormalizer.kt`, `ProviderCostEstimator.kt`, `ProviderPricingCatalog.kt`, `BaseOpenAiSdkInferenceService.kt`, `openai/StreamState.kt`, `openai/OpenAiResponseStreamHandler.kt`, `AnthropicInferenceServiceImpl.kt`, `OpenRouterInferenceServiceImpl.kt`, `GoogleInferenceServiceImpl.kt`, `RustLoopInferenceService.kt`, `LiteRtInferenceServiceImpl.kt`, `LlamaInferenceServiceImpl.kt`
   - Outcome: OpenAI/xAI/OpenRouter exact usage where available, Anthropic estimated cost with cache fields, Google/local timing metrics, and Rust-loop bridging for tool-capable providers.
   - Scenarios: `TEL-001`, `TEL-002`, `TEL-005`, `TEL-008`, `TEL-009`, `TEL-013`

4. Persist telemetry and expose aggregate read models in `core/data`.
   - Files: `MessageInferenceTelemetryEntity.kt`, `MessageInferenceTelemetryDao.kt`, `EnclaveDatabase.kt`, `EnclaveDatabaseMigrations.kt`, `MessageInferenceTelemetryMappers.kt`, `ChatRepositoryImpl.kt`, `InferenceTelemetryRepositoryImpl.kt`, `DataModule.kt`, `CleanupDao.kt`, `ChatSyncDaos.kt`, `FirestoreSyncAdapterRegistry.kt`, `FirestoreSyncDependencyGraph.kt`, `FirestoreSyncPendingSyncLookup.kt`, `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/FirestoreSensitiveFieldRefMapping.kt`, `FirestoreCloudPurgeClient.kt`, `MessageInferenceTelemetrySyncHandler.kt`, `MessageInferenceTelemetryFirestoreMapper.kt`
   - Outcome: assistant content/state and telemetry persist together; chat/settings read models can query detail, chat aggregate, and period aggregate summaries.
   - Scenarios: `TEL-003`, `TEL-005`, `TEL-006`, `TEL-007`, `TEL-008`, `TEL-010`, `TEL-011`, `TEL-012`, `TEL-013`

5. Add response-level stats affordance and sheet to the chat message surface.
   - Files: `ChatModels.kt`, `ChatMessageUiMapper.kt`, `AssistantResponse.kt`, `AssistantResponseFinalMessage.kt`, `AssistantResponseActionRow.kt`, `MessageList.kt`, `MessageListItem.kt`, `ChatViewModel.kt`, `ChatRoute.kt`, `ChatScreen.kt`, `ChatTelemetryUiModels.kt`, `ChatTelemetryUiMapper.kt`, `ResponseStatsBottomSheet.kt`
   - Outcome: every completed assistant response gets a stats action, and the sheet observes telemetry by `messageId` and renders exact or unavailable values without exposing domain models directly to composables.
   - Scenarios: `TEL-001`, `TEL-002`, `TEL-003`, `TEL-009`, `TEL-013`

6. Replace the top-bar new-chat icon flow with the overflow menu and add chat stats.
   - Files: `ChatTopBar.kt`, `ChatScreen.kt`, `ChatRoute.kt`, `ChatViewModel.kt`, `ChatTelemetryUiModels.kt`, `ChatTelemetryUiMapper.kt`, `ChatStatsBottomSheet.kt`
   - Outcome: `MoreVert` menu dispatches `New chat` unchanged and opens whole-chat stats with provider/model breakdowns.
   - Scenarios: `TEL-004`, `TEL-005`, `TEL-006`, `TEL-013`

7. Add settings `Usage & Spend` entry and bottom sheet.
   - Files: `SettingsModels.kt`, `SettingsUiStateFactory.kt`, `SettingsViewModel.kt`, `SettingsRoute.kt`, `SettingsScreen.kt`, `UsageSpendUiMapper.kt`, `UsageSpendBottomSheet.kt`
   - Outcome: settings shows lifetime/current-month spend and usage per provider using the shared read model.
   - Scenarios: `TEL-007`, `TEL-008`, `TEL-011`, `TEL-012`

8. Add fleet analytics metadata and server-side provider daily rollups.
   - Files: `TelemetryClientBuild.kt`, `MessageInferenceTelemetryEntity.kt`, `MessageInferenceTelemetryMappers.kt`, `MessageInferenceTelemetryFirestoreMapper.kt`, `functions/src/index.ts`, `functions/src/telemetry/providerDailyRollup.ts`, `functions/src/telemetry/providerDailyRollupTypes.ts`
   - Outcome: every telemetry row carries app version metadata, and Cloud Functions can produce provider/model/app-version daily rollups for admin dashboards and release regression analysis.
   - Scenarios: `TEL-014`, `TEL-015`

9. Add mutation-resistant domain/data/provider/UI/functions tests and run the affected module suites.
   - Files: all test files listed in the file map.
   - Outcome: response detail, aggregate math, provider/model drift protection, unavailable-value rendering, and period filtering are locked by tests before implementation completes.
   - Scenarios: `TEL-001` through `TEL-015`

## Scenario Mapping

| Scenario ID | Covered By Tasks |
|---|---|
| `TEL-001` | 2, 3, 5, 8 |
| `TEL-002` | 2, 3, 5, 8 |
| `TEL-003` | 2, 4, 5, 8 |
| `TEL-004` | 6, 8 |
| `TEL-005` | 2, 3, 4, 6, 8 |
| `TEL-006` | 4, 6, 8 |
| `TEL-007` | 4, 7, 8 |
| `TEL-008` | 3, 4, 7, 8 |
| `TEL-009` | 2, 3, 5, 8 |
| `TEL-010` | 1, 2, 4, 8 |
| `TEL-011` | 1, 4, 7, 8 |
| `TEL-012` | 4, 7, 8 |
| `TEL-013` | 2, 3, 4, 5, 6, 8 |
| `TEL-014` | 2, 4, 8, 9 |
| `TEL-015` | 8, 9 |

## Drift And Escalation Rules

- If schema authorization is denied, stop and request explicit approval. Do not implement an in-memory-only variant under the same ticket.
- If maintainers reject destructive migration for the Room version bump, stop and request a separate migration strategy decision before editing `core/data`.
- If a provider does not expose a final usage block, keep TTFT/output-speed/output-token values if they can be measured and render every unsupported field as `Not available`. Do not silently coerce unknown cost to `$0.000000`.
- If a provider returns both exact cost and a local estimate, persist the exact provider cost and ignore the estimate.
- If the current active model assignment changes after a message was generated, message detail and chat/settings breakdowns must continue using the persisted telemetry provider/model identity from that historical turn.
- If cloud sync for telemetry is explicitly waived, update the acceptance scenarios before implementation. Do not ship local-only behavior while still claiming cross-device parity.
- If pricing for an OpenAI or Anthropic model is missing from the checked-in catalog, persist `costStatus = UNAVAILABLE` and render `Not available`; do not guess.
- If an assistant response is cancelled or fails after partial generation, implementation must either persist the measured partial telemetry with `completedAtMs = null` and exclude it from spend/speed aggregates, or persist no telemetry row. Safety-blocked behavior is intentionally out of scope for this ticket.
- If fleet analytics requires cross-user access, implement it only through server-side/admin Firestore or Cloud Functions paths. Do not add client UI that can read other users' raw telemetry.
