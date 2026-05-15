# Spec: FEAT-swipe-to-delete — Swipe-to-Delete Across Settings Bottom Sheets

## Objective

Replace the long-press-to-delete dropdown pattern across all five settings bottom sheets (`ByokBottomSheet`, `LocalModelsBottomSheet`, `TtsProvidersBottomSheet`, `MediaProvidersBottomSheet`, `MemoriesBottomSheet`) with `SwipeToDismissBox` (Material 3, `EndToStart` direction). Swiping left reveals a red background with a trash icon; reaching the dismiss threshold triggers the existing confirmation dialog and, on cancel, snaps the row back. This improves discoverability and aligns the deletion UX across all settings list surfaces.

## File Structure

| Action | Path | Responsibility |
|--------|------|----------------|
| Modify | `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokBottomSheet.kt:227-521` | Replace `combinedClickable`+`DropdownMenu` in `ByokAssetListView` and `ByokConfigListView` item rows with `SwipeToDismissBox`; remove hint text |
| Modify | `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/LocalModelsBottomSheet.kt:249-633` | Replace `combinedClickable`+`DropdownMenu` in `LocalModelAssetCard` and `LocalModelConfigListView` config rows with `SwipeToDismissBox`; replace trash `IconButton` in config header with "Delete Model" `TextButton`; remove hint text |
| Modify | `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/TtsProvidersBottomSheet.kt:107-213` | Replace `combinedClickable`+`DropdownMenu` in TTS asset list with `SwipeToDismissBox`; remove hint text |
| Modify | `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/MediaProvidersBottomSheet.kt:100-200` | Replace `combinedClickable`+`DropdownMenu` in media asset list with `SwipeToDismissBox`; remove hint text |
| Modify | `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/MemoriesBottomSheet.kt:212-268` | Replace `combinedClickable`+`DropdownMenu` in `MemoryItem` with `SwipeToDismissBox`; tap stays as edit |
| Create | `feature/settings/src/test/kotlin/com/browntowndev/pocketcrew/feature/settings/SwipeToDeleteBehaviorTest.kt` | Compose behavioral tests for swipe guard logic: last-preset guard, available-for-download guard, DismissState reset on dialog cancel |

## Data Models & Schemas

None. No new data models or schema changes. The feature is purely a UI-layer change operating on the existing `SettingsUiState` hierarchy (`ApiModelAssetUi`, `ApiModelConfigUi`, `LocalModelAssetUi`, `LocalModelConfigUi`, `TtsProviderAssetUi`, `MediaProviderAssetUi`, `StoredMemory`).

## API Contracts & Interfaces

No new public interfaces or method signatures. The existing callback signatures on each composable remain unchanged:

- `ByokAssetListView`: `onRequestDeleteAsset: (ApiModelAssetUi) -> Unit` — unchanged, now triggered by swipe threshold.
- `ByokConfigListView`: `onRequestDeleteConfig: (ApiModelConfigUi) -> Unit` — unchanged, now triggered by swipe threshold.
- `LocalModelAssetCard`: `onRequestDeleteAsset: ((LocalModelAssetUi) -> Unit)?` — unchanged; `null` value continues to disable swipe.
- `LocalModelConfigListView` config row: `onRequestDeleteConfig: (LocalModelConfigUi) -> Unit` — unchanged, now triggered by swipe threshold.
- `LocalModelConfigListView` header: replaces bare `IconButton(onClick = { onRequestDeleteAsset(asset) })` with a `TextButton` labeled "Delete Model" calling the same `onRequestDeleteAsset` lambda.
- `TtsProvidersBottomSheet` asset card: `pendingDeleteTarget = asset` assignment — same logic, now triggered by swipe.
- `MediaProvidersBottomSheet` asset card: `pendingDeleteTarget = asset` assignment — same logic, now triggered by swipe.
- `MemoryItem`: `onDelete: () -> Unit` — unchanged, now triggered by swipe threshold.

Swipe guard predicate used in each config list:
```kotlin
val isSwipeEnabled: Boolean = configurations.size > 1
```
Where `configurations` refers to `ApiModelAssetUi.configurations: List<ApiModelConfigUi>` or `LocalModelAssetUi.configurations: List<LocalModelConfigUi>` depending on context.

Swipe background composable (inline in each `SwipeToDismissBox` background slot):
```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.errorContainer),
    contentAlignment = Alignment.CenterEnd
) {
    Icon(
        imageVector = Icons.Default.Delete,
        contentDescription = "Delete",
        tint = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.padding(end = 16.dp)
    )
}
```

DismissState pattern used in all five files — `confirmValueChange` returns `false` to prevent auto-dismiss; the `LaunchedEffect` resets the row when the dialog is cancelled:
```kotlin
val dismissState = rememberSwipeToDismissBoxState(
    confirmValueChange = { value ->
        if (value == SwipeToDismissBoxValue.EndToStart) {
            onRequestDelete(item)
            false   // prevent auto-dismiss; dialog handles the actual action
        } else true
    }
)
// Per-item, keyed on the pending delete state:
LaunchedEffect(pendingDeleteTarget) {
    if (pendingDeleteTarget == null) dismissState.reset()
}
```

## Config / Permissions Delta

None. No new Gradle dependencies, manifest entries, or build script changes. `SwipeToDismissBox` and `rememberSwipeToDismissBoxState` are part of `androidx.compose.material3` which is already declared in `feature/settings/build.gradle.kts`. `androidx.compose.foundation.background` is already available via the compose BOM. `Icons.Default.Delete` is already imported in all five files. `@OptIn(ExperimentalMaterial3Api::class)` is already present on all affected composables.

## Constitution Audit

This design modifies only `:feature:settings` UI composables, does not touch `:domain`, `:data`, or `:core:inference`, and adds Compose UI tests in the existing test source set — consistent with the layer dependency rules and test placement conventions observed in AGENTS.md.

---

## Subagent-Driven Execution

Downstream executors (TDD Red Sub-Agent, Implementation Sub-Agent, or a human following this plan) act as **coordinators first, implementers second**. For each **Implementation Task** below:

1. **Spawn** a child subagent scoped to that task alone (for example: author the failing tests for the task's named scenarios, or implement the production change the task describes).
2. **Review** the child subagent's output against this document as the contract — accept nothing that contradicts the File Structure, API Contracts & Interfaces, the task **Files** lists, or the task-to-scenario bindings — before advancing to the next task.
3. **Repeat** task by task in order. Do not fold multiple unrelated tasks into one child run unless a task explicitly instructs a bundled scope.

Scenario IDs are the **unit of work** each child subagent must satisfy for its task; cross-check every ID the task names under **Verifies scenarios** against `test_spec.md` before closing the task.

**Per-child review checklist (parent executor):**
- Paths touched match the task **Files** entries and the File Structure table; no edits outside listed paths.
- Every **Verifies scenarios** ID for that task shows the intended test state for the current phase (red where failure is expected; green where pass is expected), per `test_spec.md`.
- No scope drift: no unlisted files, no extra behaviors, no shortcuts that bypass this spec.
- Deliverables live in real source and test files aligned with this spec — no substitute that embeds runnable tests or implementation bodies here or otherwise diverges from the contract.

---

## Implementation Tasks

### Task 1: Write Failing Tests for Swipe Guard Logic

**Files:**
- Create: `feature/settings/src/test/kotlin/com/browntowndev/pocketcrew/feature/settings/SwipeToDeleteBehaviorTest.kt`

**Change:** Create `SwipeToDeleteBehaviorTest` with Compose UI tests covering the last-preset guard, available-for-download non-swipeability, and DismissState-reset-on-cancel behaviors. All tests must be red (fail) at this stage because the production changes have not yet been made.

**Verifies scenarios:** `S1, S2, S3, S4, S5, E1, E2, M1`

**Commit message:**
```
test(FEAT-swipe-to-delete): add failing behavioral tests for swipe guard logic
```

---

### Task 2: Refactor ByokBottomSheet — ByokAssetListView

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokBottomSheet.kt:227-361`

**Change:** In `ByokAssetListView`, remove the "Hint: long press to edit/delete the provider." `Text` composable; replace `combinedClickable(onClick, onLongClick)` with plain `clickable { onSelectAsset(asset) }` on the `Card`; remove `var menuExpanded` state and the `Box`/`DropdownMenu`/`DropdownMenuItem` callsites; wrap each item in `SwipeToDismissBox(enableDismissFromStartToEnd = false, enableDismissFromEndToStart = true)` with the red background slot from API Contracts; call `onRequestDeleteAsset(asset)` when `SwipeToDismissBoxValue.EndToStart` is confirmed; `confirmValueChange` returns `false`. Note: `DropdownMenu`/`DropdownMenuItem` imports stay because `ByokConfigListView`'s asset-level MoreVert still uses them.

**Verifies scenarios:** `S1, S5`

**Commit message:**
```
feat(FEAT-swipe-to-delete): swipe-to-delete on ByokAssetListView items
```

---

### Task 3: Refactor ByokBottomSheet — ByokConfigListView Config Rows

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokBottomSheet.kt:440-521`

**Change:** In `ByokConfigListView` config item rows: compute `val isSwipeEnabled = asset.configurations.size > 1`; remove `var menuExpanded` state, the `Box` wrapping `DropdownMenu`, and `DropdownMenuItem`; replace `combinedClickable` with plain `clickable { onEditConfig(config) }`; wrap with `SwipeToDismissBox(gesturesEnabled = isSwipeEnabled)` in `EndToStart` direction; call `onRequestDeleteConfig(config)` on threshold with `confirmValueChange` returning `false`. The MoreVert header dropdown for asset-level Edit/Delete is untouched.

**Verifies scenarios:** `S2, E1`

**Commit message:**
```
feat(FEAT-swipe-to-delete): swipe-to-delete on ByokConfigListView preset rows
```

---

### Task 4: Refactor LocalModelsBottomSheet — LocalModelAssetCard

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/LocalModelsBottomSheet.kt:342-498`

**Change:** In `LocalModelAssetCard`: remove `var menuExpanded` state, the outer `Box` wrapping the `DropdownMenu`, and `DropdownMenu`/`DropdownMenuItem`; replace `combinedClickable` with plain `clickable { onSelectAsset(asset) }`; when `onRequestDeleteAsset != null`, wrap the card with `SwipeToDismissBox(EndToStart)` calling `onRequestDeleteAsset!!(asset)` on threshold with `confirmValueChange = false`; when `onRequestDeleteAsset == null` ("Available for download" items), render the card directly without `SwipeToDismissBox`; apply `testTag("swipe_dismiss_box")` to the `SwipeToDismissBox` wrapper to allow S4 assertion. Also in `LocalModelAssetListView`, remove the "Hint: long press to delete the model." `Text`. Remove `combinedClickable`, `DropdownMenu`, `DropdownMenuItem` imports.

**Verifies scenarios:** `S3, S4, E2`

**Commit message:**
```
feat(FEAT-swipe-to-delete): swipe-to-delete on LocalModelAssetCard (downloaded only)
```

---

### Task 5: Refactor LocalModelsBottomSheet — Config Rows and Header Delete Button

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/LocalModelsBottomSheet.kt:500-633`

**Change:** In `LocalModelConfigListView` header `Row`: replace the bare trash `IconButton` with a `TextButton` whose `Text` reads "Delete Model" and whose `onClick` calls `onRequestDeleteAsset(asset)`. For each config row in the `LazyColumn`: compute `val isSwipeEnabled = asset.configurations.size > 1`; remove `var menuExpanded` and `DropdownMenu`/`DropdownMenuItem`; replace `combinedClickable` with plain `clickable { onEditConfig(config) }`; wrap with `SwipeToDismissBox(gesturesEnabled = isSwipeEnabled, EndToStart)` calling `onRequestDeleteConfig(config)` on threshold with `confirmValueChange` returning `false`.

**Verifies scenarios:** `S3, E2`

**Commit message:**
```
feat(FEAT-swipe-to-delete): swipe-to-delete on LocalModelConfigListView rows; replace trash icon with Delete Model button
```

---

### Task 6: Refactor TtsProvidersBottomSheet

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/TtsProvidersBottomSheet.kt:107-213`

**Change:** Remove "Hint: long press to delete the provider. Tap to edit." `Text`; replace `combinedClickable` with plain `clickable { onSelectTtsProviderAsset(asset); hideAndNavigate(onNavigateToTtsConfigure) }` on the `Card`; remove `var menuExpanded`, the surrounding `Box`, and `DropdownMenu`/`DropdownMenuItem`; wrap card with `SwipeToDismissBox(EndToStart)` setting `pendingDeleteTarget = asset` on threshold with `confirmValueChange` returning `false`; remove `combinedClickable`, `DropdownMenu`, `DropdownMenuItem` imports; remove the `@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)` opt-in since `combinedClickable` is removed.

**Verifies scenarios:** `S5`

**Commit message:**
```
feat(FEAT-swipe-to-delete): swipe-to-delete on TtsProvidersBottomSheet asset list
```

---

### Task 7: Refactor MediaProvidersBottomSheet

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/MediaProvidersBottomSheet.kt:92-200`

**Change:** Remove "Hint: long press to delete the provider. Tap to edit." `Text`; replace `combinedClickable` with plain `clickable { onSelectMediaProviderAsset(asset); hideAndNavigate(onNavigateToMediaConfigure) }` on the `Card`; remove `var menuExpanded`, the surrounding `Box`, and `DropdownMenu`/`DropdownMenuItem`; wrap card with `SwipeToDismissBox(EndToStart)` setting `pendingDeleteTarget = asset` on threshold with `confirmValueChange` returning `false`; remove `combinedClickable`, `DropdownMenu`, `DropdownMenuItem` imports; remove the `@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)` opt-in.

**Verifies scenarios:** `S5`

**Commit message:**
```
feat(FEAT-swipe-to-delete): swipe-to-delete on MediaProvidersBottomSheet asset list
```

---

### Task 8: Refactor MemoriesBottomSheet — MemoryItem

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/MemoriesBottomSheet.kt:212-268`

**Change:** In `MemoryItem`: remove `var menuExpanded`, the outer `Box` wrapping card+`DropdownMenu`, and `DropdownMenu`/`DropdownMenuItem`; replace `combinedClickable(onClick = onEdit, onLongClick = { menuExpanded = true })` with plain `clickable(onClick = onEdit)`; wrap card with `SwipeToDismissBox(EndToStart)` calling `onDelete()` on `EndToStart` threshold with `confirmValueChange` returning `false`; add `LaunchedEffect(memoryIdToDelete)` inside the per-item scope so that when `memoryIdToDelete` becomes `null` (dialog dismissed without confirming), `dismissState.reset()` is called; remove `combinedClickable`, `DropdownMenu`, `DropdownMenuItem` imports.

**Verifies scenarios:** `S1, M1`

**Commit message:**
```
feat(FEAT-swipe-to-delete): swipe-to-delete on MemoriesBottomSheet memory items
```

---

### Task 9: Make All Tests Pass

**Files:**
- Modify: `feature/settings/src/test/kotlin/com/browntowndev/pocketcrew/feature/settings/SwipeToDeleteBehaviorTest.kt`

**Change:** Update any test helper construction that relied on `combinedClickable` semantics or `DropdownMenu` presence so all eight scenarios in `SwipeToDeleteBehaviorTest` pass green. Run `./gradlew :feature:settings:testDebugUnitTest` and confirm zero failures.

**Verifies scenarios:** `S1, S2, S3, S4, S5, E1, E2, M1`

**Commit message:**
```
test(FEAT-swipe-to-delete): all swipe-to-delete behavioral tests passing
```
