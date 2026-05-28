# TELEMETRY-001: Local-First Telemetry & Observability Infrastructure

## Feature Summary

Add a local-first telemetry and observability infrastructure to the Enclave app. This system comprises:
1. A `TelemetryEventPort` domain port in `:core:domain` with structured event models (InferenceOutcome, ToolOutcome, ExceptionRecord, ProviderMetric) — no Android dependencies
2. Consolidate Crashlytics non-fatal reporting into `LoggingPort.error()` when a throwable is present, replacing the redundant `recordException()` method
3. A Room-backed telemetry event store in `:core:data` with a `telemetry_events` table buffering events locally with an `uploaded` flag for at-least-once delivery and server-side dedupe, capped at ~50k rows with LRU eviction
4. Instrumentation of inference services (Google, Anthropic, OpenAI SDK, RustLoop) and tool executors to emit telemetry events via the domain port
5. A `PeriodicWorkRequest`-based `TelemetryUploadWorker` that batches and uploads telemetry events to a Grafana Cloud OTLP endpoint
6. Fix Crashlytics breadcrumb logging: use `crashlytics.log()` for context and remove incorrect `setCustomKey` usage

## File Structure Table

| File | Module | Action | Responsibility |
|------|--------|--------|---------------|
| `TelemetryEventPort.kt` | `:core:domain` | Create | Domain port interface for emitting telemetry events |
| `TelemetryEventModels.kt` | `:core:domain` | Create | Structured event models: InferenceOutcome, ToolOutcome, ExceptionRecord, ProviderMetric |
| `TelemetrySeverity.kt` | `:core:domain` | Create | Enum for event severity levels |
| `TelemetryEventEntity.kt` | `:core:data` | Create | Room entity for `telemetry_events` table |
| `TelemetryEventDao.kt` | `:core:data` | Create | DAO with insert/query/evict/mark-uploaded operations |
| `TelemetryEventMappers.kt` | `:core:data` | Create | Bidirectional mappers: domain models ↔ Room entity |
| `TelemetryEventRepositoryImpl.kt` | `:core:data` | Create | Repository implementing TelemetryEventPort, buffers events in Room |
| `TelemetryUploadWorker.kt` | `:core:data` | Create | PeriodicWorkRequest-based worker for batch OTLP upload |
| `TelemetryOtlpClient.kt` | `:core:data` | Create | HTTP client for Grafana Cloud OTLP endpoint |
| `TelemetryUploadScheduler.kt` | `:core:data` | Create | Schedules periodic upload work request |
| `EnclaveDatabase.kt` | `:core:data` | Modify | Register TelemetryEventEntity, bump schema version, add DAO accessor |
| `DataModule.kt` | `:core:data` | Modify | Provide TelemetryEventDao, bind TelemetryEventPort, inject WorkManager for scheduler |
| `LoggingPort.kt` | `:core:domain` | Modify | Remove `recordException()` method — superseded by `error()` with throwable |
| `AnthropicInferenceServiceImpl.kt` | `:feature:inference` | Modify | Emit InferenceOutcome telemetry on success/failure |
| `GoogleInferenceServiceImpl.kt` | `:feature:inference` | Modify | Emit InferenceOutcome telemetry on success/failure |
| `BaseOpenAiSdkInferenceService.kt` | `:feature:inference` | Modify | Emit InferenceOutcome telemetry on success/failure |
| `RustLoopInferenceService.kt` | `:feature:inference` | Modify | Emit InferenceOutcome telemetry for agent loop events |
| `CompositeToolExecutor.kt` | `:core:data` | Modify | Emit ToolOutcome telemetry for tool executions |
| `SearchToolExecutorImpl.kt` | `:core:data` | Modify | Emit ToolOutcome telemetry |
| `ExtractToolExecutorImpl.kt` | `:core:data` | Modify | Emit ToolOutcome telemetry |
| `XSearchToolExecutorImpl.kt` | `:core:data` | Modify | Emit ToolOutcome telemetry |
| `MediaToolExecutor.kt` | `:core:data` | Modify | Emit ToolOutcome telemetry |
| `AndroidLoggingAdapter.kt` | `:feature:inference` | Modify | Add Crashlytics non-fatal reporting + breadcrumb in `error()` when throwable present, remove `recordException()` implementation, fix `setCustomKey` misuse |
| `GlobalErrorHandler.kt` | `:core:ui` | Modify | Replace `recordException()` call with `error()` |
| `ViewModelErrorHandlerImpl.kt` | `:core:ui` | Modify | Replace `recordException()` call with `error()` |
| `SensitivePersistenceSelfHealer.kt` | `:core:data` | Modify | Replace `recordException()` calls with `error()` |
| `DataModule.kt` | `:app` | Modify | Bind TelemetryEventPort to TelemetryEventRepositoryImpl |
| `EnclaveApplication.kt` | `:app` | Modify | Initialize telemetry upload scheduler on app startup |

## Changes to Existing Interfaces

### LoggingPort (Domain Port) — Modification

**Location:** `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/port/inference/LoggingPort.kt`

**Current:** 5 methods (`debug`, `info`, `warning`, `error`, `recordException`)
**Target:** 4 methods (`debug`, `info`, `warning`, `error`)

Remove `recordException()`. The `error()` method becomes the single entry point for all error logging with optional Crashlytics non-fatal reporting when a throwable is present.

Updated `error()` behavior:
- `Log.e(tag, message, throwable)` — always
- `crashlytics.log("$tag: $message")` — breadcrumb, when throwable != null
- `crashlytics.recordException(throwable)` — non-fatal, when throwable != null
- Wrapped in `runCatching` for graceful degradation

Telemetry upload is opt-in. The scheduler must no-op until telemetry configuration is present, and upload failures must never block inference or app startup.

## API Contracts and Interfaces

### TelemetryEventPort (Domain Port)

**Location:** `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/port/telemetry/TelemetryEventPort.kt`

```kotlin
interface TelemetryEventPort {
    suspend fun emitInferenceOutcome(outcome: InferenceOutcome)
    suspend fun emitToolOutcome(outcome: ToolOutcome)
    suspend fun emitException(record: ExceptionRecord)
    suspend fun emitProviderMetric(metric: ProviderMetric)
}
```

### TelemetryEventModels (Domain Models)

**Location:** `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/telemetry/TelemetryEventModels.kt`

```kotlin
data class InferenceOutcome(
    val eventId: String,
    val timestampMs: Long,
    val provider: String,
    val modelId: String,
    val latencyMs: Long,
    val tokenCount: Int?,
    val costUsd: BigDecimal?,
    val success: Boolean,
    val errorMessage: String?,
    val severity: TelemetrySeverity,
)

data class ToolOutcome(
    val eventId: String,
    val timestampMs: Long,
    val toolName: String,
    val latencyMs: Long,
    val success: Boolean,
    val errorMessage: String?,
    val severity: TelemetrySeverity,
)

data class ExceptionRecord(
    val eventId: String,
    val timestampMs: Long,
    val tag: String,
    val message: String,
    val throwableType: String,
    val stackTrace: String,
    val severity: TelemetrySeverity,
)

data class ProviderMetric(
    val eventId: String,
    val timestampMs: Long,
    val provider: String,
    val metricName: String,
    val metricValue: Double,
    val severity: TelemetrySeverity,
)
```

### TelemetrySeverity Enum

**Location:** `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/telemetry/TelemetrySeverity.kt`

```kotlin
enum class TelemetrySeverity {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
}
```

### TelemetryEventEntity (Room Entity)

**Location:** `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/local/TelemetryEventEntity.kt`

```kotlin
@Entity(tableName = "telemetry_events")
data class TelemetryEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "timestamp_ms") val timestampMs: Long,
    @ColumnInfo(name = "severity") val severity: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "uploaded") val uploaded: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
```

Payload redaction is mandatory before persistence. Stack traces, error messages, and tool arguments can contain user text, file paths, or provider content and must be sanitized before they are written to JSON.

### TelemetryEventDao (Room DAO)

**Location:** `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/local/TelemetryEventDao.kt`

```kotlin
@Dao
abstract class TelemetryEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(event: TelemetryEventEntity)

    @Query("SELECT * FROM telemetry_events WHERE uploaded = 0 ORDER BY timestamp_ms ASC LIMIT :limit")
    abstract suspend fun getPendingUploads(limit: Int = 100): List<TelemetryEventEntity>

    @Query("UPDATE telemetry_events SET uploaded = 1 WHERE id IN (:ids)")
    abstract suspend fun markUploaded(ids: List<String>)

    @Query("DELETE FROM telemetry_events WHERE id IN (SELECT id FROM telemetry_events ORDER BY created_at ASC LIMIT :count)")
    abstract suspend fun evictOldest(count: Int)

    @Query("SELECT COUNT(*) FROM telemetry_events")
    abstract suspend fun getCount(): Int

    @Query("DELETE FROM telemetry_events WHERE uploaded = 1")
    abstract suspend fun deleteUploaded()
}
```

### TelemetryOtlpClient

**Location:** `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/telemetry/TelemetryOtlpClient.kt`

```kotlin
interface TelemetryOtlpClient {
    suspend fun uploadBatch(events: List<TelemetryEventEntity>): Result<Unit>
}
```

## Data Model / Schema Changes

### telemetry_events Table

- **Table name:** `telemetry_events`
- **Primary key:** `id` (UUID string)
- **Columns:** id, event_type, timestamp_ms, severity, payload_json, uploaded, created_at
- **Indices:** Index on `uploaded` for efficient pending queries, Index on `created_at` for LRU eviction
- **Eviction policy:** When row count exceeds 50,000, delete oldest 1,000 rows by `created_at`
- **Upload semantics:** `uploaded` flag (0 = pending, 1 = uploaded) for at-least-once delivery with server-side dedupe keyed by `eventId`

### Schema Version Bump

- Current version: 4
- New version: 5
- Migration strategy: Destructive migration (existing pattern)

## Ordered Implementation Tasks

### Phase 1: Domain Layer

- [ ] Task 1. Create `TelemetrySeverity` enum in `:core:domain`
  - **Files:** Create `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/telemetry/TelemetrySeverity.kt`
  - **Scenarios:** TEL-001, TEL-002, TEL-003, TEL-004

- [ ] Task 2. Create `TelemetryEventModels` in `:core:domain`
  - **Files:** Create `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/telemetry/TelemetryEventModels.kt`
  - **Scenarios:** TEL-005, TEL-006, TEL-007, TEL-008

- [ ] Task 3. Create `TelemetryEventPort` interface in `:core:domain`
  - **Files:** Create `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/port/telemetry/TelemetryEventPort.kt`
  - **Scenarios:** TEL-009, TEL-010, TEL-011, TEL-012

### Phase 2: Data Layer - Room Storage

- [ ] Task 4. Create `TelemetryEventEntity` Room entity
  - **Files:** Create `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/local/TelemetryEventEntity.kt`
  - **Scenarios:** TEL-013, TEL-014

- [ ] Task 5. Create `TelemetryEventDao` with CRUD and eviction operations
  - **Files:** Create `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/local/TelemetryEventDao.kt`
  - **Scenarios:** TEL-015, TEL-016, TEL-017, TEL-018, TEL-019

- [ ] Task 6. Create `TelemetryEventMappers` for domain ↔ entity conversion
  - **Files:** Create `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/mapper/telemetry/TelemetryEventMappers.kt`
  - **Scenarios:** TEL-020, TEL-021, TEL-059

- [ ] Task 7. Update `EnclaveDatabase` to register entity and bump schema version
  - **Files:** Modify `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/local/EnclaveDatabase.kt`
  - **Scenarios:** TEL-022

- [ ] Task 8. Update `DataModule` to provide `TelemetryEventDao`
  - **Files:** Modify `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/DataModule.kt`
  - **Scenarios:** TEL-023

### Phase 3: Data Layer - Repository & Upload

- [ ] Task 9. Create `TelemetryEventRepositoryImpl` implementing `TelemetryEventPort`
  - **Files:** Create `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/repository/TelemetryEventRepositoryImpl.kt`
  - **Scenarios:** TEL-024, TEL-025, TEL-026, TEL-027, TEL-028, TEL-060, TEL-061

- [ ] Task 10. Create `TelemetryOtlpClient` for Grafana Cloud OTLP upload
  - **Files:** Create `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/telemetry/TelemetryOtlpClient.kt`
  - **Scenarios:** TEL-029, TEL-030, TEL-063

- [ ] Task 11. Create `TelemetryUploadWorker` with periodic upload logic
  - **Files:** Create `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/sync/TelemetryUploadWorker.kt`
  - **Scenarios:** TEL-031, TEL-032, TEL-033, TEL-034

- [ ] Task 12. Create `TelemetryUploadScheduler` to enqueue periodic work
  - **Files:** Create `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/sync/TelemetryUploadScheduler.kt`
  - **Scenarios:** TEL-035, TEL-058

- [ ] Task 13. Update `DataModule` to bind `TelemetryEventPort` and provide scheduler
  - **Files:** Modify `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/DataModule.kt`
  - **Scenarios:** TEL-036

### Phase 4: Instrumentation - Inference Services

- [ ] Task 14. Instrument `AnthropicInferenceServiceImpl` to emit `InferenceOutcome`
  - **Files:** Modify `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/AnthropicInferenceServiceImpl.kt`
  - **Scenarios:** TEL-037, TEL-038, TEL-062

- [ ] Task 15. Instrument `GoogleInferenceServiceImpl` to emit `InferenceOutcome`
  - **Files:** Modify `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/GoogleInferenceServiceImpl.kt`
  - **Scenarios:** TEL-039, TEL-040, TEL-062

- [ ] Task 16. Instrument `BaseOpenAiSdkInferenceService` to emit `InferenceOutcome`
  - **Files:** Modify `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/BaseOpenAiSdkInferenceService.kt`
  - **Scenarios:** TEL-041, TEL-042, TEL-062

- [ ] Task 17. Instrument `RustLoopInferenceService` to emit `InferenceOutcome`
  - **Files:** Modify `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/RustLoopInferenceService.kt`
  - **Scenarios:** TEL-043, TEL-044, TEL-062

### Phase 5: Instrumentation - Tool Executors

- [ ] Task 18. Instrument `CompositeToolExecutor` to emit `ToolOutcome`
  - **Files:** Modify `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/CompositeToolExecutor.kt`
  - **Scenarios:** TEL-045, TEL-046, TEL-062

- [ ] Task 19. Instrument individual tool executors to emit `ToolOutcome`
  - **Files:** Modify `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/repository/SearchToolExecutorImpl.kt`, `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/repository/ExtractToolExecutorImpl.kt`, `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/repository/XSearchToolExecutorImpl.kt`, `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/repository/MediaToolExecutor.kt`
  - **Scenarios:** TEL-047, TEL-048, TEL-062

### LoggingPort & Crashlytics Consolidation

- [ ] Task 20. Update `LoggingPort` to remove `recordException()` method
  - **Files:** Modify `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/port/inference/LoggingPort.kt`
  - **Scenarios:** TEL-049

- [ ] Task 21. Update `AndroidLoggingAdapter`: add Crashlytics non-fatal + breadcrumb to `error()`, remove `recordException()`, fix `setCustomKey` misuse
  - **Files:** Modify `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/AndroidLoggingAdapter.kt`
  - **Scenarios:** TEL-050, TEL-051, TEL-052, TEL-053

- [ ] Task 22. Migrate all `recordException()` call sites to `error()`
  - **Files:** Modify `core/ui/src/main/kotlin/com/browntowndev/enclave/core/ui/error/ViewModelErrorHandlerImpl.kt`, `core/ui/src/main/kotlin/com/browntowndev/enclave/core/ui/error/GlobalErrorHandler.kt`, `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/security/SensitivePersistenceSelfHealer.kt`
  - **Scenarios:** TEL-054

### Phase 7: App Layer Integration

- [ ] Task 23. Update app Hilt module to bind `TelemetryEventPort`
  - **Files:** Modify `app/src/main/kotlin/com/browntowndev/enclave/app/DataModule.kt`
  - **Scenarios:** TEL-055

- [ ] Task 24. Update `EnclaveApplication` to initialize telemetry upload scheduler
  - **Files:** Modify `app/src/main/kotlin/com/browntowndev/enclave/EnclaveApplication.kt`
  - **Scenarios:** TEL-056

- [ ] Task 25. Update test fakes to remove `recordException()` override
  - **Files:** Modify `app/src/test/kotlin/com/browntowndev/enclave/testing/Fakes.kt`, `core/domain/src/test/kotlin/com/browntowndev/enclave/testing/Fakes.kt`, and all `RecordingLogger`/`RecordingLoggingPort` test doubles across test files
  - **Scenarios:** TEL-057

## Drift and Escalation Rules

1. **Schema changes:** Any modification to `telemetry_events` table structure requires explicit developer authorization per DATA_LAYER_RULES.md
2. **OTLP endpoint:** Grafana Cloud OTLP endpoint URL and API key must be provided via BuildConfig or secure storage — never hardcoded
3. **Upload frequency:** Default periodic interval is 15 minutes — adjustable via `TelemetryUploadScheduler` constants
4. **Eviction threshold:** Hard cap at 50,000 rows — if performance degrades, reduce to 25,000
5. **Error handling:** All telemetry operations must fail silently — telemetry failures must never crash the app or block inference
6. **Privacy:** Payload JSON must not contain PII — only anonymized metrics and error details
7. **Backpressure:** If Room insert fails, log warning and continue — never retry indefinitely
8. **Scope:** This telemetry stream is additive. It must not duplicate or replace the existing `MessageInferenceTelemetry` persistence path unless a separate migration plan explicitly says so.
