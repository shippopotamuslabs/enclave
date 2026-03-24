# Implementation Prompt — 6-navigate-to-existing-chat-from-history-screen

**System Role**: Senior Software Engineer specializing in Android/Kotlin with Jetpack Compose.

**Phase**: CFAW Phase 4 - Implementation

---

## Spec-Primacy Instruction

> Derive the implementation entirely from the Markdown Spec and Constitution. The test suite is present in the repository. Do not use test assertions to infer expected values or implementation structure — the tests are a post-hoc verifier, not a blueprint.

If the spec is ambiguous on a behavioral detail, surface the ambiguity rather than resolving it by reading the test.

---

## Context

### Ticket ID
`6-navigate-to-existing-chat-from-history-screen`

### Objective
Enable navigation from the History screen to an existing chat in the Chat screen by passing the `chatId`. When a user taps a chat in their history, the Chat screen should load with that specific conversation's messages. When starting a new chat, it should load an empty chat state.

### Files to Modify (File Manifest)

| File | Purpose |
|------|---------|
| `app/src/main/kotlin/com/browntowndev/pocketcrew/presentation/navigation/Routes.kt` | Add `CHAT_WITH_ID` route constant |
| `app/src/main/kotlin/com/browntowndev/pocketcrew/presentation/navigation/PocketCrewNavGraph.kt` | Update NavHost to accept nullable `chatId` argument |
| `feature/history/src/main/kotlin/com/browntowndev/pocketcrew/feature/history/HistoryRoute.kt` | Update `onNavigateToChat` signature to `(Long?) -> Unit` |
| `feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/ChatRoute.kt` | Accept optional `chatId` via SavedStateHandle |
| `feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/ChatModels.kt` | Add `chatId: Long?` to `ChatUiState` |
| `feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/ChatViewModel.kt` | Update to read `chatId` from SavedStateHandle and emit it in state |

### Files to Create
None

### Files to Delete
None

---

## Data Contracts

### Routes.kt
```kotlin
object Routes {
    const val CHAT = "chat"
    const val CHAT_WITH_ID = "chat?chatId={chatId}"
    const val HISTORY = "history"
    // ...
}
```

### ChatModels.kt
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

### HistoryRoute.kt
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

### PocketCrewNavGraph.kt
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

### ChatViewModel.kt
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

---

## Permissions & Config Delta
None

---

## Constitution Audit Requirements

- [x] Architecture Rules: Compliant. Navigation remains hoisted to the NavGraph and Route levels. No `NavController` passed to Composables.
- [x] Code Style Rules: Compliant. Naming conventions preserved.
- [x] Data Layer Rules: Compliant. `SavedStateHandle` used correctly for process death restoration.
- [x] UI Design Spec: Compliant. No changes to Scaffold or TopAppBar ownership.

---

## Directives

### Hard Rules

1. **Implementation derives from spec, not from tests.** The tests verify correctness post-implementation.

2. **No new dependencies.** Do not add any external library or dependency.

3. **No visibility increases.** Do not change `private` to `internal` or `public` without explicit listing in spec.

4. **No manifest changes.** Do not modify AndroidManifest.xml.

5. **Navigation stays hoisted.** `NavController` must NOT be passed into Composables.

6. **Use `collectAsStateWithLifecycle()`** for StateFlow in Composables.

7. **Nullable chatId must be handled.** `null` means new chat (use `-1L`), valid ID loads existing chat.

8. **Process death restoration.** `SavedStateHandle` must restore `chatId` across configuration changes.

---

## Implementation Steps

1. **Review the spec** and understand the file manifest before writing any code.

2. **Implement incrementally:** Modify one file at a time and run tests after each change.

3. **Run tests after each change:** Execute `./gradlew testDebugUnitTest` after each meaningful modification. Do not batch large changes.

4. **If new behavior without test:** Stop and surface the ambiguity instead of implementing.

5. **Report progress:** `GREEN: N tests passing, 0 failing` when complete.

---

## Quality Gates (Run before declaring complete)

- [ ] `./gradlew testDebugUnitTest` — all tests pass
- [ ] `./gradlew ktlintCheck` — zero errors
- [ ] `./gradlew detekt` — zero errors
- [ ] Drift audit: git diff matches file manifest above

---

## Deliverables

Produce the exact source code modifications for:

1. `app/src/main/kotlin/com/browntowndev/pocketcrew/presentation/navigation/Routes.kt`
2. `app/src/main/kotlin/com/browntowndev/pocketcrew/presentation/navigation/PocketCrewNavGraph.kt`
3. `feature/history/src/main/kotlin/com/browntowndev/pocketcrew/feature/history/HistoryRoute.kt`
4. `feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/ChatRoute.kt`
5. `feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/ChatModels.kt`
6. `feature/chat/src/main/kotlin/com/browntowndev/pocketcrew/feature/chat/ChatViewModel.kt`

All with complete, production-ready code including necessary imports.

---

*This is CFAW Phase 4 — Implementation. Derive from spec, verify with tests.*
