# Implementation Plan — #5: Wire History Screen to Repository

## Objective

Wire the History screen to pull actual chats from the repository instead of displaying hardcoded mock data. The screen shall load chats from the database on launch, separate them into pinned and unpinned sections, and display them sorted by `lastModified` descending.

---

## Acceptance Criteria

- [ ] History screen loads chats from database on launch
- [ ] Pinned chats appear in "Pinned" section
- [ ] Unpinned chats appear in "Recent" section
- [ ] Chats are sorted by `last_modified` descending (from DAO query)
- [ ] Relative timestamp formatting ("Today", "Yesterday", or date) applied in ViewModel
- [ ] All tests pass

---

## Architecture

### Data Flow (Conceptual)

```
Room Database (SQLite)
    ↓
ChatDao.getAllChats() → Flow<List<ChatEntity>>
    ↓
ChatRepositoryImpl (maps Entity → Domain)
    ↓
ChatRepository.getAllChats() → Flow<List<Chat>]
    ↓
GetAllChatsUseCase.invoke() → Flow<List<Chat>]
    ↓
HistoryViewModel (subscribes to Flow, maps Chat → HistoryChat)
    ↓
HistoryUiState (pinnedChats, otherChats)
    ↓
HistoryScreen Composable
```

### Module Boundaries

| Module | Responsibility |
|--------|----------------|
| `:domain` | Pure Kotlin. `Chat` model, `ChatRepository` port, `GetAllChatsUseCase`. No framework imports. |
| `:data` | Implements `ChatRepository`. Uses `ChatDao` (Room). Maps `ChatEntity` → `Chat`. |
| `:feature:history` | `HistoryViewModel` subscribes to use case. Maps domain → UI models. Formats timestamps. |
| `:app` | Composables consume `HistoryUiState`. No business logic. |

---

## Data Contracts

### Domain Model: `Chat` (Existing)

**File:** `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/chat/Chat.kt`

```kotlin
data class Chat(
    val id: Long = 0,
    val name: String,
    val created: Date,
    val lastModified: Date,
    val pinned: Boolean = false
)
```

### UI Model: `HistoryChat` (Existing)

**File:** `feature/history/src/main/kotlin/com/browntowndev/pocketcrew/feature/history/HistoryModels.kt`

```kotlin
data class HistoryChat(
    val id: Long,
    val name: String,
    val lastMessageDateTime: String,  // Pre-formatted relative date string
    val isPinned: Boolean
)

data class HistoryUiState(
    val pinnedChats: List<HistoryChat> = emptyList(),
    val otherChats: List<HistoryChat> = emptyList(),
    val isLoading: Boolean = false,
    val hapticPress: Boolean = true,
)
```

### Entity: `ChatEntity` (Existing)

**File:** `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ChatEntity.kt`

```kotlin
@Entity(tableName = "chat")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created") val created: Date,
    @ColumnInfo(name = "last_modified") val lastModified: Date,
    @ColumnInfo(name = "pinned") val pinned: Boolean
)
```

---

## Files to Create

### 1. `ChatRepository.kt` — Add Interface Methods

**File:** `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/repository/ChatRepository.kt`

**Changes:** Add two new methods to existing interface:

```kotlin
/**
 * Returns all chats as a Flow.
 * Listens to database changes.
 *
 * @return Flow of chats sorted by last_modified DESC
 */
fun getAllChats(): Flow<List<Chat>>

/**
 * Toggles the pinned status of a chat.
 *
 * @param chatId The ID of the chat to toggle
 */
suspend fun togglePinStatus(chatId: Long)
```

### 2. `GetAllChatsUseCase.kt` — New Use Case

**File:** `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/GetAllChatsUseCase.kt`

```kotlin
package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for getting all chats as a Flow.
 * Listens to database changes via Room Flow.
 */
class GetAllChatsUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    /**
     * Returns all chats as a Flow.
     *
     * @return Flow of chats sorted by lastModified DESC
     */
    operator fun invoke(): Flow<List<Chat>> {
        return chatRepository.getAllChats()
    }
}
```

### 3. `FakeChatRepository.kt` — Add Test Methods

**File:** `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/FakeChatRepository.kt`

**Changes:** Add for testing support:

```kotlin
private val chatsFlow = MutableStateFlow<List<Chat>>(emptyList())

override fun getAllChats(): Flow<List<Chat>> = chatsFlow

fun setChats(chats: List<Chat>) {
    chatsFlow.value = chats
}

override suspend fun togglePinStatus(chatId: Long) {
    // For testing: toggle in-memory
    val current = chatsFlow.value.toMutableList()
    val index = current.indexOfFirst { it.id == chatId }
    if (index != -1) {
        current[index] = current[index].copy(pinned = !current[index].pinned)
        chatsFlow.value = current
    }
}
```

---

## Files to Modify

### 1. `ChatDao.kt` — Add Update Pin Method

**File:** `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ChatDao.kt`

**Changes:** Add new query method:

```kotlin
@Query("UPDATE chat SET pinned = NOT pinned WHERE id = :chatId")
abstract suspend fun updatePinStatus(chatId: Long)
```

### 2. `ChatRepositoryImpl.kt` — Implement New Methods

**File:** `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ChatRepositoryImpl.kt`

**Changes:** Implement the two new interface methods:

```kotlin
override fun getAllChats(): Flow<List<Chat>> {
    return chatDao.getAllChats().map { entities ->
        entities.map { it.toDomain() }
    }
}

override suspend fun togglePinStatus(chatId: Long) {
    chatDao.updatePinStatus(chatId)
}
```

### 3. `HistoryViewModel.kt` — Wire to Repository

**File:** `feature/history/src/main/kotlin/com/browntowndev/pocketcrew/feature/history/HistoryViewModel.kt`

**Changes:**

1. Inject `GetAllChatsUseCase`
2. Remove hardcoded mock data from `_baseState`
3. Initialize `_baseState` with empty lists and `isLoading = true`
4. Subscribe to `GetAllChatsUseCase()` in `init` block using `viewModelScope.launch`
5. Map `Chat` → `HistoryChat` with relative timestamp formatting
6. Separate into `pinnedChats` and `otherChats` based on `pinned` property
7. Update `_baseState` with mapped results and `isLoading = false`

**Relative Date Formatting Logic (in ViewModel):**

```kotlin
private fun formatRelativeDate(date: Date): String {
    val now = Calendar.getInstance()
    val chatDate = Calendar.getInstance().apply { time = date }
    
    return when {
        isSameDay(now, chatDate) -> {
            "Today, ${formatTime(date)}"
        }
        isYesterday(now, chatDate) -> {
            "Yesterday, ${formatTime(date)}"
        }
        else -> {
            "${getMonthAbbr(chatDate.get(Calendar.MONTH))} ${chatDate.get(Calendar.DAY_OF_MONTH)}, ${formatTime(date)}"
        }
    }
}

private fun formatTime(date: Date): String {
    val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    return formatter.format(date)
}
```

---

## Permissions & Config Delta

**None required.** No new permissions, no manifest changes, no build configuration changes.

---

## Visual Spec (Conceptual)

The UI remains unchanged. The screen displays:

```
┌─────────────────────────────────┐
│  History                    🔍 │
├─────────────────────────────────┤
│  Pinned                         │
│  ┌─────────────────────────────┐ │
│  │ 📌 Project Alpha     Today, │ │
│  │              10:30 AM      │ │
│  └─────────────────────────────┘ │
│  ┌─────────────────────────────┐ │
│  │ 📌 Grocery List    Yesterday,│ │
│  │              6:15 PM        │ │
│  └─────────────────────────────┘ │
├─────────────────────────────────┤
│  Recent                         │
│  ┌─────────────────────────────┐ │
│  │    Meeting Notes    Oct 24, │ │
│  │                2:00 PM      │ │
│  └─────────────────────────────┘ │
│  ┌─────────────────────────────┐ │
│  │    Weekend Plans    Oct 22, │ │
│  │                9:45 AM       │ │
│  └─────────────────────────────┘ │
└─────────────────────────────────┘
```

**Note:** Data-driven now, not hardcoded.

---

## Constitution Audit

| Rule | Compliance | Evidence |
|------|------------|----------|
| `:domain` has no framework imports | ✅ Compliant | `Chat`, `ChatRepository`, `GetAllChatsUseCase` are pure Kotlin |
| One `StateFlow<UiState>` per ViewModel | ✅ Compliant | `uiState: StateFlow<HistoryUiState>` |
| Domain models map to presentation before UI | ✅ Compliant | `Chat` → `HistoryChat` in ViewModel |
| No repository calls in Composables | ✅ Compliant | All logic in ViewModel |
| Hilt injection via `@Inject constructor` | ✅ Compliant | `GetAllChatsUseCase @Inject constructor` |
| `invoke()` operator for use cases | ✅ Compliant | Pattern matches `GetChatUseCase` |
| Clean Architecture dependency inversion | ✅ Compliant | Domain → Repository (port) ← Data (adapter) |
| Flow used for reactive data | ✅ Compliant | `Flow<List<Chat>>` from DAO → Repository → UseCase → ViewModel |

---

## Cross-Spec Dependencies

| Dependency | Status | Notes |
|------------|--------|-------|
| `ChatDao.getAllChats()` exists | ✅ Ready | Already exists with `ORDER BY last_modified DESC` |
| `ChatEntity.toDomain()` mapper exists | ✅ Ready | `core/data/.../mapper/ChatMappers.kt:9-15` |
| `HistoryChat` UI model defined | ✅ Ready | `HistoryModels.kt:3-8` |
| `HistoryUiState` defined | ✅ Ready | `HistoryModels.kt:10-15` |

---

## Discovery Document Decisions (from `discovery-v2.md`)

All decisions from the Discovery phase are incorporated:

| Decision | Location in Spec |
|----------|------------------|
| Timestamp formatting in ViewModel | Section: "Relative Date Formatting Logic" |
| Relative time formats ("Today", "Yesterday", date) | Section: "Relative Date Formatting Logic" |
| Always AM/PM with device timezone | Section: "Relative Date Formatting Logic" |
| Pinned sorted descending by `lastModified` | Section: "Data Flow" (from DAO query) |
| `lastModified` unchanged on pin/unpin | Section: "ChatDao — Add Update Pin Method" |
| Entity → Domain mapping in Repository | Section: "ChatRepositoryImpl — Implement New Methods" |
| `togglePinStatus(chatId: Long)` method | Section: "ChatRepository — Add Interface Methods" |

---

## Implementation Order

1. [ ] Write unit tests for `GetAllChatsUseCase`
2. [ ] Write unit tests for `HistoryViewModel`
3. [ ] Add `updatePinStatus()` to `ChatDao`
4. [ ] Add `getAllChats()` and `togglePinStatus()` to `ChatRepository` interface
5. [ ] Implement new methods in `ChatRepositoryImpl`
6. [ ] Create `GetAllChatsUseCase`
7. [ ] Update `FakeChatRepository` with test methods
8. [ ] Modify `HistoryViewModel` to inject use case and subscribe to Flow
9. [ ] Run `ktlintCheck` and `detekt`
10. [ ] Run full test suite

---

*Derive the implementation entirely from this Markdown Spec and Constitution. The test suite is present in the repository. Do not use test assertions to infer expected values or implementation structure — the tests are a post-hoc verifier, not a blueprint.*
