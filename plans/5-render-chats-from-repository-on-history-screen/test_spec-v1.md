# Test Specification — #5: Wire History Screen to Repository

## Overview

Behavioral test scenarios for wiring the History screen to pull chats from the repository instead of displaying hardcoded mock data. Tests verify data flow from DAO → Repository → Use Case → ViewModel → UI model mapping.

---

## A. GetAllChatsUseCase Tests

### Scenario: Returns chats from repository

**Given** a `ChatRepository` that returns a `Flow` emitting a list of 3 chats when `getAllChats()` is called

**When** `GetAllChatsUseCase()` is invoked

**Then** the returned `Flow` emits exactly 3 `Chat` domain objects in the order returned by the repository

**Mutation Heuristic:** A broken implementation returning a hardcoded list and ignoring the repository would fail this test.

---

### Scenario: Empty database returns empty list

**Given** a `ChatRepository` that returns a `Flow` emitting an empty list when `getAllChats()` is called

**When** `GetAllChatsUseCase()` is invoked

**Then** the returned `Flow` emits an empty list

**Mutation Heuristic:** A broken implementation returning hardcoded non-empty data would fail this test.

---

### Scenario: Flow emissions are forwarded

**Given** a `ChatRepository` that returns a `Flow` that emits twice with different chat lists

**When** `GetAllChatsUseCase()` is invoked and the repository flow emits twice

**Then** the use case flow receives both emissions with their respective chat lists

**Mutation Heuristic:** A broken implementation returning a one-shot value without proper Flow handling would fail this test.

---

### Scenario: Repository error propagates

**Given** a `ChatRepository` where `getAllChats()` returns a `Flow` that errors with a runtime exception

**When** `GetAllChatsUseCase()` is invoked and the flow is collected

**Then** the error propagates through the use case

**Mutation Heuristic:** A broken implementation catching all exceptions silently would fail this test.

---

## B. HistoryViewModel Tests

### Scenario: Loads chats on initialization

**Given** a `GetAllChatsUseCase` that returns a `Flow` emitting a list of 4 chats where 2 have `pinned=true` and 2 have `pinned=false`

**When** the `HistoryViewModel` is initialized

**Then** `uiState.pinnedChats` contains 2 `HistoryChat` objects and `uiState.otherChats` contains 2 `HistoryChat` objects

**Mutation Heuristic:** A broken implementation putting all chats in one list would fail this test.

---

### Scenario: Maps Chat to HistoryChat correctly

**Given** a `Chat` domain object with:
- `id = 42`
- `name = "Test Chat"`
- `lastModified = today's date at 10:30 AM`
- `pinned = false`

**When** the `HistoryViewModel` processes this chat

**Then** the resulting `HistoryChat` has:
- `id = 42`
- `name = "Test Chat"`
- `lastMessageDateTime = "Today, 10:30 AM"`
- `isPinned = false`

**Mutation Heuristic:** A broken implementation mapping wrong fields (e.g., `id` to `name`) would fail this test.

---

### Scenario: Pinned chats appear in pinned section

**Given** a `Chat` with `pinned = true`

**When** the `HistoryViewModel` processes the chat list

**Then** the chat appears in `uiState.pinnedChats`

**Mutation Heuristic:** A broken implementation filtering nothing and putting all chats in `pinnedChats` would fail this test when combined with unpinned chat tests.

---

### Scenario: Unpinned chats appear in recent section

**Given** a `Chat` with `pinned = false`

**When** the `HistoryViewModel` processes the chat list

**Then** the chat appears in `uiState.otherChats`

**Mutation Heuristic:** A broken implementation putting all chats in `otherChats` would fail this test when combined with pinned chat tests.

---

### Scenario: Empty database produces empty lists

**Given** a `GetAllChatsUseCase` that returns a `Flow` emitting an empty list

**When** the `HistoryViewModel` is initialized

**Then** `uiState.pinnedChats` is empty and `uiState.otherChats` is empty

**Mutation Heuristic:** A broken implementation initializing with mock data would fail this test.

---

### Scenario: Chats maintain DAO sort order (DESC by lastModified)

**Given** a `Chat` list with 3 chats sorted by DAO as:
1. `lastModified = 1700` (most recent)
2. `lastModified = 1500`
3. `lastModified = 1200` (oldest)

**When** the `HistoryViewModel` processes the chat list

**Then** the resulting list order matches the DAO ordering (descending by `lastModified`)

**Mutation Heuristic:** A broken implementation re-sorting alphabetically or by ID would fail this test.

---

### Scenario: Loading state true on init before data arrives

**Given** a `GetAllChatsUseCase` that returns a `Flow` that delays emission (simulating slow database)

**When** the `HistoryViewModel` is initialized

**Then** `uiState.isLoading` is `true` initially before any emission

**Mutation Heuristic:** A broken implementation setting `isLoading = false` immediately without waiting for data would fail this test.

---

### Scenario: Loading state false after data arrives

**Given** a `GetAllChatsUseCase` that returns a `Flow` emitting a non-empty list

**When** the `HistoryViewModel` is initialized and the flow emits

**Then** `uiState.isLoading` is `false` after the emission

**Mutation Heuristic:** A broken implementation never setting `isLoading = false` would fail this test.

---

## C. Relative Date Formatting Tests

### Scenario: Today format for same-day dates

**Given** a `Chat` with `lastModified` set to today's date at 10:30 AM

**When** the `HistoryViewModel` formats the timestamp

**Then** `lastMessageDateTime = "Today, 10:30 AM"`

**Mutation Heuristic:** A broken implementation always returning "Today, 12:00 AM" would fail this test.

---

### Scenario: Yesterday format for previous-day dates

**Given** a `Chat` with `lastModified` set to yesterday's date at 6:15 PM

**When** the `HistoryViewModel` formats the timestamp

**Then** `lastMessageDateTime = "Yesterday, 6:15 PM"`

**Mutation Heuristic:** A broken implementation always returning "Today, 12:00 AM" would fail this test.

---

### Scenario: Date format for older dates

**Given** a `Chat` with `lastModified` set to October 24 at 2:00 PM

**When** the `HistoryViewModel` formats the timestamp

**Then** `lastMessageDateTime = "Oct 24, 2:00 PM"`

**Mutation Heuristic:** A broken implementation always returning "Today, 12:00 AM" would fail this test.

---

### Scenario: Uses device timezone for formatting

**Given** a `Chat` with `lastModified` at a time that differs between UTC and local

**When** the `HistoryViewModel` formats the timestamp

**Then** the formatted time reflects the device's local timezone, not UTC

**Mutation Heuristic:** A broken implementation using fixed UTC formatting would fail this test.

---

## D. Repository Implementation Tests

### Scenario: Repository maps Entity to Domain

**Given** a `ChatEntity` with `id = 1`, `name = "Test"`, `pinned = true`

**When** `ChatRepositoryImpl.getAllChats()` is called

**Then** the returned `Flow` emits a `Chat` domain object with matching `id`, `name`, and `pinned` values

**Mutation Heuristic:** A broken implementation returning entities directly without mapping would fail this test.

---

### Scenario: togglePinStatus updates the database

**Given** a chat with `chatId = 5` in the database with `pinned = false`

**When** `ChatRepository.togglePinStatus(5)` is called

**Then** the chat with `id = 5` has `pinned = true` in the database

**Mutation Heuristic:** A broken implementation doing nothing would fail this test.

---

### Scenario: togglePinStatus toggles from true to false

**Given** a chat with `chatId = 10` in the database with `pinned = true`

**When** `ChatRepository.togglePinStatus(10)` is called

**Then** the chat with `id = 10` has `pinned = false` in the database

**Mutation Heuristic:** A broken implementation always setting `pinned = true` would fail this test.

---

## E. Error Path Tests

### Scenario: Repository throws on getAllChats

**Given** a `ChatRepository` where `getAllChats()` throws a `RuntimeException`

**When** `HistoryViewModel` is initialized and subscribes to the use case

**Then** the error is handled gracefully without crashing (logged or stored in error state)

**Mutation Heuristic:** A broken implementation not catching exceptions would crash the app.

---

### Scenario: Empty pinned section

**Given** all chats have `pinned = false`

**When** the `HistoryViewModel` processes the chat list

**Then** `uiState.pinnedChats` is empty and `uiState.otherChats` contains all chats

**Mutation Heuristic:** A broken implementation putting all chats in `pinnedChats` would fail this test.

---

### Scenario: Empty recent section

**Given** all chats have `pinned = true`

**When** the `HistoryViewModel` processes the chat list

**Then** `uiState.otherChats` is empty and `uiState.pinnedChats` contains all chats

**Mutation Heuristic:** A broken implementation putting all chats in `otherChats` would fail this test.

---

### Scenario: Non-existent chat toggle is handled gracefully

**Given** a `ChatRepository` with no chat having `chatId = 9999`

**When** `ChatRepository.togglePinStatus(9999)` is called

**Then** the operation completes without error (no-op)

**Mutation Heuristic:** A broken implementation throwing an exception for missing rows would fail this test.

---

## F. Integration Test Scenarios

### Scenario: End-to-end from DAO to UI state

**Given** the database contains:
- Chat A: `id=1`, `name="Alpha"`, `pinned=true`, `lastModified=today`
- Chat B: `id=2`, `name="Beta"`, `pinned=false`, `lastModified=yesterday`
- Chat C: `id=3`, `name="Gamma"`, `pinned=true`, `lastModified=Oct 15`

**When** the `HistoryViewModel` is initialized and flow stabilizes

**Then** `uiState.pinnedChats` contains Chat A and Chat C with correct formatted timestamps, and `uiState.otherChats` contains Chat B with correct formatted timestamp

**Mutation Heuristic:** A broken implementation in any layer (DAO, Repository, UseCase, ViewModel) would fail this test.

---

## Test File Locations

| Test Class | File Path |
|------------|-----------|
| `GetAllChatsUseCaseTest` | `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/GetAllChatsUseCaseTest.kt` |
| `HistoryViewModelTest` | `feature/history/src/test/kotlin/com/browntowndev/pocketcrew/feature/history/HistoryViewModelTest.kt` |
| `ChatRepositoryImplTest` | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/repository/ChatRepositoryImplTest.kt` |

---

## Testing Patterns Reference

### Coroutine Testing Harness (Mandatory)

All async tests MUST use:

```kotlin
private val testDispatcher = StandardTestDispatcher()
private val testScope = TestScope(testDispatcher)

@BeforeEach
fun setup() {
    Dispatchers.setMain(testDispatcher)
}

@Test
fun scenario() = testScope.runTest {
    // Test code with advanceUntilIdle()
}
```

### Flow Testing with Turbine

```kotlin
@Test
fun scenario() = testScope.runTest {
    // Given
    val chatFlow = MutableStateFlow(listOf(testChat))
    every { mockRepository.getAllChats() } returns chatFlow
    
    // When
    val useCase = GetAllChatsUseCase(mockRepository)
    
    // Then
    useCase().test {
        awaitItem() shouldBe listOf(testChat)
        awaitComplete()
    }
}
```

### ViewModel Testing

```kotlin
@Test
fun scenario() = testScope.runTest {
    // Given
    val chatFlow = MutableStateFlow(listOf(testChat))
    every { mockGetAllChatsUseCase() } returns chatFlow
    
    // When
    val viewModel = HistoryViewModel(mockGetAllChatsUseCase, mockSettingsUseCase)
    
    // Then
    val state = viewModel.uiState.value
    state.pinnedChats shouldHaveSize 1
}
```

---

## Mutation Heuristic Summary

| Scenario | Broken Implementation That Would Pass |
|----------|--------------------------------------|
| Returns chats from repository | Return hardcoded list, ignore repository |
| Maps Chat to HistoryChat | Map wrong fields (id→name, name→id) |
| Pinned section | Put all chats in pinnedChats, filter nothing |
| Today format | Always return "Today, 12:00 AM" |
| Loading state | Never set isLoading = false |
| Empty pinned section | Put all chats in pinnedChats regardless |

---

*All test scenarios must pass before the feature is considered complete. The implementation must satisfy all scenarios without modification to the test code.*
