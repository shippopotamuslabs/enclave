package com.browntowndev.enclave.core.data.remote.firestore.handlers

import com.browntowndev.enclave.core.data.local.MessageInferenceTelemetryDao
import com.browntowndev.enclave.core.data.local.MessageInferenceTelemetryEntity
import com.browntowndev.enclave.core.data.mapper.telemetry.toEntity
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.enclave.core.data.remote.testFirestoreSyncDaos
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.browntowndev.enclave.domain.telemetry.TelemetryTestFixtures
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

class MessageInferenceTelemetrySyncHandlerTest {
    private val telemetryDao = mockk<MessageInferenceTelemetryDao>(relaxed = true)
    private lateinit var handler: MessageInferenceTelemetrySyncHandler
    private val upsertedSlot = slot<MessageInferenceTelemetryEntity>()

    companion object {
        private const val UPDATED_AT = 5_000L
    }

    @BeforeEach
    fun setup() {
        handler = MessageInferenceTelemetrySyncHandler(createContext(telemetryDao))
        coEvery { telemetryDao.upsert(capture(upsertedSlot)) } returns Unit
    }

    @Test
    fun `upsertLocal coalesces null token fields on equal updated_at`() =
        runTest {
            val existing =
                TelemetryTestFixtures.telOpenAi1
                    .toEntity()
                    .copy(
                        updatedAt = UPDATED_AT,
                        pendingSync = 1,
                    )
            val remote =
                existing.copy(
                    inputTokens = null,
                    outputTokens = null,
                    reasoningTokens = null,
                    cacheReadInputTokens = null,
                    firstOutputTokenAtMs = null,
                    completedAtMs = null,
                    costUsdDecimal = null,
                    pendingSync = 0,
                )
            coEvery { telemetryDao.getById(existing.id) } returns existing

            handler.upsertLocal(
                entity = remote,
                doc = mockk<DocumentSnapshot>(relaxed = true),
                overwrite = false,
            )

            // Coalesced merge matches the local row (except pendingSync), so the handler must not
            // upsert a remote doc that would have clobbered token columns with nulls.
            coVerify(exactly = 0) { telemetryDao.upsert(any()) }
        }

    @Test
    fun `upsertLocal coalesces null token fields and upserts when remote adds completedAt`() =
        runTest {
            val existing =
                TelemetryTestFixtures.telOpenAi1
                    .toEntity()
                    .copy(
                        updatedAt = UPDATED_AT,
                        pendingSync = 1,
                        completedAtMs = null,
                    )
            val remote =
                existing.copy(
                    inputTokens = null,
                    outputTokens = null,
                    reasoningTokens = null,
                    cacheReadInputTokens = null,
                    completedAtMs = 9_999L,
                    pendingSync = 0,
                )
            coEvery { telemetryDao.getById(existing.id) } returns existing

            handler.upsertLocal(
                entity = remote,
                doc = mockk<DocumentSnapshot>(relaxed = true),
                overwrite = false,
            )

            coVerify(exactly = 1) { telemetryDao.upsert(any()) }
            val merged = upsertedSlot.captured
            assertEquals(1_000, merged.inputTokens)
            assertEquals(200, merged.outputTokens)
            assertEquals(9_999L, merged.completedAtMs)
            assertEquals(0, merged.pendingSync)
        }

    private fun createContext(telemetryDao: MessageInferenceTelemetryDao): FirestoreSyncContext {
        val localEncryption = FakeLocalDataEncryptionService()
        val loggingPort = mockk<LoggingPort>(relaxed = true)
        return FirestoreSyncContext(
            firestore = mockk<FirebaseFirestore>(relaxed = true),
            daos = testFirestoreSyncDaos(messageInferenceTelemetryDao = telemetryDao),
            localEncryption = localEncryption,
            sensitiveSelfHealer = SensitivePersistenceSelfHealer(localEncryption, loggingPort),
            firestoreSensitiveFieldRepairer = mockk(relaxed = true),
            cryptoManager = mockk<CryptoManagerPort>(relaxed = true),
            loggingPort = loggingPort,
            apiKeyManager = mockk(relaxed = true),
            embeddingCoordinator = mockk(relaxed = true),
            userIdProvider = { "user-1" },
            purgeInProgress = AtomicBoolean(false),
        )
    }
}