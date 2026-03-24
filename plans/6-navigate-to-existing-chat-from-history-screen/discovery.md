# Discovery Report — Navigation Fix (chatId Parameter)

**Ticket ID**: 6-navigate-to-existing-chat-from-history-screen
**Phase**: CFAW Phase 1 - Discovery
**Date**: 2026-03-24

---

## Executive Summary

This discovery examines the navigation flow between the History screen and Chat screen in the Pocket Crew Android application. The goal is to pass a `chatId` parameter when navigating from History to Chat, allowing existing chat messages to be pre-loaded. Current implementation has callback signatures wired but the parameter is not passed through the navigation layer.

**Tier Assessment**: **Tier 1 — Atomic** (self-contained navigation change, minimal cross-layer ripple)

---

## Module Structure

```
app/src/main/kotlin/com/browntowndev/pocketcrew/presentation/
├── navigation/
│   ├── Routes.kt                    # Navigation route constants
│   └── PocketCrewNavGraph.kt        # NavHost composable with all routes

feature/chat/
├── ChatRoute.kt                     # Route composable (entry point)
├── ChatViewModel.kt                 # ViewModel with SavedStateHandle
├── ChatScreen.kt                    # Main screen composable
├── ChatTopBar.kt                    # Top app bar
└── ChatModels.kt                   # ChatUiState, ChatMessage data classes

feature/history/
├── HistoryRoute.kt                  # Route composable with onNavigateToChat callback
├── HistoryViewModel.kt              # ViewModel
├── HistoryScreen.kt                # Main screen composable
└── HistoryModels.kt                # HistoryUiState, HistoryChat data classes
```

---

## Existing Data Models

### Routes.kt (Current State)
```kotlin
object Routes {
    const val CHAT = "chat"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val MODEL_DOWNLOAD = "model_download"
}
```

### ChatUiState (Current State)
```kotlin
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val selectedMode: ChatModeUi = ChatModeUi.FAST,
    val isGlobalInferenceBlocked: Boolean = false,
    val shieldReason: String? = null,
    val hapticPress: Boolean = false,
    val hapticResponse: Boolean = false,
) {
    val isGenerating: Boolean
        get() = messages.any {
            it.indicatorState is IndicatorState.Generating ||
                    it.indicatorState is IndicatorState.Thinking ||
                    it.indicatorState is IndicatorState.Processing
        }
}
```
**Note**: `ChatUiState` does NOT currently include a `chatId` field. User requested this be added.

### HistoryRoute Callback Signature
```kotlin
@Composable
fun HistoryRoute(
    onNavigateBack: () -> Unit,
    onNavigateToChat: (Long) -> Unit,  // Currently wired but not passing id
    onNavigateToSettings: () -> Unit,
    onShowSnackbar: (message: String, actionLabel: String?) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
)
```

### ChatViewModel SavedStateHandle
```kotlin
val initialChatId: Long?
    get() = savedStateHandle.get<Long>("chatId")
```

---

## API Surface

### Navigation Layer
| Route | Current Composable Signature | Current Route Pattern |
|-------|------------------------------|----------------------|
| `CHAT` | `ChatRoute(onNavigateToHistory, onShowSnackbar)` | `"chat"` |
| `HISTORY` | `HistoryRoute(onNavigateBack, onNavigateToChat, onNavigateToSettings, onShowSnackbar)` | `"history"` |

### ViewModel API
- **ChatViewModel**: Already has `initialChatId` property (reads from `SavedStateHandle("chatId")`)
- **ChatViewModel.uiState**: Returns `StateFlow<ChatUiState>`
- **ChatViewModel.flatMapLatest**: Message loading triggered by `_currentChatId` + `initialChatId`

---

## Dependencies

| Dependency | Version/Source | Purpose |
|------------|----------------|---------|
| `navigation-compose` | Jetpack Navigation | NavHost, NavController |
| `hilt-navigation-compose` | Hilt | ViewModel injection |
| `room` | Local persistence | Chat and Message storage |
| `kotlinx-coroutines` | Flow-based state | Reactive data streams |

---

## Utility Patterns

### Message Loading Flow (ChatViewModel.kt:87-94)
```kotlin
_currentChatId.flatMapLatest { chatId: Long? ->
    val id: Long = chatId ?: initialChatId ?: 0L
    if (id == 0L) {
        flowOf(emptyList())
    } else {
        chatUseCases.getChat(id).debounce(50)
    }
}
```
**Pattern**: Uses `flatMapLatest` to switch message streams based on chatId. Currently falls back to `initialChatId` when `_currentChatId` is null.

### SavedStateHandle Pattern (ChatViewModel.kt:53-54)
```kotlin
val initialChatId: Long?
    get() = savedStateHandle.get<Long>("chatId")
```
**Pattern**: Read-only property that delegates to SavedStateHandle for restoration across process death.

### Navigation Pattern (PocketCrewNavGraph.kt:114)
```kotlin
onNavigateToChat = { id -> navController.navigate(Routes.CHAT) }
```
**Current Issue**: The `id` parameter is accepted but NOT passed through — route is hardcoded to `Routes.CHAT`.

---

## Gap Analysis

### What Exists
| Component | Status | Notes |
|-----------|--------|-------|
| `Routes.CHAT` constant | ✅ | Simple string `"chat"` |
| `ChatRoute` composable | ✅ | No chatId parameter |
| `HistoryRoute.onNavigateToChat` | ✅ | Signature is `(Long) -> Unit` |
| `ChatViewModel.initialChatId` | ✅ | Reads from SavedStateHandle |
| Message loading logic | ✅ | Handles existing chatId |
| "New Chat" handling | ✅ | Treats `-1` or `0` as new chat |

### What Needs to Be Added/Modified
| Component | Change Required |
|-----------|-----------------|
| `Routes.kt` | Add `CHAT_WITH_ID = "chat?chatId={chatId}"` route |
| `PocketCrewNavGraph.kt` | Add composable for parameterized route OR modify existing to accept arguments |
| `HistoryRoute.kt` | Update `onNavigateToChat` to `(Long?) -> Unit` |
| `PocketCrewNavGraph.kt` | Update `onNavigateToChat` call to pass id in route |
| `ChatUiState` | Add `chatId: Long?` field for debugging/analytics |
| `ChatRoute.kt` | Accept optional `chatId` parameter |

---

## Risk Assessment

### Low Risk
- **SavedStateHandle integration**: Already implemented, just needs argument passing
- **Message loading**: Logic already handles various chatId values
- **Back stack**: Simple pop-back-stack behavior (History ← Chat)

### Medium Risk
- **Route pattern choice**: Query parameter (`chat?chatId=5`) vs. path parameter (`chat/5`) — affects how arguments are extracted
- **Click debouncing**: User uncertain if Compose handles rapid taps — may need explicit debouncing

### No Risk
- **Deep linking**: User explicitly stated not required
- **Cloud sync**: No sync operations that could modify chat during navigation
- **Authentication**: All local Room DB, no auth checks needed

---

## Clarifying Questions (with Answers)

### 1. Navigation Architecture

**Q1**: Use query parameters (`chat?chatId={chatId}`) where the absence of `chatId` implicitly means "new chat"?
**A**: ✅ **Yes** — This is the agreed approach.

**Q2**: Does the existing NavGraph use `navArgs()` with generated argument classes, or manual `arguments?.getString()` extraction?
**A**: No generated classes. This is the first navigation with data passing — manual extraction will be used.

**Q3**: What is the expected back stack behavior?
**A**: Pressing back from Chat returns directly to History.

**Q4**: Is deep linking support required?
**A**: No (not applicable).

**Q5**: Should `HistoryRoute.onNavigateToChat` be `(Long?) -> Unit`?
**A**: **Yes** — Make it nullable since it can be a new chat coming from the "New Chat" button.

---

### 2. State Management & ViewModel

**Q6**: Does SavedStateHandle auto-populate when using navArgs or manual argument extraction?
**A**: This is a brand new implementation. SavedStateHandle currently always returns null — needs implementation.

**Q7**: What happens on device rotation or process death?
**A**: chatId should be preserved on rotation. If app is killed/dies, it's fine to lose it and default to fresh chat.

**Q8**: Does the ViewModel re-trigger loading when navigating with a new chatId?
**A**: **Yes** — If a user navigates to the screen by clicking an existing chat, it should re-trigger to load that chat.

**Q9**: Is there a loading state?
**A**: No current loading state. User suggested there probably should be one.

**Q10**: Should `chatId` be exposed in UI state for debugging/analytics?
**A**: **Yes**.

---

### 3. Data Layer & Persistence

**Q11**: What happens if `chatId` refers to a chat that no longer exists?
**A**: Show a toast "chat not found" and set chatId back to -1 for a new chat.

**Q12**: How are chat messages loaded?
**A**: Via `ChatRepository.getMessagesForChat(chatId)` returning `Flow<List<Message>>`. See `ChatViewModel` for details.

**Q13**: Is authentication/authorization needed?
**A**: No — all local Room DB.

**Q14**: What happens if message loading fails?
**A**: Show toast "failed to load message(s)".

---

### 4. UI/UX Flows

**Q15**: For "New Chat" (`chatId = -1L` or `null`), what exactly should happen?
**A**: By default, SavedStateHandle returns null/-1 and it's a new chat. If navigated with null or -1, ChatViewModel treats it as new chat.

**Q16**: What does the empty Chat screen look like?
**A**: There's already a placeholder.

**Q17**: Should UI scroll to bottom when loading existing chat?
**A**: **Yes** — scroll to bottom.

**Q18**: Are there loading skeletons or progress indicators?
**A**: No, but fine for now.

**Q19**: Haptic feedback or animations for navigation?
**A**: No not right now.

**Q20**: Toast/snackbar confirmations?
**A**: Could be wired up but not required for this task.

---

### 5. Edge Cases & Error Handling

**Q21**: What is the valid range for `chatId`?
**A**: -1 is new chat, 0 is invalid (Room ID), > 0 is valid. So `-1` and `> 0` are the valid cases.

**Q22**: What if user spam-clicks before navigation completes?
**A**: Compose's default click debouncing may handle it. If not, may need explicit debouncing.

**Q23**: What if chat is deleted by another process?
**A**: Not applicable — no cloud sync.

**Q24**: Can `chatId` be null from navigation arguments?
**A**: **Yes** — null means new chat, should default to -1.

---

### 6. Testing & Quality

**Q25**: Are there existing unit tests for ChatViewModel?
**A**: Yes (`ChatViewModelTest.kt`, `ChatViewModelFlowTest.kt`) — check these when implementing. Tests mock `SavedStateHandle` but don't pass `chatId`.

**Q26**: Are there instrumented tests for navigation?
**A**: Unknown — user suggested checking.

**Q27**: Performance/accessibility tests?
**A**: No not right now.

---

## Scope Assessment

### Files to Modify
1. **`Routes.kt`** — Add `CHAT_WITH_ID` route constant
2. **`PocketCrewNavGraph.kt`** — Update composable for `CHAT` to accept `chatId` argument
3. **`HistoryRoute.kt`** — Update callback signature to `(Long?) -> Unit` and pass id in navigate call
4. **`ChatRoute.kt`** — Accept optional `chatId` parameter
5. **`ChatModels.kt`** — Add `chatId: Long?` to `ChatUiState`

### Files to Verify (Existing Tests)
1. **`ChatViewModelTest.kt`** — May need updates if testing SavedStateHandle with chatId
2. **`ChatViewModelFlowTest.kt`** — May need updates

### No New Files Required
The implementation can be achieved with modifications to existing files only.

---

## Recommendation

**Proceed to Phase 2 (Specification)** ✅

This is a **Tier 1** task — atomic, self-contained navigation fix with minimal cross-layer ripple. All critical questions have been answered. The implementation is well-bounded:

- **4-5 files to modify**
- **No new files required**
- **Clear message loading flow already exists in ChatViewModel**
- **SavedStateHandle pattern already in place**

The implementation should use query parameters (`chat?chatId={chatId}`) for optional chatId, update the History callback to nullable `(Long?) -> Unit`, and add chatId to ChatUiState for debugging.

---

## Next Steps

1. Load `agents/SPEC.md` for Phase 2
2. Generate `spec.md` with exact implementation details
3. Proceed to Phase 3 (TDD) for any new test cases
4. Implement in Phase 4

---

*Discovery complete. Awaiting Architect approval to proceed to Specification phase.*
