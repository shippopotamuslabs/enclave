package com.browntowndev.enclave.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.browntowndev.enclave.core.data.local.ChatEntity
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.messageDao
import com.browntowndev.enclave.core.data.mapper.telemetry.toEntity
import com.browntowndev.enclave.domain.model.telemetry.UsagePeriod
import com.browntowndev.enclave.domain.telemetry.TelemetryTestFixtures
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Date

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class InferenceTelemetryRepositoryImplTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var repository: InferenceTelemetryRepositoryImpl

    @BeforeEach
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        repository =
            InferenceTelemetryRepositoryImpl(
                dao = database.messageInferenceTelemetryDao(),
                messageDao = database.messageDao(),
                clock = Clock.fixed(Instant.parse("2026-05-25T12:00:00Z"), ZoneId.of("UTC")),
                zoneId = ZoneId.of("America/New_York"),
            )
        runBlocking {
            database.chatDao().upsert(
                ChatEntity(
                    id = TelemetryTestFixtures.chat1,
                    name = "Chat",
                    created = Date(),
                    lastModified = Date(),
                    pinned = false,
                ),
            )
            val messageDao = database.messageDao()
            listOf(
                TelemetryTestFixtures.mOpenAi1,
                TelemetryTestFixtures.mAnthropic1,
                TelemetryTestFixtures.mLitert1,
            ).forEach { messageId ->
                messageDao.upsert(
                    MessageEntity(
                        id = messageId,
                        chatId = TelemetryTestFixtures.chat1,
                        content = "Hi",
                        role = com.browntowndev.enclave.domain.model.chat.Role.ASSISTANT,
                    ),
                )
            }
            database.messageInferenceTelemetryDao().insertAll(
                TelemetryTestFixtures.chat1Rows.map { it.toEntity() },
            )
        }
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    @Test
    fun `TEL_012 missing cost rows do not get coerced to zero in totals and are counted explicitly`() =
        runTest {
            repository.observeUsageSpendSummary(UsagePeriod.CURRENT_MONTH).test {
                val summary = awaitItem()
                assertEquals(java.math.BigDecimal("0.005500"), summary.totalCostUsd)
                assertEquals(1, summary.missingCostTurnCount)
                val liteRtRow =
                    summary.providerBreakdown.single { it.providerLabel == "LiteRT" }
                assertEquals(null, liteRtRow.totalCostUsd)
                assertEquals(1, liteRtRow.turnCount)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
