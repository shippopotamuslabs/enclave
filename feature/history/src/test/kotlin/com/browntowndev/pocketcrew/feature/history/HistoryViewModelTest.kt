package com.browntowndev.pocketcrew.feature.history
import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.settings.AppTheme
import com.browntowndev.pocketcrew.domain.model.settings.SystemPromptOption
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.usecase.chat.DeleteChatUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.GetAllChatsUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.RenameChatUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.SearchChatsUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.TogglePinChatUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.GetSettingsUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension


@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var mockGetAllChatsUseCase: GetAllChatsUseCase
    private lateinit var mockSearchChatsUseCase: SearchChatsUseCase
    private lateinit var mockDeleteChatUseCase: DeleteChatUseCase
    private lateinit var mockRenameChatUseCase: RenameChatUseCase
    private lateinit var mockTogglePinChatUseCase: TogglePinChatUseCase
    private lateinit var mockGetSettingsUseCase: GetSettingsUseCase
    private lateinit var mockSettingsUseCases: SettingsUseCases
    private lateinit var mockErrorHandler: ViewModelErrorHandler
    private lateinit var viewModel: HistoryViewModel

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
        // Ensure year is set to a known value that won't be affected by current date
        set(Calendar.YEAR, 2024)
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
        mockSearchChatsUseCase = mockk(relaxed = true)
        mockDeleteChatUseCase = mockk(relaxed = true)
        mockRenameChatUseCase = mockk(relaxed = true)
        mockTogglePinChatUseCase = mockk(relaxed = true)
        mockGetSettingsUseCase = mockk(relaxed = true)
        mockSettingsUseCases = mockk(relaxed = true)
        mockErrorHandler = mockk(relaxed = true)

        every { mockSettingsUseCases.getSettings } returns mockGetSettingsUseCase
        every { mockGetSettingsUseCase.invoke() } returns MutableStateFlow(
            SettingsData(
                theme = AppTheme.SYSTEM,
                hapticPress = true,
                hapticResponse = true,
                customizationEnabled = false,
                selectedPromptOption = SystemPromptOption.CONCISE,
                customPromptText = "",
                allowMemories = false
            )
        )
    }

    private fun createViewModel(): HistoryViewModel {
        return HistoryViewModel(
            getAllChatsUseCase = mockGetAllChatsUseCase,
            searchChatsUseCase = mockSearchChatsUseCase,
            deleteChatUseCase = mockDeleteChatUseCase,
            renameChatUseCase = mockRenameChatUseCase,
            togglePinChatUseCase = mockTogglePinChatUseCase,
            getSettingsUseCase = mockGetSettingsUseCase,
            errorHandler = mockErrorHandler
        )
    }

    @Test
    fun `B1 loads chats on initialization`() = runTest(testDispatcher) {
        val testChats = listOf(
            createTestChat(1, "Alpha", today, pinned = true),
            createTestChat(2, "Beta", yesterday, pinned = true),
            createTestChat(3, "Gamma", oct24, pinned = false),
            createTestChat(4, "Delta", today, pinned = false)
        )
        val chatsFlow = MutableStateFlow<List<Chat>>(emptyList())
        every { mockGetAllChatsUseCase.invoke() } returns chatsFlow

        viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        
        chatsFlow.value = testChats
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.pinnedChats.size)
        assertEquals(2, state.otherChats.size)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `B2 maps Chat to HistoryChat correctly`() = runTest(testDispatcher) {
        val testChat = createTestChat(42, "Test Chat", today, pinned = false)
        val chatsFlow = MutableStateFlow<List<Chat>>(emptyList())
        every { mockGetAllChatsUseCase.invoke() } returns chatsFlow

        viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        
        chatsFlow.value = listOf(testChat)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.otherChats.size)
        val historyChat = state.otherChats.first()
        assertEquals(42L, historyChat.id)
        assertEquals("Test Chat", historyChat.name)
        assertEquals("Today, 10:30 AM", historyChat.lastMessageDateTime)
        assertFalse(historyChat.isPinned)
    }

    @Test
    fun `B3 pinned chats appear in pinned section`() = runTest(testDispatcher) {
        val pinnedChat = createTestChat(1, "Pinned Chat", today, pinned = true)
        val chatsFlow = MutableStateFlow<List<Chat>>(emptyList())
        every { mockGetAllChatsUseCase.invoke() } returns chatsFlow

        viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        
        chatsFlow.value = listOf(pinnedChat)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.pinnedChats.any { it.id == 1L })
        assertEquals(1, state.pinnedChats.size)
    }

    @Test
    fun `B4 unpinned chats appear in recent section`() = runTest(testDispatcher) {
        val unpinnedChat = createTestChat(1, "Recent Chat", today, pinned = false)
        val chatsFlow = MutableStateFlow<List<Chat>>(emptyList())
        every { mockGetAllChatsUseCase.invoke() } returns chatsFlow

        viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        
        chatsFlow.value = listOf(unpinnedChat)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.otherChats.any { it.id == 1L })
        assertEquals(1, state.otherChats.size)
    }

    @Test
    fun `B5 empty database produces empty lists`() = runTest(testDispatcher) {
        val chatsFlow = MutableStateFlow<List<Chat>>(emptyList())
        every { mockGetAllChatsUseCase.invoke() } returns chatsFlow

        viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.pinnedChats.isEmpty())
        assertTrue(state.otherChats.isEmpty())
        assertFalse(state.isLoading)
    }

    @Test
    fun `B6 chats maintain DAO sort order`() = runTest(testDispatcher) {
        val chat1 = createTestChat(1, "Most Recent", Date(1700), pinned = false)
        val chat2 = createTestChat(2, "Middle", Date(1500), pinned = false)
        val chat3 = createTestChat(3, "Oldest", Date(1200), pinned = false)
        val chatsFlow = MutableStateFlow<List<Chat>>(emptyList())
        every { mockGetAllChatsUseCase.invoke() } returns chatsFlow

        viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        
        chatsFlow.value = listOf(chat1, chat2, chat3)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(3, state.otherChats.size)
        assertEquals(1L, state.otherChats[0].id)
        assertEquals(2L, state.otherChats[1].id)
        assertEquals(3L, state.otherChats[2].id)
    }

    @Test
    fun `B7 loading state managed correctly`() = runTest(testDispatcher) {
        val chatsFlow = MutableStateFlow<List<Chat>>(emptyList())
        every { mockGetAllChatsUseCase.invoke() } returns chatsFlow

        viewModel = createViewModel()
        // Check initial value directly before any collectors or flow emissions
        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `B8 loading state false after data arrives`() = runTest(testDispatcher) {
        val testChat = createTestChat(1, "Test", today, pinned = false)
        val chatsFlow = MutableStateFlow<List<Chat>>(emptyList())
        every { mockGetAllChatsUseCase.invoke() } returns chatsFlow

        viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        
        chatsFlow.value = listOf(testChat)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
    }

    @Test
    fun `B9 pinned chats are sorted by lastModified descending`() = runTest(testDispatcher) {
        val chat1 = createTestChat(1, "Older Pinned", Date(1000), pinned = true)
        val chat2 = createTestChat(2, "Newer Pinned", Date(2000), pinned = true)
        val chatsFlow = MutableStateFlow<List<Chat>>(emptyList())
        every { mockGetAllChatsUseCase.invoke() } returns chatsFlow

        viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        
        chatsFlow.value = listOf(chat1, chat2)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.pinnedChats.size)
        assertEquals(2L, state.pinnedChats[0].id)
        assertEquals(1L, state.pinnedChats[1].id)
    }

    @Test
    fun `deleteChat error invokes errorHandler`() = runTest(testDispatcher) {
        // Given
        val chatId = 1L
        val exception = RuntimeException("Delete Error")
        coEvery { mockDeleteChatUseCase.invoke(chatId) } throws exception
        
        viewModel = createViewModel()
        
        // When
        viewModel.deleteChat(chatId)
        advanceUntilIdle()
        
        // Then
        io.mockk.verify {
            mockErrorHandler.handleError(
                tag = "HistoryViewModel",
                message = "Failed to delete chat with id: 1",
                throwable = exception,
                userMessage = "Failed to delete chat"
            )
        }
    }

    // Suite C: Relative Date Formatting Tests

    @Test
    fun `C1 today format for same-day dates`() = runTest(testDispatcher) {
        val todayDate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        val testChat = createTestChat(1, "Test", todayDate, pinned = false)
        val chatsFlow = MutableStateFlow<List<Chat>>(emptyList())
        every { mockGetAllChatsUseCase.invoke() } returns chatsFlow

        viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        
        chatsFlow.value = listOf(testChat)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Today, 10:30 AM", state.otherChats.first().lastMessageDateTime)
    }

    @Test
    fun `C2 yesterday format for previous-day dates`() = runTest(testDispatcher) {
        val yesterdayDate = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 15)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        val testChat = createTestChat(1, "Test", yesterdayDate, pinned = false)
        val chatsFlow = MutableStateFlow<List<Chat>>(emptyList())
        every { mockGetAllChatsUseCase.invoke() } returns chatsFlow

        viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        
        chatsFlow.value = listOf(testChat)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Yesterday, 6:15 PM", state.otherChats.first().lastMessageDateTime)
    }

    @Test
    fun `C3 date format for older dates`() = runTest(testDispatcher) {
        val oct24Date = Calendar.getInstance().apply {
            set(Calendar.MONTH, Calendar.OCTOBER)
            set(Calendar.DAY_OF_MONTH, 24)
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        val testChat = createTestChat(1, "Test", oct24Date, pinned = false)
        val chatsFlow = MutableStateFlow<List<Chat>>(emptyList())
        every { mockGetAllChatsUseCase.invoke() } returns chatsFlow

        viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        
        chatsFlow.value = listOf(testChat)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Oct 24, 2:00 PM", state.otherChats.first().lastMessageDateTime)
    }

    @Test
    fun `C4 uses device timezone for formatting`() = runTest(testDispatcher) {
        // Create a date at a specific hour in UTC
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val utcDate = calendar.time
        
        // Change the default timezone for the test to one where the hour will definitely be different from 23
        val originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York")) // UTC-5 (or -4)
        
        try {
            val testChat = createTestChat(1, "Test", utcDate, pinned = false)
            val chatsFlow = MutableStateFlow<List<Chat>>(emptyList())
            every { mockGetAllChatsUseCase.invoke() } returns chatsFlow

            viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
            
            chatsFlow.value = listOf(testChat)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            val formattedTime = state.otherChats.first().lastMessageDateTime
            
            // In New York, 23:30 UTC is either 18:30 or 19:30
            // The formatted string should NOT contain "11:30 PM" (which is 23:30)
            assertFalse(formattedTime.contains("11:30 PM"), "Should reflect local time, not UTC. Got: $formattedTime")
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    // Suite E: Error Path Tests

    @Test
    fun `E1 repository throws on getAllChats handled gracefully`() = runTest(testDispatcher) {
        every { mockGetAllChatsUseCase.invoke() } returns flow {
            throw RuntimeException("DB Error")
        }

        viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.pinnedChats.isEmpty())
        assertTrue(state.otherChats.isEmpty())
    }

    @Test
    fun `E2 empty pinned section when all chats unpinned`() = runTest(testDispatcher) {
        val unpinnedChats = listOf(
            createTestChat(1, "Chat A", today, pinned = false),
            createTestChat(2, "Chat B", today, pinned = false)
        )
        val chatsFlow = MutableStateFlow<List<Chat>>(emptyList())
        every { mockGetAllChatsUseCase.invoke() } returns chatsFlow

        viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        
        chatsFlow.value = unpinnedChats
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.pinnedChats.isEmpty())
        assertEquals(2, state.otherChats.size)
    }

    @Test
    fun `E3 empty recent section when all chats pinned`() = runTest(testDispatcher) {
        val pinnedChats = listOf(
            createTestChat(1, "Chat A", today, pinned = true),
            createTestChat(2, "Chat B", today, pinned = true)
        )
        val chatsFlow = MutableStateFlow<List<Chat>>(emptyList())
        every { mockGetAllChatsUseCase.invoke() } returns chatsFlow

        viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        
        chatsFlow.value = pinnedChats
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.pinnedChats.size)
        assertTrue(state.otherChats.isEmpty())
    }

    @Test
    fun `E4 non-existent chat toggle handled gracefully`() = runTest(testDispatcher) {
        val chatsFlow = MutableStateFlow<List<Chat>>(emptyList())
        every { mockGetAllChatsUseCase.invoke() } returns chatsFlow

        viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        advanceUntilIdle()
        
        coEvery { mockTogglePinChatUseCase.invoke(9999L) } throws IllegalArgumentException("Chat not found")
        viewModel.pinChat(9999L)
        viewModel.unpinChat(9999L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.pinnedChats.size)
        assertEquals(0, state.otherChats.size)
    }

    @Test
    fun `E5 settings flow error uses defaults`() = runTest(testDispatcher) {
        val testChat = createTestChat(1, "Test", today, pinned = false)
        val chatsFlow = MutableStateFlow(listOf(testChat))
        every { mockGetAllChatsUseCase.invoke() } returns chatsFlow
        
        every { mockGetSettingsUseCase.invoke() } returns flow {
            throw RuntimeException("Settings Error")
        }

        viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.otherChats.size)
        assertTrue(state.hapticPress) // verified from default emit
    }

    // Suite F: Integration Test

    @Test
    fun `F1 end-to-end from DAO to UI state`() = runTest(testDispatcher) {
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
        val chatsFlow = MutableStateFlow<List<Chat>>(emptyList())
        every { mockGetAllChatsUseCase.invoke() } returns chatsFlow

        viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        
        chatsFlow.value = chats
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertEquals(2, state.pinnedChats.size)
        val pinnedNames = state.pinnedChats.map { it.name }.toSet()
        assertTrue(pinnedNames.contains("Alpha"))
        assertTrue(pinnedNames.contains("Gamma"))

        assertEquals(1, state.otherChats.size)
        assertEquals("Beta", state.otherChats.first().name)

        val alphaChat = state.pinnedChats.find { it.name == "Alpha" }
        assertEquals("Today, 12:00 AM", alphaChat?.lastMessageDateTime)

        val betaChat = state.otherChats.find { it.name == "Beta" }
        assertEquals("Yesterday, 12:00 AM", betaChat?.lastMessageDateTime)

        val gammaChat = state.pinnedChats.find { it.name == "Gamma" }
        assertEquals("Oct 15, 12:00 AM", gammaChat?.lastMessageDateTime)
    }

    // Suite S: Search Tests (from test_spec.md)

    @Test
    fun `Scenario 1 Initial State Shows All Chats`() = runTest(testDispatcher) {
        val testChats = listOf(
            createTestChat(1, "Chat 1", today, pinned = false),
            createTestChat(2, "Chat 2", today, pinned = false),
            createTestChat(3, "Chat 3", today, pinned = false)
        )
        val chatsFlow = MutableStateFlow(testChats)
        every { mockGetAllChatsUseCase.invoke() } returns chatsFlow

        viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        
        // Initial state has empty search query
        assertEquals("", viewModel.searchQuery.value)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(3, state.otherChats.size + state.pinnedChats.size)
        assertFalse(state.isLoading)
    }

    @Test
    fun `Scenario 2 Search by Chat Name`() = runTest(testDispatcher) {
        val allChatsFlow = MutableStateFlow<List<Chat>>(emptyList())
        val matchingChat = createTestChat(1, "Project Plan", today, false)
        val searchChatsFlow = MutableStateFlow(listOf(matchingChat))
        
        every { mockGetAllChatsUseCase.invoke() } returns allChatsFlow
        every { mockSearchChatsUseCase.invoke("Project") } returns searchChatsFlow

        viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        
        viewModel.onSearchQueryChange("Project")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val allVisibleChats = state.otherChats + state.pinnedChats
        assertEquals(1, allVisibleChats.size)
        assertEquals("Project Plan", allVisibleChats.first().name)
        assertEquals(1L, allVisibleChats.first().id)
    }

    @Test
    fun `Scenario 3 Search by Message Content`() = runTest(testDispatcher) {
        val allChatsFlow = MutableStateFlow<List<Chat>>(emptyList())
        val matchingChat = createTestChat(10, "Kotlin Chat", today, false)
        val searchChatsFlow = MutableStateFlow(listOf(matchingChat))
        
        every { mockGetAllChatsUseCase.invoke() } returns allChatsFlow
        every { mockSearchChatsUseCase.invoke("coroutines") } returns searchChatsFlow

        viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        
        viewModel.onSearchQueryChange("coroutines")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val allVisibleChats = state.otherChats + state.pinnedChats
        assertEquals(1, allVisibleChats.size)
        assertEquals(10L, allVisibleChats.first().id)
    }

    @Test
    fun `Scenario 4 No Matching Results`() = runTest(testDispatcher) {
        val allChatsFlow = MutableStateFlow<List<Chat>>(emptyList())
        val searchChatsFlow = MutableStateFlow<List<Chat>>(emptyList())
        
        every { mockGetAllChatsUseCase.invoke() } returns allChatsFlow
        every { mockSearchChatsUseCase.invoke("xyz123nonexistent") } returns searchChatsFlow

        viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        
        viewModel.onSearchQueryChange("xyz123nonexistent")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.pinnedChats.isEmpty(), "Pinned chats should be empty")
        assertTrue(state.otherChats.isEmpty(), "Other chats should be empty")
    }

    @Test
    fun `Scenario 5 Search Query Debouncing`() = runTest(testDispatcher) {
        val allChatsFlow = MutableStateFlow<List<Chat>>(emptyList())
        val searchChatsFlow = MutableStateFlow<List<Chat>>(emptyList())
        
        every { mockGetAllChatsUseCase.invoke() } returns allChatsFlow
        every { mockSearchChatsUseCase.invoke(any()) } returns searchChatsFlow

        viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        
        // Simulating rapid typing
        viewModel.onSearchQueryChange("h")
        viewModel.onSearchQueryChange("he")
        viewModel.onSearchQueryChange("hel")
        viewModel.onSearchQueryChange("hell")
        viewModel.onSearchQueryChange("hello")
        
        advanceUntilIdle()

        // Verify that the use case was only invoked exactly once with the final string
        io.mockk.verify(exactly = 1) { mockSearchChatsUseCase.invoke("hello") }
        io.mockk.verify(exactly = 0) { mockSearchChatsUseCase.invoke("h") }
        io.mockk.verify(exactly = 0) { mockSearchChatsUseCase.invoke("he") }
        io.mockk.verify(exactly = 0) { mockSearchChatsUseCase.invoke("hel") }
        io.mockk.verify(exactly = 0) { mockSearchChatsUseCase.invoke("hell") }
    }

    @Test
    fun `Scenario 6 Clear Search Query Restores All Chats`() = runTest(testDispatcher) {
        val testChats = listOf(
            createTestChat(1, "Chat 1", today, false),
            createTestChat(2, "Chat 2", today, false),
            createTestChat(3, "Chat 3", today, false),
            createTestChat(4, "Chat 4", today, false),
            createTestChat(5, "Chat 5", today, false)
        )
        val allChatsFlow = MutableStateFlow(testChats)
        val searchResult = createTestChat(1, "Project Chat", today, false)
        val searchChatsFlow = MutableStateFlow(listOf(searchResult))
        
        every { mockGetAllChatsUseCase.invoke() } returns allChatsFlow
        every { mockSearchChatsUseCase.invoke("Project") } returns searchChatsFlow
        every { mockSearchChatsUseCase.invoke("") } returns allChatsFlow

        viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        
        viewModel.onSearchQueryChange("Project")
        advanceUntilIdle()
        
        var state = viewModel.uiState.value
        assertEquals(1, state.otherChats.size + state.pinnedChats.size)
        assertEquals("Project Chat", (state.otherChats + state.pinnedChats).first().name)
        
        // Clear search query
        viewModel.onSearchQueryChange("")
        advanceUntilIdle()
        
        state = viewModel.uiState.value
        assertEquals(5, state.otherChats.size + state.pinnedChats.size)
        val chatIds = (state.otherChats + state.pinnedChats).map { it.id }.toSet()
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), chatIds)
    }

    @Test
    fun `Error 1 FTS Query with Special Characters Handled via Sanitizer`() = runTest(testDispatcher) {
        // In this unit test, we verify that the ViewModel correctly passes the "raw" 
        // special character query to the use case without crashing.
        // The actual crash prevention (Sanitization) is verified in FtsSanitizerTest.
        val searchChatsFlow = MutableStateFlow<List<Chat>>(emptyList())
        val invalidQuery = "\"*^\" OR AND"
        
        every { mockGetAllChatsUseCase.invoke() } returns MutableStateFlow(emptyList())
        every { mockSearchChatsUseCase.invoke(invalidQuery) } returns searchChatsFlow

        viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
        
        viewModel.onSearchQueryChange(invalidQuery)
        advanceUntilIdle()
        
        // Ensure it reached the use case
        io.mockk.verify { mockSearchChatsUseCase.invoke(invalidQuery) }
        assertTrue(viewModel.uiState.value.otherChats.isEmpty())
    }
}

