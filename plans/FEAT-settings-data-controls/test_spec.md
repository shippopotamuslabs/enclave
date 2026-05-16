# FEAT-settings-data-controls — Behavioral Test Specification

## Notation

- **S** = success / happy-path scenario
- **E** = error / failure scenario
- **M** = model / state-machine transition scenario
- **Then** clauses contain **literal expected values** — no "should" hand-waving.

All tests use JUnit 5 (`@Test`, `@BeforeEach`, `@Nested`, `@DisplayName`) + MockK for third-party collaborators + Turbine for `Flow`/`StateFlow`. Where a Fake is practical, Fakes are preferred over mocks.

---

## Section 1 — Use Cases (`:domain`)

### `DeleteAllChatsUseCase`

**S1** — Delete three chats, all succeed
```
Given FakeChatRepository contains chats with IDs ["chat-1", "chat-2", "chat-3"]
When  DeleteAllChatsUseCase.invoke() completes
Then  FakeChatRepository.deletedChatIds == setOf("chat-1", "chat-2", "chat-3")
```

**S2** — Empty repository produces no deletions
```
Given FakeChatRepository contains zero chats
When  DeleteAllChatsUseCase.invoke() completes
Then  FakeChatRepository.deletedChatIds == emptySet()
```

**E1** — Repository throws on deleteChat, exception propagates
```
Given chatRepository.getAllChats() emits [Chat(id="chat-A")]
  And chatRepository.deleteChat(ChatId("chat-A")) throws RuntimeException("disk full")
When  DeleteAllChatsUseCase.invoke() is called
Then  a RuntimeException with message "disk full" is thrown to the caller
```

---

### `DeleteAllMediaUseCase`

**S3** — Delete two media assets, all succeed
```
Given FakeStudioRepository contains assets with IDs ["media-1", "media-2"]
When  DeleteAllMediaUseCase.invoke() completes
Then  FakeStudioRepository.deletedMediaIds == setOf("media-1", "media-2")
```

**S4** — Empty repository produces no deletions
```
Given FakeStudioRepository contains zero assets
When  DeleteAllMediaUseCase.invoke() completes
Then  FakeStudioRepository.deletedMediaIds == emptySet()
```

**E2** — Repository throws on deleteMedia, exception propagates
```
Given studioRepository.observeAllMedia() emits [StudioMediaAsset(id="m-1", ...)]
  And studioRepository.deleteMedia("m-1") throws RuntimeException("permission denied")
When  DeleteAllMediaUseCase.invoke() is called
Then  a RuntimeException with message "permission denied" is thrown to the caller
```

---

### `DeleteAllMemoriesUseCase`

**S5** — Delete four memories, all succeed
```
Given FakeMemoriesRepository contains memories with IDs ["mem-1", "mem-2", "mem-3", "mem-4"]
When  DeleteAllMemoriesUseCase.invoke() completes
Then  FakeMemoriesRepository.deletedMemoryIds == setOf("mem-1", "mem-2", "mem-3", "mem-4")
```

**S6** — Empty repository produces no deletions
```
Given FakeMemoriesRepository contains zero memories
When  DeleteAllMemoriesUseCase.invoke() completes
Then  FakeMemoriesRepository.deletedMemoryIds == emptySet()
```

**E3** — Repository throws on deleteMemory, exception propagates
```
Given memoriesRepository.getAllMemoriesFlow() emits [Memory(id="mem-X")]
  And memoriesRepository.deleteMemory("mem-X") throws RuntimeException("db locked")
When  DeleteAllMemoriesUseCase.invoke() is called
Then  a RuntimeException with message "db locked" is thrown to the caller
```

---

### `DeleteAllArtifactsUseCase`

**S7** — Delegates to CleanupRepository.deleteAllArtifacts exactly once
```
Given cleanupRepository is a mock
When  DeleteAllArtifactsUseCase.invoke() completes
Then  cleanupRepository.deleteAllArtifacts() is called exactly 1 time
```

**E4** — CleanupRepository throws, exception propagates
```
Given cleanupRepository.deleteAllArtifacts() throws RuntimeException("io error")
When  DeleteAllArtifactsUseCase.invoke() is called
Then  a RuntimeException with message "io error" is thrown to the caller
```

---

### `DeleteAccountUseCase`

**S8** — Cloud purge succeeds, then sign-out is called
```
Given cloudSyncPort.deleteUserAccount() returns Result.success(Unit)
  And authManager.signOut() returns Result.success(Unit)
When  DeleteAccountUseCase.invoke() completes
Then  the return value is Result.success(Unit)
  And authManager.signOut() was called exactly 1 time
```

**S9** — Cloud purge succeeds, sign-out returns failure, failure is returned
```
Given cloudSyncPort.deleteUserAccount() returns Result.success(Unit)
  And authManager.signOut() returns Result.failure(Exception("session expired"))
When  DeleteAccountUseCase.invoke() completes
Then  the return value is Result.failure(...)
  And Result.exceptionOrNull().message == "session expired"
  And authManager.signOut() was called exactly 1 time
```

**E5** — Cloud purge fails, sign-out is NOT called
```
Given cloudSyncPort.deleteUserAccount() returns Result.failure(Exception("network error"))
When  DeleteAccountUseCase.invoke() completes
Then  the return value is Result.failure(...)
  And Result.exceptionOrNull().message == "network error"
  And authManager.signOut() was called exactly 0 times
```

---

## Section 2 — SettingsDataControlsDelegate

Tests use:
- `MutableStateFlow<SheetVisibilityState>` (real)
- `MutableStateFlow<DataControlsTransientState>` (real)
- Mocked `SettingsDataControlsUseCases` with `MockK`
- `runTest` + `advanceUntilIdle`

### Sheet visibility

**M1** — `onShowDataControlsSheet(true)` opens the sheet
```
Given sheetVisibility.value.dataControls == false
When  delegate.onShowDataControlsSheet(sheetVisibility, show = true) is called
Then  sheetVisibility.value.dataControls == true
```

**M2** — `onShowDataControlsSheet(false)` closes the sheet and resets transient state
```
Given sheetVisibility.value.dataControls == true
  And dataControlsState.value == DataControlsTransientState(pendingAction = DeleteChats, isDeleting = true)
When  delegate.onShowDataControlsSheet(sheetVisibility, show = false)
Then  sheetVisibility.value.dataControls == false
  And dataControlsState.value == DataControlsTransientState()
  (i.e. pendingAction == null, isDeleting == false, deleteError == null)
```

### Confirmation state machine

**M3** — `onRequestDeleteAllChats()` sets pending action to `DeleteChats`
```
Given dataControlsState.value.pendingAction == null
When  delegate.onRequestDeleteAllChats(dataControlsState)
Then  dataControlsState.value.pendingAction == DataControlsPendingAction.DeleteChats
  And dataControlsState.value.isDeleting == false
```

**M4** — `onRequestDeleteAllMedia()` sets pending action to `DeleteMedia`
```
Given dataControlsState.value.pendingAction == null
When  delegate.onRequestDeleteAllMedia(dataControlsState)
Then  dataControlsState.value.pendingAction == DataControlsPendingAction.DeleteMedia
```

**M5** — `onRequestDeleteAllMemories()` sets pending action to `DeleteMemories`
```
Given dataControlsState.value.pendingAction == null
When  delegate.onRequestDeleteAllMemories(dataControlsState)
Then  dataControlsState.value.pendingAction == DataControlsPendingAction.DeleteMemories
```

**M6** — `onRequestDeleteAllArtifacts()` sets pending action to `DeleteArtifacts`
```
Given dataControlsState.value.pendingAction == null
When  delegate.onRequestDeleteAllArtifacts(dataControlsState)
Then  dataControlsState.value.pendingAction == DataControlsPendingAction.DeleteArtifacts
```

**M7** — `onRequestDeleteAccount()` sets pending action to `DeleteAccount`
```
Given dataControlsState.value.pendingAction == null
When  delegate.onRequestDeleteAccount(dataControlsState)
Then  dataControlsState.value.pendingAction == DataControlsPendingAction.DeleteAccount
```

**M8** — `onDismissDataControlsConfirmation()` clears pending action
```
Given dataControlsState.value.pendingAction == DataControlsPendingAction.DeleteChats
When  delegate.onDismissDataControlsConfirmation(dataControlsState)
Then  dataControlsState.value.pendingAction == null
  And dataControlsState.value.isDeleting == false
```

### Confirm — Delete Chats

**S10** — `onConfirmDeleteAllChats()` happy path: sets isDeleting, completes, resets state
```
Given dataControlsState.value.pendingAction == DataControlsPendingAction.DeleteChats
  And dataControlsUseCases.deleteAllChats.invoke() completes without error
When  delegate.onConfirmDeleteAllChats(scope, "tag", dataControlsState) is launched
  And advanceUntilIdle()
Then  dataControlsState.value.isDeleting == false  (was true during execution)
  And dataControlsState.value.pendingAction == null
  And dataControlsState.value.deleteError == null
```

**E6** — `onConfirmDeleteAllChats()` error path: sets deleteError, clears isDeleting
```
Given dataControlsUseCases.deleteAllChats.invoke() throws RuntimeException("db error")
When  delegate.onConfirmDeleteAllChats(scope, "tag", dataControlsState) is launched
  And advanceUntilIdle()
Then  dataControlsState.value.isDeleting == false
  And dataControlsState.value.deleteError == "db error"
  And dataControlsState.value.pendingAction == null
```

### Confirm — Delete Media

**S11** — `onConfirmDeleteAllMedia()` happy path resets state fully
```
Given dataControlsUseCases.deleteAllMedia.invoke() completes without error
When  delegate.onConfirmDeleteAllMedia(scope, "tag", dataControlsState) and advanceUntilIdle()
Then  dataControlsState.value == DataControlsTransientState()
```

**E7** — `onConfirmDeleteAllMedia()` error path sets deleteError
```
Given dataControlsUseCases.deleteAllMedia.invoke() throws RuntimeException("media error")
When  delegate.onConfirmDeleteAllMedia(scope, "tag", dataControlsState) and advanceUntilIdle()
Then  dataControlsState.value.deleteError == "media error"
  And dataControlsState.value.isDeleting == false
```

### Confirm — Delete Memories

**S12** — `onConfirmDeleteAllMemories()` happy path resets state fully
```
Given dataControlsUseCases.deleteAllMemories.invoke() completes without error
When  delegate.onConfirmDeleteAllMemories(scope, "tag", dataControlsState) and advanceUntilIdle()
Then  dataControlsState.value == DataControlsTransientState()
```

**E8** — `onConfirmDeleteAllMemories()` error path sets deleteError
```
Given dataControlsUseCases.deleteAllMemories.invoke() throws RuntimeException("memory error")
When  delegate.onConfirmDeleteAllMemories(scope, "tag", dataControlsState) and advanceUntilIdle()
Then  dataControlsState.value.deleteError == "memory error"
  And dataControlsState.value.isDeleting == false
```

### Confirm — Delete Artifacts

**S13** — `onConfirmDeleteAllArtifacts()` happy path resets state fully
```
Given dataControlsUseCases.deleteAllArtifacts.invoke() completes without error
When  delegate.onConfirmDeleteAllArtifacts(scope, "tag", dataControlsState) and advanceUntilIdle()
Then  dataControlsState.value == DataControlsTransientState()
```

**E9** — `onConfirmDeleteAllArtifacts()` error path sets deleteError
```
Given dataControlsUseCases.deleteAllArtifacts.invoke() throws RuntimeException("artifact error")
When  delegate.onConfirmDeleteAllArtifacts(scope, "tag", dataControlsState) and advanceUntilIdle()
Then  dataControlsState.value.deleteError == "artifact error"
  And dataControlsState.value.isDeleting == false
```

### Confirm — Delete Account

**S14** — `onConfirmDeleteAccount()` happy path resets state fully
```
Given dataControlsUseCases.deleteAccount.invoke() returns Result.success(Unit)
When  delegate.onConfirmDeleteAccount(scope, "tag", dataControlsState) and advanceUntilIdle()
Then  dataControlsState.value == DataControlsTransientState()
```

**E10** — `onConfirmDeleteAccount()` returns failure result, sets deleteError
```
Given dataControlsUseCases.deleteAccount.invoke() returns Result.failure(Exception("purge failed"))
When  delegate.onConfirmDeleteAccount(scope, "tag", dataControlsState) and advanceUntilIdle()
Then  dataControlsState.value.deleteError == "purge failed"
  And dataControlsState.value.isDeleting == false
  And dataControlsState.value.pendingAction == null
```

---

## Section 3 — `SettingsUseCasesImpl` integration

**M9** — `SettingsUseCasesImpl` exposes `dataControls`
```
Given SettingsUseCasesImpl constructed with all required grouped use case instances
  including dataControls = mockDataControlsUseCases
When  useCases.dataControls is accessed
Then  useCases.dataControls === mockDataControlsUseCases  (same reference)
```

---

## Section 4 — Mutation Defense

These scenarios verify that important guard conditions cannot be accidentally removed.

**MD1** — `DeleteAccountUseCase` short-circuits on cloud purge failure (sign-out not called)
```
Mutation: remove the early-return on cloud purge failure (let sign-out always run)
Then S8 still passes (sign-out is called after success)
But  E5 FAILS because authManager.signOut() would now be called 1 time instead of 0
```

**MD2** — `onShowDataControlsSheet(false)` resets transient state (not just visibility)
```
Mutation: remove the dataControlsState reset on sheet close
Then M1 still passes
But  M2 FAILS because dataControlsState.value.pendingAction would still be DeleteChats
```

**MD3** — `onConfirmDeleteAllChats` sets `isDeleting = true` before the coroutine body
```
Mutation: remove the isDeleting = true assignment at start of confirm handlers
Then S10 still passes (final state is idle)
But  a test asserting the intermediate StateFlow emission of isDeleting = true would fail
(Implementation note: add an intermediate state assertion using Turbine in the delegate test)
```

**MD4** — Confirmation dialog only renders when `pendingAction != null`
```
Mutation: render AlertDialog unconditionally regardless of pendingAction
Then a UI test asserting AlertDialog is absent when pendingAction == null would fail
(This is enforced at the Compose semantic level using `assertDoesNotExist()`)
```

**MD5** — `DeleteAllChatsUseCase` iterates all chats (not just first)
```
Mutation: change .first() on getAllChats() to return single-element list always
Then S1 passes if FakeChatRepository emits only 1 chat
But  S1 FAILS when seeded with 3 chats because deletedChatIds would only contain 1 ID
```

---

## Fake Implementations Required

| Fake | Interface | State tracked |
|---|---|---|
| `FakeChatRepository` | `ChatRepository` | `MutableStateFlow<List<Chat>>` + `deletedChatIds: MutableSet<String>` |
| `FakeStudioRepository` | `StudioRepositoryPort` | `MutableStateFlow<List<StudioMediaAsset>>` + `deletedMediaIds: MutableSet<String>` |
| `FakeMemoriesRepository` | `MemoriesRepository` | `MutableStateFlow<List<Memory>>` + `deletedMemoryIds: MutableSet<String>` |

All Fakes implement the full interface; unused methods return stubs (`throw UnsupportedOperationException()`). Fakes are in `src/test/kotlin` of the module where they are used.

---

## Test File Placement

| Test class | Source set |
|---|---|
| `DeleteAllChatsUseCaseTest` | `core/domain/src/test/kotlin/.../domain/usecase/settings/` |
| `DeleteAllMediaUseCaseTest` | `core/domain/src/test/kotlin/.../domain/usecase/settings/` |
| `DeleteAllMemoriesUseCaseTest` | `core/domain/src/test/kotlin/.../domain/usecase/settings/` |
| `DeleteAllArtifactsUseCaseTest` | `core/domain/src/test/kotlin/.../domain/usecase/settings/` |
| `DeleteAccountUseCaseTest` | `core/domain/src/test/kotlin/.../domain/usecase/settings/` |
| `SettingsDataControlsDelegateTest` | `feature/settings/src/test/kotlin/.../feature/settings/` |
| `SettingsUseCasesImplTest` (extend) | `core/domain/src/test/kotlin/.../domain/usecase/settings/` |

---

## Run Command

```bash
./gradlew :core:domain:test :feature:settings:test
```
