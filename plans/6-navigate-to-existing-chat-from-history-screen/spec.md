# Implementation Plan — 6-navigate-to-existing-chat-from-history-screen

## Objective
Enable navigation from the History screen to an existing chat in the Chat screen by passing the `chatId`. When a user taps a chat in their history, the Chat screen should load with that specific conversation's messages. When starting a new chat, it should load an empty chat state.

## Acceptance Criteria
- [ ] Tapping an existing chat in the History screen navigates to the Chat screen and loads the selected chat's messages.
- [ ] Tapping "New Chat" in the History screen navigates to the Chat screen with a new, empty conversation.
- [ ] The `chatId` is passed safely through the navigation graph using query parameters (`chat?chatId={chatId}`).
- [ ] `ChatUiState` includes the current `chatId` for debugging and analytics purposes.
- [ ] The ViewModel correctly restores the `chatId` from `SavedStateHandle` across configuration changes.

## Architecture
### Files to Create
None

### Files to Modify
- `app/src/main/kotlin/com/browntowndev/pocketcrew/presentation/navigation/Routes.kt`
- `app/src/main/kotlin/com/browntowndev/pocketcrew/presentation/navigation/PocketCrewNavGraph.kt`
- `feature/history/src/main/kotlin/com/browntowndev/pocketcrew/feature/history/HistoryRoute.kt`
- `feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/ChatRoute.kt`
- `feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/ChatModels.kt`
- `feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/ChatViewModel.kt`

### Files to Delete
None

## Data Contracts
### Modified Types

**`Routes.kt`**
```kotlin
object Routes {
    const val CHAT = "chat"
    const val CHAT_WITH_ID = "chat?chatId={chatId}"
    const val HISTORY = "history"
    // ...
}
```

**`ChatModels.kt`**
```kotlin
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val selectedMode: ChatModeUi = ChatModeUi.FAST,
    val isGlobalInferenceBlocked: Boolean = false,
    val shieldReason: String? = null,
    val hapticPress: Boolean = false,
    val hapticResponse: Boolean = false,
    val chatId: Long? = null, // Added field
)
```

**`HistoryRoute.kt`**
```kotlin
@Composable
fun HistoryRoute(
    onNavigateBack: () -> Unit,
    onNavigateToChat: (Long?) -> Unit, // Changed from (Long) to (Long?)
    onNavigateToSettings: () -> Unit,
    onShowSnackbar: (message: String, actionLabel: String?) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
)
```

**`ChatRoute.kt`**
```kotlin
@Composable
fun ChatRoute(
    onNavigateToHistory: () -> Unit,
    onShowSnackbar: (message: String, actionLabel: String?) -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) 
// Note: No signature change needed here since ViewModel reads from SavedStateHandle,
// but we ensure any internal routing logic is updated if needed.
```

**`PocketCrewNavGraph.kt`**
```kotlin
// In the NavHost:
composable(
    route = Routes.CHAT_WITH_ID,
    arguments = listOf(
        navArgument("chatId") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        }
    ),
    // transitions...
) {
    ChatRoute(
        onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
        onShowSnackbar = onShowSnackbar,
    )
}

// In the History route setup:
HistoryRoute(
    onNavigateBack = { navController.popBackStack() },
    onNavigateToChat = { id -> 
        if (id != null && id != -1L) {
            navController.navigate(Routes.CHAT_WITH_ID.replace("{chatId}", id.toString()))
        } else {
            navController.navigate(Routes.CHAT)
        }
    },
    // ...
)
```

**`ChatViewModel.kt`**
```kotlin
// Ensure the string argument from SavedStateHandle is parsed correctly to Long
val initialChatId: Long?
    get() = savedStateHandle.get<String>("chatId")?.toLongOrNull()

// Update uiState combine block to include the current active chatId in the emitted state
ChatUiState(
    // ...
    chatId = _currentChatId.value ?: initialChatId ?: -1L
)
```

## Permissions & Config Delta
None

## Visual Spec (if applicable)
No visual changes to components. The Chat screen will simply populate with the messages of the selected chat instead of remaining empty when navigating from History.

## Constitution Audit
- [x] Architecture Rules: Compliant. Navigation remains hoisted to the NavGraph and Route levels. No `NavController` passed to Composables.
- [x] Code Style Rules: Compliant. Naming conventions preserved.
- [x] Data Layer Rules: Compliant. `SavedStateHandle` used correctly for process death restoration.
- [x] UI Design Spec: Compliant. No changes to Scaffold or TopAppBar ownership.

## Cross-Spec Dependencies
None

> Derive the implementation entirely from the Markdown Spec and Constitution. The test suite is present in the repository. Do not use test assertions to infer expected values or implementation structure — the tests are a post-hoc verifier, not a blueprint.