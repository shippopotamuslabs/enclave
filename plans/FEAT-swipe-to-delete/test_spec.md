# Test Spec: FEAT-swipe-to-delete — Swipe-to-Delete Across Settings Bottom Sheets

## Test Framework & Run Command

Framework: JUnit 5 + `createComposeRule()` (Compose UI testing via `androidx.compose.ui.test.junit4`).
Run: `./gradlew :feature:settings:testDebugUnitTest --tests "*SwipeToDeleteBehaviorTest*"`

## Scenario Index

- `S1` — Swipe threshold on a normal MemoryItem triggers `onDelete` callback
- `S2` — Last-preset guard: swipe is disabled when exactly 1 config remains
- `S3` — Downloaded LocalModelAssetCard is swipeable and triggers `onRequestDeleteAsset`
- `S4` — Available-for-download asset is not swipeable (no `SwipeToDismissBox` wrapper)
- `S5` — BYOK asset list swipe left triggers `onRequestDeleteAsset`
- `E1` — Swipe enabled for a config row when 2 or more configs exist
- `E2` — isSoftDeleted asset in the Downloaded section IS swipeable
- `M1` — Dialog cancel resets DismissState so the row returns to its original position

---

## Happy Path Scenarios

### S1: Swipe threshold on a normal MemoryItem triggers `onDelete` callback
- **Given:** A `MemoryItem` composable is rendered with `memory = StoredMemory(id = "mem-1", text = "Test memory", category = MemoryCategory.PREFERENCES)`, `onEdit = {}`, and `onDelete = { capturedId = it }` where `capturedId` is a `String?` mutable holder initialized to `null`.
- **When:** A horizontal swipe from right to left exceeding the `SwipeToDismissBoxValue.EndToStart` threshold is performed on the item (via `onNodeWithText("Test memory").performTouchInput { swipeLeft() }`).
- **Then:** `capturedId` equals `"mem-1"`.

### S2: Last-preset guard disables swipe when exactly 1 config remains
- **Given:** A `ByokConfigListView` is rendered with an `ApiModelAssetUi` whose `configurations` list contains exactly one `ApiModelConfigUi(id = ApiModelConfigurationId("cfg-only"), displayName = "Solo")`. A boolean `deleteTriggered` is initialized to `false`; `onRequestDeleteConfig = { deleteTriggered = true }`.
- **When:** A swipe-left gesture is performed on the row with text "Solo".
- **Then:** `deleteTriggered` equals `false`.

### S3: Downloaded LocalModelAssetCard is swipeable and triggers `onRequestDeleteAsset`
- **Given:** A `LocalModelAssetCard` composable is rendered with `asset = LocalModelAssetUi(metadataId = LocalModelId("lm-1"), friendlyName = "Llama 3", isSoftDeleted = false, configurations = listOf(/* one config */), ...)`, `reDownloadProgress = null`, and `onRequestDeleteAsset` is a non-null lambda that sets `capturedAsset` to the received asset.
- **When:** A swipe-left gesture is performed on the node with text "Llama 3".
- **Then:** `capturedAsset` is not null and `capturedAsset!!.metadataId` equals `LocalModelId("lm-1")`.

### S4: Available-for-download asset is not swipeable
- **Given:** A `LocalModelAssetCard` composable is rendered with `asset = LocalModelAssetUi(metadataId = LocalModelId("dl-1"), friendlyName = "Gemma 2", isSoftDeleted = true, ...)`, `onRequestDeleteAsset = null`, and `reDownloadProgress = null`.
- **When:** A swipe-left gesture is performed on the node with text "Gemma 2".
- **Then:** No `onRequestDeleteAsset` callback fires (the lambda is null); a node matching `hasAnyAncestor(hasTestTag("swipe_dismiss_box"))` does not exist in the composition (the `SwipeToDismissBox` wrapper is absent when `onRequestDeleteAsset == null`).

### S5: BYOK asset list swipe left triggers `onRequestDeleteAsset`
- **Given:** A `ByokAssetListView` is rendered with `apiModels = listOf(ApiModelAssetUi(credentialsId = ApiCredentialsId("cred-1"), displayName = "GPT-4o", configurations = listOf(/* one config */), ...))`. A boolean `deleteTriggered` is initialized to `false`; `onRequestDeleteAsset = { deleteTriggered = true }`.
- **When:** A swipe-left gesture is performed on the node with text "GPT-4o".
- **Then:** `deleteTriggered` equals `true`.

---

## Error & Edge Case Scenarios

### E1: Swipe enabled for a config row when 2 or more configs exist
- **Given:** A `ByokConfigListView` is rendered with an `ApiModelAssetUi` whose `configurations` list contains two entries: `ApiModelConfigUi(id = ApiModelConfigurationId("cfg-a"), displayName = "Alpha")` and `ApiModelConfigUi(id = ApiModelConfigurationId("cfg-b"), displayName = "Beta")`. A `String?` holder `capturedConfigId` is initialized to `null`; `onRequestDeleteConfig = { capturedConfigId = it.id.value }`.
- **When:** A swipe-left gesture is performed on the row with text "Alpha".
- **Then:** `capturedConfigId` equals `"cfg-a"`.

### E2: isSoftDeleted asset in the Downloaded section IS swipeable
- **Given:** A `LocalModelAssetCard` composable is rendered with `asset = LocalModelAssetUi(metadataId = LocalModelId("soft-1"), friendlyName = "Soft Deleted Model", isSoftDeleted = true, ...)`, `reDownloadProgress = null`, and `onRequestDeleteAsset` is a non-null lambda that sets `capturedAsset`.
- **When:** A swipe-left gesture is performed on the node with text "Soft Deleted Model".
- **Then:** `capturedAsset` is not null and `capturedAsset!!.metadataId` equals `LocalModelId("soft-1")`.

---

## Mutation Defense

### Lazy Implementation Risk

A lazy implementation could set `confirmValueChange` to return `true`, which auto-dismisses the row permanently instead of snapping it back — making the confirmation dialog irrelevant because the item vanishes from the list before the user confirms deletion.

### M1: Dialog cancel resets DismissState so the row returns to its original position
- **Given:** A `MemoryItem` composable is rendered with `memory = StoredMemory(id = "mem-snap", text = "Snap back test", category = MemoryCategory.PREFERENCES)`. The parent holds `var memoryIdToDelete: String? by mutableStateOf(null)`; `onDelete = { memoryIdToDelete = it }`. The item's `SwipeToDismissBoxState` is accessible from the test (via a `rememberSwipeToDismissBoxState` passed in through a test seam or captured by `SemanticsPropertyKey`).
- **When:** (Step 1) A swipe-left gesture is performed until the `EndToStart` threshold is reached, setting `memoryIdToDelete = "mem-snap"`. (Step 2) `memoryIdToDelete` is then set to `null` (simulating the user dismissing the confirmation dialog without confirming). (Step 3) The Compose test framework advances until idle.
- **Then:** The `SwipeToDismissBoxState.currentValue` equals `SwipeToDismissBoxValue.Settled` (the row is back in its original position, not `EndToStart`).
