# FEAT-settings-data-controls — Implementation Plan

## Overview

Implement the "Data Controls" bottom sheet in the Settings screen.
The sheet surfaces five destructive actions, each guarded by an `AlertDialog` confirmation step.

---

## Architecture Decisions

### UiState vs TransientState

All confirmation / loading state for the five actions lives in a new **`DataControlsTransientState`** internal data class in `SettingsUiStateFactory.kt` (mirrors `MemoriesTransientState`, `DeletionTransientState`, etc.). This transient state is merged into `DataControlsUiState` by the factory. `DataControlsUiState` (already in `SettingsModels.kt`) is expanded with confirmation and loading fields.

### Confirmation sealed class

A `DataControlsPendingAction` sealed interface placed in `SettingsModels.kt` in the `feature/settings` module (presentation layer — not domain). It represents which action the user has tapped and is waiting to confirm.

### Delegate

A new `SettingsDataControlsDelegate` class in `feature/settings`, parallel to `SettingsMemoriesDelegate`. The ViewModel calls it for all data-control events.

### Use cases in `:domain`

Five new use cases added to `:domain/usecase/settings/` — pure Kotlin, no Android/Compose/Hilt types:

| Use case | Repositories used |
|---|---|
| `DeleteAllChatsUseCase` | `ChatRepository.getAllChats()` + `deleteChat()` |
| `DeleteAllMediaUseCase` | `StudioRepositoryPort.observeAllMedia()` + `deleteMedia()` |
| `DeleteAllMemoriesUseCase` | `MemoriesRepository.getAllMemoriesFlow()` + `deleteMemory()` |
| `DeleteAllArtifactsUseCase` | `CleanupRepository.clearAllData()` — scoped: **do not** use `clearAllData()` directly; instead add `deleteAllArtifacts()` to `CleanupRepository` so the data layer can implement selective artifact deletion |
| `DeleteAccountUseCase` | `AuthManager.signOut()` + `CloudSyncPort` (see note) |

**Note on `DeleteAccountUseCase`:** `CloudSyncPort` has no purge function. Add `suspend fun deleteUserAccount(): Result<Unit>` to `CloudSyncPort`. The data layer impl will delete the Firestore user document and then call `AuthManager.signOut()`.

**Note on `DeleteAllArtifactsUseCase`:** `CleanupRepository.clearAllData()` nukes everything — too broad. Add a focused method `suspend fun deleteAllArtifacts()` to `CleanupRepository`. The data layer impl deletes only document/artifact rows.

### Use case grouping

Add a new `SettingsDataControlsUseCases` interface and `SettingsDataControlsUseCasesImpl` class in `SettingsUseCases.kt` / `SettingsUseCasesImpl.kt`, and expose it via `SettingsUseCases.dataControls`.

### DI wiring

`SettingsUseCasesModule.kt` (`:app`) gets a new `@Binds` entry for `SettingsDataControlsUseCases → SettingsDataControlsUseCasesImpl`.
`SettingsUseCasesImpl` gains a new constructor parameter `override val dataControls: SettingsDataControlsUseCases`.

### `onShowDataControlsSheet` relocation

`onShowDataControlsSheet` currently lives in `SettingsMemoriesDelegate`. It is **moved** to `SettingsDataControlsDelegate` to keep cohesion. The ViewModel call-site (`fun onShowDataControlsSheet(show: Boolean)`) is updated accordingly.

### Sheet rendering

`DataControlsBottomSheet.kt` is a new composable in `feature/settings`. It follows the `MemoriesBottomSheet.kt` pattern: `JumpFreeModalBottomSheet` wrapping a `LazyColumn` of `ListItem` rows. One `AlertDialog` is conditionally rendered when `DataControlsUiState.pendingAction != null`.

---

## Files to Create

| File | Module | Purpose |
|---|---|---|
| `core/domain/src/main/kotlin/.../domain/usecase/settings/DeleteAllChatsUseCase.kt` | `:domain` | Use case |
| `core/domain/src/main/kotlin/.../domain/usecase/settings/DeleteAllMediaUseCase.kt` | `:domain` | Use case |
| `core/domain/src/main/kotlin/.../domain/usecase/settings/DeleteAllMemoriesUseCase.kt` | `:domain` | Use case |
| `core/domain/src/main/kotlin/.../domain/usecase/settings/DeleteAllArtifactsUseCase.kt` | `:domain` | Use case |
| `core/domain/src/main/kotlin/.../domain/usecase/settings/DeleteAccountUseCase.kt` | `:domain` | Use case |
| `feature/settings/src/main/kotlin/.../feature/settings/SettingsDataControlsDelegate.kt` | `:feature:settings` | Delegate |
| `feature/settings/src/main/kotlin/.../feature/settings/DataControlsBottomSheet.kt` | `:feature:settings` | Composable |
| `core/domain/src/test/kotlin/.../domain/usecase/settings/DeleteAllChatsUseCaseTest.kt` | `:domain` test | Unit test |
| `core/domain/src/test/kotlin/.../domain/usecase/settings/DeleteAllMediaUseCaseTest.kt` | `:domain` test | Unit test |
| `core/domain/src/test/kotlin/.../domain/usecase/settings/DeleteAllMemoriesUseCaseTest.kt` | `:domain` test | Unit test |
| `core/domain/src/test/kotlin/.../domain/usecase/settings/DeleteAllArtifactsUseCaseTest.kt` | `:domain` test | Unit test |
| `core/domain/src/test/kotlin/.../domain/usecase/settings/DeleteAccountUseCaseTest.kt` | `:domain` test | Unit test |
| `feature/settings/src/test/kotlin/.../feature/settings/SettingsDataControlsDelegateTest.kt` | `:feature:settings` test | Unit test |

---

## Files to Modify

| File | Change |
|---|---|
| `core/domain/.../port/repository/CleanupRepository.kt` | Add `suspend fun deleteAllArtifacts()` |
| `core/domain/.../port/repository/CloudSyncPort.kt` | Add `suspend fun deleteUserAccount(): Result<Unit>` |
| `core/domain/.../usecase/settings/SettingsUseCases.kt` | Add `SettingsDataControlsUseCases` interface; extend `SettingsUseCases` with `val dataControls` |
| `core/domain/.../usecase/settings/SettingsUseCasesImpl.kt` | Add `SettingsDataControlsUseCasesImpl`; add `dataControls` param to `SettingsUseCasesImpl` |
| `feature/settings/.../SettingsModels.kt` | Add `DataControlsPendingAction` sealed interface; expand `DataControlsUiState` |
| `feature/settings/.../SettingsUiStateFactory.kt` | Accept `DataControlsTransientState`; wire into `DataControlsUiState` construction |
| `feature/settings/.../SettingsMemoriesDelegate.kt` | Remove `onShowDataControlsSheet` (moved to new delegate) |
| `feature/settings/.../SettingsViewModel.kt` | Inject `SettingsDataControlsDelegate`; add `_dataControlsState`; update `onShowDataControlsSheet`; add 11 new handler funs; extend `combine()` with new state arg |
| `feature/settings/.../SettingsScreen.kt` | Add 11 new callback params; render `DataControlsBottomSheet` when `isDataControlsSheetOpen` |
| `app/.../SettingsUseCasesModule.kt` | Add `@Binds` for `SettingsDataControlsUseCases` |
| `core/domain/src/test/.../SettingsUseCasesImplTest.kt` | Assert `dataControls` is exposed by `SettingsUseCasesImpl` |

---

## Task List (TDD Order)

Tasks are ordered: failing tests first, then production code to make them pass.

### Phase 1 — Domain Port Extensions

**Task 1.1 — Extend `CleanupRepository`**
Add `suspend fun deleteAllArtifacts()` to the `CleanupRepository` interface in `:domain`. No implementation — that lives in `:data`.

**Task 1.2 — Extend `CloudSyncPort`**
Add `suspend fun deleteUserAccount(): Result<Unit>` to `CloudSyncPort`. No implementation — that lives in `:data`.

### Phase 2 — Domain Use Cases (tests first)

**Task 2.1 — Write `DeleteAllChatsUseCaseTest`**
Write failing unit test: given a `FakeChatRepository` seeded with 3 chats, when `DeleteAllChatsUseCase.invoke()` is called, then all 3 chat IDs are deleted (verified by state in the fake).

**Task 2.2 — Implement `DeleteAllChatsUseCase`**
Collect `ChatRepository.getAllChats()` as a single snapshot (`.first()`), then iterate and call `deleteChat()` for each `chatId`.

**Task 2.3 — Write `DeleteAllMediaUseCaseTest`**
Write failing unit test: given a `FakeStudioRepository` seeded with 2 media assets, when `DeleteAllMediaUseCase.invoke()` is called, then both asset IDs are deleted.

**Task 2.4 — Implement `DeleteAllMediaUseCase`**
Collect `StudioRepositoryPort.observeAllMedia().first()`, then iterate and call `deleteMedia(id)`.

**Task 2.5 — Write `DeleteAllMemoriesUseCaseTest`**
Write failing unit test: given a `FakeMemoriesRepository` seeded with 4 memories, when `DeleteAllMemoriesUseCase.invoke()` is called, then all 4 memory IDs are deleted.

**Task 2.6 — Implement `DeleteAllMemoriesUseCase`**
Collect `MemoriesRepository.getAllMemoriesFlow().first()`, then iterate and call `deleteMemory(id)`.

**Task 2.7 — Write `DeleteAllArtifactsUseCaseTest`**
Write failing unit test: when `DeleteAllArtifactsUseCase.invoke()` is called, then `CleanupRepository.deleteAllArtifacts()` is called exactly once.

**Task 2.8 — Implement `DeleteAllArtifactsUseCase`**
Delegate directly to `cleanupRepository.deleteAllArtifacts()`.

**Task 2.9 — Write `DeleteAccountUseCaseTest`**
Write two failing tests:
- Happy path: given `cloudSyncPort.deleteUserAccount()` returns `Result.success(Unit)`, then `signOut()` is called and the use case returns `Result.success(Unit)`.
- Error path: given `cloudSyncPort.deleteUserAccount()` returns `Result.failure(Exception("network"))`, then `signOut()` is NOT called and the use case returns a failure.

**Task 2.10 — Implement `DeleteAccountUseCase`**
Call `cloudSyncPort.deleteUserAccount()`. If it fails, return the failure immediately without signing out. If it succeeds, call `authManager.signOut()` and return its result.

### Phase 3 — Use Case Grouping

**Task 3.1 — Add `SettingsDataControlsUseCases` interface and impl**
In `SettingsUseCases.kt`:
```
interface SettingsDataControlsUseCases {
    val deleteAllChats: DeleteAllChatsUseCase
    val deleteAllMedia: DeleteAllMediaUseCase
    val deleteAllMemories: DeleteAllMemoriesUseCase
    val deleteAllArtifacts: DeleteAllArtifactsUseCase
    val deleteAccount: DeleteAccountUseCase
}
```
In `SettingsUseCasesImpl.kt`, add `SettingsDataControlsUseCasesImpl @Inject constructor(...)`.
Add `val dataControls: SettingsDataControlsUseCases` to `SettingsUseCases` interface and `SettingsUseCasesImpl`.

**Task 3.2 — Update `SettingsUseCasesImplTest`**
Extend the existing test to assert `assertSame(dataControls, useCases.dataControls)`.

### Phase 4 — Presentation Models

**Task 4.1 — Add `DataControlsPendingAction` and expand `DataControlsUiState`**
In `SettingsModels.kt`:

```kotlin
@Immutable
sealed interface DataControlsPendingAction {
    data object DeleteChats : DataControlsPendingAction
    data object DeleteMedia : DataControlsPendingAction
    data object DeleteMemories : DataControlsPendingAction
    data object DeleteArtifacts : DataControlsPendingAction
    data object DeleteAccount : DataControlsPendingAction
}

@Immutable
data class DataControlsUiState(
    val isSheetOpen: Boolean = false,
    val allowMemories: Boolean = true,
    val pendingAction: DataControlsPendingAction? = null,
    val isDeleting: Boolean = false,
    val deleteError: String? = null,
)
```

**Task 4.2 — Add `DataControlsTransientState`**
In `SettingsUiStateFactory.kt` (before the class declaration):

```kotlin
internal data class DataControlsTransientState(
    val pendingAction: DataControlsPendingAction? = null,
    val isDeleting: Boolean = false,
    val deleteError: String? = null,
)
```

**Task 4.3 — Extend `SettingsUiStateFactory.create()`**
Add `dataControlsState: DataControlsTransientState` parameter.
Rewrite the `dataControls = DataControlsUiState(...)` block to merge `sheetVisibility.dataControls`, `persistedSettings.allowMemories`, and all fields from `dataControlsState`.

### Phase 5 — Delegate (tests first)

**Task 5.1 — Write `SettingsDataControlsDelegateTest`**
Write failing unit tests for the delegate covering:
- `onShowDataControlsSheet(true)` → `sheetVisibility.dataControls == true`
- `onShowDataControlsSheet(false)` → `sheetVisibility.dataControls == false`
- `onRequestDeleteAllChats()` → `dataControlsState.pendingAction == DeleteChats` and `isDeleting == false`
- `onDismissDataControlsConfirmation()` → `dataControlsState.pendingAction == null`
- `onConfirmDeleteAllChats()` happy path → `DeleteAllChatsUseCase.invoke()` is called; after completion `isDeleting == false` and `pendingAction == null`
- `onConfirmDeleteAllChats()` error path → use case throws; `deleteError` is set; `isDeleting == false`
- Repeat request/confirm/error tests for `Media`, `Memories`, `Artifacts`
- `onConfirmDeleteAccount()` happy path → use case succeeds; state resets
- `onConfirmDeleteAccount()` error path → `deleteError` is set

**Task 5.2 — Implement `SettingsDataControlsDelegate`**
Create `SettingsDataControlsDelegate @Inject constructor(dataControlsUseCases, errorHandler)`:

- `onShowDataControlsSheet(sheetVisibility, show)` — update `sheetVisibility.dataControls`; if closing, reset `dataControlsState`
- `onRequestDeleteAllChats(dataControlsState)` — set `pendingAction = DeleteChats`
- `onRequestDeleteAllMedia(dataControlsState)` — set `pendingAction = DeleteMedia`
- `onRequestDeleteAllMemories(dataControlsState)` — set `pendingAction = DeleteMemories`
- `onRequestDeleteAllArtifacts(dataControlsState)` — set `pendingAction = DeleteArtifacts`
- `onRequestDeleteAccount(dataControlsState)` — set `pendingAction = DeleteAccount`
- `onDismissDataControlsConfirmation(dataControlsState)` — clear `pendingAction`
- `onConfirmDeleteAllChats(scope, tag, dataControlsState)` — set `isDeleting = true`; launch; call `deleteAllChats()`; on success reset to idle; on failure set `deleteError`
- Same pattern for `Media`, `Memories`, `Artifacts`, `Account`

### Phase 6 — ViewModel Wiring

**Task 6.1 — Update `SettingsViewModel`**
- Inject `SettingsDataControlsDelegate` (constructor parameter)
- Add `private val _dataControlsState = MutableStateFlow(DataControlsTransientState())`
- Relocate the `onShowDataControlsSheet` call from `memoriesDelegate` to `dataControlsDelegate`
- Extend the `combine()` block: add `_dataControlsState` as an argument (requires adding a new `ARG_DATA_CONTROLS_STATE` constant) and pass it to `settingsUiStateFactory.create()`
- Expose 11 new public funs that forward to the delegate

**Task 6.2 — Update `SettingsUiStateFactory.create()` signature**
(Already covered by Task 4.3 above — reconcile both tasks as one PR change.)

**Task 6.3 — Update `SettingsMemoriesDelegate`**
Remove `onShowDataControlsSheet` — it now lives in `SettingsDataControlsDelegate`.

### Phase 7 — UI

**Task 7.1 — Implement `DataControlsBottomSheet`**
Create `feature/settings/src/main/kotlin/.../feature/settings/DataControlsBottomSheet.kt`:

```
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataControlsBottomSheet(
    uiState: SettingsUiState,
    onDismiss: () -> Unit,
    onRequestDeleteAllChats: () -> Unit,
    onConfirmDeleteAllChats: () -> Unit,
    onRequestDeleteAllMedia: () -> Unit,
    onConfirmDeleteAllMedia: () -> Unit,
    onRequestDeleteAllMemories: () -> Unit,
    onConfirmDeleteAllMemories: () -> Unit,
    onRequestDeleteAllArtifacts: () -> Unit,
    onConfirmDeleteAllArtifacts: () -> Unit,
    onRequestDeleteAccount: () -> Unit,
    onConfirmDeleteAccount: () -> Unit,
    onDismissConfirmation: () -> Unit,
)
```

Body:
- `JumpFreeModalBottomSheet` with `rememberModalBottomSheetState(skipPartiallyExpanded = true)`
- Sheet title `Text("Data Controls")` (headlineSmall, bold)
- Five `ListItem` rows with `leadingContent` = `Icon` tinted `MaterialTheme.colorScheme.error`
- Each row disabled (alpha 0.5) when `uiState.dataControls.isDeleting == true`
- When `pendingAction != null`, render one `AlertDialog`:
  - `title = { Text("Are you sure?") }`
  - `text = { Text(<action-specific message>) }`
  - `confirmButton`: `TextButton` with `Text("Delete", color = MaterialTheme.colorScheme.error)`
  - `dismissButton`: `TextButton` with `Text("Cancel")`
- When `deleteError != null`, show it inline below the list (small `Text` in error color); error clears on next action

Row definitions:

| Title | Subtitle | `onConfirm` callback | Alert body text |
|---|---|---|---|
| "Delete all chats" | "Permanently removes all conversations and their messages." | `onConfirmDeleteAllChats` | "All your chats and messages will be permanently deleted. This cannot be undone." |
| "Delete all media" | "Permanently removes all generated images, videos, and music." | `onConfirmDeleteAllMedia` | "All studio media assets will be permanently deleted. This cannot be undone." |
| "Delete all memories" | "Permanently removes all stored memories." | `onConfirmDeleteAllMemories` | "All your memories will be permanently deleted. This cannot be undone." |
| "Delete all artifacts" | "Permanently removes all generated documents and code files." | `onConfirmDeleteAllArtifacts` | "All generated artifacts and attachments will be permanently deleted. This cannot be undone." |
| "Delete account" | "Permanently purges all cloud data and signs you out." | `onConfirmDeleteAccount` | "Your account and all associated cloud data will be permanently deleted. You will be signed out. This cannot be undone." |

Include `@Preview` composables for light/dark and loading states.

**Task 7.2 — Wire `DataControlsBottomSheet` into `SettingsScreen`**
In `SettingsScreen.kt`:
- Add 11 new callback parameters (matching the composable signature above, plus `onDismissConfirmation`)
- Add the conditional block (after the memories sheet, before vault reauth blocks):
```kotlin
if (uiState.home.isDataControlsSheetOpen) {
    DataControlsBottomSheet(
        uiState = uiState,
        onDismiss = { onShowDataControlsSheet(false) },
        ...
    )
}
```
- Pass stub lambdas in existing `@Preview` calls at the bottom of the file

### Phase 8 — DI Binding

**Task 8.1 — Add DI binding in `SettingsUseCasesModule`**
Add:
```kotlin
@Binds @Singleton
abstract fun bindSettingsDataControlsUseCases(
    impl: SettingsDataControlsUseCasesImpl
): SettingsDataControlsUseCases
```

### Phase 9 — Data Layer Stubs

**Note:** The data layer implementations of `CleanupRepository.deleteAllArtifacts()` and `CloudSyncPort.deleteUserAccount()` must exist for the app to compile. If the `:data` module implementations do not exist, create minimal stub bodies (`TODO("implement")` is NOT acceptable — use `throw NotImplementedError(...)` only in test doubles; production stubs must be functionally correct or delegate to an existing method that is close enough).

**Task 9.1 — Implement `CleanupRepository.deleteAllArtifacts()` in `:data`**
Find the existing `CleanupRepositoryImpl` in `:data` and add a `deleteAllArtifacts()` body that calls the appropriate Room DAO method to delete artifact/document rows.

**Task 9.2 — Implement `CloudSyncPort.deleteUserAccount()` in `:data`**
Find the existing `CloudSyncPortImpl` and implement `deleteUserAccount()`: delete the Firestore user document (and subcollections where accessible), then call `authManager.signOut()`.

---

## Constraint Checklist

- [ ] No Android/Compose/Hilt types in `:domain` use cases
- [ ] `DataControlsTransientState` is `internal` — not part of any public API
- [ ] `DataControlsPendingAction` has no state beyond the sealed variant — all deleting state is in `DataControlsTransientState.isDeleting`
- [ ] `SettingsDataControlsDelegate` operates on `MutableStateFlow` passed by the ViewModel — it does not hold its own state
- [ ] All composables in `DataControlsBottomSheet.kt` follow `modifier: Modifier = Modifier` as last param on layout-emitting composables
- [ ] `DataControlsBottomSheet` uses `collectAsStateWithLifecycle` (not `collectAsState`) — but since state is passed as a parameter this constraint is at the callsite in `SettingsScreen`
- [ ] Previews do not reference Hilt or real ViewModels
