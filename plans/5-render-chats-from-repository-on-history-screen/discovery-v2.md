# Discovery Phase Report: History Screen Data Wiring

## Assumptions Confirmed

| Assumption | Confidence | Evidence |
|------------|------------|----------|
| `ChatEntity.toDomain()` mapper exists and works | **High** | `core/data/src/main/kotlin/.../mapper/ChatMappers.kt:9-15` - `ChatEntity.toDomain()` maps all fields including `Date` types |
| `ChatDao.getAllChats()` returns `Flow<List<ChatEntity>>` | **High** | `ChatDao.kt:22-23` - `@Query("SELECT * FROM chat ORDER BY last_modified DESC")` already exists |
| `ChatRepositoryImpl` has access to `chatDao` | **High** | `ChatRepositoryImpl.kt:21-24` - constructor injects `ChatDao` |
| `HistoryChat` field is `lastMessageDateTime: String` | **High** | `HistoryModels.kt:6` - UI model expects pre-formatted String |
| Existing use case pattern is `invoke()` operator | **High** | `GetChatUseCase.kt:21` - pattern is `operator fun invoke(): Flow<List<...>>` |
| `FakeChatRepository` exists for testing | **High** | `core/domain/src/test/.../FakeChatRepository.kt` - needs `getAllChats()` added |
| No existing tests for `HistoryViewModel` | **Confirmed** | No test files found in `feature/history` |

---

## Resolved Questions

### A. Core Logic & Data Flow

1. **Where should the timestamp formatting logic live?**

   ✅ **RESOLVED**
   - Formatting should happen in the **presentation layer** (ViewModel) since it's a UI concern
   - The `ChatRepository` should update `lastModified` on `ChatEntity` whenever a new user message is added
   - `formatTimestamp` should be updated to produce relative time strings ("Today", "Yesterday", date format)

2. **What relative time formats are expected?**

   ✅ **RESOLVED**
   - "Today, 10:30 AM"
   - "Yesterday, 6:15 PM"
   - "Oct 24, 2:00 PM" (date-only for older)
   - Always use AM/PM time based on user's device timezone

3. **Should pinned chats maintain separate sorting?**

   ✅ **RESOLVED**
   - Pinned chats should be sorted **descending by `lastModified`** within their section
   - Both pinned and unpinned sections use `last_modified DESC` ordering

4. **Should `last_modified` be updated when a chat is pinned/unpinned?**

   ✅ **RESOLVED**
   - No. `lastModified` is only updated when a **new user message arrives**
   - Pinning/unpinning does not affect the timestamp

### B. Data Persistence & Repository Layer

5. **`ChatRepository.getAllChats()` return type**

   ✅ **RESOLVED**
   - Repository should return `Flow<List<Chat>>` (domain model)
   - Mapper transformation (Entity → Domain) occurs in Repository layer

6. **Should the repository implement the Flow transformation inline?**

   ✅ **RESOLVED**
   - Yes, always map entities to domain before returning from the repository
   - Follow pattern from `getMessagesForChat()`: `chatDao.getAllChats().map { entities -> entities.map { it.toDomain() } }`

7. **Is there a need for `updatePin` or `setPin` method in `ChatRepository`?**

   ✅ **RESOLVED**
   - Add granular method: `togglePinStatus(chatId: Long)` to the repository
   - This explicitly communicates intent rather than generic `update()` with full entity

8. **Transaction requirements for pin/unpin operations**

   ✅ **RESOLVED**
   - No transaction needed for pin/unpin
   - Single DAO update operation is sufficient

---

## Remaining Open Questions

### C. UI/UX Flows & State Management

9. **Loading state behavior**
   - `HistoryUiState.isLoading` exists but is never set to `true` in current mock
   - When real data loads from DB, should `isLoading` be `true` initially, then `false` once data arrives?
   - Or should it only show loading if the Flow hasn't emitted yet?

10. **Empty state handling**
    - What should the UI display when there are no chats?
    - Should there be an empty state illustration/message?
    - Should this be a separate field in `HistoryUiState` or derived in the Composable?

11. **Search functionality**
    - `HistoryTopBar` has a search field (`searchQuery`)
    - `HistoryViewModel` has stub methods but no search logic
    - Should search filtering be included in this ticket, or deferred?

12. **Navigation callback for chat click**
    - `HistoryRoute` calls `onNavigateToChat(id)` with the chat ID
    - But `PocketCrewNavGraph.kt:114` shows `Routes.CHAT` without the ID parameter
    - How should the chat screen receive/handle the chat ID for existing chats?

13. **Should stub methods (deleteChat, renameChat) be implemented?**
    - `deleteChat`, `renameChat`, `pinChat`, `unpinChat` are stubs
    - Should these be implemented in this ticket, or deferred?
    - If deferred, should acceptance criteria be updated to exclude them?

### D. Error Handling & Edge Cases

14. **Database error handling**
    - What happens if the Flow from Room fails?
    - Should errors be caught and displayed in UI?
    - Should there be a retry mechanism?

15. **Large chat lists**
    - No pagination is specified
    - Should the query include pagination (`LIMIT`, `OFFSET`)?
    - Room Flow with `getAllChats()` will emit all chats — is this acceptable for expected data sizes?

16. **Date timezone handling**
    - `Chat.lastModified` is `java.util.Date` stored as Long in Room
    - Relative time formatting should use device timezone
    - Is UTC storage acceptable with local display?

### E. Testing Requirements

17. **Unit test scope**
    - Should `GetAllChatsUseCase` tests cover:
      - Happy path: returns Flow of chats
      - Empty database: returns empty list
      - Flow emissions: verifies Flow behavior
    - Should `HistoryViewModel` tests cover:
      - Mapping domain → UI model
      - Separation into pinned/other lists
      - Loading state transitions

18. **FakeChatRepository**
    - Does `FakeChatRepository` need a `getAllChats()` method for testing?
    - Should it return a `MutableStateFlow<List<Chat>>` for controlling test data?

---

## Summary of Findings

| Category | Status | Notes |
|----------|--------|-------|
| **DAO Layer** | ✅ Ready | `ChatDao.getAllChats()` exists |
| **Repository Interface** | ❌ Missing | `getAllChats()` and `togglePinStatus()` not in `ChatRepository` |
| **Repository Impl** | ❌ Missing | `getAllChats()` and `togglePinStatus()` not implemented |
| **Domain Model** | ✅ Ready | `Chat` has all required fields |
| **UI Model** | ✅ Ready | `HistoryChat` structure defined |
| **Mapper** | ✅ Ready | `ChatEntity.toDomain()` exists |
| **Use Case Pattern** | ✅ Clear | Follow `GetChatUseCase` pattern |
| **Timestamp Formatting** | ✅ Resolved | Update `formatTimestamp` in ViewModel for relative time |
| **Stub Methods** | ⚠️ Deferred | pin/unpin/delete/rename stubs exist |
| **Tests** | ⚠️ Scope TBD | No existing `HistoryViewModel` tests |

---

## Implementation Decisions (from Answers)

| Decision | Value |
|----------|-------|
| Timestamp formatting location | Presentation layer (ViewModel) |
| Relative time formats | "Today", "Yesterday", then date-only; always AM/PM |
| Pinned sorting | Descending by `lastModified` |
| `lastModified` on pin/unpin | No change (only on new user message) |
| Repository entity mapping | Always map Entity → Domain before return |
| Pin method | Add `togglePinStatus(chatId: Long)` |
| Transaction for pin/unpin | Not needed (single record update) |
