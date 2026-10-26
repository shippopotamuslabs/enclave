package com.browntowndev.enclave.core.data.telemetry

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.browntowndev.enclave.core.data.DataModule
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.TelemetryEventDao
import com.browntowndev.enclave.core.data.local.TelemetryEventEntity
import com.browntowndev.enclave.core.data.mapper.telemetry.toDomain
import com.browntowndev.enclave.core.data.mapper.telemetry.toEntity
import com.browntowndev.enclave.core.data.remote.telemetry.TelemetryOtlpClientImpl
import com.browntowndev.enclave.core.data.remote.telemetry.TelemetryOtlpTransport
import com.browntowndev.enclave.core.data.repository.CompositeToolExecutor
import com.browntowndev.enclave.core.data.repository.ExtractToolExecutorImpl
import com.browntowndev.enclave.core.data.repository.SearchToolExecutorImpl
import com.browntowndev.enclave.core.data.repository.TelemetryEventRepositoryImpl
import com.browntowndev.enclave.core.data.sync.TelemetryUploadConfig
import com.browntowndev.enclave.core.data.sync.TelemetryUploadScheduler
import com.browntowndev.enclave.core.data.sync.TelemetryUploadWorker
import com.browntowndev.enclave.domain.model.entitlement.EntitlementState
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.browntowndev.enclave.domain.model.telemetry.ExceptionRecord
import com.browntowndev.enclave.domain.model.telemetry.InferenceOutcome
import com.browntowndev.enclave.domain.model.telemetry.ProviderMetric
import com.browntowndev.enclave.domain.model.telemetry.TelemetrySeverity
import com.browntowndev.enclave.domain.model.telemetry.ToolOutcome
import com.browntowndev.enclave.core.data.FakeTelemetryEventDao
import com.browntowndev.enclave.core.data.artifact.ArtifactToolExecutor
import com.browntowndev.enclave.core.data.artifact.SearchArtifactToolExecutor
import com.browntowndev.enclave.core.data.remote.telemetry.TelemetryOtlpClient
import com.browntowndev.enclave.core.data.repository.ExaSearchRepository
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.data.repository.GetMessageContextToolExecutor
import com.browntowndev.enclave.core.data.repository.ImageInspectToolExecutor
import com.browntowndev.enclave.core.data.repository.ManageMemoriesToolExecutor
import com.browntowndev.enclave.core.data.repository.MediaToolExecutor
import com.browntowndev.enclave.core.data.repository.SearchChatHistoryToolExecutor
import com.browntowndev.enclave.core.data.repository.SearchChatToolExecutor
import com.browntowndev.enclave.core.data.repository.TavilySearchRepository
import com.browntowndev.enclave.core.data.repository.ToolExecutionEventBus
import com.browntowndev.enclave.core.data.repository.XGetThreadToolExecutor
import com.browntowndev.enclave.core.data.repository.XMentionSearchToolExecutor
import com.browntowndev.enclave.core.data.repository.XSearchRepository
import com.browntowndev.enclave.core.data.repository.XSearchToolExecutorImpl
import com.browntowndev.enclave.core.data.repository.XTrendingToolExecutor
import com.browntowndev.enclave.core.data.repository.XUserTimelineFetcher
import com.browntowndev.enclave.core.data.repository.XUserTweetsToolExecutor
import com.browntowndev.enclave.core.data.repository.testApiKeyManager
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.data.testWorkerParameters
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeEntitlementPort
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeDocumentValidationPort
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeExtractedUrlTracker
import com.browntowndev.enclave.core.testing.FakeGpuCapability
import com.browntowndev.enclave.core.testing.FakeImageGenerationPort
import com.browntowndev.enclave.core.testing.FakeInferenceFactory
import com.browntowndev.enclave.core.testing.FakeLocalImageGenerationCapability
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.core.testing.FakeMemoriesRepository
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.core.testing.FakeMusicGenerationPort
import com.browntowndev.enclave.core.testing.FakeSettingsRepository
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.core.testing.FakeTelemetryEventPort
import com.browntowndev.enclave.core.testing.FakeUtilityModelFile
import com.browntowndev.enclave.core.testing.FakeVideoGenerationPort
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort
import com.browntowndev.enclave.domain.usecase.chat.AnalyzeImageUseCase
import com.browntowndev.enclave.domain.usecase.inference.BuildXQueryUseCase
import com.browntowndev.enclave.domain.usecase.media.GenerateImageUseCase
import com.browntowndev.enclave.domain.usecase.media.GenerateMediaUseCase
import com.browntowndev.enclave.domain.usecase.media.GenerateMusicUseCase
import com.browntowndev.enclave.domain.usecase.media.GenerateVideoUseCase
import com.browntowndev.enclave.domain.usecase.media.LocalStableDiffusionTargetResolver
import com.browntowndev.enclave.domain.usecase.media.ResolveAssignedImageGenerationTargetUseCase
import com.browntowndev.enclave.domain.usecase.media.ResolveAssignedMediaProviderUseCase
import com.browntowndev.enclave.domain.usecase.media.ResolveMediaModelTypeUseCase
import com.browntowndev.enclave.domain.usecase.media.SelectLocalImageGenerationBackendUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import com.browntowndev.enclave.domain.util.SystemClock
import java.util.concurrent.Executor
import javax.inject.Provider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.IOException
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.reflect.full.memberFunctions

/**
 * TELEMETRY-001 TDD Red — data layer scenarios TEL-009..TEL-012, TEL-013..TEL-036, TEL-045..TEL-048,
 * TEL-058..TEL-061, TEL-063.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class Telemetry001DataTddRedTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var telemetryEventDao: TelemetryEventDao

    @BeforeEach
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        telemetryEventDao = database.telemetryEventDao()
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    @Test
    fun `TEL-009 TelemetryEventPort emitInferenceOutcome succeeds`() =
        runTest {
            val repository = telemetryEventRepository()
            assertDoesNotThrow {
                repository.emitInferenceOutcome(sampleInferenceOutcome())
            }
        }

    @Test
    fun `TEL-010 TelemetryEventPort emitToolOutcome succeeds`() =
        runTest {
            val repository = telemetryEventRepository()
            assertDoesNotThrow {
                repository.emitToolOutcome(sampleToolOutcome())
            }
        }

    @Test
    fun `TEL-011 TelemetryEventPort emitException succeeds`() =
        runTest {
            val repository = telemetryEventRepository()
            assertDoesNotThrow {
                repository.emitException(sampleExceptionRecord())
            }
        }

    @Test
    fun `TEL-012 TelemetryEventPort emitProviderMetric succeeds`() =
        runTest {
            val repository = telemetryEventRepository()
            assertDoesNotThrow {
                repository.emitProviderMetric(sampleProviderMetric())
            }
        }

    @Test
    fun `TEL-013 TelemetryEventEntity creation with default uploaded=false`() {
        val before = System.currentTimeMillis()
        val entity =
            TelemetryEventEntity(
                id = "evt-1",
                eventType = "INFERENCE_OUTCOME",
                timestampMs = 1_717_000_000_000L,
                severity = "INFO",
                payloadJson = "{}",
            )
        val after = System.currentTimeMillis()
        assertFalse(entity.uploaded)
        assertTrue(entity.createdAt in before..after)
    }

    @Test
    fun `TEL-014 TelemetryEventEntity creation with uploaded=true`() {
        val entity =
            TelemetryEventEntity(
                id = "evt-2",
                eventType = "TOOL_OUTCOME",
                timestampMs = 1_717_000_000_000L,
                severity = "INFO",
                payloadJson = "{}",
                uploaded = true,
            )
        assertTrue(entity.uploaded)
    }

    @Test
    fun `TEL-015 TelemetryEventDao insert stores event`() =
        runTest {
            val entity =
                TelemetryEventEntity(
                    id = "evt-1",
                    eventType = "INFERENCE_OUTCOME",
                    timestampMs = 1_717_000_000_000L,
                    severity = "INFO",
                    payloadJson = "{}",
                )
            telemetryEventDao.insert(entity)
            val pending = telemetryEventDao.getPendingUploads(limit = 100)
            assertEquals(1, pending.size)
            assertEquals("evt-1", pending.single().id)
        }

    @Test
    fun `TEL-016 TelemetryEventDao getPendingUploads returns unuploaded events`() =
        runTest {
            repeat(3) { i ->
                telemetryEventDao.insert(
                    TelemetryEventEntity(
                        id = "pending-$i",
                        eventType = "INFERENCE_OUTCOME",
                        timestampMs = i.toLong(),
                        severity = "INFO",
                        payloadJson = "{}",
                        uploaded = false,
                    ),
                )
            }
            repeat(2) { i ->
                telemetryEventDao.insert(
                    TelemetryEventEntity(
                        id = "uploaded-$i",
                        eventType = "INFERENCE_OUTCOME",
                        timestampMs = (10 + i).toLong(),
                        severity = "INFO",
                        payloadJson = "{}",
                        uploaded = true,
                    ),
                )
            }
            val pending = telemetryEventDao.getPendingUploads(limit = 100)
            assertEquals(3, pending.size)
            assertTrue(pending.all { !it.uploaded })
        }

    @Test
    fun `TEL-017 TelemetryEventDao markUploaded sets uploaded flag`() =
        runTest {
            telemetryEventDao.insert(
                TelemetryEventEntity(
                    id = "evt-1",
                    eventType = "INFERENCE_OUTCOME",
                    timestampMs = 1L,
                    severity = "INFO",
                    payloadJson = "{}",
                    uploaded = false,
                ),
            )
            telemetryEventDao.insert(
                TelemetryEventEntity(
                    id = "evt-2",
                    eventType = "INFERENCE_OUTCOME",
                    timestampMs = 2L,
                    severity = "INFO",
                    payloadJson = "{}",
                    uploaded = false,
                ),
            )
            telemetryEventDao.markUploaded(listOf("evt-1"))
            val pending = telemetryEventDao.getPendingUploads(limit = 100)
            assertEquals(1, pending.size)
            assertEquals("evt-2", pending.single().id)
        }

    @Test
    fun `TEL-018 TelemetryEventDao evictOldest deletes oldest rows`() =
        runTest {
            listOf(1000L, 2000L, 3000L, 4000L, 5000L).forEachIndexed { index, createdAt ->
                telemetryEventDao.insert(
                    TelemetryEventEntity(
                        id = "evt-$index",
                        eventType = "INFERENCE_OUTCOME",
                        timestampMs = createdAt,
                        severity = "INFO",
                        payloadJson = "{}",
                        createdAt = createdAt,
                    ),
                )
            }
            telemetryEventDao.evictOldest(count = 2)
            val remaining = telemetryEventDao.getPendingUploads(limit = 100)
            assertEquals(3, remaining.size)
            assertEquals(listOf(3000L, 4000L, 5000L), remaining.map { it.timestampMs }.sorted())
        }

    @Test
    fun `TEL-019 TelemetryEventDao getCount returns accurate count`() =
        runTest {
            repeat(7) { i ->
                telemetryEventDao.insert(
                    TelemetryEventEntity(
                        id = "evt-$i",
                        eventType = "INFERENCE_OUTCOME",
                        timestampMs = i.toLong(),
                        severity = "INFO",
                        payloadJson = "{}",
                    ),
                )
            }
            assertEquals(7, telemetryEventDao.getCount())
        }

    @Test
    fun `TEL-020 TelemetryEventMappers toEntity converts InferenceOutcome`() {
        val outcome =
            InferenceOutcome(
                eventId = "evt-1",
                timestampMs = 1_717_000_000_000L,
                provider = "OpenAI",
                modelId = "gpt-4.1",
                latencyMs = 1500,
                tokenCount = null,
                costUsd = null,
                success = true,
                errorMessage = null,
                severity = TelemetrySeverity.INFO,
            )
        val entity = outcome.toEntity()
        assertEquals("evt-1", entity.id)
        assertEquals("INFERENCE_OUTCOME", entity.eventType)
        assertEquals(1_717_000_000_000L, entity.timestampMs)
        assertEquals("INFO", entity.severity)
        assertFalse(entity.uploaded)
    }

    @Test
    fun `TEL-021 TelemetryEventMappers toDomain converts entity back`() {
        val entity =
            TelemetryEventEntity(
                id = "evt-1",
                eventType = "INFERENCE_OUTCOME",
                timestampMs = 1_717_000_000_000L,
                severity = "INFO",
                payloadJson =
                    """{"eventId":"evt-1","timestampMs":1717000000000,"provider":"OpenAI","modelId":"gpt-4.1","latencyMs":1500,"success":true,"severity":"INFO"}""",
            )
        val domain = entity.toDomain() as InferenceOutcome
        assertEquals("evt-1", domain.eventId)
        assertEquals("OpenAI", domain.provider)
        assertTrue(domain.success)
    }

    @Test
    fun `TEL-022 EnclaveDatabase includes TelemetryEventEntity`() {
        val schemaFile =
            projectRoot().resolve(
                "core/data/schemas/com.browntowndev.enclave.core.data.local.EnclaveDatabase/5.json",
            )
        assertTrue(Files.exists(schemaFile), "Expected Room schema export for version 5")
        val schemaJson = schemaFile.readText()
        assertTrue(schemaJson.contains("\"version\": 5"))
        assertTrue(schemaJson.contains("\"tableName\": \"telemetry_events\""))
    }

    private fun projectRoot(): Path {
        var current = Path.of(System.getProperty("user.dir"))
        while (current.parent != null && !Files.exists(current.resolve("settings.gradle.kts"))) {
            current = current.parent
        }
        return current
    }

    @Test
    fun `TEL-023 DataModule provides TelemetryEventDao`() {
        val provideMethod =
            DataModule::class.memberFunctions.firstOrNull { it.name == "provideTelemetryEventDao" }
        assertNotNull(provideMethod, "DataModule must provide TelemetryEventDao")
    }

    @Test
    fun `TEL-024 TelemetryEventRepositoryImpl buffers InferenceOutcome`() =
        runTest {
            val dao = FakeTelemetryEventDao()
            val repository = TelemetryEventRepositoryImpl(dao, FakeLoggingPort())
            repository.emitInferenceOutcome(sampleInferenceOutcome())
            assertEquals(1, dao.insertedEvents.size)
            assertEquals("INFERENCE_OUTCOME", dao.insertedEvents.single().eventType)
        }

    @Test
    fun `TEL-025 TelemetryEventRepositoryImpl buffers ToolOutcome`() =
        runTest {
            val dao = FakeTelemetryEventDao()
            val repository = TelemetryEventRepositoryImpl(dao, FakeLoggingPort())
            repository.emitToolOutcome(sampleToolOutcome())
            assertEquals(1, dao.insertedEvents.size)
            assertEquals("TOOL_OUTCOME", dao.insertedEvents.single().eventType)
        }

    @Test
    fun `TEL-026 TelemetryEventRepositoryImpl buffers ExceptionRecord`() =
        runTest {
            val dao = FakeTelemetryEventDao()
            val repository = TelemetryEventRepositoryImpl(dao, FakeLoggingPort())
            repository.emitException(sampleExceptionRecord())
            assertEquals(1, dao.insertedEvents.size)
            assertEquals("EXCEPTION_RECORD", dao.insertedEvents.single().eventType)
        }

    @Test
    fun `TEL-027 TelemetryEventRepositoryImpl triggers eviction at 50k rows`() =
        runTest {
            val dao = FakeTelemetryEventDao()
            dao.countResult = 50_001
            val repository = TelemetryEventRepositoryImpl(dao, FakeLoggingPort())
            repository.emitInferenceOutcome(sampleInferenceOutcome())
            assertEquals(listOf(1000), dao.evictOldestCalls)
        }

    @Test
    fun `TEL-028 TelemetryEventRepositoryImpl handles Room insert failure silently`() =
        runTest {
            val dao = FakeTelemetryEventDao()
            dao.insertFailure = RuntimeException("Disk full")
            val repository = TelemetryEventRepositoryImpl(dao, FakeLoggingPort())
            assertDoesNotThrow {
                repository.emitInferenceOutcome(sampleInferenceOutcome())
            }
        }

    @Test
    fun `TEL-029 TelemetryOtlpClient uploadBatch succeeds`() =
        runTest {
            val transport = TelemetryOtlpTransport { Result.success(Unit) }
            val client = TelemetryOtlpClientImpl(transport)
            val events =
                (1..5).map { i ->
                    TelemetryEventEntity(
                        id = "evt-$i",
                        eventType = "INFERENCE_OUTCOME",
                        timestampMs = i.toLong(),
                        severity = "INFO",
                        payloadJson = """{"eventId":"evt-$i"}""",
                    )
                }
            val result = client.uploadBatch(events)
            assertTrue(result.isSuccess)
            val requestBody = client.buildOtlpRequestBodyForTest(events)
            events.forEach { event ->
                assertTrue(requestBody.contains(event.id))
                assertTrue(requestBody.contains("enclave.event.payload"))
            }
            assertTrue(requestBody.contains("resourceLogs"))
        }

    @Test
    fun `TEL-030 TelemetryOtlpClient uploadBatch handles network failure`() =
        runTest {
            val transport = TelemetryOtlpTransport { Result.failure(IOException("network unavailable")) }
            val client = TelemetryOtlpClientImpl(transport)
            val events =
                (1..3).map { i ->
                    TelemetryEventEntity(
                        id = "evt-$i",
                        eventType = "INFERENCE_OUTCOME",
                        timestampMs = i.toLong(),
                        severity = "INFO",
                        payloadJson = "{}",
                    )
                }
            val result = client.uploadBatch(events)
            assertTrue(result.isFailure)
            assertInstanceOf(IOException::class.java, result.exceptionOrNull())
        }

    @Test
    fun `TEL-031 TelemetryUploadWorker doWork uploads pending events`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val params = testWorkerParameters()
            val dao = FakeTelemetryEventDao()
            val otlp = FakeTelemetryOtlpClient()
            val loggingPort = FakeLoggingPort()
            val pending =
                (1..50).map { i ->
                    TelemetryEventEntity(
                        id = "evt-$i",
                        eventType = "INFERENCE_OUTCOME",
                        timestampMs = i.toLong(),
                        severity = "INFO",
                        payloadJson = "{}",
                    )
                }
            dao.pendingUploadsResult = pending
            val worker = TelemetryUploadWorker(context, params, dao, otlp, loggingPort, FakeEntitlementPort())
            val result = worker.doWork()
            assertEquals(
                androidx.work.ListenableWorker.Result
                    .success(),
                result,
            )
            assertEquals(listOf(pending), otlp.uploadCalls)
        }

    @Test
    fun `TEL-032 TelemetryUploadWorker doWork marks events as uploaded`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val params = testWorkerParameters()
            val dao = FakeTelemetryEventDao()
            val otlp = FakeTelemetryOtlpClient()
            val loggingPort = FakeLoggingPort()
            val pending =
                (1..10).map { i ->
                    TelemetryEventEntity(
                        id = "evt-$i",
                        eventType = "INFERENCE_OUTCOME",
                        timestampMs = i.toLong(),
                        severity = "INFO",
                        payloadJson = "{}",
                    )
                }
            dao.pendingUploadsQueue.addLast(pending)
            dao.pendingUploadsQueue.addLast(emptyList())
            val worker = TelemetryUploadWorker(context, params, dao, otlp, loggingPort, FakeEntitlementPort())
            worker.doWork()
            assertEquals(listOf(pending.map { it.id }), dao.markUploadedCalls)
            assertEquals(1, dao.deleteUploadedCallCount)
        }

    @Test
    fun `TEL-033 TelemetryUploadWorker doWork returns retry on partial failure`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val params = testWorkerParameters()
            val dao = FakeTelemetryEventDao()
            val otlp = FakeTelemetryOtlpClient()
            val loggingPort = FakeLoggingPort()
            val batch1 = (1..100).map { i -> pendingEntity("batch1-$i", i.toLong()) }
            val batch2 = (1..100).map { i -> pendingEntity("batch2-$i", (100 + i).toLong()) }
            dao.pendingUploadsQueue.addLast(batch1)
            dao.pendingUploadsQueue.addLast(batch2)
            dao.pendingUploadsQueue.addLast(emptyList())
            otlp.uploadResults.addLast(Result.success(Unit))
            otlp.uploadResults.addLast(Result.failure(IOException("network")))
            val worker = TelemetryUploadWorker(context, params, dao, otlp, loggingPort, FakeEntitlementPort())
            val result = worker.doWork()
            assertEquals(
                androidx.work.ListenableWorker.Result
                    .retry(),
                result,
            )
            assertEquals(listOf(batch1.map { it.id }), dao.markUploadedCalls)
        }

    @Test
    fun `TEL-034 TelemetryUploadWorker doWork returns success when no pending events`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val params = testWorkerParameters()
            val dao = FakeTelemetryEventDao()
            val otlp = FakeTelemetryOtlpClient()
            val loggingPort = FakeLoggingPort()
            dao.pendingUploadsResult = emptyList()
            val worker = TelemetryUploadWorker(context, params, dao, otlp, loggingPort, FakeEntitlementPort())
            val result = worker.doWork()
            assertEquals(
                androidx.work.ListenableWorker.Result
                    .success(),
                result,
            )
            assertTrue(otlp.uploadCalls.isEmpty())
        }

    /**
     * Real test [WorkManager] whose executor never runs enqueued work (ENC-28):
     * the scheduler tests assert enqueue-time WorkInfo state only. WorkManager
     * cannot be subclassed in work-runtime 2.11, so the former FakeWorkManager
     * recordings are converted to behavioral assertions on real WorkInfo.
     */
    private fun holdingWorkManager(): WorkManager {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config =
            Configuration
                .Builder()
                .setTaskExecutor(SynchronousExecutor())
                .setExecutor(Executor { /* hold: enqueued work never runs */ })
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        return WorkManager.getInstance(context)
    }

    private fun uniqueWorkInfos(
        workManager: WorkManager,
        uniqueName: String,
    ): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(uniqueName).get()

    @Test
    fun `TEL-035 TelemetryUploadScheduler enqueues periodic work`() {
        val workManager = holdingWorkManager()
        val scheduler =
            TelemetryUploadScheduler(
                workManager = workManager,
                config =
                    TelemetryUploadConfig(
                        endpointUrl = "https://enclaveapp.grafana.net/otlp/v1/logs",
                        apiKey = "test-key",
                    ),
                // Unknown entitlement uploads exactly as before the gate.
                entitlementPort = FakeEntitlementPort(),
            )
        scheduler.schedule()
        // One-time upload: exactly one entry under the one-time name, CONNECTED-constrained.
        val oneTime = uniqueWorkInfos(workManager, TelemetryUploadScheduler.ONE_TIME_WORK_NAME)
        assertEquals(1, oneTime.size)
        assertEquals(
            NetworkType.CONNECTED,
            oneTime.single().constraints.requiredNetworkType,
        )
        // REPLACE, proven behaviorally (the fake recorded the policy): scheduling
        // again leaves exactly one live one-time entry, with a new work id.
        scheduler.schedule()
        val liveOneTime =
            uniqueWorkInfos(workManager, TelemetryUploadScheduler.ONE_TIME_WORK_NAME)
                .filter { it.state != WorkInfo.State.CANCELLED }
        assertEquals(1, liveOneTime.size)
        assertNotEquals(oneTime.single().id, liveOneTime.single().id)
        // Periodic upload: exactly one entry, CONNECTED, 15-minute interval.
        val periodic = uniqueWorkInfos(workManager, TelemetryUploadScheduler.WORK_NAME)
        assertEquals(1, periodic.size)
        assertEquals(
            NetworkType.CONNECTED,
            periodic.single().constraints.requiredNetworkType,
        )
        assertEquals(
            TimeUnit.MINUTES.toMillis(15),
            periodic.single().periodicityInfo?.repeatIntervalMillis,
        )
        // UPDATE, proven behaviorally: the second schedule() above added no
        // second periodic entry (the size-1 assertion already ran after it).
    }

    @Test
    fun `TEL-036 DataModule binds TelemetryEventPort to repository`() {
        val bindsMethod =
            DataModule::class.memberFunctions.firstOrNull { function ->
                function.name.contains("TelemetryEventPort", ignoreCase = true) ||
                    function.annotations.any { it.toString().contains("Binds") }
            }
        val providesTelemetryPort =
            DataModule::class.memberFunctions.any { it.name == "provideTelemetryEventPort" } ||
                DataModule::class.nestedClasses.any { nested ->
                    nested.simpleName?.contains("Bind", ignoreCase = true) == true
                }
        assertTrue(
            bindsMethod != null || providesTelemetryPort,
            "DataModule must bind TelemetryEventPort to TelemetryEventRepositoryImpl",
        )
    }

    @Test
    fun `TEL-045 CompositeToolExecutor emits ToolOutcome on success`() =
        runTest {
            val telemetryPort = FakeTelemetryEventPort()
            val extractExecutor = scriptedExtractExecutor()
            extractExecutor.nextResult =
                com.browntowndev.enclave.domain.model.inference.ToolExecutionResult(
                    toolName = ToolDefinition.WEB_EXTRACT.name,
                    resultJson = "{}",
                )
            val executor = createCompositeToolExecutor(telemetryPort, extractToolExecutor = extractExecutor)
            val request =
                ToolCallRequest(
                    toolName = ToolDefinition.WEB_EXTRACT.name,
                    argumentsJson = """{"urls":["https://example.com"]}""",
                    provider = "test",
                    modelType = com.browntowndev.enclave.domain.model.inference.ModelType.FAST,
                )
            executor.execute(request)
            assertTrue(
                telemetryPort.emittedToolOutcomes.any {
                    it.toolName == "extract" && it.success && it.severity == TelemetrySeverity.INFO
                },
            )
        }

    @Test
    fun `TEL-046 CompositeToolExecutor emits ToolOutcome on failure`() =
        runTest {
            val telemetryPort = FakeTelemetryEventPort()
            val extractExecutor = scriptedExtractExecutor()
            extractExecutor.nextFailure = RuntimeException("Tool failed")
            val executor = createCompositeToolExecutor(telemetryPort, extractToolExecutor = extractExecutor)
            val request =
                ToolCallRequest(
                    toolName = ToolDefinition.WEB_EXTRACT.name,
                    argumentsJson = """{"urls":["https://example.com"]}""",
                    provider = "test",
                    modelType = com.browntowndev.enclave.domain.model.inference.ModelType.FAST,
                )
            try {
                executor.execute(request)
            } catch (_: RuntimeException) {
            }
            assertTrue(
                telemetryPort.emittedToolOutcomes.any {
                    !it.success &&
                        it.errorMessage == "Tool failed" &&
                        it.severity == TelemetrySeverity.ERROR
                },
            )
        }

    @Test
    fun `TEL-047 Individual tool executors emit ToolOutcome`() =
        runTest {
            val telemetryPort = FakeTelemetryEventPort()
            val settingsRepository =
                FakeSettingsRepository(
                    com.browntowndev.enclave.domain.port.repository.SettingsData(
                        searchEnabled = true,
                        searchProvider = SearchProvider.TAVILY,
                    ),
                )
            val context = ApplicationProvider.getApplicationContext<Context>()
            val apiKeyManager = testApiKeyManager(context, CoroutineScope(SupervisorJob() + Dispatchers.Default), "tel_047_prefs")
            val tavily = FakeTavilySearchRepository(apiKeyManager)
            tavily.searchResult = """{"results":[]}"""
            val exa = ExaSearchRepository(OkHttpClient(), apiKeyManager)
            val executor =
                SearchToolExecutorImpl(
                    loggingPort = FakeLoggingPort(),
                    settingsRepository = settingsRepository,
                    tavilySearchRepository = tavily,
                    exaSearchRepository = exa,
                    telemetryEventPort = telemetryPort,
                )
            executor.execute(
                ToolCallRequest(
                    toolName = ToolDefinition.WEB_SEARCH.name,
                    argumentsJson = """{"query":"kotlin"}""",
                    provider = "test",
                    modelType = com.browntowndev.enclave.domain.model.inference.ModelType.FAST,
                ),
            )
            assertTrue(
                telemetryPort.emittedToolOutcomes.any { it.toolName == "search" && it.success },
            )
        }

    @Test
    fun `TEL-048 Individual tool executors emit ToolOutcome on failure`() =
        runTest {
            val telemetryPort = FakeTelemetryEventPort()
            val settingsRepository =
                FakeSettingsRepository(
                    com.browntowndev.enclave.domain.port.repository.SettingsData(
                        searchEnabled = true,
                        searchProvider = SearchProvider.TAVILY,
                    ),
                )
            val context = ApplicationProvider.getApplicationContext<Context>()
            val apiKeyManager = testApiKeyManager(context, CoroutineScope(SupervisorJob() + Dispatchers.Default), "tel_048_prefs")
            val tavily = FakeTavilySearchRepository(apiKeyManager)
            tavily.searchFailure = IOException("Network error")
            val exa = ExaSearchRepository(OkHttpClient(), apiKeyManager)
            val executor =
                SearchToolExecutorImpl(
                    loggingPort = FakeLoggingPort(),
                    settingsRepository = settingsRepository,
                    tavilySearchRepository = tavily,
                    exaSearchRepository = exa,
                    telemetryEventPort = telemetryPort,
                )
            executor.execute(
                ToolCallRequest(
                    toolName = ToolDefinition.WEB_SEARCH.name,
                    argumentsJson = """{"query":"kotlin"}""",
                    provider = "test",
                    modelType = com.browntowndev.enclave.domain.model.inference.ModelType.FAST,
                ),
            )
            assertTrue(
                telemetryPort.emittedToolOutcomes.any {
                    !it.success &&
                        it.errorMessage == "Network error" &&
                        it.severity == TelemetrySeverity.ERROR
                },
            )
        }

    @Test
    fun `TEL-058 TelemetryUploadScheduler no-ops when telemetry is not configured`() {
        val workManager = holdingWorkManager()
        val scheduler = TelemetryUploadScheduler(workManager, TelemetryUploadConfig(), FakeEntitlementPort())
        scheduler.schedule()
        // Conversion (strengthened): the fake-era test asserted only that no
        // periodic call was recorded; against a real WorkManager, neither
        // unique name may hold any work.
        assertTrue(uniqueWorkInfos(workManager, TelemetryUploadScheduler.WORK_NAME).isEmpty())
        assertTrue(uniqueWorkInfos(workManager, TelemetryUploadScheduler.ONE_TIME_WORK_NAME).isEmpty())
    }

    @Test
    fun `TEL-059 TelemetryEventMappers round-trip ProviderMetric`() {
        val metric =
            ProviderMetric(
                eventId = "evt-5",
                timestampMs = 1_717_000_000_000L,
                provider = "OpenAI",
                metricName = "ttfb_ms",
                metricValue = 250.0,
                severity = TelemetrySeverity.INFO,
            )
        val entity = metric.toEntity()
        val roundTripped = entity.toDomain() as ProviderMetric
        assertEquals("evt-5", roundTripped.eventId)
        assertEquals("ttfb_ms", roundTripped.metricName)
        assertEquals(250.0, roundTripped.metricValue)
        assertEquals(null, roundTripped.baseUrl)

        val metricWithUrl =
            ProviderMetric(
                eventId = "evt-5-url",
                timestampMs = 1_717_000_000_000L,
                provider = "OpenAI",
                metricName = "cache_hit_rate",
                metricValue = 0.95,
                severity = TelemetrySeverity.INFO,
                baseUrl = "https://api.deepseek.com",
            )
        val entityWithUrl = metricWithUrl.toEntity()
        val roundTrippedWithUrl = entityWithUrl.toDomain() as ProviderMetric
        assertEquals("evt-5-url", roundTrippedWithUrl.eventId)
        assertEquals("cache_hit_rate", roundTrippedWithUrl.metricName)
        assertEquals(0.95, roundTrippedWithUrl.metricValue)
        assertEquals("https://api.deepseek.com", roundTrippedWithUrl.baseUrl)
    }

    @Test
    fun `TEL-062 TelemetryEventMappers round-trip ToolOutcome`() {
        val outcome =
            ToolOutcome(
                eventId = "evt-6",
                timestampMs = 1_717_000_000_000L,
                toolName = "search",
                latencyMs = 1500,
                success = true,
                errorMessage = null,
                severity = TelemetrySeverity.INFO,
                provider = "Anthropic",
                modelId = "claude-3-5-sonnet",
            )
        val entity = outcome.toEntity()
        val roundTripped = entity.toDomain() as ToolOutcome
        assertEquals("evt-6", roundTripped.eventId)
        assertEquals("search", roundTripped.toolName)
        assertEquals("Anthropic", roundTripped.provider)
        assertEquals("claude-3-5-sonnet", roundTripped.modelId)
    }

    @Test
    fun `TEL-060 TelemetryEventRepositoryImpl buffers ProviderMetric`() =
        runTest {
            val dao = FakeTelemetryEventDao()
            val repository = TelemetryEventRepositoryImpl(dao, FakeLoggingPort())
            repository.emitProviderMetric(sampleProviderMetric())
            assertEquals(1, dao.insertedEvents.size)
            assertEquals("PROVIDER_METRIC", dao.insertedEvents.single().eventType)
        }

    @Test
    fun `TEL-061 TelemetryEventRepositoryImpl redacts sensitive fields before persistence`() =
        runTest {
            val dao = FakeTelemetryEventDao()
            val repository = TelemetryEventRepositoryImpl(dao, FakeLoggingPort())
            val record =
                ExceptionRecord(
                    eventId = "evt-pii",
                    timestampMs = 1_717_000_000_000L,
                    tag = "Tool",
                    message = "Failed for user@example.com with card 4111-1111-1111-1111",
                    throwableType = "RuntimeException",
                    stackTrace = "at com.example.Tool.run(/home/user/secret.txt:42)",
                    severity = TelemetrySeverity.ERROR,
                )
            repository.emitException(record)
            val entity = dao.insertedEvents.single()
            assertFalse(entity.payloadJson.contains("user@example.com"))
            assertFalse(entity.payloadJson.contains("4111-1111-1111-1111"))
            assertFalse(entity.payloadJson.contains("/home/user/secret.txt"))
        }

    @Test
    fun `TEL-063 Telemetry upload uses eventId for server-side dedupe`() =
        runTest {
            val transport = TelemetryOtlpTransport { Result.success(Unit) }
            val client = TelemetryOtlpClientImpl(transport)
            val events =
                listOf(
                    TelemetryEventEntity(
                        id = "dedupe-evt-1",
                        eventType = "INFERENCE_OUTCOME",
                        timestampMs = 1L,
                        severity = "INFO",
                        payloadJson = """{"eventId":"dedupe-evt-1"}""",
                    ),
                )
            val requestBody = client.buildOtlpRequestBodyForTest(events)
            assertTrue(requestBody.contains("resourceLogs"))
            assertTrue(requestBody.contains("dedupe-evt-1"))
            assertTrue(requestBody.contains("enclave.event.id"))
        }

    private fun telemetryEventRepository(): TelemetryEventRepositoryImpl =
        TelemetryEventRepositoryImpl(
            dao = FakeTelemetryEventDao(),
            loggingPort = FakeLoggingPort(),
        )

    private fun pendingEntity(
        id: String,
        timestampMs: Long,
    ) = TelemetryEventEntity(
        id = id,
        eventType = "INFERENCE_OUTCOME",
        timestampMs = timestampMs,
        severity = "INFO",
        payloadJson = "{}",
    )

    private fun createCompositeToolExecutor(
        telemetryPort: TelemetryEventPort,
        extractToolExecutor: ExtractToolExecutorImpl,
    ): CompositeToolExecutor {
        val deps = InertDeps()
        return CompositeToolExecutor(
            searchToolExecutor =
                SearchToolExecutorImpl(
                    loggingPort = deps.loggingPort,
                    settingsRepository = deps.settingsRepository,
                    tavilySearchRepository = deps.tavilySearchRepository,
                    exaSearchRepository = deps.exaSearchRepository,
                    telemetryEventPort = null,
                ),
            imageInspectToolExecutor =
                ImageInspectToolExecutor(
                    loggingPort = deps.loggingPort,
                    messageRepository = deps.messageRepository,
                    analyzeImageUseCase = deps.analyzeImageUseCase,
                ),
            extractToolExecutor = extractToolExecutor,
            searchChatHistoryToolExecutor =
                SearchChatHistoryToolExecutor(
                    loggingPort = deps.loggingPort,
                    messageRepository = deps.messageRepository,
                    chatRepository = FakeChatRepository(),
                    embeddingEngine = FakeEmbeddingEngine(),
                ),
            searchChatToolExecutor =
                SearchChatToolExecutor(
                    loggingPort = deps.loggingPort,
                    messageRepository = deps.messageRepository,
                    embeddingEngine = FakeEmbeddingEngine(),
                ),
            getMessageContextToolExecutor =
                GetMessageContextToolExecutor(
                    loggingPort = deps.loggingPort,
                    messageRepository = deps.messageRepository,
                ),
            manageMemoriesToolExecutor =
                ManageMemoriesToolExecutor(
                    memoriesRepository = FakeMemoriesRepository(),
                    embeddingEnginePort = FakeEmbeddingEngine(),
                ),
            artifactToolExecutor =
                ArtifactToolExecutor(
                    documentDao = database.documentDao(),
                    embeddingDao = database.embeddingDao(),
                    embeddingEngine = FakeEmbeddingEngine(),
                    documentValidation = FakeDocumentValidationPort(),
                    localEncryption = FakeLocalDataEncryptionService(),
                    externalScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                ),
            searchArtifactToolExecutor =
                SearchArtifactToolExecutor(
                    documentDao = database.documentDao(),
                    embeddingEngine = FakeEmbeddingEngine(),
                    localEncryption = FakeLocalDataEncryptionService(),
                ),
            xSearchToolExecutor =
                XSearchToolExecutorImpl(
                    loggingPort = deps.loggingPort,
                    settingsRepository = deps.settingsRepository,
                    xSearchRepository = deps.xSearchRepository,
                    buildXQueryUseCase = BuildXQueryUseCase(),
                ),
            xGetThreadToolExecutor =
                XGetThreadToolExecutor(
                    loggingPort = deps.loggingPort,
                    settingsRepository = deps.settingsRepository,
                    xSearchRepository = deps.xSearchRepository,
                ),
            xTrendingToolExecutor =
                XTrendingToolExecutor(
                    loggingPort = deps.loggingPort,
                    settingsRepository = deps.settingsRepository,
                    xSearchRepository = deps.xSearchRepository,
                ),
            xUserTweetsToolExecutor =
                XUserTweetsToolExecutor(
                    loggingPort = deps.loggingPort,
                    settingsRepository = deps.settingsRepository,
                    xSearchRepository = deps.xSearchRepository,
                ),
            xMentionSearchToolExecutor =
                XMentionSearchToolExecutor(
                    loggingPort = deps.loggingPort,
                    settingsRepository = deps.settingsRepository,
                    xSearchRepository = deps.xSearchRepository,
                ),
            mediaToolExecutor =
                MediaToolExecutor(
                    generateMediaUseCase = deps.generateMediaUseCase,
                    loggingPort = deps.loggingPort,
                ),
            eventBus = ToolExecutionEventBus(),
            telemetryEventPort = telemetryPort,
        )
    }

    private fun scriptedExtractExecutor(): ScriptedExtractToolExecutor {
        val deps = InertDeps()
        return ScriptedExtractToolExecutor(
            loggingPort = deps.loggingPort,
            settingsRepository = deps.settingsRepository,
            tavilySearchRepository = deps.tavilySearchRepository,
            exaSearchRepository = deps.exaSearchRepository,
            webSearchSourceDao = database.webSearchSourceDao(),
            eventBus = ToolExecutionEventBus(),
            extractedUrlTracker = FakeExtractedUrlTracker(),
        )
    }

    /** Extract executor whose [execute] result/failure is scripted per test. */
    private class ScriptedExtractToolExecutor(
        loggingPort: com.browntowndev.enclave.domain.port.inference.LoggingPort,
        settingsRepository: com.browntowndev.enclave.domain.port.repository.SettingsRepository,
        tavilySearchRepository: TavilySearchRepository,
        exaSearchRepository: ExaSearchRepository,
        webSearchSourceDao: com.browntowndev.enclave.core.data.local.WebSearchSourceDao,
        eventBus: ToolExecutionEventBus,
        extractedUrlTracker: com.browntowndev.enclave.domain.port.repository.ExtractedUrlTrackerPort,
    ) : ExtractToolExecutorImpl(
            loggingPort,
            settingsRepository,
            tavilySearchRepository,
            exaSearchRepository,
            webSearchSourceDao,
            eventBus,
            extractedUrlTracker,
        ) {
        var nextResult: com.browntowndev.enclave.domain.model.inference.ToolExecutionResult? = null
        var nextFailure: Throwable? = null

        override suspend fun execute(
            request: ToolCallRequest,
        ): com.browntowndev.enclave.domain.model.inference.ToolExecutionResult {
            nextFailure?.let { throw it }
            return nextResult ?: error("ScriptedExtractToolExecutor: no nextResult programmed")
        }
    }

    /** Tavily repository whose [search] result/failure is scripted per test. */
    private class FakeTavilySearchRepository(
        apiKeyManager: ApiKeyManager,
    ) : TavilySearchRepository(OkHttpClient(), apiKeyManager) {
        var searchResult: String = "{}"
        var searchFailure: Throwable? = null

        override fun search(query: String): String {
            searchFailure?.let { throw it }
            return searchResult
        }
    }

    /** [TelemetryOtlpClient] fake: records batches and replays scripted results. */
    private class FakeTelemetryOtlpClient : TelemetryOtlpClient {
        val uploadCalls = mutableListOf<List<TelemetryEventEntity>>()
        val uploadResults = ArrayDeque<Result<Unit>>()

        override suspend fun uploadBatch(events: List<TelemetryEventEntity>): Result<Unit> {
            uploadCalls += events
            return if (uploadResults.isNotEmpty()) uploadResults.removeFirst() else Result.success(Unit)
        }
    }

    /** Inert real collaborators used only to satisfy executor constructors. */
    private inner class InertDeps {
        private val context = ApplicationProvider.getApplicationContext<Context>()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val apiKeyManager: ApiKeyManager = testApiKeyManager(context, scope, "tel_composite_prefs")
        val loggingPort = FakeLoggingPort()
        val settingsRepository = FakeSettingsRepository()
        val messageRepository = FakeMessageRepository()
        val tavilySearchRepository = TavilySearchRepository(OkHttpClient(), apiKeyManager)
        val exaSearchRepository = ExaSearchRepository(OkHttpClient(), apiKeyManager)
        val xSearchRepository =
            XSearchRepository(OkHttpClient(), apiKeyManager, XUserTimelineFetcher(OkHttpClient(), apiKeyManager))
        val analyzeImageUseCase =
            AnalyzeImageUseCase(
                inferenceFactoryProvider = Provider { FakeInferenceFactory() },
                loggingPort = FakeLoggingPort(),
            )
        val generateMediaUseCase: GenerateMediaUseCase

        init {
            val defaultModelRepository = FakeDefaultModelRepository()
            val mediaProviderRepository = FakeMediaProviderRepository()
            val resolveMediaModelTypeUseCase = ResolveMediaModelTypeUseCase()
            val resolveAssignedImageGenerationTargetUseCase =
                ResolveAssignedImageGenerationTargetUseCase(
                    defaultModelRepository = defaultModelRepository,
                    mediaProviderRepository = mediaProviderRepository,
                    localStableDiffusionTargetResolver =
                        LocalStableDiffusionTargetResolver(
                            localModelRepository = FakeLocalModelRepository(),
                            utilityModelFilePort = FakeUtilityModelFile(),
                            gpuCapabilityPort = FakeGpuCapability(),
                            capabilityPort = FakeLocalImageGenerationCapability(),
                            selectBackend = SelectLocalImageGenerationBackendUseCase(),
                        ),
                )
            generateMediaUseCase =
                GenerateMediaUseCase(
                    generateImageUseCase =
                        GenerateImageUseCase(
                            resolveMediaModelTypeUseCase = resolveMediaModelTypeUseCase,
                            resolveAssignedImageGenerationTargetUseCase = resolveAssignedImageGenerationTargetUseCase,
                            imageGenerationPort = FakeImageGenerationPort(),
                        ),
                    generateVideoUseCase =
                        GenerateVideoUseCase(
                            resolveMediaModelTypeUseCase = resolveMediaModelTypeUseCase,
                            resolveAssignedMediaProviderUseCase =
                                ResolveAssignedMediaProviderUseCase(
                                    defaultModelRepository = defaultModelRepository,
                                    mediaProviderRepository = mediaProviderRepository,
                                ),
                            videoGenerationPort = FakeVideoGenerationPort(),
                        ),
                    generateMusicUseCase =
                        GenerateMusicUseCase(
                            defaultModelRepository = defaultModelRepository,
                            mediaProviderRepository = mediaProviderRepository,
                            musicGenerationPort = FakeMusicGenerationPort(),
                        ),
                    resolveMediaModelTypeUseCase = resolveMediaModelTypeUseCase,
                    resolveAssignedImageGenerationTargetUseCase = resolveAssignedImageGenerationTargetUseCase,
                    studioRepository = FakeStudioRepository(),
                    clock = SystemClock(),
                )
        }
    }

    private fun sampleInferenceOutcome() =
        InferenceOutcome(
            eventId = "evt-1",
            timestampMs = 1_717_000_000_000L,
            provider = "OpenAI",
            modelId = "gpt-4.1",
            latencyMs = 1500,
            tokenCount = 500,
            costUsd = BigDecimal("0.0025"),
            success = true,
            errorMessage = null,
            severity = TelemetrySeverity.INFO,
        )

    private fun sampleToolOutcome() =
        ToolOutcome(
            eventId = "evt-2",
            timestampMs = 1_717_000_000_000L,
            toolName = "search",
            latencyMs = 3000,
            success = true,
            errorMessage = null,
            severity = TelemetrySeverity.INFO,
        )

    private fun sampleExceptionRecord() =
        ExceptionRecord(
            eventId = "evt-3",
            timestampMs = 1_717_000_000_000L,
            tag = "InferenceService",
            message = "Connection timeout",
            throwableType = "java.io.IOException",
            stackTrace = "at com.example.InferenceService.connect(InferenceService.kt:10)",
            severity = TelemetrySeverity.ERROR,
        )

    private fun sampleProviderMetric() =
        ProviderMetric(
            eventId = "evt-4",
            timestampMs = 1_717_000_000_000L,
            provider = "OpenAI",
            metricName = "ttfb_ms",
            metricValue = 250.0,
            severity = TelemetrySeverity.INFO,
        )

    @Test
    fun `TEL-062 TelemetryUploadScheduler triggerImmediateUpload enqueues one-time work with KEEP`() {
        val workManager = holdingWorkManager()
        val scheduler =
            TelemetryUploadScheduler(
                workManager = workManager,
                config =
                    TelemetryUploadConfig(
                        endpointUrl = "https://enclaveapp.grafana.net/otlp/v1/logs",
                        apiKey = "test-key",
                    ),
                entitlementPort = FakeEntitlementPort(),
            )
        scheduler.triggerImmediateUpload()
        val oneTime = uniqueWorkInfos(workManager, TelemetryUploadScheduler.ONE_TIME_WORK_NAME)
        assertEquals(1, oneTime.size)
        assertEquals(
            NetworkType.CONNECTED,
            oneTime.single().constraints.requiredNetworkType,
        )
        // KEEP, proven behaviorally (the fake recorded the policy): triggering
        // again enqueues nothing new — still one entry, with the same work id.
        scheduler.triggerImmediateUpload()
        val after = uniqueWorkInfos(workManager, TelemetryUploadScheduler.ONE_TIME_WORK_NAME)
        assertEquals(1, after.size)
        assertEquals(oneTime.single().id, after.single().id)
    }

    @Test
    fun `TEL-063 TelemetryEventRepositoryImpl triggers immediate upload on emit`() =
        runTest {
            val dao = FakeTelemetryEventDao()
            val workManager = holdingWorkManager()
            val scheduler =
                TelemetryUploadScheduler(
                    workManager = workManager,
                    config =
                        TelemetryUploadConfig(
                            endpointUrl = "https://enclaveapp.grafana.net/otlp/v1/logs",
                            apiKey = "test-key",
                        ),
                    entitlementPort = FakeEntitlementPort(),
                )
            val repository = TelemetryEventRepositoryImpl(dao, FakeLoggingPort(), scheduler)
            repository.emitInferenceOutcome(sampleInferenceOutcome())
            // triggerImmediateUpload() on a configured scheduler enqueues exactly
            // one one-time upload under ONE_TIME_WORK_NAME (the KEEP policy itself
            // is proven behaviorally in TEL-062).
            val oneTime = uniqueWorkInfos(workManager, TelemetryUploadScheduler.ONE_TIME_WORK_NAME)
            assertEquals(1, oneTime.size)
        }

    @Test
    fun `TEL-064 TelemetryUploadScheduler enqueues nothing when NotEntitled`() {
        val workManager = holdingWorkManager()
        val scheduler =
            TelemetryUploadScheduler(
                workManager = workManager,
                config =
                    TelemetryUploadConfig(
                        endpointUrl = "https://enclaveapp.grafana.net/otlp/v1/logs",
                        apiKey = "test-key",
                    ),
                entitlementPort = FakeEntitlementPort(EntitlementState.NotEntitled),
            )
        scheduler.schedule()
        scheduler.triggerImmediateUpload()
        assertTrue(uniqueWorkInfos(workManager, TelemetryUploadScheduler.WORK_NAME).isEmpty())
        assertTrue(uniqueWorkInfos(workManager, TelemetryUploadScheduler.ONE_TIME_WORK_NAME).isEmpty())
    }

    @Test
    fun `TEL-065 TelemetryUploadScheduler enqueues when Entitled`() {
        val workManager = holdingWorkManager()
        val scheduler =
            TelemetryUploadScheduler(
                workManager = workManager,
                config =
                    TelemetryUploadConfig(
                        endpointUrl = "https://enclaveapp.grafana.net/otlp/v1/logs",
                        apiKey = "test-key",
                    ),
                entitlementPort = FakeEntitlementPort(EntitlementState.Entitled()),
            )
        scheduler.schedule()
        assertEquals(1, uniqueWorkInfos(workManager, TelemetryUploadScheduler.WORK_NAME).size)
        assertEquals(1, uniqueWorkInfos(workManager, TelemetryUploadScheduler.ONE_TIME_WORK_NAME).size)
    }

    @Test
    fun `TEL-066 TelemetryUploadWorker succeeds without uploading when NotEntitled`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val params = testWorkerParameters()
            val dao = FakeTelemetryEventDao()
            val otlp = FakeTelemetryOtlpClient()
            dao.pendingUploadsResult = listOf(pendingEntity("evt-gated", 1L))
            val worker =
                TelemetryUploadWorker(
                    context,
                    params,
                    dao,
                    otlp,
                    FakeLoggingPort(),
                    FakeEntitlementPort(EntitlementState.NotEntitled),
                )
            val result = worker.doWork()
            assertEquals(
                androidx.work.ListenableWorker.Result
                    .success(),
                result,
            )
            assertTrue(otlp.uploadCalls.isEmpty())
            assertTrue(dao.markUploadedCalls.isEmpty())
            assertEquals(0, dao.deleteUploadedCallCount)
        }
}
