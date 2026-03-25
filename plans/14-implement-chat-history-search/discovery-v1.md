# Discovery Report — Search Chat History Feature

## Executive Summary

The Search Chat History feature enables users to filter the displayed chats on the `HistoryScreen` by typing in a search field located in the top app bar. The search should filter chats based on (1) chat name matches and (2) message content matches, leveraging the existing FTS4 `MessageSearch` entity. The current implementation has a `HistoryTopBar` with a `searchQuery` state that is purely local and disconnected from the ViewModel/data layer, representing the primary gap to be addressed.

## Module Structure

```
feature/history/
├── HistoryViewModel.kt      # Main ViewModel with combine() for UI state
├── HistoryScreen.kt         # Composable consuming HistoryUiState
├── HistoryTopBar.kt         # UI with OutlinedTextField (searchQuery local state)
├── HistoryRoute.kt          # Hilt-wired route composable
├── HistoryModels.kt         # HistoryUiState, HistoryChat
├── HistoryMappers.kt       # Chat.toHistoryChat() mapping
└── HistoryEvent.kt         # Sealed class for one-time events

core/data/
├── MessageDao.kt            # searchMessages(query) FTS query exists
├── MessageSearch.kt         # FTS4 entity (id, content)
├── ChatDao.kt               # getAllChats(), basic CRUD
├── ChatRepositoryImpl.kt    # Repository implementation
└── PocketCrewDatabase.kt    # Room database with FTS4 triggers

core/domain/
├── model/chat/Chat.kt       # Domain model
├── port/repository/ChatRepository.kt  # Repository interface
└── usecase/                 # Use cases for chat operations
```

## Data Models

### HistoryUiState (`HistoryModels.kt:10-15`)
```kotlin
data class HistoryUiState(
    val pinnedChats: List<HistoryChat> = emptyList(),
    val otherChats: List<HistoryChat> = emptyList(),
    val isLoading: Boolean = false,
    val hapticPress: Boolean = true,
)
```

### HistoryChat (`HistoryModels.kt:3-8`)
```kotlin
data class HistoryChat(
    val id: Long,
    val name: String,
    val lastMessageDateTime: String,
    val isPinned: Boolean
)
```

### MessageSearch Entity (`MessageSearch.kt:7-14`)
```kotlin
@Entity(tableName = "message_search")
@Fts4(contentEntity = MessageEntity::class)
data class MessageSearch(
    @ColumnInfo(name = "id")
    val id: Long,
    @ColumnInfo(name = "content")
    val content: String
)
```

### FTS4 Table Schema (from database schema JSON)
- **Table**: `message_search`
- **Type**: Virtual table using FTS4
- **Content Table**: `message` (auto-synced via triggers)
- **Tokenizer**: `simple`
- **Sync Triggers**: BEFORE/AFTER UPDATE and BEFORE/AFTER DELETE on `message` table

## API Surface

### ChatRepository Interface (`core/domain/.../ChatRepository.kt`)
- `getAllChats(): Flow<List<Chat>>` — Current source for all chats
- `deleteChat(id: Long): Unit`
- `renameChat(id: Long, name: String): Unit`
- `togglePinChat(id: Long): Unit`

### MessageDao FTS Query (`MessageDao.kt:24-28`)
```kotlin
@Query("""
    SELECT message.* FROM message
    JOIN message_search ON message.id = message_search.rowid
    WHERE message_search MATCH :query
""")
abstract fun searchMessages(query: String): Flow<List<MessageEntity>>
```

### GetAllChatsUseCase
- Injected into `HistoryViewModel`
- Returns `Flow<List<Chat>>`

## Dependencies

| Dependency | Purpose |
|---|---|
| `com.browntowndev.pocketcrew.core.data` | Room database, DAOs, Repository implementations |
| `com.browntowndev.pocketcrew.domain` | Domain models, repository ports, use cases |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | `hiltViewModel()`, `collectAsStateWithLifecycle()` |
| `androidx.compose.material3` | Material 3 UI components |
| `kotlinx.coroutines` | `Flow`, `combine`, `debounce`, `flatMapLatest` |

## Utility Patterns

### Existing Search Infrastructure
- `MessageDao.searchMessages(query: String)` already exists and returns `Flow<List<MessageEntity>>`
- FTS4 triggers are already set up on the `message` table
- The `MessageSearch` FTS entity is content-linked to `MessageEntity`

### ViewModel StateFlow Pattern
- `HistoryViewModel` uses `combine()` to merge `getAllChatsUseCase()` and `getSettingsUseCase()`
- `stateIn()` with `SharingStarted.WhileSubscribed(5000)` for lifecycle-aware collection
- Initial value set to `HistoryUiState(isLoading = true)`

### Date Formatting
- `formatRelativeDate()` in ViewModel handles "Today", "Yesterday", and date formatting

## Gap Analysis

| What Exists | What Is Needed |
|---|---|
| `HistoryTopBar.searchQuery` as local `mutableStateOf("")` | `searchQuery` hoisted to ViewModel via callback |
| `HistoryUiState` without search state | `HistoryUiState` optionally includes `searchQuery` for persistence |
| `MessageDao.searchMessages()` returns messages | New DAO method to return chat IDs (not messages) from FTS |
| `getAllChatsUseCase()` returns all chats | New `SearchChatsUseCase()` or extended repository method |
| No search filtering in ViewModel | Debounced search logic with `flatMapLatest` |
| No FTS query sanitization | Input sanitization to prevent SQLite FTS crashes |

### Critical Gaps

1. **State Hoisting**: `HistoryTopBar` must accept `searchQuery: String` and `onSearchQueryChange: (String) -> Unit` parameters
2. **FTS Query Safety**: Direct user input into `MATCH` clauses can crash SQLite if special characters (`, "`, `*`, `-`, `^`) are present
3. **Search Chats by Message**: `searchMessages()` returns individual messages; need to extract unique chat IDs

## Risk Assessment

| Risk | Severity | Mitigation |
|---|---|---|
| SQLite FTS crashes with special characters | **High** | Sanitize input: escape FTS operators, append `*` for prefix matching |
| Compose recomposition thrashing | **High** | Keep `searchQuery` in separate `StateFlow`, NOT in `HistoryUiState` |
| Race conditions with fast typing | **Medium** | Use `flatMapLatest` with debounce to cancel stale queries |
| In-memory filtering inefficiency | **Medium** | Push filtering to SQLite via JOIN query, not Kotlin `.filter {}` |
| FTS table sync reliability | **Low** | Verify existing triggers cover all message CRUD paths |

## Scope Assessment

### Files to Modify
1. `HistoryTopBar.kt` — Add search state parameters, remove local state
2. `HistoryScreen.kt` — Pass search props through to TopBar
3. `HistoryRoute.kt` — Collect search query, wire callbacks
4. `HistoryViewModel.kt` — Add search `StateFlow`, debounced filtering logic
5. `HistoryModels.kt` — Optionally add `searchQuery` to `HistoryUiState`

### Files to Add
1. FTS sanitization extension function (data layer)
2. `ChatDao.searchChatsByMessageContent()` method (or similar)
3. `SearchChatsUseCase` (domain layer)
4. `ChatRepository.searchChatsByMessageContent()` (domain port + data impl)

### Files to Test
1. `HistoryViewModel` — New search filtering unit tests
2. `HistoryViewModelTest` — Add search behavior tests

## Recommendation

**Proceed to Spec Phase**

The feature is well-scoped and the codebase has solid foundations:
- FTS4 infrastructure already exists and is production-ready
- ViewModel pattern supports the required state management
- Clear path from UI to database through existing layers

The primary work involves:
1. Proper state hoisting from TopBar to ViewModel
2. FTS query sanitization for safety
3. Optimized database query for chat-level search
4. Debounced reactive pipeline in ViewModel

This is a **Tier 2 — Modular** feature: new functionality with well-defined cross-layer ripple. Proceed to Phase 2 (Spec) to detail the implementation.
