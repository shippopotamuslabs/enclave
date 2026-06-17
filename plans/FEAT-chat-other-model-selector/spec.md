# FEAT-chat-other-model-selector — Implementation Plan

## Feature Summary

Add a fourth entry **"Other >"** below Council in the chat mode dropdown (`ChatTopBar`). Tapping it does **not** select a normal chat mode; it opens `AssignmentSelectionBottomSheet` (extracted to `:core:ui`) so the user can pick any configured **Local** or **API** chat preset. The selection applies an **ephemeral per-chat model override** for the current `ChatViewModel` session only. Fast/Thinking/Council slot assignments in settings are **never** mutated (`setDefaultModel` is never called). While Other is active, the top bar shows **"Other >"** as the mode title and the picked preset's **display name** as the subtitle.

### Confirmed Architecture (do not relitigate)

| Decision | Resolution |
|----------|------------|
| Override scope | Ephemeral session state in `ChatInputCoordinator` (same lifecycle as `_selectedMode`). Survives config refresh; lost when `ChatViewModel` is destroyed. Never persisted to DB. |
| Shared picker | Extract `AssignmentSelectionBottomSheet`, `AssignmentSelectionContent`, `AssignmentSelectionView`, and private `ConfigSelectionCard` from `ModelConfigurationScreen.kt` into `:core:ui`. **Not** `ModelSelectionBottomSheet`. |
| Mode representation | **UI/persistence:** `Mode.OTHER` + `ChatModeMapper` exhaustive (`OTHER` ↔ `OTHER`). **Inference dispatch:** sealed `ChatResponseMode` — override is carried only on `ChatResponseMode.Other(override)`, never as an optional param paired with `Mode.FAST`/`THINKING`/`COUNCIL`. Council remains multi-model; Other is always single-model. |
| Preset sourcing | New `GetChatCapablePresetsUseCase` in `:core:domain` (Local + API chat presets only). |
| Explicit config resolution | Extend `ActiveModelProviderPort` with by-id loading extracted from `ActiveModelProviderImpl` local/api branches. |

### Override Lifecycle

| Event | Behavior |
|-------|----------|
| **Set** | User confirms a Local or API preset in the sheet → `ChatInputCoordinator.onModelOverrideSelected(override)` sets `_selectedModelOverride` and `_selectedMode = ChatModeUi.OTHER`. Sheet dismissed. |
| **Cleared** | (1) `createNewChat()` → `clearModelOverride()` and reset `selectedMode` to persisted default from `UiSelectionRepository` (same as ViewModel init). (2) `onModeChange(FAST \| THINKING \| COUNCIL)` → `clearModelOverride()`. (3) `ChatViewModel` cleared/destroyed → state discarded. |
| **Persisted** | Never. No `UiSelectionRepository.setChatMode(Mode.OTHER)`. No Room schema changes. |
| **Visible** | When `selectedMode == ChatModeUi.OTHER` and override is non-null: top bar title = `"Other >"` (`R.string.mode_other`), subtitle = `override.displayName`. |
| **Inference** | `generateChatResponse(..., responseMode = ChatResponseMode.Other(override))` resolves explicit config by id; does **not** read Fast/Thinking/Council slot assignments. Slot modes pass `ChatResponseMode.Fast` / `.Thinking` / `.Council` with **no** override field. |

---

## API Contracts

### Domain — `ChatModelOverride`

**File:** `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/chat/ChatModelOverride.kt` (Create)

```kotlin
sealed interface ChatModelOverride {
    val displayName: String

    data class Local(
        val configId: LocalModelConfigurationId,
        override val displayName: String,
    ) : ChatModelOverride

    data class Api(
        val configId: ApiModelConfigurationId,
        override val displayName: String,
    ) : ChatModelOverride
}
```

### Domain — `Mode` (UI / persistence only)

**File:** `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/chat/Mode.kt` (Modify)

```kotlin
enum class Mode {
    FAST,
    THINKING,
    COUNCIL,
    OTHER,  // UI selection marker only — NOT passed to generateChatResponse
}
// DEFAULT remains FAST
```

`Mode.OTHER` exists so `ChatModeMapper` stays exhaustive and the top bar can show Other as a distinct selection. It is **not** the inference dispatch type.

### Domain — `ChatResponseMode` (inference dispatch — invariant-enforced)

**File:** `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/chat/ChatResponseMode.kt` (Create)

```kotlin
/**
 * Discriminated union for chat inference dispatch.
 * Invalid states (e.g. FAST + override) are unrepresentable — callers cannot
 * pass a slot mode and an override in the same argument list.
 */
sealed interface ChatResponseMode {
    data object Fast : ChatResponseMode
    data object Thinking : ChatResponseMode
    data object Council : ChatResponseMode
    data class Other(val modelOverride: ChatModelOverride) : ChatResponseMode
}
```

**Invariant:** `ChatModelOverride` appears **only** on `ChatResponseMode.Other`. The three slot variants carry no override field. `generateChatResponse` accepts `responseMode: ChatResponseMode` — not `Mode` + optional override.

### Domain — `ActiveModelProviderPort`

**File:** `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/port/repository/ActiveModelProviderPort.kt` (Modify)

```kotlin
interface ActiveModelProviderPort {
    suspend fun getActiveConfiguration(modelType: ModelType): ActiveModelConfiguration?

    /** Loads ActiveModelConfiguration by explicit preset id (not slot assignment). */
    suspend fun getActiveConfigurationByOverride(override: ChatModelOverride): ActiveModelConfiguration?
}
```

**Impl:** `ActiveModelProviderImpl` — extract private `loadLocalConfiguration(configId)` / `loadApiConfiguration(configId)` from existing slot-resolution branches; delegate `getActiveConfigurationByOverride` to the matching branch.

### Domain — `GetChatCapablePresetsUseCase`

**File:** `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/GetChatCapablePresetsUseCase.kt` (Create)

```kotlin
data class ChatCapablePresets(
    val localAssets: List<LocalModelAsset>,
    val apiAssets: List<ApiModelAsset>,
)

class GetChatCapablePresetsUseCase @Inject constructor(
    private val localModelRepository: LocalModelRepositoryPort,
    private val apiModelRepository: ApiModelRepositoryPort,
) {
    /** Emits whenever local or API model catalogs change. */
    operator fun invoke(): Flow<ChatCapablePresets>
}
```

**Filter rules (mirror settings text-slot eligibility):**

- **Local:** `observeAllLocalAssets()` → exclude soft-deleted (`configurations.isEmpty()`), exclude `isImageGenerationCapable` assets (same as `selectableLocalAssetsForSlot` when `modelType.isTextModel`).
- **API:** group credentials + configurations into `ApiModelAsset` list; include only credentials that have ≥1 configuration suitable for text chat (exclude image/video/music-only generation configs; keep multimodal chat configs). Exclude TTS, Live Voice, and Media provider entries entirely.

Wire into `ChatUseCases`:

```kotlin
// ChatUseCases.kt (Modify)
val getChatCapablePresets: GetChatCapablePresetsUseCase
```

### Domain — `generateChatResponse` / `executeResponseMode`

**Files:** `ChatUseCases.kt`, `ChatUseCasesImpl.kt`, `GenerateChatResponseUseCase.kt` (Modify)

Replace the existing `mode: Mode` parameter with `responseMode: ChatResponseMode`:

```kotlin
// ChatUseCases.kt
suspend fun generateChatResponse(
    prompt: String,
    userMessageId: MessageId,
    assistantMessageId: MessageId,
    chatId: ChatId,
    responseMode: ChatResponseMode,
    backgroundInferenceEnabled: Boolean,
)

// GenerateChatResponseUseCase.kt — invoke()
operator fun invoke(
    prompt: String,
    userMessageId: MessageId,
    assistantMessageId: MessageId,
    chatId: ChatId,
    responseMode: ChatResponseMode,
    backgroundInferenceEnabled: Boolean,
): Flow<AccumulatedMessages>

// Rename executeMode → executeResponseMode; exhaustive on ChatResponseMode
private fun executeResponseMode(
    responseMode: ChatResponseMode,
    prompt: String,
    userMessageId: MessageId,
    assistantMessageId: MessageId,
    chatId: ChatId,
    userHasImage: Boolean,
    fileAttachments: List<FileAttachment>,
    backgroundInferenceEnabled: Boolean,
): Flow<MessageGenerationState> =
    when (responseMode) {
        ChatResponseMode.Fast ->
            executeSingleModelMode(..., modelType = ModelType.FAST, ...)
        ChatResponseMode.Thinking ->
            executeSingleModelMode(..., modelType = ModelType.THINKING, ...)
        ChatResponseMode.Council ->
            executeCrewMode(...)
        is ChatResponseMode.Other ->
            executeOverrideMode(responseMode.modelOverride, ...)
    }
```

**`ChatResponseMode.Other` branch** (`executeOverrideMode`):

1. Resolves `ActiveModelConfiguration` via `activeModelProvider.getActiveConfigurationByOverride(modelOverride)`.
2. Runs single-model inference using explicit config (API path: `InferenceFactoryPort.withRawApiServiceForConfig(configId)`; local path: equivalent explicit-config inference entry — do **not** call `getActiveConfiguration(ModelType.FAST)`).

**Internal telemetry / accumulator:** `ChatGenerationAccumulatorManager` may continue to accept `Mode` internally; map `ChatResponseMode.Other` → `Mode.OTHER` only inside `GenerateChatResponseUseCase` — this mapping is private implementation detail, not part of the public `generateChatResponse` API.

### Presentation — `ChatResponseModeMapper` (send-boundary guard)

**File:** `feature/chat/.../ChatResponseModeMapper.kt` (Create)

Builds the domain dispatch type from ephemeral UI state **once**, at send time:

```kotlin
object ChatResponseModeMapper {
    fun fromSelection(
        selectedMode: ChatModeUi,
        override: ChatModelOverride?,
    ): ChatResponseMode =
        when (selectedMode) {
            ChatModeUi.FAST -> ChatResponseMode.Fast
            ChatModeUi.THINKING -> ChatResponseMode.Thinking
            ChatModeUi.COUNCIL -> ChatResponseMode.Council
            ChatModeUi.OTHER ->
                ChatResponseMode.Other(
                    modelOverride = requireNotNull(override) {
                        "Cannot send in OTHER mode without a selected preset"
                    },
                )
        }
}
```

`onSendMessage` calls `fromSelection` before `InferenceOrchestrator.sendMessage`. If `selectedMode == OTHER` and override is null (user opened sheet but never confirmed), send is **blocked** at this boundary — `generateChatResponse` is never called with an invalid combination.

### Presentation — `ChatModeUi` / `ChatModeMapper`

**Files:** `ChatModels.kt`, `ChatModeMapper.kt` (Modify)

```kotlin
enum class ChatModeUi(...) {
    FAST(...),
    THINKING(...),
    COUNCIL(...),
    OTHER(R.string.mode_other, R.drawable.pageview),  // string = "Other >"; reuse existing drawable
}

// ChatModeMapper.kt — exhaustive both directions
ChatModeUi.OTHER -> Mode.OTHER
Mode.OTHER -> ChatModeUi.OTHER
```

### Presentation — `ChatUiState`

**File:** `feature/chat/.../ChatModels.kt` (Modify)

```kotlin
data class ChatUiState(
    // ... existing fields ...
    val isModelOverrideSheetVisible: Boolean = false,
    val modelOverrideSheetLocalAssets: List<LocalModelAssetUi> = emptyList(),
    val modelOverrideSheetApiAssets: List<ApiModelAssetUi> = emptyList(),
    // selectedModeModelName: when selectedMode == OTHER, shows override.displayName
)
```

Use `kotlinx.collections.immutable` (`ImmutableList` / `persistentListOf`) for sheet asset lists per CODE_STYLE §4.1.

### Presentation — `ChatInputCoordinator`

**File:** `feature/chat/.../delegates/ChatInputCoordinator.kt` (Modify)

```kotlin
private val _selectedModelOverride = MutableStateFlow<ChatModelOverride?>(null)
val selectedModelOverride: StateFlow<ChatModelOverride?>

fun onModelOverrideSelected(override: ChatModelOverride) {
    _selectedModelOverride.value = override
    _selectedMode.value = ChatModeUi.OTHER
}

fun clearModelOverride() {
    _selectedModelOverride.value = null
}

fun onModeChange(mode: ChatModeUi) {
    if (mode != ChatModeUi.OTHER) clearModelOverride()
    _selectedMode.value = mode
}
```

### Presentation — `ChatUiStateReducer`

**File:** `feature/chat/.../delegates/ChatUiStateReducer.kt` (Modify)

- Add `selectedModelOverride: ChatModelOverride?` to `ReduceParams`.
- `selectedModeModelName`: when `selectedMode == ChatModeUi.OTHER`, return `selectedModelOverride?.displayName`; else existing `resolveModelNameForMode`.
- `modeModelNames`: build from `ChatModeUi.entries.filter { it != ChatModeUi.OTHER }` — OTHER excluded from slot name resolution.
- Pass through `isModelOverrideSheetVisible`, sheet asset lists from reduce params.

### Presentation — `ChatTopBar`

**File:** `feature/chat/.../ChatTopBar.kt` (Modify)

```kotlin
fun ChatTopBar(
    selectedMode: ChatModeUi = ChatModeUi.FAST,
    selectedModeModelName: String? = null,
    modeModelNames: Map<ChatModeUi, String?> = emptyMap(),
    onModeChange: (ChatModeUi) -> Unit = {},
    onOtherClick: () -> Unit = {},          // NEW
    onMenuClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onViewChatStatsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
)
```

**Dropdown behavior:**

1. Render `ChatModeUi.entries.filter { it != ChatModeUi.OTHER }` (FAST, THINKING, COUNCIL) — each calls `onModeChange(mode)`.
2. After the loop, render a **separate** `DropdownMenuItem` labeled `"Other >"` with trailing chevron affordance; `onClick` calls `onOtherClick()` only — **must not** call `onModeChange`.

### Presentation — `ChatScreen` / `ChatRoute`

**File:** `feature/chat/.../ChatScreen.kt` (Modify)

Host sheet following `ShareBottomSheet` pattern:

```kotlin
// New callbacks on ChatScreen
onOtherClick: () -> Unit,
onDismissModelOverrideSheet: () -> Unit,
onModelOverrideSelected: (
    localId: LocalModelConfigurationId?,
    apiId: ApiModelConfigurationId?,
    ttsId: TtsProviderId?,
    mediaId: MediaProviderId?,
    liveVoiceId: LiveVoiceProviderId?,
) -> Unit,

// When uiState.isModelOverrideSheetVisible:
AssignmentSelectionBottomSheet(
    slotLabel = "Other",
    localAssets = uiState.modelOverrideSheetLocalAssets,
    apiAssets = uiState.modelOverrideSheetApiAssets,
    ttsAssets = emptyList(),
    liveVoiceAssets = emptyList(),
    mediaAssets = emptyList(),
    onDismiss = onDismissModelOverrideSheet,
    onSelect = onModelOverrideSelected,
)
```

**File:** `feature/chat/.../ChatRoute.kt` (Modify) — wire ViewModel handlers to `ChatScreen` callbacks.

### Presentation — `ChatViewModel`

**File:** `feature/chat/.../ChatViewModel.kt` (Modify)

New handlers:

```kotlin
fun onOtherClick()
fun onDismissModelOverrideSheet()
fun onModelOverrideSelected(
    localId: LocalModelConfigurationId?,
    apiId: ApiModelConfigurationId?,
    ttsId: TtsProviderId?,
    mediaId: MediaProviderId?,
    liveVoiceId: LiveVoiceProviderId?,
)
```

Behavior:

- `onOtherClick`: set `isModelOverrideSheetVisible = true` (via reducer inputs); sheet assets populated from `getChatCapablePresets` Flow mapped through `AssignmentSelectionUiMappers`.
- `onModelOverrideSelected`: when `localId != null`, build `ChatModelOverride.Local(configId, displayName)` from selected config's `displayName`; when `apiId != null`, build `ChatModelOverride.Api(...)`; ignore tts/media/liveVoice ids; call `chatInputCoordinator.onModelOverrideSelected`; dismiss sheet. **Do not** call `setDefaultModel` or `uiSelectionRepository.setChatMode`.
- `onModeChange`: existing logic + `clearModelOverride()` already in coordinator; do **not** persist `Mode.OTHER` to `UiSelectionRepository`.
- `createNewChat`: add `chatInputCoordinator.clearModelOverride()` and reload persisted mode from `UiSelectionRepository` (reset off OTHER).
- `uiInputsFlow`: combine `chatInputCoordinator.selectedModelOverride`.
- `onSendMessage`: call `ChatResponseModeMapper.fromSelection(selectedMode, override)`; pass resulting `ChatResponseMode` to orchestrator. Block send when `selectedMode == OTHER` and override is null.

**Moderation / photo policy when OTHER:** resolve active config from override (not FAST slot) for `shouldModeratePromptUseCase` and `PhotoAttachmentPolicyResolver` — extend resolver to accept optional `ChatModelOverride`.

### Presentation — `InferenceOrchestrator`

**File:** `feature/chat/.../delegates/InferenceOrchestrator.kt` (Modify)

```kotlin
fun sendMessage(
    scope: CoroutineScope,
    input: String,
    responseMode: ChatResponseMode,   // built by ChatResponseModeMapper at ViewModel
    // ... existing params ...
)

// Inside inference job:
chatUseCases.generateChatResponse(
    // ...
    responseMode = responseMode,
)
```

`InferenceOrchestrator` no longer accepts parallel `selectedMode` + `modelOverride` params for inference — only the already-validated `ChatResponseMode`.

### `:core:ui` — Extracted Assignment Selection

**File:** `core/ui/.../component/assignment/AssignmentSelectionModels.kt` (Create)

Move from `SettingsModels.kt` (domain-only dependencies):

- `TtsProviderAssetUi`
- `LiveVoiceProviderAssetUi`
- `MediaProviderAssetUi`
- `ApiModelAssetUi`
- `ReusableApiCredentialUi`
- `CustomHeaderUi`
- `ApiModelConfigUi`
- `LocalModelAssetUi`
- `LocalModelConfigUi`

**Do NOT move:** `ModelSortOption`, `DiscoveredApiModelUi`.

**File:** `core/ui/.../component/assignment/AssignmentSelectionUiMappers.kt` (Create)

Move `LocalModelAssetUiMapper` and `ApiModelAssetUiMapper` from `SettingsUiMappers.kt` (package → `com.browntowndev.enclave.core.ui.component.assignment`). Register via Hilt in `:core:ui` `AssignmentSelectionModule` or existing UI DI module.

**File:** `core/ui/.../component/assignment/AssignmentSelectionBottomSheet.kt` (Create)

Move from `ModelConfigurationScreen.kt`:

```kotlin
@Composable
fun AssignmentSelectionBottomSheet(
    slotLabel: String,
    localAssets: List<LocalModelAssetUi>,
    apiAssets: List<ApiModelAssetUi>,
    ttsAssets: List<TtsProviderAssetUi>,
    liveVoiceAssets: List<LiveVoiceProviderAssetUi>,
    mediaAssets: List<MediaProviderAssetUi>,
    onDismiss: () -> Unit,
    onSelect: (
        localId: LocalModelConfigurationId?,
        apiId: ApiModelConfigurationId?,
        ttsId: TtsProviderId?,
        mediaId: MediaProviderId?,
        liveVoiceId: LiveVoiceProviderId?,
    ) -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
fun AssignmentSelectionContent(/* same params + onBack */)

private sealed interface AssignmentSelectionView { /* AssetList, LocalConfigList, ApiConfigList */ }

@Composable
private fun ConfigSelectionCard(...)
```

`selectableLocalAssetsForSlot` and image-pack helpers **remain** in `ModelConfigurationScreen.kt` (settings-specific).

### Resources

**File:** `feature/chat/src/main/res/values/strings.xml` (Modify)

```xml
<string name="mode_other">Other &gt;</string>
```

**File:** `feature/chat/src/main/res/drawable/` — reuse `pageview` drawable for `ChatModeUi.OTHER.iconRes` (no new vector required).

---

## File Structure Table

### Create

| File | Module | Responsibility |
|------|--------|----------------|
| `core/domain/.../model/chat/ChatModelOverride.kt` | `:core:domain` | Override sealed type |
| `core/domain/.../model/chat/ChatResponseMode.kt` | `:core:domain` | Inference dispatch sealed type (invariant-enforced) |
| `feature/chat/.../ChatResponseModeMapper.kt` | `:feature:chat` | UI state → `ChatResponseMode` at send boundary |
| `core/domain/.../usecase/chat/GetChatCapablePresetsUseCase.kt` | `:core:domain` | Chat-capable Local+API preset catalog |
| `core/domain/src/test/.../GetChatCapablePresetsUseCaseTest.kt` | `:core:domain` | Preset filter unit tests |
| `core/domain/src/test/.../ChatResponseModeInferenceTest.kt` | `:core:domain` | `ChatResponseMode.Other` inference path |
| `feature/chat/src/test/.../ChatResponseModeMapperTest.kt` | `:feature:chat` | Mapper invariants; OTHER-without-override guard |
| `feature/chat/src/test/.../ChatModeMapperTest.kt` | `:feature:chat` | OTHER↔OTHER mapper tests |
| `core/ui/.../component/assignment/AssignmentSelectionModels.kt` | `:core:ui` | Shared presentation models |
| `core/ui/.../component/assignment/AssignmentSelectionUiMappers.kt` | `:core:ui` | Domain→UI mappers for assignment picker |
| `core/ui/.../component/assignment/AssignmentSelectionBottomSheet.kt` | `:core:ui` | Extracted bottom sheet composables |
| `core/ui/src/test/.../AssignmentSelectionBottomSheetTest.kt` | `:core:ui` | Compose regression: tabs, empty state, confirm |
| `feature/chat/src/test/.../ChatInputCoordinatorOverrideTest.kt` | `:feature:chat` | Coordinator override lifecycle |
| `feature/chat/src/test/.../ChatUiStateReducerOverrideTest.kt` | `:feature:chat` | Reducer override display name |
| `feature/chat/src/test/.../ChatTopBarOtherEntryTest.kt` | `:feature:chat` | Other> menu item behavior |
| `feature/settings/src/test/.../AssignmentSelectionExtractionRegressionTest.kt` | `:feature:settings` | Settings screen identical after extraction |

### Modify

| File | Change |
|------|--------|
| `core/domain/.../model/chat/Mode.kt` | Add `OTHER` |
| `core/domain/.../port/repository/ActiveModelProviderPort.kt` | Add `getActiveConfigurationByOverride` |
| `core/data/.../repository/ActiveModelProviderImpl.kt` | Extract by-id loaders; implement override method |
| `core/data/src/test/.../ActiveModelProviderImplTest.kt` | Override resolution tests |
| `core/domain/.../usecase/chat/ChatUseCases.kt` | Replace `mode: Mode` with `responseMode: ChatResponseMode`; expose `getChatCapablePresets` |
| `core/domain/.../usecase/chat/ChatUseCasesImpl.kt` | Wire new use case + `responseMode` param |
| `core/domain/.../usecase/chat/GenerateChatResponseUseCase.kt` | `executeResponseMode` exhaustive on `ChatResponseMode` + `executeOverrideMode` |
| `core/domain/.../usecase/chat/DirectChatInferenceExecutor.kt` | Support explicit-config execution for override path (if needed by factory seam) |
| `feature/chat/.../ChatModels.kt` | `ChatModeUi.OTHER`; `ChatUiState` sheet + override fields |
| `feature/chat/.../ChatModeMapper.kt` | Exhaustive `OTHER` mapping |
| `feature/chat/.../delegates/ChatInputCoordinator.kt` | Override state + lifecycle hooks |
| `feature/chat/.../delegates/ChatUiStateReducer.kt` | Override display; exclude OTHER from `modeModelNames` |
| `feature/chat/.../delegates/InferenceOrchestrator.kt` | Accept `responseMode: ChatResponseMode`; thread to `generateChatResponse` |
| `feature/chat/.../ChatResponseModeMapper.kt` | Create mapper (send-boundary guard) |
| `feature/chat/.../ChatTopBar.kt` | Other> menu item + `onOtherClick` |
| `feature/chat/.../ChatScreen.kt` | Host `AssignmentSelectionBottomSheet` |
| `feature/chat/.../ChatRoute.kt` | Wire new callbacks |
| `feature/chat/.../ChatViewModel.kt` | Presets flow, sheet visibility, override handlers, send path |
| `feature/chat/.../mapping/PhotoAttachmentPolicyResolver.kt` | Resolve policy from override when `Mode.OTHER` |
| `feature/chat/.../ChatPreviews.kt` | Update previews for OTHER + sheet (no Hilt) |
| `feature/chat/src/main/res/values/strings.xml` | `mode_other` string |
| `feature/chat/.../di/ChatUseCasesModule.kt` | Bind `GetChatCapablePresetsUseCase` if needed |
| `feature/settings/.../ModelConfigurationScreen.kt` | Remove extracted composables; import from `:core:ui` |
| `feature/settings/.../SettingsModels.kt` | Remove moved UI models; re-import from `:core:ui` where needed |
| `feature/settings/.../SettingsUiMappers.kt` | Delegate to `:core:ui` mappers or thin wrappers |
| `feature/settings/.../SettingsScreen.kt` | Update imports for moved models |
| `feature/settings/.../ByokBottomSheet.kt` | Update imports |
| `feature/settings/.../ByokConfigureForms.kt` | Update imports |
| `feature/settings/.../ByokConfigureComponents.kt` | Update imports |
| `feature/settings/.../ByokConfigureScreen.kt` | Update imports |
| `feature/settings/.../ByokCustomHeadersScreen.kt` | Update imports |
| `feature/settings/.../ByokApiModelPricingSection.kt` | Update imports |
| `feature/settings/.../LocalModelsBottomSheet.kt` | Update imports |
| `feature/settings/.../SettingsLocalModelsDelegate.kt` | Update imports |
| `feature/settings/.../SettingsApiProvidersDelegate.kt` | Update imports |
| `feature/settings/.../SettingsUiStateAssembler.kt` | Update imports |
| `feature/settings/.../SettingsUiStateFactory.kt` | Update imports |
| `feature/settings/.../SettingsViewModel.kt` | Update imports |
| `feature/settings/.../MediaConfigureScreen.kt` | Update imports |
| `feature/settings/.../TtsConfigureScreen.kt` | Update imports |
| `feature/settings/.../TtsProvidersBottomSheet.kt` | Update imports |
| `feature/settings/.../TtsProvidersCoordinator.kt` | Update imports |
| `feature/settings/.../TtsProviderAssetUiMapper.kt` | Update imports if referencing moved types |
| `feature/settings/.../ApiProviderDiscoverySupport.kt` | Update imports |
| `feature/settings/.../ByokProvidersCoordinator.kt` | Update imports |
| All `feature/settings/src/test/**` files referencing moved types | Update imports (see test files in grep) |

---

## Ordered Implementation Tasks

Tasks are TDD-ordered: failing tests first, then production code. AndroJack validation (`mcp_androjack_android_code_validator`, minSdk 34, targetSdk 36) runs in **implementation phase only** — not during Red.

### Phase 1 — Domain Foundation

**Task 1.1** — Add `ChatModelOverride` sealed interface.  
Scenarios: **S04**, **S05**, **S06**

**Task 1.2** — Add `ChatResponseMode` sealed interface (`Fast` / `Thinking` / `Council` / `Other(override)`).  
Scenarios: **S06**, **E01**

**Task 1.3** — Add `Mode.OTHER` to domain enum (UI/persistence only).  
Scenarios: **S16**

**Task 1.4** — Write `ChatModeMapperTest` (feature:chat): `OTHER` ↔ `OTHER` exhaustive; compile fails until mapper updated.  
Scenarios: **S16**

**Task 1.5** — Update `ChatModeMapper` with `OTHER` branches.  
Scenarios: **S16**

**Task 1.6** — Write `ChatResponseModeMapperTest`: FAST/THINKING/COUNCIL map to slot variants with no override; OTHER + override maps to `ChatResponseMode.Other`; OTHER without override throws `IllegalArgumentException`.  
Scenarios: **E01**

**Task 1.7** — Implement `ChatResponseModeMapper`.  
Scenarios: **E01**

**Task 1.8** — Write `GetChatCapablePresetsUseCaseTest`: local image-gen assets excluded; API media-only configs excluded; empty catalog returns empty lists.  
Scenarios: **S15**, **S03**

**Task 1.9** — Implement `GetChatCapablePresetsUseCase`.  
Scenarios: **S15**, **S03**

**Task 1.10** — Extend `ActiveModelProviderPort` + write `ActiveModelProviderImplTest` for `getActiveConfigurationByOverride` (local id + api id).  
Scenarios: **S04**, **S05**, **S06**

**Task 1.11** — Implement `getActiveConfigurationByOverride` in `ActiveModelProviderImpl`.  
Scenarios: **S04**, **S05**, **S06**

### Phase 2 — Domain Inference Path

**Task 2.1** — Write `ChatResponseModeInferenceTest`: `responseMode=ChatResponseMode.Other(override)` resolves explicit config; `responseMode=ChatResponseMode.Fast` never reads override; `setDefaultModel` never involved.  
Scenarios: **S06**, **S07**, **MD01**

**Task 2.2** — Replace `mode: Mode` with `responseMode: ChatResponseMode` on `ChatUseCases.generateChatResponse` + `GenerateChatResponseUseCase`; implement exhaustive `executeResponseMode`.  
Scenarios: **S06**, **MD01**

### Phase 3 — `:core:ui` Extraction

**Task 3.1** — Create `AssignmentSelectionModels.kt` in `:core:ui`; move listed `@Immutable` data classes from `SettingsModels.kt`.  
Scenarios: **S14**

**Task 3.2** — Create `AssignmentSelectionUiMappers.kt`; move `LocalModelAssetUiMapper` + `ApiModelAssetUiMapper`.  
Scenarios: **S14**

**Task 3.3** — Write `AssignmentSelectionExtractionRegressionTest` (settings): `PreviewAssignmentSelectionContent` semantic structure unchanged (Local/API tab labels, Confirm button).  
Scenarios: **S14**

**Task 3.4** — Extract `AssignmentSelectionBottomSheet.kt` to `:core:ui`; delete moved code from `ModelConfigurationScreen.kt`; fix settings imports across ripple files.  
Scenarios: **S14**, **S03**

**Task 3.5** — Run `./gradlew :feature:settings:testDebugUnitTest` — all settings tests pass.  
Scenarios: **S14**

### Phase 4 — Chat Coordinator + Reducer

**Task 4.1** — Write `ChatInputCoordinatorOverrideTest`: override set on select; cleared on `onModeChange(FAST)`; cleared on `clearModelOverride`.  
Scenarios: **S04**, **S05**, **S08**, **S09**, **S10**, **S11**

**Task 4.2** — Implement `ChatInputCoordinator` override state.  
Scenarios: **S04**, **S05**, **S08**, **S09**, **S10**, **S11**

**Task 4.3** — Write `ChatUiStateReducerOverrideTest`: when `selectedMode=OTHER` + override displayName `"Grok Beta"`, `selectedModeModelName == "Grok Beta"`; `modeModelNames` omits OTHER key.  
Scenarios: **S12**, **S17**

**Task 4.4** — Update `ChatUiState`, `ReduceParams`, reducer logic.  
Scenarios: **S12**, **S17**

### Phase 5 — Chat UI + ViewModel

**Task 5.1** — Add `mode_other` string; add `ChatModeUi.OTHER`.  
Scenarios: **S01**, **S13**

**Task 5.2** — Write `ChatTopBarOtherEntryTest`: menu order Fast→Thinking→Council→Other>; Other click invokes `onOtherClick` exactly once and does **not** invoke `onModeChange`.  
Scenarios: **S01**, **S02**

**Task 5.3** — Update `ChatTopBar` with Other> item + `onOtherClick`.  
Scenarios: **S01**, **S02**, **S13**

**Task 5.4** — Update `ChatScreen` to host `AssignmentSelectionBottomSheet`; update `ChatRoute` callbacks.  
Scenarios: **S02**, **S03**, **S15**

**Task 5.5** — Wire `ChatViewModel`: presets flow, sheet visibility, `onModelOverrideSelected`, `createNewChat` clear, `InferenceOrchestrator` override threading.  
Scenarios: **S02**, **S03**, **S04**, **S05**, **S06**, **S07**, **S08**, **S12**

**Task 5.6** — Write `InferenceOrchestratorSendTest` extension: verify `generateChatResponse` receives `responseMode=ChatResponseMode.Other(ChatModelOverride.Api(configId="api-grok-beta", displayName="Grok Beta"))` — not `mode=Mode.FAST` with a sidecar override.  
Scenarios: **S06**, **MD01**

**Task 5.7** — Write `ChatViewModelActionsTest` extensions: new chat clears override; mode change clears override.  
Scenarios: **S08**, **S09**

### Phase 6 — Validation (Implementation Phase)

**Task 6.1** — AndroJack: validate all new/modified Compose code blocks (`mcp_androjack_android_code_validator`, minSdk 34, targetSdk 36). Fix until PASS.

**Task 6.2** — Run CI-aligned checks:
- `./gradlew :core:domain:testDebugUnitTest`
- `./gradlew :core:data:testDebugUnitTest`
- `./gradlew :core:ui:testDebugUnitTest`
- `./gradlew :feature:chat:testDebugUnitTest`
- `./gradlew :feature:settings:testDebugUnitTest`
- `./gradlew detekt`
- `./gradlew ktlintCheck`

---

## Drift and Escalation Rules

| Condition | Action |
|-----------|--------|
| Implementation calls `setDefaultModel` or `SettingsAssignmentsDelegate` from chat override path | **Blocker** — contract violation; fix before merge |
| `ChatResponseMode.Other` inference falls back to `ModelType.FAST` slot assignment | **Blocker** — fails **MD01** |
| `generateChatResponse` accepts `Mode` + optional `modelOverride` (parallel params) | **Blocker** — use `ChatResponseMode` only; invalid combos must be unrepresentable |
| Send invoked with `selectedMode=OTHER` and null override reaches `generateChatResponse` | **Blocker** — fails **E01**; must be blocked at `ChatResponseModeMapper` |
| New DB table/column for override state | **Blocker** — Schema Lock §3.4; requires explicit Contract Waiver |
| `:feature:chat` imports `:feature:settings` | **Blocker** — Clean Architecture violation |
| Extracted sheet missing Local/API tabs or Confirm/Cancel buttons | **Blocker** — fails **S14** |
| `ChatModeMapper` non-exhaustive after adding `OTHER` | **Blocker** — compile error; fix immediately |
| AndroJack FAIL on Compose output | Fix and re-validate before `TASK_STATUS: COMPLETE` |
| Spec behavior ambiguous during Red Review | Stop loop; escalate to human with spec section + scenario ID |
| Need to share additional settings-only helpers beyond ticket scope | Document in PR; do not expand without human approval |

---

## Schema Changes

**None.** Ephemeral override lives in `ChatInputCoordinator` `MutableStateFlow` only.