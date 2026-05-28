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
import com.browntowndev.enclave.domain.usecase.telemetry.ObserveChatTelemetryUseCase
import com.browntowndev.enclave.domain.usecase.telemetry.ObserveMessageTelemetryUseCase
import com.browntowndev.enclave.domain.usecase.telemetry.ObserveUsageSpendSummaryUseCase
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
class InferenceTelemetryRehydrateTest {
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
            database.chatDao().upsert(
                ChatEntity(
                    id = TelemetryTestFixtures.chatOther,
                    name = "Other",
                    created = Date(),
                    lastModified = Date(),
                    pinned = false,
                ),
            )
            messageDao.upsert(
                MessageEntity(
                    id = TelemetryTestFixtures.mOpenRouter1,
                    chatId = TelemetryTestFixtures.chatOther,
                    content = "Hi",
                    role = com.browntowndev.enclave.domain.model.chat.Role.ASSISTANT,
                ),
            )
            database.messageInferenceTelemetryDao().insertAll(
                TelemetryTestFixtures.chat1Rows.map { it.toEntity() } +
                    listOf(TelemetryTestFixtures.telOpenRouter1.toEntity()),
            )
        }
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    @Test
    fun `TEL_010 persisted telemetry survives rehydrate and feeds all three surfaces`() =
        runTest {
            val messageUseCase = ObserveMessageTelemetryUseCase(repository)
            val chatUseCase = ObserveChatTelemetryUseCase(repository)
            val spendUseCase = ObserveUsageSpendSummaryUseCase(repository = repository)

            messageUseCase(TelemetryTestFixtures.mOpenAi1).test {
                assertEquals("0.002500", awaitItem().totalCostUsd?.toPlainString())
                cancelAndIgnoreRemainingEvents()
            }
            chatUseCase(TelemetryTestFixtures.chat1).test {
                assertEquals("0.005500", awaitItem().totalCostUsd.toPlainString())
                cancelAndIgnoreRemainingEvents()
            }
            spendUseCase(UsagePeriod.LIFETIME).test {
                assertEquals("0.006700", awaitItem().totalCostUsd.toPlainString())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
