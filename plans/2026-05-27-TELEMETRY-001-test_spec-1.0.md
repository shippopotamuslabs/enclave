# TELEMETRY-001 Test Specification

## Test Framework and Run Commands

**Framework:** JUnit 5 + MockK + Turbine
**Run Command:** `./gradlew testDebugUnitTest`

## Scenario Index

| ID | Scenario | Type | Implementation Task |
|----|----------|------|---------------------|
| TEL-001 | TelemetrySeverity enum has DEBUG value | Happy Path | Task 1 |
| TEL-002 | TelemetrySeverity enum has INFO value | Happy Path | Task 1 |
| TEL-003 | TelemetrySeverity enum has WARNING value | Happy Path | Task 1 |
| TEL-004 | TelemetrySeverity enum has ERROR value | Happy Path | Task 1 |
| TEL-005 | InferenceOutcome data class creation with all fields | Happy Path | Task 2 |
| TEL-006 | ToolOutcome data class creation with all fields | Happy Path | Task 2 |
| TEL-007 | ExceptionRecord data class creation with all fields | Happy Path | Task 2 |
| TEL-008 | ProviderMetric data class creation with all fields | Happy Path | Task 2 |
| TEL-009 | TelemetryEventPort.emitInferenceOutcome succeeds | Happy Path | Task 3 |
| TEL-010 | TelemetryEventPort.emitToolOutcome succeeds | Happy Path | Task 3 |
| TEL-011 | TelemetryEventPort.emitException succeeds | Happy Path | Task 3 |
| TEL-012 | TelemetryEventPort.emitProviderMetric succeeds | Happy Path | Task 3 |
| TEL-013 | TelemetryEventEntity creation with default uploaded=false | Happy Path | Task 4 |
| TEL-014 | TelemetryEventEntity creation with uploaded=true | Happy Path | Task 4 |
| TEL-015 | TelemetryEventDao.insert stores event | Happy Path | Task 5 |
| TEL-016 | TelemetryEventDao.getPendingUploads returns unuploaded events | Happy Path | Task 5 |
| TEL-017 | TelemetryEventDao.markUploaded sets uploaded flag | Happy Path | Task 5 |
| TEL-018 | TelemetryEventDao.evictOldest deletes oldest rows | Happy Path | Task 5 |
| TEL-019 | TelemetryEventDao.getCount returns accurate count | Happy Path | Task 5 |
| TEL-020 | TelemetryEventMappers.toEntity converts InferenceOutcome | Happy Path | Task 6 |
| TEL-021 | TelemetryEventMappers.toDomain converts entity back | Happy Path | Task 6 |
| TEL-022 | EnclaveDatabase includes TelemetryEventEntity | Happy Path | Task 7 |
| TEL-023 | DataModule provides TelemetryEventDao | Happy Path | Task 8 |
| TEL-024 | TelemetryEventRepositoryImpl buffers InferenceOutcome | Happy Path | Task 9 |
| TEL-025 | TelemetryEventRepositoryImpl buffers ToolOutcome | Happy Path | Task 9 |
| TEL-026 | TelemetryEventRepositoryImpl buffers ExceptionRecord | Happy Path | Task 9 |
| TEL-027 | TelemetryEventRepositoryImpl triggers eviction at 50k rows | Happy Path | Task 9 |
| TEL-028 | TelemetryEventRepositoryImpl handles Room insert failure silently | Error Path | Task 9 |
| TEL-029 | TelemetryOtlpClient.uploadBatch succeeds | Happy Path | Task 10 |
| TEL-030 | TelemetryOtlpClient.uploadBatch handles network failure | Error Path | Task 10 |
| TEL-031 | TelemetryUploadWorker.doWork uploads pending events | Happy Path | Task 11 |
| TEL-032 | TelemetryUploadWorker.doWork marks events as uploaded | Happy Path | Task 11 |
| TEL-033 | TelemetryUploadWorker.doWork returns retry on partial failure | Error Path | Task 11 |
| TEL-034 | TelemetryUploadWorker.doWork returns success when no pending events | Edge Case | Task 11 |
| TEL-035 | TelemetryUploadScheduler enqueues periodic work | Happy Path | Task 12 |
| TEL-036 | DataModule binds TelemetryEventPort to repository | Happy Path | Task 13 |
| TEL-037 | AnthropicInferenceServiceImpl emits InferenceOutcome on success | Happy Path | Task 14 |
| TEL-038 | AnthropicInferenceServiceImpl emits InferenceOutcome on failure | Error Path | Task 14 |
| TEL-039 | GoogleInferenceServiceImpl emits InferenceOutcome on success | Happy Path | Task 15 |
| TEL-040 | GoogleInferenceServiceImpl emits InferenceOutcome on failure | Error Path | Task 15 |
| TEL-041 | BaseOpenAiSdkInferenceService emits InferenceOutcome on success | Happy Path | Task 16 |
| TEL-042 | BaseOpenAiSdkInferenceService emits InferenceOutcome on failure | Error Path | Task 16 |
| TEL-043 | RustLoopInferenceService emits InferenceOutcome on success | Happy Path | Task 17 |
| TEL-044 | RustLoopInferenceService emits InferenceOutcome on failure | Error Path | Task 17 |
| TEL-045 | CompositeToolExecutor emits ToolOutcome on success | Happy Path | Task 18 |
| TEL-046 | CompositeToolExecutor emits ToolOutcome on failure | Error Path | Task 18 |
| TEL-047 | Individual tool executors emit ToolOutcome | Happy Path | Task 19 |
| TEL-048 | Individual tool executors emit ToolOutcome on failure | Error Path | Task 19 |
| TEL-049 | LoggingPort interface no longer declares recordException method | Happy Path | Task 20 |
| TEL-050 | AndroidLoggingAdapter.error reports non-fatal to Crashlytics when throwable present | Happy Path | Task 21 |
| TEL-051 | AndroidLoggingAdapter.error does not report when throwable is null | Edge Case | Task 21 |
| TEL-052 | AndroidLoggingAdapter.error handles Crashlytics failure gracefully | Error Path | Task 21 |
| TEL-053 | AndroidLoggingAdapter.error uses crashlytics.log() for breadcrumbs, not setCustomKey | Happy Path | Task 21 |
| TEL-054 | All recordException call sites migrated to error() | Happy Path | Task 22 |
| TEL-055 | App Hilt module binds TelemetryEventPort | Happy Path | Task 23 |
| TEL-056 | EnclaveApplication initializes telemetry scheduler | Happy Path | Task 24 |
| TEL-057 | Test fakes no longer override recordException | Happy Path | Task 25 |
| TEL-058 | TelemetryUploadScheduler no-ops when telemetry is not configured | Edge Case | Task 12 |
| TEL-059 | TelemetryEventMappers round-trip ProviderMetric | Happy Path | Task 6 |
| TEL-060 | TelemetryEventRepositoryImpl buffers ProviderMetric | Happy Path | Task 9 |
| TEL-061 | TelemetryEventRepositoryImpl redacts sensitive fields before persistence | Edge Case | Task 9 |
| TEL-062 | Telemetry wrappers rethrow CancellationException | Edge Case | Tasks 14-19 |
| TEL-063 | Telemetry upload uses eventId for server-side dedupe | Happy Path | Task 10 |

## Happy Path Scenarios

### TEL-001: TelemetrySeverity enum has DEBUG value
**Given** the TelemetrySeverity enum is defined
**When** I access TelemetrySeverity.DEBUG
**Then** the value equals "DEBUG"

### TEL-002: TelemetrySeverity enum has INFO value
**Given** the TelemetrySeverity enum is defined
**When** I access TelemetrySeverity.INFO
**Then** the value equals "INFO"

### TEL-003: TelemetrySeverity enum has WARNING value
**Given** the TelemetrySeverity enum is defined
**When** I access TelemetrySeverity.WARNING
**Then** the value equals "WARNING"

### TEL-004: TelemetrySeverity enum has ERROR value
**Given** the TelemetrySeverity enum is defined
**When** I access TelemetrySeverity.ERROR
**Then** the value equals "ERROR"

### TEL-005: InferenceOutcome data class creation with all fields
**Given** I have valid telemetry data
**When** I create an InferenceOutcome with eventId="evt-1", timestampMs=1717000000000, provider="OpenAI", modelId="gpt-4.1", latencyMs=1500, tokenCount=500, costUsd=BigDecimal("0.0025"), success=true, errorMessage=null, severity=TelemetrySeverity.INFO
**Then** the data class stores all fields correctly and eventId equals "evt-1"

### TEL-006: ToolOutcome data class creation with all fields
**Given** I have valid tool execution data
**When** I create a ToolOutcome with eventId="evt-2", timestampMs=1717000000000, toolName="search", latencyMs=3000, success=true, errorMessage=null, severity=TelemetrySeverity.INFO
**Then** the data class stores all fields correctly and toolName equals "search"

### TEL-007: ExceptionRecord data class creation with all fields
**Given** I have valid exception data
**When** I create an ExceptionRecord with eventId="evt-3", timestampMs=1717000000000, tag="InferenceService", message="Connection timeout", throwableType="java.io.IOException", stackTrace="...", severity=TelemetrySeverity.ERROR
**Then** the data class stores all fields correctly and throwableType equals "java.io.IOException"

### TEL-008: ProviderMetric data class creation with all fields
**Given** I have valid metric data
**When** I create a ProviderMetric with eventId="evt-4", timestampMs=1717000000000, provider="Anthropic", metricName="tttf_ms", metricValue=450.0, severity=TelemetrySeverity.INFO
**Then** the data class stores all fields correctly and metricValue equals 450.0

### TEL-009: TelemetryEventPort.emitInferenceOutcome succeeds
**Given** I have a TelemetryEventPort implementation
**When** I call emitInferenceOutcome with a valid InferenceOutcome
**Then** the method completes without throwing an exception

### TEL-010: TelemetryEventPort.emitToolOutcome succeeds
**Given** I have a TelemetryEventPort implementation
**When** I call emitToolOutcome with a valid ToolOutcome
**Then** the method completes without throwing an exception

### TEL-011: TelemetryEventPort.emitException succeeds
**Given** I have a TelemetryEventPort implementation
**When** I call emitException with a valid ExceptionRecord
**Then** the method completes without throwing an exception

### TEL-012: TelemetryEventPort.emitProviderMetric succeeds
**Given** I have a TelemetryEventPort implementation
**When** I call emitProviderMetric with a valid ProviderMetric
**Then** the method completes without throwing an exception

### TEL-013: TelemetryEventEntity creation with default uploaded=false
**Given** I create a TelemetryEventEntity without specifying uploaded
**When** I instantiate the entity with id="evt-1", eventType="INFERENCE_OUTCOME", timestampMs=1717000000000, severity="INFO", payloadJson="{}"
**Then** uploaded equals false and createdAt is approximately equal to System.currentTimeMillis()

### TEL-014: TelemetryEventEntity creation with uploaded=true
**Given** I create a TelemetryEventEntity with uploaded=true
**When** I instantiate the entity with id="evt-2", eventType="TOOL_OUTCOME", timestampMs=1717000000000, severity="INFO", payloadJson="{}", uploaded=true
**Then** uploaded equals true

### TEL-015: TelemetryEventDao.insert stores event
**Given** I have an in-memory Room database with TelemetryEventDao
**When** I insert a TelemetryEventEntity with id="evt-1"
**Then** the entity is stored and can be retrieved by id

### TEL-016: TelemetryEventDao.getPendingUploads returns unuploaded events
**Given** I have inserted 3 events with uploaded=false and 2 events with uploaded=true
**When** I call getPendingUploads(limit=100)
**Then** I receive exactly 3 events with uploaded=false

### TEL-017: TelemetryEventDao.markUploaded sets uploaded flag
**Given** I have inserted 2 events with uploaded=false (ids: "evt-1", "evt-2")
**When** I call markUploaded(listOf("evt-1"))
**Then** event "evt-1" has uploaded=true and event "evt-2" has uploaded=false

### TEL-018: TelemetryEventDao.evictOldest deletes oldest rows
**Given** I have inserted 5 events with timestamps [1000, 2000, 3000, 4000, 5000]
**When** I call evictOldest(count=2)
**Then** 3 events remain with timestamps [3000, 4000, 5000]

### TEL-019: TelemetryEventDao.getCount returns accurate count
**Given** I have inserted 7 events
**When** I call getCount()
**Then** the result equals 7

### TEL-020: TelemetryEventMappers.toEntity converts InferenceOutcome
**Given** I have an InferenceOutcome with eventId="evt-1", timestampMs=1717000000000, provider="OpenAI", success=true, severity=TelemetrySeverity.INFO
**When** I call toEntity() on the mapper
**Then** the result is a TelemetryEventEntity with id="evt-1", eventType="INFERENCE_OUTCOME", timestampMs=1717000000000, severity="INFO", uploaded=false

### TEL-021: TelemetryEventMappers.toDomain converts entity back
**Given** I have a TelemetryEventEntity with id="evt-1", eventType="INFERENCE_OUTCOME", payloadJson containing valid InferenceOutcome JSON
**When** I call toDomain() on the mapper
**Then** the result is an InferenceOutcome with eventId="evt-1" and all original fields preserved

### TEL-022: EnclaveDatabase includes TelemetryEventEntity
**Given** the EnclaveDatabase is defined
**When** I inspect the @Database annotation
**Then** TelemetryEventEntity.class is in the entities array and version equals 5

### TEL-023: DataModule provides TelemetryEventDao
**Given** the DataModule is configured
**When** I request TelemetryEventDao via Hilt
**Then** a non-null instance is provided from the database

### TEL-024: TelemetryEventRepositoryImpl buffers InferenceOutcome
**Given** I have a TelemetryEventRepositoryImpl with a mock TelemetryEventDao
**When** I call emitInferenceOutcome with a valid InferenceOutcome
**Then** the dao.insert() is called exactly once with a TelemetryEventEntity where eventType="INFERENCE_OUTCOME"

### TEL-025: TelemetryEventRepositoryImpl buffers ToolOutcome
**Given** I have a TelemetryEventRepositoryImpl with a mock TelemetryEventDao
**When** I call emitToolOutcome with a valid ToolOutcome
**Then** the dao.insert() is called exactly once with a TelemetryEventEntity where eventType="TOOL_OUTCOME"

### TEL-026: TelemetryEventRepositoryImpl buffers ExceptionRecord
**Given** I have a TelemetryEventRepositoryImpl with a mock TelemetryEventDao
**When** I call emitException with a valid ExceptionRecord
**Then** the dao.insert() is called exactly once with a TelemetryEventEntity where eventType="EXCEPTION_RECORD"

### TEL-027: TelemetryEventRepositoryImpl triggers eviction at 50k rows
**Given** I have a TelemetryEventRepositoryImpl where getCount() returns 50001
**When** I call emitInferenceOutcome with a valid InferenceOutcome
**Then** evictOldest(1000) is called after the insert

### TEL-028: TelemetryEventRepositoryImpl handles Room insert failure silently
**Given** I have a TelemetryEventRepositoryImpl where dao.insert() throws RuntimeException("Disk full")
**When** I call emitInferenceOutcome with a valid InferenceOutcome
**Then** no exception is thrown to the caller and the method completes normally

### TEL-029: TelemetryOtlpClient.uploadBatch succeeds
**Given** I have a TelemetryOtlpClient with a valid OTLP endpoint
**When** I call uploadBatch with 5 TelemetryEventEntity objects
**Then** the result is Result.success(Unit) and the HTTP request contains all 5 events in OTLP format

### TEL-030: TelemetryOtlpClient.uploadBatch handles network failure
**Given** I have a TelemetryOtlpClient where the network is unavailable
**When** I call uploadBatch with 3 TelemetryEventEntity objects
**Then** the result is Result.failure with an IOException and no events are marked uploaded

### TEL-031: TelemetryUploadWorker.doWork uploads pending events
**Given** I have a TelemetryUploadWorker with 50 pending events in the DAO
**When** I call doWork()
**Then** the OTLP client receives a batch of 50 events and the method returns Result.success()

### TEL-032: TelemetryUploadWorker.doWork marks events as uploaded
**Given** I have a TelemetryUploadWorker with 10 pending events
**When** I call doWork() and the upload succeeds
**Then** markUploaded() is called with the IDs of all 10 events

### TEL-033: TelemetryUploadWorker.doWork returns retry on partial failure
**Given** I have a TelemetryUploadWorker where the OTLP client throws IOException on the second batch
**When** I call doWork() with 200 pending events (batch size 100)
**Then** the first batch is marked uploaded and the method returns Result.retry()

### TEL-034: TelemetryUploadWorker.doWork returns success when no pending events
**Given** I have a TelemetryUploadWorker with 0 pending events
**When** I call doWork()
**Then** the method returns Result.success() without calling the OTLP client

### TEL-035: TelemetryUploadScheduler enqueues periodic work
**Given** I have a TelemetryUploadScheduler with a mock WorkManager
**When** I call schedule()
**Then** workManager.enqueueUniquePeriodicWork() is called with ExistingPeriodicWorkPolicy.KEEP and a PeriodicWorkRequest with interval 15 minutes

### TEL-036: DataModule binds TelemetryEventPort to repository
**Given** the DataModule is configured
**When** I request TelemetryEventPort via Hilt
**Then** a TelemetryEventRepositoryImpl instance is provided

### TEL-037: AnthropicInferenceServiceImpl emits InferenceOutcome on success
**Given** I have an AnthropicInferenceServiceImpl with a mock TelemetryEventPort
**When** a completeSingleTurn() call succeeds with latency 2000ms
**Then** emitInferenceOutcome is called with an InferenceOutcome where success=true, provider="Anthropic", latencyMs=2000, severity=TelemetrySeverity.INFO

### TEL-038: AnthropicInferenceServiceImpl emits InferenceOutcome on failure
**Given** I have an AnthropicInferenceServiceImpl with a mock TelemetryEventPort
**When** a completeSingleTurn() call throws IOException("Connection refused")
**Then** emitInferenceOutcome is called with an InferenceOutcome where success=false, errorMessage="Connection refused", severity=TelemetrySeverity.ERROR

### TEL-039: GoogleInferenceServiceImpl emits InferenceOutcome on success
**Given** I have a GoogleInferenceServiceImpl with a mock TelemetryEventPort
**When** a completeSingleTurn() call succeeds with latency 1500ms
**Then** emitInferenceOutcome is called with an InferenceOutcome where success=true, provider="Google", latencyMs=1500, severity=TelemetrySeverity.INFO

### TEL-040: GoogleInferenceServiceImpl emits InferenceOutcome on failure
**Given** I have a GoogleInferenceServiceImpl with a mock TelemetryEventPort
**When** a completeSingleTurn() call throws IOException("Timeout")
**Then** emitInferenceOutcome is called with an InferenceOutcome where success=false, errorMessage="Timeout", severity=TelemetrySeverity.ERROR

### TEL-041: BaseOpenAiSdkInferenceService emits InferenceOutcome on success
**Given** I have a BaseOpenAiSdkInferenceService with a mock TelemetryEventPort
**When** a completeSingleTurn() call succeeds with latency 1800ms
**Then** emitInferenceOutcome is called with an InferenceOutcome where success=true, provider="OpenAI", latencyMs=1800, severity=TelemetrySeverity.INFO

### TEL-042: BaseOpenAiSdkInferenceService emits InferenceOutcome on failure
**Given** I have a BaseOpenAiSdkInferenceService with a mock TelemetryEventPort
**When** a completeSingleTurn() call throws IOException("Rate limited")
**Then** emitInferenceOutcome is called with an InferenceOutcome where success=false, errorMessage="Rate limited", severity=TelemetrySeverity.ERROR

### TEL-043: RustLoopInferenceService emits InferenceOutcome on success
**Given** I have a RustLoopInferenceService with a mock TelemetryEventPort
**When** a sendPrompt() call completes successfully
**Then** emitInferenceOutcome is called with an InferenceOutcome where success=true, severity=TelemetrySeverity.INFO

### TEL-044: RustLoopInferenceService emits InferenceOutcome on failure
**Given** I have a RustLoopInferenceService with a mock TelemetryEventPort
**When** a sendPrompt() call receives AgentLoopEvent.Failed
**Then** emitInferenceOutcome is called with an InferenceOutcome where success=false, severity=TelemetrySeverity.ERROR

### TEL-045: CompositeToolExecutor emits ToolOutcome on success
**Given** I have a CompositeToolExecutor with a mock TelemetryEventPort
**When** execute() completes successfully for tool "search"
**Then** emitToolOutcome is called with a ToolOutcome where toolName="search", success=true, severity=TelemetrySeverity.INFO

### TEL-046: CompositeToolExecutor emits ToolOutcome on failure
**Given** I have a CompositeToolExecutor with a mock TelemetryEventPort
**When** execute() throws RuntimeException("Tool failed")
**Then** emitToolOutcome is called with a ToolOutcome where success=false, errorMessage="Tool failed", severity=TelemetrySeverity.ERROR

### TEL-047: Individual tool executors emit ToolOutcome
**Given** I have a SearchToolExecutorImpl with a mock TelemetryEventPort
**When** execute() completes successfully
**Then** emitToolOutcome is called with a ToolOutcome where toolName="search", success=true

### TEL-048: Individual tool executors emit ToolOutcome on failure
**Given** I have a SearchToolExecutorImpl with a mock TelemetryEventPort
**When** execute() throws IOException("Network error")
**Then** emitToolOutcome is called with a ToolOutcome where success=false, errorMessage="Network error", severity=TelemetrySeverity.ERROR

### TEL-049: LoggingPort interface no longer declares recordException method
**Given** the LoggingPort interface is defined in `:core:domain`
**When** I inspect the interface methods
**Then** the interface declares exactly 4 methods: `debug`, `info`, `warning`, `error` — and `recordException` is not present

### TEL-050: AndroidLoggingAdapter.error reports non-fatal to Crashlytics when throwable present
**Given** I have an AndroidLoggingAdapter with mocked Crashlytics
**When** I call error("TestTag", "Test message", RuntimeException("Test"))
**Then** crashlytics.recordException() is called exactly once with the RuntimeException

### TEL-051: AndroidLoggingAdapter.error does not report when throwable is null
**Given** I have an AndroidLoggingAdapter with mocked Crashlytics
**When** I call error("TestTag", "Test message", null)
**Then** crashlytics.recordException() is not called

### TEL-052: AndroidLoggingAdapter.error handles Crashlytics failure gracefully
**Given** I have an AndroidLoggingAdapter where FirebaseCrashlytics.getInstance() throws RuntimeException("Not initialized")
**When** I call error("TestTag", "Test message", RuntimeException("Test"))
**Then** no exception is thrown to the caller and Log.e() is called as fallback

### TEL-053: AndroidLoggingAdapter.error uses crashlytics.log() for breadcrumbs, not setCustomKey
**Given** I have an AndroidLoggingAdapter with mocked Crashlytics
**When** I call error("TestTag", "Test message", RuntimeException("Test"))
**Then** crashlytics.log() is called with message containing "TestTag" and "Test message" and crashlytics.setCustomKey() is not called with "LogTag" or "Message" keys

### TEL-054: All recordException call sites migrated to error()
**Given** the codebase is compiled
**When** I search for `recordException` in production source files (`src/main`)
**Then** zero call sites remain — all former recordException callers now use error() with the throwable

### TEL-055: App Hilt module binds TelemetryEventPort
**Given** the app Hilt module is configured
**When** I inspect the @Binds methods
**Then** TelemetryEventPort is bound to TelemetryEventRepositoryImpl with @Singleton scope

### TEL-056: EnclaveApplication initializes telemetry scheduler
**Given** the EnclaveApplication is running
**When** onCreate() completes
**Then** TelemetryUploadScheduler.schedule() is called exactly once

### TEL-057: Test fakes no longer override recordException
**Given** the test fake classes (FakeLoggingPort, RecordingLogger, RecordingLoggingPort) are defined
**When** I inspect their implementations
**Then** none of them override a `recordException` method

### TEL-058: TelemetryUploadScheduler no-ops when telemetry is not configured
**Given** I have a TelemetryUploadScheduler with missing endpoint or credentials
**When** I call schedule()
**Then** no periodic work is enqueued and the method completes normally

### TEL-059: TelemetryEventMappers round-trip ProviderMetric
**Given** I have a ProviderMetric with eventId="evt-5", timestampMs=1717000000000, provider="OpenAI", metricName="ttfb_ms", metricValue=250.0, severity=TelemetrySeverity.INFO
**When** I convert it to an entity and back to domain
**Then** all fields are preserved, including eventId, metricName, and metricValue

### TEL-060: TelemetryEventRepositoryImpl buffers ProviderMetric
**Given** I have a TelemetryEventRepositoryImpl with a mock TelemetryEventDao
**When** I call emitProviderMetric with a valid ProviderMetric
**Then** the dao.insert() is called exactly once with a TelemetryEventEntity where eventType="PROVIDER_METRIC"

### TEL-061: TelemetryEventRepositoryImpl redacts sensitive fields before persistence
**Given** I have telemetry input that contains an email address, file path, or raw provider response
**When** the repository persists an ExceptionRecord or ToolOutcome
**Then** the stored payload omits or redacts sensitive text and does not persist raw PII

### TEL-062: Telemetry wrappers rethrow CancellationException
**Given** an inference or tool wrapper is cancelled mid-call
**When** the underlying coroutine throws CancellationException
**Then** the cancellation is rethrown and no failure telemetry is emitted

### TEL-063: Telemetry upload uses eventId for server-side dedupe
**Given** I upload a batch whose events were previously attempted once
**When** the server receives the batch again after an ambiguous timeout
**Then** the request includes stable event IDs so the backend can deduplicate repeated deliveries

## Error and Edge Case Scenarios

### TEL-028: TelemetryEventRepositoryImpl handles Room insert failure silently
**Given** I have a TelemetryEventRepositoryImpl where dao.insert() throws RuntimeException("Disk full")
**When** I call emitInferenceOutcome with a valid InferenceOutcome
**Then** no exception is thrown to the caller and the method completes normally

### TEL-030: TelemetryOtlpClient.uploadBatch handles network failure
**Given** I have a TelemetryOtlpClient where the network is unavailable
**When** I call uploadBatch with 3 TelemetryEventEntity objects
**Then** the result is Result.failure with an IOException and no events are marked uploaded

### TEL-033: TelemetryUploadWorker.doWork returns retry on partial failure
**Given** I have a TelemetryUploadWorker where the OTLP client throws IOException on the second batch
**When** I call doWork() with 200 pending events (batch size 100)
**Then** the first batch is marked uploaded and the method returns Result.retry()

### TEL-034: TelemetryUploadWorker.doWork returns success when no pending events
**Given** I have a TelemetryUploadWorker with 0 pending events
**When** I call doWork()
**Then** the method returns Result.success() without calling the OTLP client

### TEL-038: AnthropicInferenceServiceImpl emits InferenceOutcome on failure
**Given** I have an AnthropicInferenceServiceImpl with a mock TelemetryEventPort
**When** a completeSingleTurn() call throws IOException("Connection refused")
**Then** emitInferenceOutcome is called with an InferenceOutcome where success=false, errorMessage="Connection refused", severity=TelemetrySeverity.ERROR

### TEL-040: GoogleInferenceServiceImpl emits InferenceOutcome on failure
**Given** I have a GoogleInferenceServiceImpl with a mock TelemetryEventPort
**When** a completeSingleTurn() call throws IOException("Timeout")
**Then** emitInferenceOutcome is called with an InferenceOutcome where success=false, errorMessage="Timeout", severity=TelemetrySeverity.ERROR

### TEL-042: BaseOpenAiSdkInferenceService emits InferenceOutcome on failure
**Given** I have a BaseOpenAiSdkInferenceService with a mock TelemetryEventPort
**When** a completeSingleTurn() call throws IOException("Rate limited")
**Then** emitInferenceOutcome is called with an InferenceOutcome where success=false, errorMessage="Rate limited", severity=TelemetrySeverity.ERROR

### TEL-044: RustLoopInferenceService emits InferenceOutcome on failure
**Given** I have a RustLoopInferenceService with a mock TelemetryEventPort
**When** a sendPrompt() call receives AgentLoopEvent.Failed
**Then** emitInferenceOutcome is called with an InferenceOutcome where success=false, severity=TelemetrySeverity.ERROR

### TEL-046: CompositeToolExecutor emits ToolOutcome on failure
**Given** I have a CompositeToolExecutor with a mock TelemetryEventPort
**When** execute() throws RuntimeException("Tool failed")
**Then** emitToolOutcome is called with a ToolOutcome where success=false, errorMessage="Tool failed", severity=TelemetrySeverity.ERROR

### TEL-048: Individual tool executors emit ToolOutcome on failure
**Given** I have a SearchToolExecutorImpl with a mock TelemetryEventPort
**When** execute() throws IOException("Network error")
**Then** emitToolOutcome is called with a ToolOutcome where success=false, errorMessage="Network error", severity=TelemetrySeverity.ERROR

### TEL-051: AndroidLoggingAdapter.error does not report when throwable is null
**Given** I have an AndroidLoggingAdapter with mocked Crashlytics
**When** I call error("TestTag", "Test message", null)
**Then** crashlytics.recordException() is not called

### TEL-052: AndroidLoggingAdapter.error handles Crashlytics failure gracefully
**Given** I have an AndroidLoggingAdapter where FirebaseCrashlytics.getInstance() throws RuntimeException("Not initialized")
**When** I call error("TestTag", "Test message", RuntimeException("Test"))
**Then** no exception is thrown to the caller and Log.e() is called as fallback

## Mutation-Defense Scenarios

### MUT-001: Eviction threshold is enforced (not hardcoded skip)
**Risk:** Lazy implementation skips eviction check entirely
**Given** I have a TelemetryEventRepositoryImpl with getCount() returning 50001
**When** I call emitInferenceOutcome with a valid InferenceOutcome
**Then** evictOldest(1000) is called exactly once
**Mutation that would fail:** Removing the `if (count > 50_000)` check would cause this test to fail because evictOldest would never be called

### MUT-002: Error severity is set correctly (not always INFO)
**Risk:** Lazy implementation always uses TelemetrySeverity.INFO regardless of success/failure
**Given** I have an AnthropicInferenceServiceImpl with a mock TelemetryEventPort
**When** a completeSingleTurn() call throws IOException("Connection refused")
**Then** the emitted InferenceOutcome has severity=TelemetrySeverity.ERROR (not INFO)
**Mutation that would fail:** Hardcoding severity=TelemetrySeverity.INFO would cause this test to fail

### MUT-003: Crashlytics non-fatal reporting is conditional on throwable
**Risk:** Lazy implementation always calls recordException regardless of throwable presence
**Given** I have an AndroidLoggingAdapter with mocked Crashlytics
**When** I call error("TestTag", "Test message", null)
**Then** crashlytics.recordException() is not called
**Mutation that would fail:** Always calling recordException would cause this test to fail

### MUT-004: Uploaded events are excluded from pending queries
**Risk:** Lazy implementation ignores the uploaded flag
**Given** I have inserted 3 events with uploaded=false and 2 events with uploaded=true
**When** I call getPendingUploads(limit=100)
**Then** I receive exactly 3 events (not 5)
**Mutation that would fail:** Querying without `WHERE uploaded = 0` would return all 5 events

### MUT-005: OTLP batch upload uses actual event data (not empty payload)
**Risk:** Lazy implementation sends empty batch to avoid complexity
**Given** I have a TelemetryOtlpClient with a mock HTTP client
**When** I call uploadBatch with 3 TelemetryEventEntity objects
**Then** the HTTP request body contains exactly 3 events with non-empty payloadJson fields
**Mutation that would fail:** Sending an empty JSON array would cause this test to fail
