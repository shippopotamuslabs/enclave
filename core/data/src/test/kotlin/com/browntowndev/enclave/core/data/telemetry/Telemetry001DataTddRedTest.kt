package com.browntowndev.enclave.core.data.telemetry

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
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
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.WebSearchParams
import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.browntowndev.enclave.domain.model.telemetry.ExceptionRecord
import com.browntowndev.enclave.domain.model.telemetry.InferenceOutcome
import com.browntowndev.enclave.domain.model.telemetry.ProviderMetric
import com.browntowndev.enclave.domain.model.telemetry.TelemetrySeverity
import com.browntowndev.enclave.domain.model.telemetry.ToolOutcome
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
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
            val dao = mockk<TelemetryEventDao>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val repository = TelemetryEventRepositoryImpl(dao, loggingPort)
            repository.emitInferenceOutcome(sampleInferenceOutcome())
            coVerify(exactly = 1) {
                dao.insert(match { it.eventType == "INFERENCE_OUTCOME" })
            }
        }

    @Test
    fun `TEL-025 TelemetryEventRepositoryImpl buffers ToolOutcome`() =
        runTest {
            val dao = mockk<TelemetryEventDao>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val repository = TelemetryEventRepositoryImpl(dao, loggingPort)
            repository.emitToolOutcome(sampleToolOutcome())
            coVerify(exactly = 1) {
                dao.insert(match { it.eventType == "TOOL_OUTCOME" })
            }
        }

    @Test
    fun `TEL-026 TelemetryEventRepositoryImpl buffers ExceptionRecord`() =
        runTest {
            val dao = mockk<TelemetryEventDao>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val repository = TelemetryEventRepositoryImpl(dao, loggingPort)
            repository.emitException(sampleExceptionRecord())
            coVerify(exactly = 1) {
                dao.insert(match { it.eventType == "EXCEPTION_RECORD" })
            }
        }

    @Test
    fun `TEL-027 TelemetryEventRepositoryImpl triggers eviction at 50k rows`() =
        runTest {
            val dao = mockk<TelemetryEventDao>(relaxed = true)
            coEvery { dao.getCount() } returns 50_001
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val repository = TelemetryEventRepositoryImpl(dao, loggingPort)
            repository.emitInferenceOutcome(sampleInferenceOutcome())
            coVerify(exactly = 1) { dao.evictOldest(1000) }
        }

    @Test
    fun `TEL-028 TelemetryEventRepositoryImpl handles Room insert failure silently`() =
        runTest {
            val dao = mockk<TelemetryEventDao>(relaxed = true)
            coEvery { dao.insert(any()) } throws RuntimeException("Disk full")
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val repository = TelemetryEventRepositoryImpl(dao, loggingPort)
            assertDoesNotThrow {
                repository.emitInferenceOutcome(sampleInferenceOutcome())
            }
        }

    @Test
    fun `TEL-029 TelemetryOtlpClient uploadBatch succeeds`() =
        runTest {
            val transport = mockk<TelemetryOtlpTransport>()
            coEvery { transport.send(any()) } returns Result.success(Unit)
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
            val transport = mockk<TelemetryOtlpTransport>()
            coEvery { transport.send(any()) } returns Result.failure(IOException("network unavailable"))
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
            val params = mockk<androidx.work.WorkerParameters>(relaxed = true)
            val dao = mockk<TelemetryEventDao>(relaxed = true)
            val otlp = mockk<com.browntowndev.enclave.core.data.remote.telemetry.TelemetryOtlpClient>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
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
            coEvery { dao.getPendingUploads(any()) } returns pending
            coEvery { otlp.uploadBatch(pending) } returns Result.success(Unit)
            val worker = TelemetryUploadWorker(context, params, dao, otlp, loggingPort)
            val result = worker.doWork()
            assertEquals(androidx.work.ListenableWorker.Result.success(), result)
            coVerify { otlp.uploadBatch(pending) }
        }

    @Test
    fun `TEL-032 TelemetryUploadWorker doWork marks events as uploaded`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val params = mockk<androidx.work.WorkerParameters>(relaxed = true)
            val dao = mockk<TelemetryEventDao>(relaxed = true)
            val otlp = mockk<com.browntowndev.enclave.core.data.remote.telemetry.TelemetryOtlpClient>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
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
            coEvery { dao.getPendingUploads(any()) } returns pending andThen emptyList()
            coEvery { otlp.uploadBatch(any()) } returns Result.success(Unit)
            val worker = TelemetryUploadWorker(context, params, dao, otlp, loggingPort)
            worker.doWork()
            coVerify { dao.markUploaded(pending.map { it.id }) }
            coVerify { dao.deleteUploaded() }
        }

    @Test
    fun `TEL-033 TelemetryUploadWorker doWork returns retry on partial failure`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val params = mockk<androidx.work.WorkerParameters>(relaxed = true)
            val dao = mockk<TelemetryEventDao>(relaxed = true)
            val otlp = mockk<com.browntowndev.enclave.core.data.remote.telemetry.TelemetryOtlpClient>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val batch1 = (1..100).map { i -> pendingEntity("batch1-$i", i.toLong()) }
            val batch2 = (1..100).map { i -> pendingEntity("batch2-$i", (100 + i).toLong()) }
            coEvery { dao.getPendingUploads(100) } returns batch1 andThen batch2 andThen emptyList()
            coEvery { otlp.uploadBatch(batch1) } returns Result.success(Unit)
            coEvery { otlp.uploadBatch(batch2) } returns Result.failure(IOException("network"))
            val worker = TelemetryUploadWorker(context, params, dao, otlp, loggingPort)
            val result = worker.doWork()
            assertEquals(androidx.work.ListenableWorker.Result.retry(), result)
            coVerify { dao.markUploaded(batch1.map { it.id }) }
        }

    @Test
    fun `TEL-034 TelemetryUploadWorker doWork returns success when no pending events`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val params = mockk<androidx.work.WorkerParameters>(relaxed = true)
            val dao = mockk<TelemetryEventDao>(relaxed = true)
            val otlp = mockk<com.browntowndev.enclave.core.data.remote.telemetry.TelemetryOtlpClient>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            coEvery { dao.getPendingUploads(any()) } returns emptyList()
            val worker = TelemetryUploadWorker(context, params, dao, otlp, loggingPort)
            val result = worker.doWork()
            assertEquals(androidx.work.ListenableWorker.Result.success(), result)
            coVerify(exactly = 0) { otlp.uploadBatch(any()) }
        }

    @Test
    fun `TEL-035 TelemetryUploadScheduler enqueues periodic work`() {
        val workManager = mockk<WorkManager>(relaxed = true)
        val scheduler =
            TelemetryUploadScheduler(
                workManager = workManager,
                config =
                    TelemetryUploadConfig(
                        endpointUrl = "https://enclaveapp.grafana.net/otlp/v1/logs",
                        apiKey = "test-key",
                    ),
            )
        scheduler.schedule()
        verify {
            workManager.enqueueUniqueWork(
                TelemetryUploadScheduler.ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                match<OneTimeWorkRequest> { request ->
                    request.workSpec.constraints.requiredNetworkType == NetworkType.CONNECTED
                },
            )
            workManager.enqueueUniquePeriodicWork(
                TelemetryUploadScheduler.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                match<PeriodicWorkRequest> { request ->
                    request.workSpec.intervalDuration == TimeUnit.MINUTES.toMillis(15) &&
                        request.workSpec.constraints.requiredNetworkType == NetworkType.CONNECTED
                },
            )
        }
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
            val telemetryPort = mockk<TelemetryEventPort>(relaxed = true)
            val extractExecutor = mockk<ExtractToolExecutorImpl>(relaxed = true)
            coEvery { extractExecutor.execute(any()) } returns
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
            coVerify {
                telemetryPort.emitToolOutcome(
                    match {
                        it.toolName == "extract" && it.success && it.severity == TelemetrySeverity.INFO
                    },
                )
            }
        }

    @Test
    fun `TEL-046 CompositeToolExecutor emits ToolOutcome on failure`() =
        runTest {
            val telemetryPort = mockk<TelemetryEventPort>(relaxed = true)
            val extractExecutor = mockk<ExtractToolExecutorImpl>(relaxed = true)
            coEvery { extractExecutor.execute(any()) } throws RuntimeException("Tool failed")
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
            coVerify {
                telemetryPort.emitToolOutcome(
                    match {
                        !it.success &&
                            it.errorMessage == "Tool failed" &&
                            it.severity == TelemetrySeverity.ERROR
                    },
                )
            }
        }

    @Test
    fun `TEL-047 Individual tool executors emit ToolOutcome`() =
        runTest {
            val telemetryPort = mockk<TelemetryEventPort>(relaxed = true)
            val settingsRepository = mockk<com.browntowndev.enclave.domain.port.repository.SettingsRepository>()
            val tavily = mockk<com.browntowndev.enclave.core.data.repository.TavilySearchRepository>()
            val exa = mockk<com.browntowndev.enclave.core.data.repository.ExaSearchRepository>()
            coEvery { settingsRepository.settingsFlow } returns
                kotlinx.coroutines.flow.flowOf(
                    com.browntowndev.enclave.domain.port.repository.SettingsData(
                        searchEnabled = true,
                        searchProvider = SearchProvider.TAVILY,
                    ),
                )
            coEvery { tavily.search(any()) } returns """{"results":[]}"""
            val executor =
                SearchToolExecutorImpl(
                    loggingPort = mockk(relaxed = true),
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
            coVerify {
                telemetryPort.emitToolOutcome(match { it.toolName == "search" && it.success })
            }
        }

    @Test
    fun `TEL-048 Individual tool executors emit ToolOutcome on failure`() =
        runTest {
            val telemetryPort = mockk<TelemetryEventPort>(relaxed = true)
            val settingsRepository = mockk<com.browntowndev.enclave.domain.port.repository.SettingsRepository>()
            val tavily = mockk<com.browntowndev.enclave.core.data.repository.TavilySearchRepository>()
            val exa = mockk<com.browntowndev.enclave.core.data.repository.ExaSearchRepository>()
            coEvery { settingsRepository.settingsFlow } returns
                kotlinx.coroutines.flow.flowOf(
                    com.browntowndev.enclave.domain.port.repository.SettingsData(
                        searchEnabled = true,
                        searchProvider = SearchProvider.TAVILY,
                    ),
                )
            coEvery { tavily.search(any()) } throws IOException("Network error")
            val executor =
                SearchToolExecutorImpl(
                    loggingPort = mockk(relaxed = true),
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
            coVerify {
                telemetryPort.emitToolOutcome(
                    match {
                        !it.success &&
                            it.errorMessage == "Network error" &&
                            it.severity == TelemetrySeverity.ERROR
                    },
                )
            }
        }

    @Test
    fun `TEL-058 TelemetryUploadScheduler no-ops when telemetry is not configured`() {
        val workManager = mockk<WorkManager>(relaxed = true)
        val scheduler = TelemetryUploadScheduler(workManager, TelemetryUploadConfig())
        scheduler.schedule()
        verify(exactly = 0) {
            workManager.enqueueUniquePeriodicWork(any(), any(), any())
        }
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
            val dao = mockk<TelemetryEventDao>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val repository = TelemetryEventRepositoryImpl(dao, loggingPort)
            repository.emitProviderMetric(sampleProviderMetric())
            coVerify(exactly = 1) {
                dao.insert(match { it.eventType == "PROVIDER_METRIC" })
            }
        }

    @Test
    fun `TEL-061 TelemetryEventRepositoryImpl redacts sensitive fields before persistence`() =
        runTest {
            val dao = mockk<TelemetryEventDao>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val repository = TelemetryEventRepositoryImpl(dao, loggingPort)
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
            coVerify {
                dao.insert(
                    match { entity ->
                        !entity.payloadJson.contains("user@example.com") &&
                            !entity.payloadJson.contains("4111-1111-1111-1111") &&
                            !entity.payloadJson.contains("/home/user/secret.txt")
                    },
                )
            }
        }

    @Test
    fun `TEL-063 Telemetry upload uses eventId for server-side dedupe`() =
        runTest {
            val transport = mockk<TelemetryOtlpTransport>()
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
            dao = mockk<TelemetryEventDao>(relaxed = true),
            loggingPort = mockk<LoggingPort>(relaxed = true),
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
        searchExecutor: SearchToolExecutorImpl = mockk(relaxed = true),
        extractToolExecutor: ExtractToolExecutorImpl = mockk(relaxed = true),
    ): CompositeToolExecutor =
        CompositeToolExecutor(
            searchToolExecutor = searchExecutor,
            imageInspectToolExecutor = mockk(relaxed = true),
            extractToolExecutor = extractToolExecutor,
            searchChatHistoryToolExecutor = mockk(relaxed = true),
            searchChatToolExecutor = mockk(relaxed = true),
            getMessageContextToolExecutor = mockk(relaxed = true),
            manageMemoriesToolExecutor = mockk(relaxed = true),
            artifactToolExecutor = mockk(relaxed = true),
            searchArtifactToolExecutor = mockk(relaxed = true),
            xSearchToolExecutor = mockk(relaxed = true),
            xGetThreadToolExecutor = mockk(relaxed = true),
            xTrendingToolExecutor = mockk(relaxed = true),
            xUserTweetsToolExecutor = mockk(relaxed = true),
            xMentionSearchToolExecutor = mockk(relaxed = true),
            mediaToolExecutor = mockk(relaxed = true),
            eventBus = mockk(relaxed = true),
            telemetryEventPort = telemetryPort,
        )

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
        val workManager = mockk<WorkManager>(relaxed = true)
        val scheduler =
            TelemetryUploadScheduler(
                workManager = workManager,
                config =
                    TelemetryUploadConfig(
                        endpointUrl = "https://enclaveapp.grafana.net/otlp/v1/logs",
                        apiKey = "test-key",
                    ),
            )
        scheduler.triggerImmediateUpload()
        verify {
            workManager.enqueueUniqueWork(
                TelemetryUploadScheduler.ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                match<OneTimeWorkRequest> { request ->
                    request.workSpec.constraints.requiredNetworkType == NetworkType.CONNECTED
                },
            )
        }
    }

    @Test
    fun `TEL-063 TelemetryEventRepositoryImpl triggers immediate upload on emit`() =
        runTest {
            val dao = mockk<TelemetryEventDao>(relaxed = true)
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val scheduler = mockk<TelemetryUploadScheduler>(relaxed = true)
            val repository = TelemetryEventRepositoryImpl(dao, loggingPort, scheduler)
            repository.emitInferenceOutcome(sampleInferenceOutcome())
            verify(exactly = 1) {
                scheduler.triggerImmediateUpload()
            }
        }
}
