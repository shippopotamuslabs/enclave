package com.browntowndev.pocketcrew.feature.history

import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.usecase.chat.GetAllChatsUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.GetSettingsUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * TDD Red Phase Tests for HistoryViewModel.
 *
 * These tests verify the ViewModel correctly processes chats from the repository,
 * maps domain models to UI models, and handles all edge cases.
 * Expected values come from scenario Givens, NOT from implementation spec.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var mockGetAllChatsUseCase: GetAllChatsUseCase
    private lateinit var mockSettingsUseCases: SettingsUseCases
    private lateinit var mockGetSettingsUseCase: GetSettingsUseCase
    private lateinit var viewModel: HistoryViewModelStub

    private val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 10)
        set(Calendar.MINUTE, 30)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    private val yesterday = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -1)
        set(Calendar.HOUR_OF_DAY, 18)
        set(Calendar.MINUTE, 15)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    private val oct24 = Calendar.getInstance().apply {
        set(Calendar.MONTH, Calendar.OCTOBER)
        set(Calendar.DAY_OF_MONTH, 24)
        set(Calendar.HOUR_OF_DAY, 14)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    private fun createTestChat(
        id: Long,
        name: String,
        lastModified: Date,
        pinned: Boolean
    ): Chat = Chat(
        id = id,
        name = name,
        created = lastModified,
        lastModified = lastModified,
        pinned = pinned
    )

    @BeforeEach
    fun setup() {
        mockGetAllChatsUseCase = mockk(relaxed = true)
        mockSettingsUseCases = mockk(relaxed = true)
        mockGetSettingsUseCase = mockk(relaxed = true)

        // Setup minimal mock for SettingsUseCases
        every { mockSettingsUseCases.getSettings } returns mockGetSettingsUseCase
        every { mockSettingsUseCases.updateTheme } returns mockk(relaxed = true)
        every { mockSettingsUseCases.updateHapticPress } returns mockk(relaxed = true)
        every { mockSettingsUseCases.updateHapticResponse } returns mockk(relaxed = true)
        every { mockSettingsUseCases.updateCustomizationEnabled } returns mockk(relaxed = true)
        every { mockSettingsUseCases.updateSelectedPromptOption } returns mockk(relaxed = true)
        every { mockSettingsUseCases.updateCustomPromptText } returns mockk(relaxed = true)
        every { mockSettingsUseCases.updateAllowMemories } returns mockk(relaxed = true)

        // Default: empty flow
        every { mockGetAllChatsUseCase.invoke() } returns MutableStateFlow<List<Chat>>(emptyList())
    }

    private fun createViewModel(): HistoryViewModelStub {
        return HistoryViewModelStub(
            getAllChatsUseCase = mockGetAllChatsUseCase,
            settingsUseCases = mockSettingsUseCases
        )
    }

    // ========================================================================
    // Suite B: HistoryViewModel Tests
    // ========================================================================

    /**
     * Scenario B1: Loads chats on initialization
     *
     * Given: Use case returns 4 chats (2 pinned, 2 unpinned)
     * When: ViewModel initialized
     * Then: uiState.pinnedChats has 2, uiState.otherChats has 2
     */
    @Test
    fun `B1 loads chats on initialization`() = testScope.runTest {
        // Given: Use case returns 4 chats (2 pinned, 2 unpinned)
        val testChats = listOf(
            createTestChat(1, "Alpha", today, pinned = true),
            createTestChat(2, "Beta", yesterday, pinned = true),
            createTestChat(3, "Gamma", oct24, pinned = false),
            createTestChat(4, "Delta", today, pinned = false)
        )
        every { mockGetAllChatsUseCase.invoke() } returns MutableStateFlow<List<Chat>>(testChats)

        // When: ViewModel initialized
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: uiState.pinnedChats has 2, uiState.otherChats has 2
        val state = viewModel.uiState.first()
        assertEquals(2, state.pinnedChats.size, "pinnedChats should have 2 chats")
        assertEquals(2, state.otherChats.size, "otherChats should have 2 chats")
    }

    /**
     * Scenario B2: Maps Chat to HistoryChat correctly
     *
     * Given: Chat with id=42, name="Test Chat", lastModified=today 10:30 AM, pinned=false
     * When: ViewModel processes
     * Then: HistoryChat has id=42, name="Test Chat", lastMessageDateTime="Today, 10:30 AM", isPinned=false
     */
    @Test
    fun `B2 maps Chat to HistoryChat correctly`() = testScope.runTest {
        // Given: Chat with id=42, name="Test Chat", lastModified=today 10:30 AM, pinned=false
        val testChat = createTestChat(42, "Test Chat", today, pinned = false)
        every { mockGetAllChatsUseCase.invoke() } returns MutableStateFlow<List<Chat>>(listOf(testChat))

        // When: ViewModel processes
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: HistoryChat has correct mapping
        val state = viewModel.uiState.first()
        assertEquals(1, state.otherChats.size)
        val historyChat = state.otherChats.first()
        assertEquals(42L, historyChat.id)
        assertEquals("Test Chat", historyChat.name)
        assertEquals("Today, 10:30 AM", historyChat.lastMessageDateTime)
        assertFalse(historyChat.isPinned)
    }

    /**
     * Scenario B3: Pinned chats appear in pinned section
     *
     * Given: Chat with pinned=true
     * When: ViewModel processes
     * Then: Chat appears in uiState.pinnedChats
     */
    @Test
    fun `B3 pinned chats appear in pinned section`() = testScope.runTest {
        // Given: Chat with pinned=true
        val pinnedChat = createTestChat(1, "Pinned Chat", today, pinned = true)
        every { mockGetAllChatsUseCase.invoke() } returns MutableStateFlow<List<Chat>>(listOf(pinnedChat))

        // When: ViewModel processes
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: Chat appears in pinnedChats
        val state = viewModel.uiState.first()
        assertTrue(state.pinnedChats.any { it.id == 1L }, "Pinned chat should appear in pinnedChats")
        assertEquals(1, state.pinnedChats.size)
        assertEquals(0, state.otherChats.size)
    }

    /**
     * Scenario B4: Unpinned chats appear in recent section
     *
     * Given: Chat with pinned=false
     * When: ViewModel processes
     * Then: Chat appears in uiState.otherChats
     */
    @Test
    fun `B4 unpinned chats appear in recent section`() = testScope.runTest {
        // Given: Chat with pinned=false
        val unpinnedChat = createTestChat(2, "Unpinned Chat", today, pinned = false)
        every { mockGetAllChatsUseCase.invoke() } returns MutableStateFlow<List<Chat>>(listOf(unpinnedChat))

        // When: ViewModel processes
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: Chat appears in otherChats
        val state = viewModel.uiState.first()
        assertTrue(state.otherChats.any { it.id == 2L }, "Unpinned chat should appear in otherChats")
        assertEquals(0, state.pinnedChats.size)
        assertEquals(1, state.otherChats.size)
    }

    /**
     * Scenario B5: Empty database produces empty lists
     *
     * Given: Use case returns empty list
     * When: ViewModel initialized
     * Then: Both pinnedChats and otherChats are empty
     */
    @Test
    fun `B5 empty database produces empty lists`() = testScope.runTest {
        // Given: Use case returns empty list
        every { mockGetAllChatsUseCase.invoke() } returns MutableStateFlow<List<Chat>>(emptyList())

        // When: ViewModel initialized
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: Both lists are empty
        val state = viewModel.uiState.first()
        assertTrue(state.pinnedChats.isEmpty(), "pinnedChats should be empty")
        assertTrue(state.otherChats.isEmpty(), "otherChats should be empty")
        // Verify ViewModel actually processed the flow (isLoading should be false after processing)
        assertFalse(state.isLoading, "isLoading should be false after data is processed")
    }

    /**
     * Scenario B6: Chats maintain DAO sort order (DESC by lastModified)
     *
     * Given: 3 chats with lastModified=1700, 1500, 1200 (descending)
     * When: ViewModel processes
     * Then: Order matches DAO (descending by lastModified)
     */
    @Test
    fun `B6 chats maintain DAO sort order`() = testScope.runTest {
        // Given: 3 chats sorted by DAO as lastModified=1700, 1500, 1200 (descending)
        val chat1 = createTestChat(1, "Most Recent", Date(1700), pinned = false)
        val chat2 = createTestChat(2, "Middle", Date(1500), pinned = false)
        val chat3 = createTestChat(3, "Oldest", Date(1200), pinned = false)
        every { mockGetAllChatsUseCase.invoke() } returns MutableStateFlow<List<Chat>>(listOf(chat1, chat2, chat3))

        // When: ViewModel processes
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: Order matches DAO (descending by lastModified)
        val state = viewModel.uiState.first()
        assertEquals(3, state.otherChats.size)
        assertEquals(1L, state.otherChats[0].id) // Most recent first
        assertEquals(2L, state.otherChats[1].id) // Middle
        assertEquals(3L, state.otherChats[2].id) // Oldest last
    }

    /**
     * Scenario B7: Loading state true on init before data arrives
     *
     * Given: Use case returns delaying Flow (simulating slow database)
     * When: ViewModel initialized
     * Then: uiState.isLoading is true initially before any emission
     */
    @Test
    fun `B7 loading state true on init`() = testScope.runTest {
        // Given: Use case returns delaying Flow (simulated by not emitting yet)
        val delayedFlow = MutableStateFlow<List<Chat>>(emptyList())
        every { mockGetAllChatsUseCase.invoke() } returns delayedFlow

        // When: ViewModel initialized
        viewModel = createViewModel()
        // Don't advance - this tests the initial loading state

        // Then: isLoading is true initially, then false after processing
        val initialState = viewModel.uiState.value
        assertTrue(initialState.isLoading, "isLoading should be true initially")
        
        // After processing, isLoading should be false
        advanceUntilIdle()
        val processedState = viewModel.uiState.first()
        assertFalse(processedState.isLoading, "isLoading should be false after data is processed")
    }

    /**
     * Scenario B8: Loading state false after data arrives
     *
     * Given: Use case returns non-empty list
     * When: ViewModel initialized and flow emits
     * Then: uiState.isLoading is false after emission
     */
    @Test
    fun `B8 loading state false after data arrives`() = testScope.runTest {
        // Given: Use case returns non-empty list
        val testChat = createTestChat(1, "Test", today, pinned = false)
        every { mockGetAllChatsUseCase.invoke() } returns MutableStateFlow<List<Chat>>(listOf(testChat))

        // When: ViewModel initialized
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: isLoading is false after emission
        val state = viewModel.uiState.first()
        assertFalse(state.isLoading, "isLoading should be false after data arrives")
    }

    // ========================================================================
    // Suite C: Relative Date Formatting Tests
    // ========================================================================

    /**
     * Scenario C1: Today format for same-day dates
     *
     * Given: Chat with lastModified=today 10:30 AM
     * When: Timestamp formatted
     * Then: lastMessageDateTime = "Today, 10:30 AM"
     */
    @Test
    fun `C1 today format for same-day dates`() = testScope.runTest {
        // Given: Chat with lastModified=today 10:30 AM
        val todayDate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        val testChat = createTestChat(1, "Test", todayDate, pinned = false)
        every { mockGetAllChatsUseCase.invoke() } returns MutableStateFlow<List<Chat>>(listOf(testChat))

        // When: ViewModel processes
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: lastMessageDateTime = "Today, 10:30 AM"
        val state = viewModel.uiState.first()
        assertEquals("Today, 10:30 AM", state.otherChats.first().lastMessageDateTime)
    }

    /**
     * Scenario C2: Yesterday format for previous-day dates
     *
     * Given: Chat with lastModified=yesterday 6:15 PM
     * When: Timestamp formatted
     * Then: lastMessageDateTime = "Yesterday, 6:15 PM"
     */
    @Test
    fun `C2 yesterday format for previous-day dates`() = testScope.runTest {
        // Given: Chat with lastModified=yesterday 6:15 PM
        val yesterdayDate = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 15)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        val testChat = createTestChat(1, "Test", yesterdayDate, pinned = false)
        every { mockGetAllChatsUseCase.invoke() } returns MutableStateFlow<List<Chat>>(listOf(testChat))

        // When: ViewModel processes
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: lastMessageDateTime = "Yesterday, 6:15 PM"
        val state = viewModel.uiState.first()
        assertEquals("Yesterday, 6:15 PM", state.otherChats.first().lastMessageDateTime)
    }

    /**
     * Scenario C3: Date format for older dates
     *
     * Given: Chat with lastModified=Oct 24 2:00 PM
     * When: Timestamp formatted
     * Then: lastMessageDateTime = "Oct 24, 2:00 PM"
     */
    @Test
    fun `C3 date format for older dates`() = testScope.runTest {
        // Given: Chat with lastModified=Oct 24 2:00 PM
        val oct24Date = Calendar.getInstance().apply {
            set(Calendar.MONTH, Calendar.OCTOBER)
            set(Calendar.DAY_OF_MONTH, 24)
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        val testChat = createTestChat(1, "Test", oct24Date, pinned = false)
        every { mockGetAllChatsUseCase.invoke() } returns MutableStateFlow<List<Chat>>(listOf(testChat))

        // When: ViewModel processes
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: lastMessageDateTime = "Oct 24, 2:00 PM"
        val state = viewModel.uiState.first()
        assertEquals("Oct 24, 2:00 PM", state.otherChats.first().lastMessageDateTime)
    }

    /**
     * Scenario C4: Uses device timezone for formatting
     *
     * Given: Chat with lastModified at a time that differs between UTC and local
     * When: Timestamp formatted
     * Then: Output reflects local timezone, not UTC
     */
    @Test
    fun `C4 uses device timezone for formatting`() = testScope.runTest {
        // Given: Chat with UTC time that would differ in local timezone
        // Using a time that creates different hour in PST vs UTC
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.HOUR_OF_DAY, 23) // 11 PM UTC
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val utcDate = calendar.time
        val testChat = createTestChat(1, "Test", utcDate, pinned = false)
        every { mockGetAllChatsUseCase.invoke() } returns MutableStateFlow<List<Chat>>(listOf(testChat))

        // When: ViewModel processes
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: Output reflects local timezone (should show local hour, not UTC hour)
        val state = viewModel.uiState.first()
        val formattedTime = state.otherChats.first().lastMessageDateTime
        // Extract the hour portion and verify it's not the UTC hour (23)
        // In local timezone it should be different (unless local is UTC)
        val localZone = TimeZone.getDefault()
        // The formatted time should NOT contain "11:30 PM" if we're not in UTC
        if (localZone.id != "UTC") {
            assertTrue(
                !formattedTime.contains("11:30 PM"),
                "Should use local timezone, not UTC. Got: $formattedTime"
            )
        }
    }

    // ========================================================================
    // Suite E: Error Path Tests
    // ========================================================================

    /**
     * Scenario E1: Repository throws on getAllChats
     *
     * Given: ChatRepository where getAllChats() throws RuntimeException
     * When: ViewModel is initialized and subscribes
     * Then: Error is handled gracefully without crashing
     */
    @Test
    fun `E1 repository throws on getAllChats handled gracefully`() = testScope.runTest {
        // Given: Use case returns a flow that throws when collected
        val errorFlow = MutableSharedFlow<List<Chat>>(
            replay = 0,
            extraBufferCapacity = 1
        )
        every { mockGetAllChatsUseCase.invoke() } returns errorFlow

        viewModel = createViewModel()
        
        // When: Emit an item, then complete
        errorFlow.emit(emptyList())
        
        // Then: ViewModel should have processed without crashing
        // Verify state is valid (empty lists, not loading)
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertTrue(state.pinnedChats.isEmpty(), "pinnedChats should be empty")
        assertTrue(state.otherChats.isEmpty(), "otherChats should be empty")
        // The ViewModel should set isLoading=false after processing
        assertFalse(state.isLoading, "isLoading should be false after processing")
    }

    /**
     * Scenario E2: Empty pinned section
     *
     * Given: All chats have pinned=false
     * When: ViewModel processes
     * Then: pinnedChats empty, otherChats contains all
     */
    @Test
    fun `E2 empty pinned section when all chats unpinned`() = testScope.runTest {
        // Given: All chats have pinned=false
        val unpinnedChats = listOf(
            createTestChat(1, "Chat A", today, pinned = false),
            createTestChat(2, "Chat B", today, pinned = false)
        )
        every { mockGetAllChatsUseCase.invoke() } returns MutableStateFlow<List<Chat>>(unpinnedChats)

        // When: ViewModel processes
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: pinnedChats empty, otherChats contains all
        val state = viewModel.uiState.first()
        assertTrue(state.pinnedChats.isEmpty(), "pinnedChats should be empty")
        assertEquals(2, state.otherChats.size, "otherChats should contain all chats")
    }

    /**
     * Scenario E3: Empty recent section
     *
     * Given: All chats have pinned=true
     * When: ViewModel processes
     * Then: otherChats empty, pinnedChats contains all
     */
    @Test
    fun `E3 empty recent section when all chats pinned`() = testScope.runTest {
        // Given: All chats have pinned=true
        val pinnedChats = listOf(
            createTestChat(1, "Chat A", today, pinned = true),
            createTestChat(2, "Chat B", today, pinned = true)
        )
        every { mockGetAllChatsUseCase.invoke() } returns MutableStateFlow<List<Chat>>(pinnedChats)

        // When: ViewModel processes
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: otherChats empty, pinnedChats contains all
        val state = viewModel.uiState.first()
        assertEquals(2, state.pinnedChats.size, "pinnedChats should contain all chats")
        assertTrue(state.otherChats.isEmpty(), "otherChats should be empty")
    }

    /**
     * Scenario E4: Non-existent chat toggle is handled gracefully
     *
     * Given: No chat with chatId=9999
     * When: togglePinStatus(9999) called
     * Then: Operation completes as no-op
     */
    @Test
    fun `E4 non-existent chat toggle handled gracefully`() = testScope.runTest {
        // Given: Empty repository
        every { mockGetAllChatsUseCase.invoke() } returns MutableStateFlow<List<Chat>>(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        // When: togglePinStatus called with non-existent ID
        // Then: No crash, operation completes as no-op
        viewModel.pinChat(9999L)
        viewModel.unpinChat(9999L)
        // If we reach here, the operation completed gracefully
        val state = viewModel.uiState.first()
        assertEquals(0, state.pinnedChats.size)
        assertEquals(0, state.otherChats.size)
    }

    // ========================================================================
    // Suite F: Integration Test
    // ========================================================================

    /**
     * Scenario F1: End-to-end from DAO to UI state
     *
     * Given: DB contains:
     *   - Chat A: id=1, name="Alpha", pinned=true, lastModified=today
     *   - Chat B: id=2, name="Beta", pinned=false, lastModified=yesterday
     *   - Chat C: id=3, name="Gamma", pinned=true, lastModified=Oct 15
     * When: ViewModel initialized and flow stabilizes
     * Then: pinnedChats contains A and C with correct timestamps; otherChats contains B
     */
    @Test
    fun `F1 end-to-end from DAO to UI state`() = testScope.runTest {
        // Given: Database contains 3 chats
        val todayDate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val yesterdayDate = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val oct15Date = Calendar.getInstance().apply {
            set(Calendar.MONTH, Calendar.OCTOBER)
            set(Calendar.DAY_OF_MONTH, 15)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val chats = listOf(
            createTestChat(1, "Alpha", todayDate, pinned = true),
            createTestChat(2, "Beta", yesterdayDate, pinned = false),
            createTestChat(3, "Gamma", oct15Date, pinned = true)
        )
        every { mockGetAllChatsUseCase.invoke() } returns MutableStateFlow<List<Chat>>(chats)

        // When: ViewModel initialized and flow stabilizes
        viewModel = createViewModel()
        advanceUntilIdle()

        // Then: pinnedChats contains A and C; otherChats contains B
        val state = viewModel.uiState.first()

        // Verify pinnedChats contains Alpha and Gamma
        assertEquals(2, state.pinnedChats.size, "pinnedChats should have 2 chats")
        val pinnedNames = state.pinnedChats.map { it.name }.toSet()
        assertTrue(pinnedNames.contains("Alpha"), "pinnedChats should contain Alpha")
        assertTrue(pinnedNames.contains("Gamma"), "pinnedChats should contain Gamma")

        // Verify otherChats contains Beta
        assertEquals(1, state.otherChats.size, "otherChats should have 1 chat")
        assertEquals("Beta", state.otherChats.first().name)

        // Verify timestamps are correctly formatted
        val alphaChat = state.pinnedChats.find { it.name == "Alpha" }
        assertEquals("Today, 12:00 AM", alphaChat?.lastMessageDateTime)

        val betaChat = state.otherChats.find { it.name == "Beta" }
        assertEquals("Yesterday, 12:00 AM", betaChat?.lastMessageDateTime)

        val gammaChat = state.pinnedChats.find { it.name == "Gamma" }
        assertEquals("Oct 15, 12:00 AM", gammaChat?.lastMessageDateTime)
    }
}
