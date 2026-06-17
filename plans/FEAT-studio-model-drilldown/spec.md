# FEAT-studio-model-drilldown — Specification

Ticket: `FEAT-studio-model-drilldown`
Author: Synthitect Plan phase
Status: **Awaiting human approval (Gate)**

---

## 1. Feature Summary

The Studio screen's `StudioOptionsBottomSheet` gains an ephemeral, inline model-selection drilldown modeled on the chat "select any model" work (commit `637e0716`), but adapted for Studio's cross-process generation pipeline. At the top of the sheet, a `NeoNavRow` always displays the active model display name (or `"Default (<ModelType>)"`) with a trailing right chevron. Tapping it slides the existing settings content off to the left and slides a model list in from the right. The list shows every preset eligible for the **currently resolved `ModelType`** (e.g. `IMAGE_GENERATION` for text-to-image, `IMAGE_EDIT` when a reference image is attached, `VIDEO_GENERATION`, `IMAGE_TO_VIDEO`, `MUSIC_GENERATION`). Rows use `NeoSelectionCard`; the selected row shows the primary border. A `Confirm` button commits the selection, slides back to settings, and updates both the sheet header row and the top-bar subtitle. The selection is **ephemeral**: it drives the next generation and the displayed name, but it never calls `setDefault` and is cleared on media-type change, session clear, or ViewModel destroy.

Because Studio generation runs in a separate foreground Service process (`MultimediaGenerationService` in `:feature:studio-generation-service`) that re-resolves the model by slot inside `GenerateImage/Video/MusicUseCase`, the override must be **carried through the request** (`MediaGenerationRequest`) and the `ActiveStudioGeneration` DataStore payload so the Service resolves from the override instead of the default slot. This is the central architectural difference from chat, where inference runs inline in the ViewModel's process.

## 2. Confirmed Architecture (cross-process fact + transport decision)

| Concern | Fact (verified in code) | Decision |
|---|---|---|
| Where the model is resolved | `MultimediaGenerationService.onStartCommand` builds a `MediaGenerationRequest` via `MediaGenerationRequestMapper.fromStudio(...)` and calls `GenerateMediaUseCase`, which dispatches to `GenerateImage/Video/MusicUseCase`. Each use case resolves the model **itself** by `ModelType` slot (`ResolveAssignedImageGenerationTargetUseCase` / `ResolveAssignedMediaProviderUseCase` / `defaultModelRepository.getDefault(MUSIC_GENERATION)`). | Thread an optional `override: StudioModelOverride?` through `MediaGenerationRequest` → `GenerateMediaUseCase` → the three leaf use cases, and short-circuit slot resolution when non-null. |
| Intent boundary | `AndroidStudioGenerationDispatcher.startGeneration` packs prompt/mediaType/generationId/settings into an `Intent` via `StudioGenerationIntentCodec`. | Extend the codec (and `EXTRA_*` constants) to round-trip the override kind + id + displayName as optional string extras. `MultimediaGenerationService` reads them back into the `MediaGenerationRequest`. |
| Process-death survival | `ActiveStudioGeneration(id, prompt, mediaType, settings)` is serialized to DataStore JSON via `ActiveStudioGenerationCodec`. This is **not** a Room schema. | Extend `ActiveStudioGeneration` with `override: StudioModelOverride? = null` and the codec with an optional `override` JSON object. Decoding uses `optJSONObject`/`optString` → backward compatible with payloads written by older versions (override absent → `null`). |
| Ephemeral lifecycle | Chat stores its override in a `MutableStateFlow` in the ViewModel path; never persisted to defaults. | Mirror this: `StudioModelOverrideCoordinator` holds a `MutableStateFlow<StudioModelOverride?>`. Cleared on media-type change, `clearSession()`, and `onCleared()`. `setDefault` is **never** called from the override path. |
| Top-bar reflection | `MultimodalViewModel.activeModelFlow` derives `activeModelDisplayNameFlow` from defaults only. `StudioTopBar` already renders `uiState.activeModelDisplayName`. No top-bar signature change needed. | Make `activeModelDisplayNameFlow` prefer the override: `override?.displayName ?: assignment?.displayName`. Both the top bar subtitle and the sheet `NeoNavRow` consume the same flow. |

## 3. API Contracts

### 3.1 `StudioModelOverride` (NEW — `:core:domain`)

```kotlin
// core/domain/.../model/media/StudioModelOverride.kt
package com.browntowndev.enclave.domain.model.media

import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaProviderId

/**
 * Ephemeral per-generation model override selected from the Studio model drilldown.
 * Carried on [com.browntowndev.enclave.domain.model.media.MediaGenerationRequest];
 * never persisted to default-model slots (setDefault is never invoked).
 */
sealed interface StudioModelOverride {
    val displayName: String

    /** On-device stable-diffusion pack. Maps to [ImageGenerationTarget.LocalStableDiffusion]. */
    data class Local(
        val configId: LocalModelConfigurationId,
        override val displayName: String,
    ) : StudioModelOverride

    /** API/text-style config (reserved for parity; media-generation API configs route here). */
    data class Api(
        val configId: ApiModelConfigurationId,
        override val displayName: String,
    ) : StudioModelOverride

    /** Remote media provider (fal, etc.). Applies to image/video/music remote slots. */
    data class MediaProvider(
        val providerId: MediaProviderId,
        override val displayName: String,
    ) : StudioModelOverride
}
```

**Why a distinct type from `ChatModelOverride`:** `ChatModelOverride` has only `Local`/`Api` because chat inference resolves through `ActiveModelProviderPort.getActiveConfigurationByOverride(ChatModelOverride)`, which never crosses a media-provider id path and has no notion of `MediaTask`/`MediaCapability`. Media generation has three provider kinds — on-device diffusion packs (`LocalModelConfigurationId`), API-style configs (`ApiModelConfigurationId`), and remote media providers (`MediaProviderId`, e.g. fal) — and resolution must branch by `ImageGenerationTarget` vs `MediaProviderAsset`. Media also has no TTS/LiveVoice variants that chat's broader assignment surface carries. A dedicated sealed type keeps each variant's id aligned with exactly one resolution branch and avoids polluting `ChatModelOverride` with media-only cases.

### 3.2 `GetStudioCapablePresetsUseCase` (NEW — `:core:domain`)

```kotlin
// core/domain/.../usecase/media/GetStudioCapablePresetsUseCase.kt
data class StudioCapablePresets(
    val localAssets: List<LocalModelAsset>,
    val apiAssets: List<ApiModelAsset>,
    val mediaAssets: List<MediaProviderAsset>,
)

class GetStudioCapablePresetsUseCase @Inject constructor(
    private val localModelRepository: LocalModelRepositoryPort,
    private val apiModelRepository: ApiModelRepositoryPort,
    private val mediaProviderRepository: MediaProviderRepositoryPort,
) {
    /** Emits whenever any of the three catalogs change, filtered for [modelType]. */
    operator fun invoke(modelType: ModelType): Flow<StudioCapablePresets>
}
```

**Filter rules (precise):**

- `modelType.capability` = the `MediaCapability` of the slot (`IMAGE` for `IMAGE_GENERATION`/`IMAGE_EDIT`, `VIDEO` for `VIDEO_GENERATION`/`IMAGE_TO_VIDEO`, `MUSIC` for `MUSIC_GENERATION`). Derive via a small `ModelType.capability` extension or an internal `when`.
- **localAssets:** `localModelRepository.observeAllLocalAssets()` filtered to `asset.configurations.isNotEmpty() && asset.metadata.utilityType == null && LocalStableDiffusionModelPolicy.supports(asset)` — but **only** when `modelType` is `IMAGE_GENERATION` or `IMAGE_EDIT` (on-device diffusion is image-only). For video/music model types, `localAssets` is always empty.
- **apiAssets:** `GetChatCapablePresetsUseCase`-style grouping (`credentials.mapNotNull` → `ApiModelAsset(credentials, configurations)`). Text/multimodal API configs are **excluded** for media slots: keep only configs whose provider can serve the target capability. (Since the current Studio surface does not route media through `ApiModelConfiguration` for generation, `apiAssets` is populated but a selected API row only applies to the `StudioModelOverride.Api` variant; the resolution use case will reject it for non-image slots with a clear error. This keeps the picker honest about what the catalogs contain without inventing a new media-API config type.)
- **mediaAssets:** `mediaProviderRepository.getMediaProviders()` filtered by:
  - `provider.capability == modelType.capability`, AND
  - `tasks` contains the relevant `MediaTask` when one exists for the slot:
    - `IMAGE_GENERATION` → `MediaTask.TEXT_TO_IMAGE`
    - `IMAGE_EDIT` → `MediaTask.EDIT_IMAGE`
    - `VIDEO_GENERATION` → `MediaTask.TEXT_TO_VIDEO`
    - `IMAGE_TO_VIDEO` → `MediaTask.IMAGE_TO_VIDEO`
    - `MUSIC_GENERATION` → no `MediaTask` exists, so include all `MUSIC`-capability providers (tasks filter is skipped).
  - Use `Int.toMediaTasksOrLegacy(capability)` semantics already present in `MediaTask.kt` when the provider's tasks mask is absent, so legacy MUSIC/IMAGE/VIDEO providers remain selectable.

**Row → override mapping:**
- Local row (`LocalModelAsset` + chosen `LocalModelConfiguration`) → `StudioModelOverride.Local(configId = configuration.id, displayName = asset display label)`
- API row (`ApiModelAsset` + chosen `ApiModelConfiguration`) → `StudioModelOverride.Api(configId = configuration.id, displayName = configuration.displayName)`
- MediaProvider row (`MediaProviderAsset`) → `StudioModelOverride.MediaProvider(providerId = asset.id, displayName = asset.displayName)`

### 3.3 `MediaGenerationRequest` extension (MODIFY — `:core:domain`)

```kotlin
data class MediaGenerationRequest(
    val prompt: String,
    val capability: MediaCapability,
    val settings: GenerationSettings,
    val origin: MediaGenerationOrigin,
    val override: StudioModelOverride? = null,   // NEW (default null = unchanged behavior)
)
```

`MediaGenerationRequestMapper.fromStudio` gains an `override: StudioModelOverride? = null` parameter threaded into the built request. `fromToolCall` leaves `override = null` (chat-tool origin never carries a studio override).

### 3.4 `ActiveStudioGeneration` extension (MODIFY — `:core:domain`)

```kotlin
data class ActiveStudioGeneration(
    val id: String,
    val prompt: String,
    val mediaType: MediaCapability,
    val settings: GenerationSettings,
    val override: StudioModelOverride? = null,   // NEW
)
```

### 3.5 Resolution use-case extensions (MODIFY — `:core:domain`)

Add an optional `override` parameter to the three resolution entry points so leaf use cases short-circuit slot lookup when an override is present:

- `ResolveAssignedImageGenerationTargetUseCase.invoke(modelType, override: StudioModelOverride? = null)`:
  - If `override is StudioModelOverride.Local` → `localStableDiffusionTargetResolver.resolve(override.configId)` (same path as a local default).
  - If `override is StudioModelOverride.MediaProvider` → `ImageGenerationTarget.Remote(providerAssetId = override.providerId, providerDisplayName = override.displayName)`.
  - If `override is StudioModelOverride.Api` → `Result.failure(IllegalArgumentException("API configuration overrides are not supported for image generation."))` (image generation has no API-config execution path; keep the variant for catalog symmetry but fail fast).
  - If `override == null` → existing slot behavior unchanged.
- `ResolveAssignedMediaProviderUseCase.invoke(modelType, override: StudioModelOverride? = null)` (used by video):
  - If `override is StudioModelOverride.MediaProvider` → `mediaProviderRepository.getMediaProvider(override.providerId)` returning `Result.success` (or failure if not found).
  - Else (`null`, `Local`, or `Api`) → existing default-slot behavior.
- `GenerateMusicUseCase.invoke(prompt, settings, override: StudioModelOverride? = null)`:
  - If `override is StudioModelProvider.MediaProvider` → resolve `mediaProviderRepository.getMediaProvider(override.providerId)` instead of `defaultModelRepository.getDefault(MUSIC_GENERATION)`.
  - Else → existing default-slot behavior.

The leaf use cases' public signatures gain the same optional parameter and forward it to their resolver. `GenerateMediaUseCase` forwards `request.override` into each leaf call. **This is the load-bearing change that prevents the mutation-defense failure mode** (a lazy impl that ignores the override and reads the default slot must fail MD01).

### 3.6 `StudioModelOverrideCoordinator` (NEW — `:feature:studio`)

```kotlin
// feature/studio/.../coordinators/StudioModelOverrideCoordinator.kt
class StudioModelOverrideCoordinator @Inject constructor() {
    val override = MutableStateFlow<StudioModelOverride?>(null)        // current committed override
    val isModelPickerVisible = MutableStateFlow(false)                 // drilldown open?
    val pendingSelection = MutableStateFlow<StudioModelOverride?>(null)// staged-but-unconfirmed row
    val pickerModelType = MutableStateFlow<ModelType?>(null)           // ModelType the list was opened for

    fun openPicker(modelType: ModelType, current: StudioModelOverride?) { /* set visible, seed pending=current, store modelType */ }
    fun stageSelection(selection: StudioModelOverride) { /* pendingSelection = selection */ }
    fun confirmSelection() { /* override = pendingSelection; isModelPickerVisible = false */ }
    fun cancelPicker() { /* pendingSelection = null; isModelPickerVisible = false */ }
    fun clearForCapabilityChange() { /* override = null; pendingSelection = null; isModelPickerVisible = false */ }
    fun clearSession() { /* override = null; pendingSelection = null; isModelPickerVisible = false; pickerModelType = null */ }
}
```

**Lifecycle invariants:** `clearForCapabilityChange()` is called from `MultimodalViewModel.onMediaTypeChange` before delegating to `promptCoordinator.onMediaTypeChange`. `clearSession()` is called from `MultimodalViewModel.clearSession`. The `override` flow is combined into `activeModelDisplayNameFlow` (see 3.8). `setDefault` is never invoked anywhere in this coordinator.

### 3.7 `StudioUiState` new fields (MODIFY — `:feature:studio`)

```kotlin
val modelOverride: StudioModelOverride? = null,
val modelPickerAssets: StudioCapablePresetsUi = StudioCapablePresetsUi(),  // presentation model
val isModelPickerVisible: Boolean = false,
val selectedModelIdInPicker: String? = null,   // id of the row currently staged (for primary-border highlight)
```

`StudioCapablePresetsUi` is an `@Immutable` presentation model in `:feature:studio` (lists of `StudioModelOptionUi(id, title, subtitle, override)` where `override` is the `StudioModelOverride` to commit on confirm). The ViewModel maps `StudioCapablePresets` → `StudioCapablePresetsUi`.

To comply strictly with ARCHITECTURE_RULES §3.3 ("Domain models MUST NOT be passed directly to composables" — interpreted as entity/repository models; `StudioModelOverride` is a value-only request DTO of value-class ids + a String), **preferred form:** `StudioModelOptionUi` carries `kind: StudioOverrideKind` + `id: String` + `displayName: String` rather than the domain sealed type, and the ViewModel reconstructs the `StudioModelOverride` on confirm. This avoids any ambiguity.

`StudioUiStateReducer.ReduceParams` gains matching fields (`modelOverride`, `modelPickerAssets`, `isModelPickerVisible`, `selectedModelIdInPicker`) and `reduce(...)` copies them through.

### 3.8 `MultimodalViewModel` changes (MODIFY — `:feature:studio`)

- Inject `StudioModelOverrideCoordinator` and `GetStudioCapablePresetsUseCase`.
- Replace `activeModelDisplayNameFlow`:
  ```kotlin
  private val activeModelDisplayNameFlow =
      combine(overrideCoordinator.override, activeModelFlow) { override, assignment ->
          override?.displayName ?: assignment?.displayName
      }.distinctUntilChanged()
  ```
- Add `modelPickerAssetsFlow = overrideCoordinator.pickerModelType.flatMapLatest { modelType -> if (modelType != null) getStudioCapablePresetsUseCase(modelType) else flowOf(StudioCapablePresetsUi.EMPTY) }` mapped to UI.
- Combine `overrideCoordinator.override`, `.isModelPickerVisible`, `.pendingSelection`, and `modelPickerAssetsFlow` into a new `overrideInputsFlow` and fold into `StudioUiStateReducer.ReduceParams`.
- `onMediaTypeChange(type)`: call `overrideCoordinator.clearForCapabilityChange()` **before** `promptCoordinator.onMediaTypeChange(type)`.
- `clearSession()`: call `overrideCoordinator.clearSession()`.
- `generate()`: pass `overrideCoordinator.override.value` through `generationCoordinator.beginGenerationAttempt(...)` (new param) so it reaches `startGeneration` → `ActiveStudioGeneration` → dispatcher → Service → request → leaf use cases.
- New handlers: `onOpenModelPicker()`, `onStageModelSelection(override)`, `onConfirmModelSelection()`, `onCancelModelPicker()` delegating to the coordinator. `onOpenModelPicker` seeds `pickerModelType` with the currently resolved `ModelType` (recomputed via `resolveMediaModelTypeUseCase(mediaType, settings)` so a reference-image attachment flips it to `IMAGE_EDIT`/`IMAGE_TO_VIDEO`).
- `onCleared()`: call `overrideCoordinator.clearSession()`.

### 3.9 `StudioGenerationCoordinator` changes (MODIFY — `:feature:studio`)

- `startGeneration(prompt, mediaType, settings, override: StudioModelOverride? = null)`: build `ActiveStudioGeneration(..., override = override)`.
- `beginGenerationAttempt(...)`: accept and forward `override`.
- `onGenerativeScrollThresholdVisible(...)` continual path: forward the last override (store it alongside `_lastSubmittedSettings`).

### 3.10 `StudioGenerationDispatcherPort` + `AndroidStudioGenerationDispatcher` (MODIFY)

- Port: `startGeneration(prompt, mediaType, settings, generationId, override: StudioModelOverride? = null)`.
- Android impl: when `override != null`, write `EXTRA_OVERRIDE_KIND` + `EXTRA_OVERRIDE_ID` + `EXTRA_OVERRIDE_DISPLAY_NAME` via a new `StudioGenerationIntentCodec.writeOverride(intent, override)` helper.

### 3.11 `StudioGenerationIntentCodec` (MODIFY — `:feature:studio-generation-service`)

- `writeOverride(intent, override)`: kind ∈ {`local`,`api`,`media_provider`} + id string + displayName.
- `readOverride(intent): StudioModelOverride?`: returns null when `EXTRA_OVERRIDE_KIND` absent (backward compatible).

### 3.12 `MultimediaGenerationService` (MODIFY — `:feature:studio-generation-service`)

- Read `StudioGenerationIntentCodec.readOverride(intent)` and pass into `MediaGenerationRequestMapper.fromStudio(prompt, capability, settings, override)`.

### 3.13 `StudioOptionsSheetContent` + `StudioOptionsBottomSheet` (MODIFY — `:feature:studio`)

New callbacks threaded through both signatures:

```kotlin
fun StudioOptionsBottomSheet(
    state: StudioUiState,
    onDismiss: () -> Unit,
    onUpdateSettings: (GenerationSettings) -> Unit,
    onContinualModeChange: (Boolean) -> Unit,
    onMediaTypeChange: (MediaCapability) -> Unit,
    onTemplateSelected: (StudioTemplate) -> Unit,
    onOpenModelPicker: () -> Unit,                 // NEW
    onStageModelSelection: (StudioModelOptionUi) -> Unit,  // NEW
    onConfirmModelSelection: () -> Unit,           // NEW
    onCancelModelPicker: () -> Unit,               // NEW
    modifier: Modifier = Modifier,
)
```

`StudioOptionsSheetContent` restructures to:

```kotlin
Column(...) {
    NeoSheetHeader(title = "Studio Settings", onDismiss = onDismiss)

    // Always-visible model row at the top of the sheet.
    NeoNavRow(
        title = state.activeModelDisplayName ?: "Default",
        subtitle = state.resolvedModelTypeLabel,   // e.g. "Image Generation"
        onClick = onOpenModelPicker,
    )
    Spacer(...)

    AnimatedContent(
        targetState = if (state.isModelPickerVisible) StudioSheetView.ModelPicker else StudioSheetView.Settings,
        transitionSpec = {
            if (targetState == StudioSheetView.ModelPicker) {
                // settings out to left, picker in from right
                (slideOutHorizontally { -it } togetherWith slideInHorizontally { it })
            } else {
                // picker out to right, settings in from left
                (slideOutHorizontally { it } togetherWith slideInHorizontally { -it })
            } using SizeTransform(clip = false)
        },
        contentKey = { it },
    ) { view ->
        when (view) {
            StudioSheetView.Settings -> SettingsPane(...)   // existing segmented control + per-media sections
            StudioSheetView.ModelPicker -> ModelPickerPane(
                assets = state.modelPickerAssets,
                selectedId = state.selectedModelIdInPicker,
                onStageSelection = onStageModelSelection,
                onConfirm = onConfirmModelSelection,
                onCancel = onCancelModelPicker,
            )
        }
    }
}
```

`ModelPickerPane` renders three optional sections (Local / API / MediaProvider) each as a `LazyColumn`-or-`Column` of `NeoSelectionCard(title, subtitle, isSelected = card.id == selectedId, onClick = { onStageSelection(card) })`, followed by a footer row of `NeoSecondaryButton("Cancel", onCancel)` + `NeoPrimaryButton("Confirm", onConfirm)`. Empty state: if all three lists are empty, show a centered `"No models available for <ModelType>."` message and disable Confirm.

`StudioSheetView` is a private `enum class { Settings, ModelPicker }`.

**Animation note:** the user explicitly requested a slide (content off-left / in-from-right), not the `AnimatedContent` fade used by `AssignmentSelectionBottomSheet`. The `transitionSpec` above implements exactly that. Back navigation (Confirm or Cancel) reverses the direction.

### 3.14 `StudioTopBar` (NO signature change)

Already renders `uiState.activeModelDisplayName` as the subtitle. Because `activeModelDisplayNameFlow` now prefers the override, the top bar automatically reflects the selection. No code change required in `StudioTopBar` itself; document this in the implementation task as a verification step only.

## 4. File Structure

| Action | Module | Path (relative) | Responsibility |
|---|---|---|---|
| Create | `:core:domain` | `.../model/media/StudioModelOverride.kt` | Sealed override type (Local/Api/MediaProvider). |
| Create | `:core:domain` | `.../usecase/media/GetStudioCapablePresetsUseCase.kt` | Emits filtered Local/Api/MediaProvider catalogs for a `ModelType`. |
| Modify | `:core:domain` | `.../model/media/MediaGenerationRequest.kt` | Add `override: StudioModelOverride? = null`. |
| Modify | `:core:domain` | `.../usecase/media/MediaGenerationRequestMapper.kt` | `fromStudio` accepts + threads override. |
| Modify | `:core:domain` | `.../usecase/media/ResolveAssignedImageGenerationTargetUseCase.kt` | Accept optional override; branch Local/MediaProvider. |
| Modify | `:core:domain` | `.../usecase/media/ResolveAssignedMediaProviderUseCase.kt` | Accept optional override; branch MediaProvider. |
| Modify | `:core:domain` | `.../usecase/media/GenerateImageUseCase.kt` | Forward override to target resolver. |
| Modify | `:core:domain` | `.../usecase/media/GenerateVideoUseCase.kt` | Forward override to provider resolver. |
| Modify | `:core:domain` | `.../usecase/media/GenerateMusicUseCase.kt` | Accept override; branch MediaProvider. |
| Modify | `:core:domain` | `.../usecase/media/GenerateMediaUseCase.kt` | Forward `request.override` to leaf use cases (incl. incremental-image path). |
| Modify | `:core:domain` | `.../port/repository/StudioRepositoryPort.kt` | Add `override` field to `ActiveStudioGeneration`. |
| Modify | `:core:data` | `.../repository/ActiveStudioGenerationCodec.kt` | Encode/decode optional override JSON object. |
| Create | `:feature:studio` | `.../coordinators/StudioModelOverrideCoordinator.kt` | Ephemeral override + picker state. |
| Modify | `:feature:studio` | `.../StudioUiState.kt` | Add `modelOverride`, `modelPickerAssets`, `isModelPickerVisible`, `selectedModelIdInPicker`, `resolvedModelTypeLabel`. |
| Modify | `:feature:studio` | `.../coordinators/StudioUiStateReducer.kt` | Thread new fields through `ReduceParams` + `reduce`. |
| Modify | `:feature:studio` | `.../coordinators/MultimodalViewModelInputs.kt` | Add override/picker input bundle. |
| Modify | `:feature:studio` | `.../MultimodalViewModel.kt` | Inject coordinator + use case; prefer override in display flow; wire handlers; pass override into generation. |
| Modify | `:feature:studio` | `.../coordinators/StudioPromptCoordinator.kt` | No change (media-type clear is driven from ViewModel to keep ordering explicit). |
| Modify | `:feature:studio` | `.../coordinators/StudioGenerationCoordinator.kt` | `startGeneration`/`beginGenerationAttempt`/continual path accept + forward override. |
| Modify | `:core:domain` | `.../port/media/StudioGenerationDispatcherPort.kt` | `startGeneration` accepts optional override. |
| Modify | `:feature:studio` | `.../service/AndroidStudioGenerationDispatcher.kt` | Write override extras. |
| Modify | `:feature:studio-generation-service` | `.../service/StudioGenerationIntentCodec.kt` | `writeOverride`/`readOverride`. |
| Modify | `:feature:studio-generation-service` | `.../service/MultimediaGenerationService.kt` | Read override; pass to mapper. |
| Modify | `:feature:studio` | `.../components/StudioComponents.kt` | `StudioOptionsBottomSheet`/`StudioOptionsSheetContent` new params; `NeoNavRow` header; `AnimatedContent` slide; `ModelPickerPane`; previews. |
| Create | `:feature:studio` | `.../components/StudioModelPicker.kt` | `ModelPickerPane`, `StudioSheetView`, `StudioCapablePresetsUi`, `StudioModelOptionUi` presentation models (if not colocated in StudioComponents). |
| Modify | `:feature:studio` | `.../di/StudioModule.kt` | Bind `StudioModelOverrideCoordinator` (Hilt) if not discovered via `@Inject constructor`. |
| No change | `:feature:studio` | `.../StudioScreen.kt` (`StudioTopBar`) | Verify only — override flows through `activeModelDisplayName`. Wire new callbacks from `StudioScreen` → `StudioOptionsBottomSheet`. |

## 5. Ordered TDD Implementation Tasks (Red first)

Each task lists the scenario IDs it satisfies (see `test_spec.md`). Red = write/extend the test first, watch it fail, then implement.

1. **[Red→Green] `StudioModelOverride` type + equality.** Create the sealed interface. Test: `StudioModelOverrideLocalTest` constructs each variant and asserts `displayName` + id equality. (Covers S01.)
2. **[Red→Green] `GetStudioCapablePresetsUseCase`.** Test with fakes for the three repositories. Assert: IMAGE_GENERATION returns local diffusion assets (utilityType null + `LocalStableDiffusionModelPolicy.supports`) + media providers whose `tasks` contains `TEXT_TO_IMAGE`; IMAGE_EDIT filters `EDIT_IMAGE`; VIDEO_GENERATION filters `TEXT_TO_VIDEO`; IMAGE_TO_VIDEO filters `IMAGE_TO_VIDEO`; MUSIC_GENERATION returns all MUSIC-capable providers (no task filter), empty local list. (Covers S07, E05.)
3. **[Red→Green] `ResolveAssignedImageGenerationTargetUseCase` override branch.** Test: `Local` override → resolves via `LocalStableDiffusionTargetResolver` and never reads `defaultModelRepository.getDefault`; `MediaProvider` override → `ImageGenerationTarget.Remote` with the override's id/name; `Api` override → `Result.failure` with the documented message; `null` → existing behavior. (Covers S05, MD01.)
4. **[Red→Green] `ResolveAssignedMediaProviderUseCase` override branch.** Test: `MediaProvider` override returns the provider without reading defaults; other variants fall through to defaults. (Covers S05, MD01.)
5. **[Red→Green] `GenerateMusicUseCase` override branch.** Test: `MediaProvider` override resolves the provider and skips `defaultModelRepository.getDefault(MUSIC_GENERATION)`. (Covers S05, MD01.)
6. **[Red→Green] `GenerateMediaUseCase` threads override.** Test: with an override set on the request and IMAGE capability, the leaf image use case receives the override (verify via a spy/fake leaf that records the override arg); without override, behavior is unchanged. (Covers S04.)
7. **[Red→Green] `MediaGenerationRequestMapper.fromStudio` override threading.** Test: override round-trips into the built request; `fromToolCall` always leaves `override == null`. (Covers S04.)
8. **[Red→Green] `ActiveStudioGenerationCodec` override round-trip + backward compatibility.** Test: encode→decode preserves a `MediaProvider` override; decoding a JSON string that omits the `override` key yields `override == null` (simulates an older-version payload). (Covers E06.)
9. **[Red→Green] `StudioGenerationIntentCodec` override round-trip + absence.** Test (Robolectric if needed for `Intent` extras): writeOverride then readOverride returns the same variant; readOverride on an intent without extras returns null. (Covers S04, E06.)
10. **[Red→Green] `StudioModelOverrideCoordinator`.** Test with Turbine: `openPicker` sets visibility + seeds pending; `stageSelection` updates pending only; `confirmSelection` commits to `override` and hides picker; `cancelPicker` clears pending and hides; `clearForCapabilityChange`/`clearSession` reset `override` to null. (Covers S02, S03, S08, S09, S10.)
11. **[Red→Green] `MultimodalViewModel` display-name prefers override.** Test with fakes: set override → `uiState.activeModelDisplayName == override.displayName`; clear → falls back to default assignment displayName. (Covers S01, S02.)
12. **[Red→Green] `MultimodalViewModel` clears override on media-type change and session clear.** Test: set override, call `onMediaTypeChange(VIDEO)` → override is null; reset, call `clearSession()` → override is null. (Covers S03, S10.)
13. **[Red→Green] `MultimodalViewModel.generate()` carries override through the coordinator.** Test with a fake `StudioGenerationDispatcherPort` that captures the last `startGeneration` args: after setting an override and calling `generate()`, the captured override equals the committed override; the `ActiveStudioGeneration` reported to the fake `StudioRepositoryPort` also carries it. (Covers S04, MD01.)
14. **[Red→Green] `StudioGenerationCoordinator` continual path forwards override.** Test: continual-mode re-generation uses the stored override. (Covers S04 edge.)
15. **[Red→Green] Picker filter reflects resolved ModelType with reference image.** Test: attach a reference image (IMAGE) → `onOpenModelPicker` resolves `IMAGE_EDIT` and the assets flow emits only `EDIT_IMAGE` providers. (Covers S06.)
16. **[Green] UI: `StudioOptionsSheetContent` + `ModelPickerPane` + slide `AnimatedContent`.** Compose UI test (`createComposeRule`) is optional per contracts; prefer a state-based unit test on the reducer proving `isModelPickerVisible`/`selectedModelIdInPicker` round-trip, plus `@Preview` additions (Light/Dark, settings vs picker, empty catalog). AndroJack validates generated Compose (AGENTS §3.2) during this task. (Covers S08, S09, E05; visual verification via previews.)
17. **[Green] Wire `StudioScreen` callbacks + verify top-bar reflection.** No new test; manual/preview verification that `uiState.activeModelDisplayName` propagates to `StudioTopBar` with no signature change. (Covers S01 visual.)

## 6. Drift / Escalation Rules (Blockers)

- **`DefaultModelRepositoryPort.setDefault(...)` invoked from any override path** → **Blocker.** The override is ephemeral and must never mutate the persisted default slot. (Violates ticket intent + Schema Lock spirit.)
- **Override silently dropped at any boundary** (ViewModel→coordinator, coordinator→`ActiveStudioGeneration`, `ActiveStudioGeneration`→dispatcher, dispatcher→Intent extras, Service→`MediaGenerationRequest`, request→leaf use case, leaf use case→resolver) → **Blocker.** Each hop has a test (S04 / MD01).
- **New Room table or column** → **Blocker.** The only persistence change is the `ActiveStudioGeneration` DataStore JSON payload (not a Room schema). (AGENTS §3.4 / DATA_LAYER_RULES §1.)
- **Domain API leaking Android/Compose/Room/Hilt types** in `StudioModelOverride`, `GetStudioCapablePresetsUseCase`, or the resolution-use-case signatures → **Blocker.** (ARCHITECTURE_RULES §1.2.)
- **`NavController` passed into `StudioOptionsSheetContent`/`ModelPickerPane`** → **Blocker.** Drilldown is in-sheet via state, not navigation. (ARCHITECTURE_RULES §4.)
- **`collectAsState()` / `GlobalScope` / `runBlocking`** in the new UI/ViewModel code → **Blocker.** Use `collectAsStateWithLifecycle()`, `viewModelScope`, `runTest`. (CODE_STYLE_RULES §5.)
- **Mutation-defense regression**: a leaf use case that ignores a non-null override and reads the default slot → **Blocker** (caught by MD01).
- **Backward-incompatible codec change**: decoding an old `ActiveStudioGeneration` payload (no `override` key) must yield `override == null`, not throw. A `JSONException` on absent key → **Blocker** (use `optJSONObject`/`optString`).

## 7. Schema Changes

**No Room schema changes.** (AGENTS §3.4 / DATA_LAYER_RULES §1 honored.)

`ActiveStudioGeneration` is a DataStore JSON payload (not a Room entity). Its codec gains an optional `override` JSON object with shape:

```json
{
  "id": "...",
  "prompt": "...",
  "mediaType": "IMAGE",
  "settingsKind": "image",
  "...settings fields...": "...",
  "override": { "kind": "media_provider", "id": "...", "displayName": "..." }
}
```

Decoding uses `optJSONObject("override")`; when absent (payload written by a prior version), `override` resolves to `null`. This is backward compatible. The `StudioGenerationIntentCodec` follows the same optional-extra convention (`EXTRA_OVERRIDE_KIND` absent → `null`).

---

*Constitution references: AGENTS.md §3.1 (Clean Architecture), §3.2 (AndroJack during Green), §3.3 (test coverage), §3.4 (Schema Lock); contracts ARCHITECTURE_RULES.md, CODE_STYLE_RULES.md, DATA_LAYER_RULES.md, INFERENCE_RULES.md, UI_DESIGN_SPEC.md, UNIT_TESTING.md.*
