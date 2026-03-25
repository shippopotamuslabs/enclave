# Implementation Plan — 14-implement-chat-history-search

## Objective
Enable users to search their chat history by typing in the `HistoryTopBar`. The search should filter the displayed chats by matching either the chat name or the contents of the messages within the chat, leveraging the existing FTS4 `MessageSearch` entity. The search query must be hoisted to the `HistoryViewModel`, debounced, and safely executed against the database without causing SQLite crashes due to special characters.

## Acceptance Criteria
- [ ] Typing in the search bar updates the displayed list of chats.
- [ ] Chats are matched if their name contains the query (case-insensitive substring match).
- [ ] Chats are matched if any of their messages contain the query (FTS match).
- [ ] The search query is debounced to prevent excessive database queries and UI thrashing.
- [ ] FTS queries are sanitized to prevent SQLite crashes from special characters.
- [ ] When the search query is empty, all chats are displayed.
- [ ] The `searchQuery` state is maintained in a separate `StateFlow` from `HistoryUiState` to avoid recomposing the entire screen on every keystroke.

## Architecture
### Files to Create
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/SearchChatsUseCase.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/util/FtsSanitizer.kt` (or similar utility in data layer)

### Files to Modify
- `feature/history/src/main/kotlin/com/browntowndev/pocketcrew/feature/history/HistoryTopBar.kt`
- `feature/history/src/main/kotlin/com/browntowndev/pocketcrew/feature/history/HistoryScreen.kt`
- `feature/history/src/main/kotlin/com/browntowndev/pocketcrew/feature/history/HistoryRoute.kt`
- `feature/history/src/main/kotlin/com/browntowndev/pocketcrew/feature/history/HistoryViewModel.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/repository/ChatRepository.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ChatDao.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ChatRepositoryImpl.kt`

### Files to Delete
- None

## Data Contracts
### New Types
```kotlin
// core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/util/FtsSanitizer.kt
package com.browntowndev.pocketcrew.core.data.util

object FtsSanitizer {
    /**
     * Sanitizes a search query for SQLite FTS MATCH clauses.
     * Removes special characters that cause FTS parsing errors and appends a wildcard.
     */
    fun sanitize(query: String): String
}

// core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/SearchChatsUseCase.kt
package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchChatsUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(query: String): Flow<List<Chat>>
}
```

### Modified Types
```kotlin
// core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/repository/ChatRepository.kt
interface ChatRepository {
    // ... existing methods ...
    fun searchChats(query: String, ftsQuery: String): Flow<List<Chat>>
}

// core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ChatDao.kt
@Dao
abstract class ChatDao {
    // ... existing methods ...
    @Query("""
        SELECT * FROM (
            SELECT chat.* FROM chat
            JOIN message ON chat.id = message.chat_id
            JOIN message_search ON message.id = message_search.rowid
            WHERE message_search MATCH :ftsQuery
            
            UNION
            
            SELECT * FROM chat
            WHERE name LIKE '%' || :query || '%'
        )
        ORDER BY pinned DESC, last_modified DESC
    """)
    abstract fun searchChats(query: String, ftsQuery: String): Flow<List<ChatEntity>>
}

// feature/history/src/main/kotlin/com/browntowndev/pocketcrew/feature/history/HistoryTopBar.kt
@Composable
fun HistoryTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    // ... existing parameters ...
)

// feature/history/src/main/kotlin/com/browntowndev/pocketcrew/feature/history/HistoryViewModel.kt
@HiltViewModel
class HistoryViewModel @Inject constructor(
    // ... existing ...
    private val searchChatsUseCase: SearchChatsUseCase
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    fun onSearchQueryChange(query: String) {
        _searchQuery.update { query }
    }
    
    // uiState uses flatMapLatest on _searchQuery with debounce to switch between getAllChatsUseCase and searchChatsUseCase
}
```

## Permissions & Config Delta
None

## Visual Spec
No new visual components. The existing `HistoryTopBar` will have its internal `searchQuery` state hoisted. The text field should function exactly as it does now, but typing in it will actively filter the `HistoryScreen`'s `LazyColumn` below it.

## Constitution Audit
- [x] Rule 1: Clean Architecture strictly followed (UseCase added in domain, FtsSanitizer in data).
- [x] Rule 2: No Android types in domain.
- [x] Rule 3: State hoisted to ViewModel (`searchQuery` exposed as `StateFlow`).
- [x] Rule 4: Data Layer rules followed (Room `@Query` returns `Flow<List<ChatEntity>>`).
- [x] Rule 5: Code Style rules followed (Debounce used, stable collections assumed via existing UI state).

## Cross-Spec Dependencies
None

> Derive the implementation entirely from the Markdown Spec and Constitution. The test suite is present in the repository. Do not use test assertions to infer expected values or implementation structure — the tests are a post-hoc verifier, not a blueprint.