package com.browntowndev.enclave.feature.chat.delegates

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.telemetry.TurnTelemetryId
import com.browntowndev.enclave.domain.telemetry.FakeInferenceTelemetryRepositoryPort
import com.browntowndev.enclave.domain.telemetry.TelemetryTestFixtures
import com.browntowndev.enclave.domain.usecase.telemetry.ObserveChatTelemetryUseCase
import com.browntowndev.enclave.domain.usecase.telemetry.ObserveMessageTelemetryUseCase
import com.browntowndev.enclave.feature.chat.stats.ChatTelemetryUiMapper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatTelemetryCoordinatorTest {
    private val mapper = ChatTelemetryUiMapper()

    @Test
    fun `response stats falls back to in-memory rows only for the clicked message when not persisted`() =
        runTest {
            // The clicked message has no persisted telemetry rows, so the coordinator must use the
            // in-memory rows it is handed (which the ViewModel scopes to the clicked message).
            val repository =
                FakeInferenceTelemetryRepositoryPort(
                    messageRows = emptyMap(),
                )
            val coordinator =
                ChatTelemetryCoordinator(
                    observeMessageTelemetryUseCase = ObserveMessageTelemetryUseCase(repository),
                    observeChatTelemetryUseCase = ObserveChatTelemetryUseCase(repository),
                    mapper = mapper,
                )

            coordinator.onResponseStatsClick(
                messageId = TelemetryTestFixtures.mOpenAi1,
                inMemoryTelemetryRows = listOf(TelemetryTestFixtures.telOpenAi1),
                scope = this,
            )
            // Let the launched collect coroutine drain the single flowOf emission.
            advanceUntilIdle()

            val sheet = coordinator.responseStatsSheet.value
            assertTrue(sheet.isVisible)
            assertEquals(TelemetryTestFixtures.mOpenAi1, sheet.messageId)
            assertEquals("1000", sheet.metricRows.first { it.label == "Input tokens" }.value)
        }

    @Test
    fun `response stats shows unavailable when clicked message has no persisted or in-memory rows`() =
        runTest {
            // Regression guard for the previous fallback bug: when the clicked message had no in-memory
            // rows, the ViewModel used to fall back to ALL messages' rows. The coordinator must now
            // receive an empty in-memory list and render the unavailable state, never another message's
            // telemetry.
            val repository =
                FakeInferenceTelemetryRepositoryPort(
                    messageRows = emptyMap(),
                )
            val coordinator =
                ChatTelemetryCoordinator(
                    observeMessageTelemetryUseCase = ObserveMessageTelemetryUseCase(repository),
                    observeChatTelemetryUseCase = ObserveChatTelemetryUseCase(repository),
                    mapper = mapper,
                )

            coordinator.onResponseStatsClick(
                messageId = TelemetryTestFixtures.mLegacy1,
                inMemoryTelemetryRows = emptyList(),
                scope = this,
            )
            advanceUntilIdle()

            val sheet = coordinator.responseStatsSheet.value
            assertTrue(sheet.isVisible)
            assertEquals("Telemetry unavailable for this response", sheet.availabilityMessage)
            // The unavailable state renders only Provider + Cost rows; no per-metric rows exist. This is
            // the correct behavior — no other message's input tokens (e.g. telOpenAi1's 1000) can leak
            // into the clicked message's sheet.
            assertEquals(2, sheet.metricRows.size)
            assertEquals("Provider", sheet.metricRows.first().label)
            assertEquals("Cost", sheet.metricRows.last().label)
        }

    @Test
    fun `response stats prefers persisted rows over in-memory rows`() =
        runTest {
            val persistedRow =
                TelemetryTestFixtures.telOpenAi1.copy(
                    inputTokens = 5_000,
                    cacheReadInputTokens = 0,
                )
            val repository =
                FakeInferenceTelemetryRepositoryPort(
                    messageRows = mapOf(TelemetryTestFixtures.mOpenAi1 to listOf(persistedRow)),
                )
            val coordinator =
                ChatTelemetryCoordinator(
                    observeMessageTelemetryUseCase = ObserveMessageTelemetryUseCase(repository),
                    observeChatTelemetryUseCase = ObserveChatTelemetryUseCase(repository),
                    mapper = mapper,
                )

            coordinator.onResponseStatsClick(
                messageId = TelemetryTestFixtures.mOpenAi1,
                // Stale in-memory rows must be ignored once persisted data exists.
                inMemoryTelemetryRows = listOf(TelemetryTestFixtures.telOpenAi1),
                scope = this,
            )
            advanceUntilIdle()

            val sheet = coordinator.responseStatsSheet.value
            assertEquals("5000", sheet.metricRows.first { it.label == "Input tokens" }.value)
        }

    @Test
    fun `chat stats sheet merges in-memory in-flight rows when not yet persisted`() =
        runTest {
            val inFlightMessageId = MessageId("m-inflight")
            val inFlightRow =
                TelemetryTestFixtures.telAgent1.copy(
                    id = TurnTelemetryId("tel-inflight-1"),
                    messageId = inFlightMessageId,
                    inputTokens = 2_500,
                )
            val repository =
                FakeInferenceTelemetryRepositoryPort(
                    chatRows = mapOf(TelemetryTestFixtures.chat1 to listOf(TelemetryTestFixtures.telOpenAi1)),
                    chatAssistantTurnCounts = mapOf(TelemetryTestFixtures.chat1 to 2),
                )
            val coordinator =
                ChatTelemetryCoordinator(
                    observeMessageTelemetryUseCase = ObserveMessageTelemetryUseCase(repository),
                    observeChatTelemetryUseCase = ObserveChatTelemetryUseCase(repository),
                    mapper = mapper,
                )

            coordinator.onChatStatsClick(
                chatId = TelemetryTestFixtures.chat1,
                inMemoryTelemetryRows = listOf(inFlightRow),
                scope = this,
            )
            advanceUntilIdle()

            val sheet = coordinator.chatStatsSheet.value
            assertTrue(sheet.isVisible)
            assertEquals("3500", sheet.summaryRows.first { it.label == "Input tokens" }.value)
        }

    @Test
    fun `chat stats sheet merges additional in-memory rows for same message when row id not persisted`() =
        runTest {
            val repository =
                FakeInferenceTelemetryRepositoryPort(
                    chatRows =
                        mapOf(
                            TelemetryTestFixtures.chat1 to listOf(TelemetryTestFixtures.telAgent1),
                        ),
                )
            val coordinator =
                ChatTelemetryCoordinator(
                    observeMessageTelemetryUseCase = ObserveMessageTelemetryUseCase(repository),
                    observeChatTelemetryUseCase = ObserveChatTelemetryUseCase(repository),
                    mapper = mapper,
                )

            coordinator.onChatStatsClick(
                chatId = TelemetryTestFixtures.chat1,
                inMemoryTelemetryRows = listOf(TelemetryTestFixtures.telAgent2),
                scope = this,
            )
            advanceUntilIdle()

            val sheet = coordinator.chatStatsSheet.value
            assertEquals("100", sheet.summaryRows.first { it.label == "Output tokens" }.value)
        }

    @Test
    fun `chat stats sheet drops in-memory rows once they are persisted`() =
        runTest {
            val persistedRow =
                TelemetryTestFixtures.telOpenAi1.copy(
                    inputTokens = 5_000,
                    cacheReadInputTokens = 0,
                )
            val repository =
                FakeInferenceTelemetryRepositoryPort(
                    chatRows = mapOf(TelemetryTestFixtures.chat1 to listOf(persistedRow)),
                )
            val coordinator =
                ChatTelemetryCoordinator(
                    observeMessageTelemetryUseCase = ObserveMessageTelemetryUseCase(repository),
                    observeChatTelemetryUseCase = ObserveChatTelemetryUseCase(repository),
                    mapper = mapper,
                )

            coordinator.onChatStatsClick(
                chatId = TelemetryTestFixtures.chat1,
                inMemoryTelemetryRows = listOf(TelemetryTestFixtures.telOpenAi1),
                scope = this,
            )
            advanceUntilIdle()

            val sheet = coordinator.chatStatsSheet.value
            assertEquals("5000", sheet.summaryRows.first { it.label == "Input tokens" }.value)
        }

    @Test
    fun `chat stats sheet maps summary from persisted chat telemetry`() =
        runTest {
            val repository =
                FakeInferenceTelemetryRepositoryPort(
                    chatRows = mapOf(TelemetryTestFixtures.chat1 to listOf(TelemetryTestFixtures.telOpenAi1)),
                )
            val coordinator =
                ChatTelemetryCoordinator(
                    observeMessageTelemetryUseCase = ObserveMessageTelemetryUseCase(repository),
                    observeChatTelemetryUseCase = ObserveChatTelemetryUseCase(repository),
                    mapper = mapper,
                )

            coordinator.onChatStatsClick(TelemetryTestFixtures.chat1, scope = this)
            advanceUntilIdle()

            val sheet = coordinator.chatStatsSheet.value
            assertTrue(sheet.isVisible)
            assertEquals("1000", sheet.summaryRows.first { it.label == "Input tokens" }.value)
        }

    @Test
    fun `dismiss response stats hides sheet`() =
        runTest {
            val repository = FakeInferenceTelemetryRepositoryPort()
            val coordinator =
                ChatTelemetryCoordinator(
                    observeMessageTelemetryUseCase = ObserveMessageTelemetryUseCase(repository),
                    observeChatTelemetryUseCase = ObserveChatTelemetryUseCase(repository),
                    mapper = mapper,
                )

            coordinator.onResponseStatsClick(
                messageId = MessageId("m-1"),
                inMemoryTelemetryRows = emptyList(),
                scope = this,
            )
            advanceUntilIdle()
            coordinator.dismissResponseStats()

            assertFalse(coordinator.responseStatsSheet.value.isVisible)
        }

    @Test
    fun `dismiss chat stats hides sheet`() =
        runTest {
            val repository = FakeInferenceTelemetryRepositoryPort()
            val coordinator =
                ChatTelemetryCoordinator(
                    observeMessageTelemetryUseCase = ObserveMessageTelemetryUseCase(repository),
                    observeChatTelemetryUseCase = ObserveChatTelemetryUseCase(repository),
                    mapper = mapper,
                )

            coordinator.onChatStatsClick(ChatId("chat-1"), scope = this)
            advanceUntilIdle()
            coordinator.dismissChatStats()

            assertFalse(coordinator.chatStatsSheet.value.isVisible)
        }

    @Test
    fun `fresh coordinator sheets start hidden and empty`() {
        val repository = FakeInferenceTelemetryRepositoryPort()
        val coordinator =
            ChatTelemetryCoordinator(
                observeMessageTelemetryUseCase = ObserveMessageTelemetryUseCase(repository),
                observeChatTelemetryUseCase = ObserveChatTelemetryUseCase(repository),
                mapper = mapper,
            )

        assertFalse(coordinator.responseStatsSheet.value.isVisible)
        assertNull(coordinator.responseStatsSheet.value.messageId)
        assertFalse(coordinator.chatStatsSheet.value.isVisible)
    }
}
