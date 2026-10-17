package com.browntowndev.enclave.feature.history
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.chat.Chat
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.usecase.chat.DeleteChatUseCase
import com.browntowndev.enclave.domain.usecase.chat.GetAllChatsUseCase
import com.browntowndev.enclave.domain.usecase.chat.RenameChatUseCase
import com.browntowndev.enclave.domain.usecase.chat.SearchChatsUseCase
import com.browntowndev.enclave.domain.usecase.chat.TogglePinChatUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
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
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    // ENC-28: real use cases over :core:testing fakes; recorded invocations live on the fakes.
    private lateinit var fakeChatRepository: FakeChatRepository
    private lateinit var fakeMessageRepository: FakeMessageRepository
    private lateinit var fakeEmbeddingEngine: FakeEmbeddingEngine
    private lateinit var getAllChatsUseCase: GetAllChatsUseCase
    private lateinit var searchChatsUseCase: SearchChatsUseCase
    private lateinit var deleteChatUseCase: DeleteChatUseCase
    private lateinit var renameChatUseCase: RenameChatUseCase
    private lateinit var togglePinChatUseCase: TogglePinChatUseCase
    private data class RecordedError(
        val tag: String,
        val message: String,
        val throwable: Throwable,
        val userMessage: String,
    )

    private val recordedErrors = mutableListOf<RecordedError>()

    private val fakeErrorHandler =
        object : ViewModelErrorHandler {
            override val errorEvents =
                kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 16)

            override fun handleError(tag: String, message: String, throwable: Throwable, userMessage: String) {
                recordedErrors += RecordedError(tag, message, throwable, userMessage)
            }

            override fun coroutineExceptionHandler(tag: String, message: String, userMessage: String) =
                kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
                    handleError(tag, message, throwable, userMessage)
                }
        }

    private lateinit var errorHandler: ViewModelErrorHandler
    private lateinit var viewModel: HistoryViewModel

    private val today =
        Calendar
            .getInstance()
            .apply {
                set(Calendar.HOUR_OF_DAY, 10)
                set(Calendar.MINUTE, 30)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

    private val yesterday =
        Calendar
            .getInstance()
            .apply {
                add(Calendar.DAY_OF_YEAR, -1)
                set(Calendar.HOUR_OF_DAY, 18)
                set(Calendar.MINUTE, 15)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

    private val oct24 =
        Calendar
            .getInstance()
            .apply {
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
        id: ChatId,
        name: String,
        lastModified: Date,
        pinned: Boolean,
    ): Chat =
        Chat(
            id = id,
            name = name,
            created = lastModified,
            lastModified = lastModified,
            pinned = pinned,
        )

    @BeforeEach
    fun setup() {
        fakeChatRepository = FakeChatRepository()
        fakeMessageRepository = FakeMessageRepository()
        fakeEmbeddingEngine = FakeEmbeddingEngine()
        getAllChatsUseCase = GetAllChatsUseCase(fakeChatRepository)
        searchChatsUseCase = SearchChatsUseCase(fakeChatRepository, fakeMessageRepository, fakeEmbeddingEngine)
        deleteChatUseCase = DeleteChatUseCase(fakeChatRepository)
        renameChatUseCase = RenameChatUseCase(fakeChatRepository)
        togglePinChatUseCase = TogglePinChatUseCase(fakeChatRepository)
        errorHandler = fakeErrorHandler
    }

    private fun createViewModel(): HistoryViewModel =
        HistoryViewModel(
            getAllChatsUseCase = getAllChatsUseCase,
            searchChatsUseCase = searchChatsUseCase,
            deleteChatUseCase = deleteChatUseCase,
            renameChatUseCase = renameChatUseCase,
            togglePinChatUseCase = togglePinChatUseCase,
            errorHandler = errorHandler,
        )

    @Test
    fun `B1 loads chats on initialization`() =
        runTest(testDispatcher) {
            val testChats =
                listOf(
                    createTestChat(ChatId("1"), "Alpha", today, pinned = true),
                    createTestChat(ChatId("2"), "Beta", yesterday, pinned = true),
                    createTestChat(ChatId("3"), "Gamma", oct24, pinned = false),
                    createTestChat(ChatId("4"), "Delta", today, pinned = false),
                )
            fakeChatRepository.seedChats(emptyList())

            viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

            fakeChatRepository.setChats(testChats)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2, state.pinnedChats.size)
            assertEquals(2, state.otherChats.size)
            assertEquals(false, state.isLoading)
        }

    @Test
    fun `B2 maps Chat to HistoryChat correctly`() =
        runTest(testDispatcher) {
            val testChat = createTestChat(ChatId("42"), "Test Chat", today, pinned = false)
            fakeChatRepository.seedChats(emptyList())

            viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

            fakeChatRepository.setChats(listOf(testChat))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(1, state.otherChats.size)
            val historyChat = state.otherChats.first()
            assertEquals(ChatId("42"), historyChat.id)
            assertEquals("Test Chat", historyChat.name)
            assertEquals("Today, 10:30 AM", historyChat.lastMessageDateTime)
            assertFalse(historyChat.isPinned)
        }

    @Test
    fun `B3 pinned chats appear in pinned section`() =
        runTest(testDispatcher) {
            val pinnedChat = createTestChat(ChatId("1"), "Pinned Chat", today, pinned = true)
            fakeChatRepository.seedChats(emptyList())

            viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

            fakeChatRepository.setChats(listOf(pinnedChat))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.pinnedChats.any { it.id == ChatId("1") })
            assertEquals(1, state.pinnedChats.size)
        }

    @Test
    fun `B4 unpinned chats appear in recent section`() =
        runTest(testDispatcher) {
            val unpinnedChat = createTestChat(ChatId("1"), "Recent Chat", today, pinned = false)
            fakeChatRepository.seedChats(emptyList())

            viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

            fakeChatRepository.setChats(listOf(unpinnedChat))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.otherChats.any { it.id == ChatId("1") })
            assertEquals(1, state.otherChats.size)
        }

    @Test
    fun `B5 empty database produces empty lists`() =
        runTest(testDispatcher) {
            fakeChatRepository.seedChats(emptyList())

            viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.pinnedChats.isEmpty())
            assertTrue(state.otherChats.isEmpty())
            assertFalse(state.isLoading)
        }

    @Test
    fun `B6 chats maintain DAO sort order`() =
        runTest(testDispatcher) {
            val chat1 = createTestChat(ChatId("1"), "Most Recent", Date(1700), pinned = false)
            val chat2 = createTestChat(ChatId("2"), "Middle", Date(1500), pinned = false)
            val chat3 = createTestChat(ChatId("3"), "Oldest", Date(1200), pinned = false)
            fakeChatRepository.seedChats(emptyList())

            viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

            fakeChatRepository.setChats(listOf(chat1, chat2, chat3))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(3, state.otherChats.size)
            assertEquals(ChatId("1"), state.otherChats[0].id)
            assertEquals(ChatId("2"), state.otherChats[1].id)
            assertEquals(ChatId("3"), state.otherChats[2].id)
        }

    @Test
    fun `B7 loading state managed correctly`() =
        runTest(testDispatcher) {
            fakeChatRepository.seedChats(emptyList())

            viewModel = createViewModel()
            // Check initial value directly before any collectors or flow emissions
            assertTrue(viewModel.uiState.value.isLoading)
        }

    @Test
    fun `B8 loading state false after data arrives`() =
        runTest(testDispatcher) {
            val testChat = createTestChat(ChatId("1"), "Test", today, pinned = false)
            fakeChatRepository.seedChats(emptyList())

            viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

            fakeChatRepository.setChats(listOf(testChat))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
        }

    @Test
    fun `B9 pinned chats are sorted by lastModified descending`() =
        runTest(testDispatcher) {
            val chat1 = createTestChat(ChatId("1"), "Older Pinned", Date(1000), pinned = true)
            val chat2 = createTestChat(ChatId("2"), "Newer Pinned", Date(2000), pinned = true)
            fakeChatRepository.seedChats(emptyList())

            viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

            fakeChatRepository.setChats(listOf(chat1, chat2))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2, state.pinnedChats.size)
            assertEquals(ChatId("2"), state.pinnedChats[0].id)
            assertEquals(ChatId("1"), state.pinnedChats[1].id)
        }

    @Test
    fun `deleteChat error invokes errorHandler`() =
        runTest(testDispatcher) {
            // Given
            val chatId = ChatId("1")
            val exception = RuntimeException("Delete Error")
            fakeChatRepository.deleteFailure = exception

            viewModel = createViewModel()

            // When
            viewModel.deleteChat(chatId)
            advanceUntilIdle()

            // Then — state-based: the recording fake captured exactly one routed error.
            assertEquals(
                listOf(
                    RecordedError(
                        tag = "HistoryViewModel",
                        message = "Failed to delete chat with id: ChatId(value=1)",
                        throwable = exception,
                        userMessage = "Failed to delete chat",
                    ),
                ),
                recordedErrors,
            )
        }

    // Suite H: Happy Path Tests

    @Test
    fun `H1 pinChat success - chat appears in pinned section`() =
        runTest(testDispatcher) {
            val chatId = ChatId("pin-me")
            val unpinned = createTestChat(chatId, "To Pin", today, pinned = false)
            fakeChatRepository.seedChats(listOf(unpinned))

            viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
            advanceUntilIdle()

            var state = viewModel.uiState.value
            assertEquals(1, state.otherChats.size)
            assertTrue(state.pinnedChats.isEmpty())

            viewModel.pinChat(chatId)
            advanceUntilIdle()

            state = viewModel.uiState.value
            assertTrue(state.pinnedChats.any { it.id == chatId })
            assertFalse(state.otherChats.any { it.id == chatId })
            assertEquals(listOf(chatId), fakeChatRepository.togglePinCalls)
        }

    @Test
    fun `H2 deleteChat success - chat removed from UI, use case invoked`() =
        runTest(testDispatcher) {
            val chatId = ChatId("delete-me")
            val toDelete = createTestChat(chatId, "Delete Me", today, pinned = false)
            val toKeep = createTestChat(ChatId("keep"), "Keep Me", today, pinned = false)
            fakeChatRepository.seedChats(listOf(toDelete, toKeep))

            viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.otherChats.size)

            viewModel.deleteChat(chatId)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(1, state.otherChats.size)
            assertEquals(ChatId("keep"), state.otherChats.first().id)
            assertFalse(state.otherChats.any { it.id == chatId })
            assertEquals(listOf(chatId), fakeChatRepository.deleteCalls)
        }

    // Suite C: Relative Date Formatting Tests

    @Test
    fun `C1 today format for same-day dates`() =
        runTest(testDispatcher) {
            val todayDate =
                Calendar
                    .getInstance()
                    .apply {
                        set(Calendar.HOUR_OF_DAY, 10)
                        set(Calendar.MINUTE, 30)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.time
            val testChat = createTestChat(ChatId("1"), "Test", todayDate, pinned = false)
            fakeChatRepository.seedChats(emptyList())

            viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

            fakeChatRepository.setChats(listOf(testChat))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Today, 10:30 AM", state.otherChats.first().lastMessageDateTime)
        }

    @Test
    fun `C2 yesterday format for previous-day dates`() =
        runTest(testDispatcher) {
            val yesterdayDate =
                Calendar
                    .getInstance()
                    .apply {
                        add(Calendar.DAY_OF_YEAR, -1)
                        set(Calendar.HOUR_OF_DAY, 18)
                        set(Calendar.MINUTE, 15)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.time
            val testChat = createTestChat(ChatId("1"), "Test", yesterdayDate, pinned = false)
            fakeChatRepository.seedChats(emptyList())

            viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

            fakeChatRepository.setChats(listOf(testChat))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Yesterday, 6:15 PM", state.otherChats.first().lastMessageDateTime)
        }

    @Test
    fun `C3 date format for older dates`() =
        runTest(testDispatcher) {
            val oct24Date =
                Calendar
                    .getInstance()
                    .apply {
                        set(Calendar.MONTH, Calendar.OCTOBER)
                        set(Calendar.DAY_OF_MONTH, 24)
                        set(Calendar.HOUR_OF_DAY, 14)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.time
            val testChat = createTestChat(ChatId("1"), "Test", oct24Date, pinned = false)
            fakeChatRepository.seedChats(emptyList())

            viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

            fakeChatRepository.setChats(listOf(testChat))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Oct 24, 2:00 PM", state.otherChats.first().lastMessageDateTime)
        }

    @Test
    fun `C4 uses device timezone for formatting`() =
        runTest(testDispatcher) {
            // Create a date at a specific hour in UTC
            val calendar =
                Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
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
                val testChat = createTestChat(ChatId("1"), "Test", utcDate, pinned = false)
                fakeChatRepository.seedChats(emptyList())
    
                viewModel = createViewModel()
                backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

                fakeChatRepository.setChats(listOf(testChat))
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
    fun `E1 repository throws on getAllChats handled gracefully`() =
        runTest(testDispatcher) {
            fakeChatRepository.getAllChatsFailure = RuntimeException("DB Error")

            viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertTrue(state.pinnedChats.isEmpty())
            assertTrue(state.otherChats.isEmpty())
        }

    @Test
    fun `E2 empty pinned section when all chats unpinned`() =
        runTest(testDispatcher) {
            val unpinnedChats =
                listOf(
                    createTestChat(ChatId("1"), "Chat A", today, pinned = false),
                    createTestChat(ChatId("2"), "Chat B", today, pinned = false),
                )
            fakeChatRepository.seedChats(emptyList())

            viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

            fakeChatRepository.setChats(unpinnedChats)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.pinnedChats.isEmpty())
            assertEquals(2, state.otherChats.size)
        }

    @Test
    fun `E3 empty recent section when all chats pinned`() =
        runTest(testDispatcher) {
            val pinnedChats =
                listOf(
                    createTestChat(ChatId("1"), "Chat A", today, pinned = true),
                    createTestChat(ChatId("2"), "Chat B", today, pinned = true),
                )
            fakeChatRepository.seedChats(emptyList())

            viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

            fakeChatRepository.setChats(pinnedChats)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(2, state.pinnedChats.size)
            assertTrue(state.otherChats.isEmpty())
        }

    @Test
    fun `E4 non-existent chat toggle handled gracefully`() =
        runTest(testDispatcher) {
            fakeChatRepository.seedChats(emptyList())

            viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }
            advanceUntilIdle()

            val nonExistentId = ChatId("9999")
            viewModel.pinChat(nonExistentId)
            viewModel.unpinChat(nonExistentId)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(0, state.pinnedChats.size)
            assertEquals(0, state.otherChats.size)
        }

    // Suite F: Integration Test

    @Test
    fun `F1 end-to-end from DAO to UI state`() =
        runTest(testDispatcher) {
            val todayDate =
                Calendar
                    .getInstance()
                    .apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.time

            val yesterdayDate =
                Calendar
                    .getInstance()
                    .apply {
                        add(Calendar.DAY_OF_YEAR, -1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.time

            val oct15Date =
                Calendar
                    .getInstance()
                    .apply {
                        set(Calendar.MONTH, Calendar.OCTOBER)
                        set(Calendar.DAY_OF_MONTH, 15)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.time

            val chats =
                listOf(
                    createTestChat(ChatId("1"), "Alpha", todayDate, pinned = true),
                    createTestChat(ChatId("2"), "Beta", yesterdayDate, pinned = false),
                    createTestChat(ChatId("3"), "Gamma", oct15Date, pinned = true),
                )
            fakeChatRepository.seedChats(emptyList())

            viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

            fakeChatRepository.setChats(chats)
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
    fun `Scenario 1 Initial State Shows All Chats`() =
        runTest(testDispatcher) {
            val testChats =
                listOf(
                    createTestChat(ChatId("1"), "Chat 1", today, pinned = false),
                    createTestChat(ChatId("2"), "Chat 2", today, pinned = false),
                    createTestChat(ChatId("3"), "Chat 3", today, pinned = false),
                )
            fakeChatRepository.seedChats(testChats)

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
    fun `Scenario 2 Search by Chat Name`() =
        runTest(testDispatcher) {
            val matchingChat = createTestChat(ChatId("1"), "Project Plan", today, false)
            val otherChat = createTestChat(ChatId("2"), "Random Chat", today, false)
            fakeChatRepository.seedChats(listOf(matchingChat, otherChat))

            viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

            viewModel.onSearchQueryChange("Project")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            val allVisibleChats = state.otherChats + state.pinnedChats
            assertEquals(1, allVisibleChats.size)
            assertEquals("Project Plan", allVisibleChats.first().name)
            assertEquals(ChatId("1"), allVisibleChats.first().id)
        }

    @Test
    fun `Scenario 3 Search by Message Content`() =
        runTest(testDispatcher) {
            val matchingChat = createTestChat(ChatId("10"), "Kotlin Chat", today, false)
            fakeChatRepository.seedChats(listOf(matchingChat))
            val matchingMessage =
                Message(
                    id = MessageId("msg-coroutines"),
                    chatId = ChatId("10"),
                    content = Content("coroutines deep dive"),
                )
            fakeChatRepository.seedMessages(listOf(matchingMessage))
            fakeMessageRepository.similarMessageIds = listOf(MessageId("msg-coroutines"))

            viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

            viewModel.onSearchQueryChange("coroutines")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            val allVisibleChats = state.otherChats + state.pinnedChats
            assertEquals(1, allVisibleChats.size)
            assertEquals(ChatId("10"), allVisibleChats.first().id)
        }

    @Test
    fun `Scenario 4 No Matching Results`() =
        runTest(testDispatcher) {
            fakeChatRepository.seedChats(
                listOf(
                    createTestChat(ChatId("1"), "Alpha", today, false),
                    createTestChat(ChatId("2"), "Beta", today, false),
                ),
            )

            viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

            viewModel.onSearchQueryChange("xyz123nonexistent")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.pinnedChats.isEmpty(), "Pinned chats should be empty")
            assertTrue(state.otherChats.isEmpty(), "Other chats should be empty")
        }

    @Test
    fun `Scenario 5 Search Query Debouncing`() =
        runTest(testDispatcher) {
            fakeChatRepository.seedChats(emptyList())

            viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

            // Simulating rapid typing
            viewModel.onSearchQueryChange("h")
            viewModel.onSearchQueryChange("he")
            viewModel.onSearchQueryChange("hel")
            viewModel.onSearchQueryChange("hell")
            viewModel.onSearchQueryChange("hello")

            advanceUntilIdle()

            // Verify that search was only invoked exactly once with the final string.
            // ENC-27: invocation-count assertion — debounced intermediate queries
            // produce identical empty UiState, so this cannot be a state assertion;
            // the queries are recorded by FakeChatRepository instead of MockK verify.
            assertEquals(1, fakeChatRepository.searchQueries.count { it == "hello" })
            assertTrue(fakeChatRepository.searchQueries.none { it in listOf("h", "he", "hel", "hell") })
        }

    @Test
    fun `Scenario 6 Clear Search Query Restores All Chats`() =
        runTest(testDispatcher) {
            val testChats =
                listOf(
                    createTestChat(ChatId("1"), "Project Chat", today, false),
                    createTestChat(ChatId("2"), "Chat 2", today, false),
                    createTestChat(ChatId("3"), "Chat 3", today, false),
                    createTestChat(ChatId("4"), "Chat 4", today, false),
                    createTestChat(ChatId("5"), "Chat 5", today, false),
                )
            fakeChatRepository.seedChats(testChats)

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
            assertEquals(setOf(ChatId("1"), ChatId("2"), ChatId("3"), ChatId("4"), ChatId("5")), chatIds)
        }

    @Test
    fun `Error 1 FTS Query with Special Characters Handled via Sanitizer`() =
        runTest(testDispatcher) {
            // In this unit test, we verify that the ViewModel correctly passes the "raw"
            // special character query to the use case without crashing.
            // The actual crash prevention (Sanitization) is verified in FtsSanitizerTest.
            val invalidQuery = "\"*^\" OR AND"


            viewModel = createViewModel()
            backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect() }

            viewModel.onSearchQueryChange(invalidQuery)
            advanceUntilIdle()

            // Ensure it reached the repository unchanged through the real use case.
            // ENC-27: raw-query pass-through is an invocation argument assertion;
            // see the debounce recording above.
            assertTrue(fakeChatRepository.searchQueries.contains(invalidQuery))
            assertTrue(
                viewModel.uiState.value.otherChats
                    .isEmpty(),
            )
        }
}
