# FEAT-studio-model-drilldown — Test Specification

Ticket: `FEAT-studio-model-drilldown`

## 1. Framework & Conventions

- **Stack:** JUnit 5 (`org.junit.jupiter.api`) + Turbine (Flow/StateFlow) + MockK (only where a Fake is impractical, e.g. `Intent` extras via Robolectric). Fakes preferred per `UNIT_TESTING.md` §1.
- **Dispatcher:** `StandardTestDispatcher` + `runTest`; JUnit 5 `MainDispatcherExtension` for ViewModel tests.
- **Assertions:** State-based (assert on `StateFlow` values / `Result` payloads), never on internal method calls except where the contract is the boundary (e.g. a fake dispatcher recording the last `startGeneration` args).
- **Never** mock `Context`/`Intent`/`Bundle` directly — use Robolectric's real `Intent` for the codec test.

### Run commands

```bash
# Domain logic (override type, use cases, codec, request mapper)
./gradlew :core:domain:testDebugUnitTest

# Data-layer codec (if ActiveStudioGenerationCodec tests live in :core:data)
./gradlew :core:data:testDebugUnitTest

# ViewModel + coordinator + reducer (ephemeral state, display-name, generation threading)
./gradlew :feature:studio:testDebugUnitTest

# Intent codec + service wiring
./gradlew :feature:studio-generation-service:testDebugUnitTest

# Merge-sized sweep
./gradlew testDebugUnitTest
```

Narrow iteration examples:
```bash
./gradlew :core:domain:testDebugUnitTest --tests "*GetStudioCapablePresetsUseCaseTest*"
./gradlew :feature:studio:testDebugUnitTest --tests "*StudioModelOverrideCoordinatorTest*"
```

## 2. Test Doubles Required

- `FakeLocalModelRepository` (extends `LocalModelRepositoryPort`) with a configurable `MutableStateFlow<List<LocalModelAsset>>` and a `getAssetByConfigId` map for the local-target resolver.
- `FakeApiModelRepository` with `MutableStateFlow` for credentials + configurations.
- `FakeMediaProviderRepository` with a `MutableStateFlow<List<MediaProviderAsset>>` and `getMediaProvider(id)` lookup.
- `FakeDefaultModelRepository` (already exists in `:core:domain` test fixtures — extend if needed) — **asserted-not-called** in override scenarios via a `calledSlotFor: MutableSet<ModelType>` recorder.
- `FakeStudioGenerationDispatcher` implementing `StudioGenerationDispatcherPort`, recording `lastStarted: StartedArgs?` where `StartedArgs(prompt, mediaType, settings, generationId, override)`.
- `FakeStudioRepository` (already exists) — extend to record `lastActiveGeneration` including the new `override` field.
- `FakeLocalStableDiffusionTargetResolver` (or MockK of `LocalStableDiffusionTargetResolver`) returning a canned `ImageGenerationTarget.LocalStableDiffusion`.
- Robolectric `@ExtendWith`/`@RunWith` only for `StudioGenerationIntentCodecTest` (needs real `Intent` extras).

## 3. Scenario Index

### Core behavior (S-series)
- **S01** Override display name reflected in top bar + sheet header
- **S02** Setting an override updates display; clearing restores default
- **S03** Override cleared on media-type change
- **S04** Override carried through every boundary into the generation request
- **S05** Leaf use cases resolve from override instead of default slot
- **S06** Picker filters by resolved ModelType (reference image → IMAGE_EDIT)
- **S07** Catalog use case filters presets per ModelType + MediaTask
- **S08** Selecting a row marks it primary (selected); Confirm commits
- **S09** Back/Cancel discards staged selection and returns to Settings view
- **S10** Override cleared on `clearSession()`

### Edge cases (E-series)
- **E01** Confirm disabled when no row staged
- **E02** Confirm re-confirmation is idempotent (no duplicate state emissions)
- **E03** Opening picker twice resets pending to current committed override
- **E04** Staging a different row replaces the prior staged selection
- **E05** Empty catalog shows "No models available for <ModelType>." and disables Confirm
- **E06** Backward-compatible decode: old `ActiveStudioGeneration` payload (no `override` key) → `override == null`; old `Intent` (no override extras) → `readOverride() == null`

### Mutation defense (MD-series)
- **MD01** When `override != null`, the leaf use case MUST NOT read the default slot (`defaultModelRepository.getDefault`/`ResolveAssigned...` default path). A lazy impl that ignores the override fails this scenario.

---

## 4. Detailed Scenarios

### S01 — Override display name reflected in top bar + sheet header
**Given** a `MultimodalViewModel` with a default `IMAGE_GENERATION` assignment whose `displayName == "Default Image"`.
**When** `overrideCoordinator.override.value = StudioModelOverride.MediaProvider(MediaProviderId("fal-1"), "Fal Aura")`.
**Then** `viewModel.uiState.value.activeModelDisplayName == "Fal Aura"` (Turbine `awaitItem`), and the sheet `NeoNavRow` title equals `"Fal Aura"`. The `StudioTopBar` subtitle equals `"Fal Aura"` with no signature change to `StudioTopBar`.

### S02 — Setting an override updates display; clearing restores default
**Given** override set as in S01.
**When** `overrideCoordinator.override.value = null`.
**Then** `viewModel.uiState.value.activeModelDisplayName == "Default Image"` (the default assignment's displayName).

### S03 — Override cleared on media-type change
**Given** override set (`MediaProvider`, capability IMAGE) and `uiState.mediaType == IMAGE`.
**When** `viewModel.onMediaTypeChange(MediaCapability.VIDEO)`.
**Then** `overrideCoordinator.override.value == null`, `overrideCoordinator.isModelPickerVisible.value == false`, and `uiState.value.activeModelDisplayName` reflects the VIDEO default (not the stale override).

### S04 — Override carried through every boundary into the generation request
**Given** override committed (`MediaProvider(MediaProviderId("fal-1"), "Fal Aura")`), `mediaType == IMAGE`, a valid prompt, and `FakeStudioGenerationDispatcher` + `FakeStudioRepository` injected.
**When** `viewModel.generate()`.
**Then** all of the following hold:
1. `FakeStudioGenerationDispatcher.lastStarted.override == StudioModelOverride.MediaProvider(MediaProviderId("fal-1"), "Fal Aura")`.
2. `FakeStudioRepository.lastActiveGeneration.override == StudioModelOverride.MediaProvider(MediaProviderId("fal-1"), "Fal Aura")`.
3. `MediaGenerationRequestMapper.fromStudio(prompt, IMAGE, settings, override).override == <the same override>`.
4. `StudioGenerationIntentCodec.readOverride(intent)` after `writeOverride(intent, override)` returns an equal override.
5. `MultimediaGenerationService` path: the `MediaGenerationRequest` passed to `GenerateMediaUseCase` has `override == <the same override>` (verified via a fake `GenerateMediaUseCase` or by inspecting the request in `GenerateMediaUseCaseTest`).

### S05 — Leaf use cases resolve from override instead of default slot
**Sub-scenario S05a (image, Local override):**
**Given** `GenerateImageUseCase` with `FakeDefaultModelRepository` (records `getDefault` calls) and `FakeLocalStableDiffusionTargetResolver`.
**When** `invoke(prompt, settings, override = StudioModelOverride.Local(configId, "My SD Pack"))`.
**Then** `FakeLocalStableDiffusionTargetResolver.lastConfigId == configId`, and `FakeDefaultModelRepository.getDefaultCalls` does **not** contain `IMAGE_GENERATION`. The returned `Result` is success with `ImageGenerationTarget.LocalStableDiffusion`.

**Sub-scenario S05b (image, MediaProvider override):**
**When** `invoke(prompt, settings, override = StudioModelOverride.MediaProvider(MediaProviderId("fal-1"), "Fal Aura"))`.
**Then** `imageGenerationPort` receives `ImageGenerationTarget.Remote(providerAssetId = MediaProviderId("fal-1"), providerDisplayName = "Fal Aura")`; `FakeDefaultModelRepository.getDefaultCalls` does not contain `IMAGE_GENERATION`.

**Sub-scenario S05c (image, Api override):**
**When** `invoke(prompt, settings, override = StudioModelOverride.Api(apiConfigId, "GPT-Image"))`.
**Then** the result is `Result.failure` whose exception is `IllegalArgumentException` with message exactly `"API configuration overrides are not supported for image generation."`.

**Sub-scenario S05d (video, MediaProvider override):**
**Given** `GenerateVideoUseCase` with fakes.
**When** `invoke(prompt, settings)` is reached via `GenerateMediaUseCase` with `request.override == StudioModelOverride.MediaProvider(MediaProviderId("fal-v1"), "Fal Video")`.
**Then** `videoGenerationPort` receives `MediaProviderAsset(id = MediaProviderId("fal-v1"), ...)`; `FakeDefaultModelRepository.getDefaultCalls` does not contain `VIDEO_GENERATION`.

**Sub-scenario S05e (music, MediaProvider override):**
**Given** `GenerateMusicUseCase` with fakes.
**When** `invoke(prompt, settings, override = StudioModelOverride.MediaProvider(MediaProviderId("fal-m1"), "Fal Music"))`.
**Then** `musicGenerationPort` receives the provider with `id == MediaProviderId("fal-m1")`; `FakeDefaultModelRepository.getDefaultCalls` does not contain `MUSIC_GENERATION`.

### S06 — Picker filters by resolved ModelType (reference image → IMAGE_EDIT)
**Given** `mediaType == IMAGE` and `settings.referenceImageUri == "content://.../ref.jpg"` (so `resolveMediaModelTypeUseCase(IMAGE, settings) == IMAGE_EDIT`).
**When** `viewModel.onOpenModelPicker()`.
**Then** `overrideCoordinator.pickerModelType.value == ModelType.IMAGE_EDIT`, and `modelPickerAssetsFlow` emits a `StudioCapablePresetsUi` whose `mediaAssets` contains **only** providers with `MediaTask.EDIT_IMAGE` in their tasks; providers offering only `TEXT_TO_IMAGE` are absent.

### S07 — Catalog use case filters presets per ModelType + MediaTask
**Given** `GetStudioCapablePresetsUseCase` with fakes seeded:
- local: one diffusion-capable asset (`utilityType == null`, `LocalStableDiffusionModelPolicy.supports == true`), one utility asset (`utilityType != null`), one chat-only asset.
- media providers: `{IMAGE, tasks={TEXT_TO_IMAGE}}`, `{IMAGE, tasks={EDIT_IMAGE}}`, `{IMAGE, tasks={TEXT_TO_IMAGE, EDIT_IMAGE}}`, `{VIDEO, tasks={TEXT_TO_VIDEO}}`, `{VIDEO, tasks={IMAGE_TO_VIDEO}}`, `{MUSIC, tasks={}}`.

**When①** `invoke(ModelType.IMAGE_GENERATION)`.
**Then①** `localAssets` contains only the diffusion-capable asset; `mediaAssets` contains exactly the two IMAGE providers whose tasks contain `TEXT_TO_IMAGE` (the `{TEXT_TO_IMAGE}` and the `{TEXT_TO_IMAGE, EDIT_IMAGE}` one).

**When②** `invoke(ModelType.IMAGE_EDIT)`.
**Then②** `localAssets` contains the diffusion asset (local diffusion serves edit too); `mediaAssets` contains exactly the IMAGE providers whose tasks contain `EDIT_IMAGE`.

**When③** `invoke(ModelType.VIDEO_GENERATION)`.
**Then③** `localAssets` is empty (no on-device video); `mediaAssets` contains the `{VIDEO, TEXT_TO_VIDEO}` provider only.

**When④** `invoke(ModelType.IMAGE_TO_VIDEO)`.
**Then④** `mediaAssets` contains the `{VIDEO, IMAGE_TO_VIDEO}` provider only.

**When⑤** `invoke(ModelType.MUSIC_GENERATION)`.
**Then⑤** `localAssets` is empty; `mediaAssets` contains the `{MUSIC, tasks={}}` provider (no task filter applied for MUSIC).

### S08 — Selecting a row marks it primary; Confirm commits
**Given** picker open with `mediaAssets = [{id="fal-1", name="Fal Aura"}, {id="fal-2", name="Fal Flux"}]`.
**When** `onStageModelSelection(StudioModelOptionUi(id="fal-2", ...))`.
**Then** `uiState.value.selectedModelIdInPicker == "fal-2"` (so `NeoSelectionCard.isSelected == true` for that row, rendering primary border + primaryContainer background).
**When** `onConfirmModelSelection()`.
**Then** `overrideCoordinator.override.value == StudioModelOverride.MediaProvider(MediaProviderId("fal-2"), "Fal Flux")`, `overrideCoordinator.isModelPickerVisible.value == false`, and `uiState.value.isModelPickerVisible == false`.

### S09 — Back/Cancel discards staged selection and returns to Settings view
**Given** picker open, pending staged to `fal-2`, committed override still `null`.
**When** `onCancelModelPicker()`.
**Then** `overrideCoordinator.override.value == null`, `overrideCoordinator.pendingSelection.value == null`, `overrideCoordinator.isModelPickerVisible.value == false`. The `AnimatedContent` target state is `StudioSheetView.Settings`.

### S10 — Override cleared on `clearSession()`
**Given** override committed.
**When** `viewModel.clearSession()`.
**Then** `overrideCoordinator.override.value == null` and `overrideCoordinator.isModelPickerVisible.value == false`.

---

### E01 — Confirm disabled when no row staged
**Given** picker open, `pendingSelection == null`.
**Then** `NeoPrimaryButton("Confirm")` is disabled (`enabled = selectedModelIdInPicker != null`); calling `onConfirmModelSelection()` is a no-op (override unchanged).

### E02 — Confirm re-confirmation is idempotent
**Given** override committed to `fal-1`.
**When** `onConfirmModelSelection()` is called again without a new staging.
**Then** `overrideCoordinator.override` emits no new distinct value (Turbine: no extra emission beyond the current state), and `isModelPickerVisible` stays `false`.

### E03 — Opening picker twice resets pending to current committed override
**Given** override committed to `fal-1`; picker opened then cancelled.
**When** `onOpenModelPicker()` a second time.
**Then** `pendingSelection.value == StudioModelOverride.MediaProvider(MediaProviderId("fal-1"), ...)` (re-seeded from the committed override), `selectedModelIdInPicker == "fal-1"`.

### E04 — Staging a different row replaces the prior staged selection
**Given** pending staged to `fal-1`.
**When** `onStageModelSelection(StudioModelOptionUi(id="fal-2", ...))`.
**Then** `selectedModelIdInPicker == "fal-2"` and `pendingSelection.value` is the `fal-2` override. Only one row is primary at a time.

### E05 — Empty catalog shows message and disables Confirm
**Given** `GetStudioCapablePresetsUseCase` fakes return empty for all three lists for the resolved ModelType.
**When** picker opened.
**Then** `ModelPickerPane` renders `"No models available for <ModelType.displayName()>."` and `Confirm.enabled == false`.

### E06 — Backward-compatible decode (codec + Intent)
**Sub-scenario E06a (ActiveStudioGenerationCodec):**
**Given** a JSON string produced by the **old** codec shape (no `override` key), e.g. `{"id":"...","prompt":"...","mediaType":"IMAGE","settingsKind":"image",...}`.
**When** `ActiveStudioGenerationCodec.decode(raw)`.
**Then** the result is non-null and `.override == null`.

**Given** a JSON string with `override = {"kind":"media_provider","id":"fal-1","displayName":"Fal Aura"}`.
**When** decode.
**Then** `.override == StudioModelOverride.MediaProvider(MediaProviderId("fal-1"), "Fal Aura")`.

**Sub-scenario E06b (StudioGenerationIntentCodec):**
**Given** an `Intent` (Robolectric) with settings extras but **no** `EXTRA_OVERRIDE_KIND`.
**When** `StudioGenerationIntentCodec.readOverride(intent)`.
**Then** result is `null`.
**Given** an `Intent` with `EXTRA_OVERRIDE_KIND = "media_provider"`, `EXTRA_OVERRIDE_ID = "fal-1"`, `EXTRA_OVERRIDE_DISPLAY_NAME = "Fal Aura"`.
**When** readOverride.
**Then** result equals `StudioModelOverride.MediaProvider(MediaProviderId("fal-1"), "Fal Aura")`.

---

### MD01 — Mutation defense: override must short-circuit default-slot reads
**Given** `FakeDefaultModelRepository.getDefaultCalls` is empty, and an override is set.

**When (image)** `GenerateImageUseCase.invoke(prompt, settings, override = StudioModelOverride.MediaProvider(MediaProviderId("fal-1"), "Fal Aura"))`.
**Then** after the call, `FakeDefaultModelRepository.getDefaultCalls` does **not** contain `IMAGE_GENERATION` **and** does **not** contain `IMAGE_EDIT`. (A lazy impl that ignores `override` and calls `resolveAssignedImageGenerationTargetUseCase(modelType)` without forwarding the override will call `getDefault(IMAGE_GENERATION)` and fail this assertion.)

**When (video)** generation runs with `request.override = StudioModelOverride.MediaProvider(...)`.
**Then** `FakeDefaultModelRepository.getDefaultCalls` does not contain `VIDEO_GENERATION`.

**When (music)** `GenerateMusicUseCase.invoke(prompt, settings, override = StudioModelOverride.MediaProvider(...))`.
**Then** `FakeDefaultModelRepository.getDefaultCalls` does not contain `MUSIC_GENERATION`.

**Failure-mode note:** an implementation that threads the override through the request but drops it at the leaf use case (e.g. `GenerateImageUseCase` keeps the old `resolveAssignedImageGenerationTargetUseCase(modelType)` call) will read the default slot and fail MD01. An implementation that drops the override at the Intent boundary will leave `readOverride(intent) == null`, causing S04 step 4 to fail. Both regressions are independently caught.

---

## 5. Coverage Matrix

| Scenario ID | Implementation task(s) | Module test path |
|---|---|---|
| S01 | 1, 11, 17 | `:feature:studio` (ViewModel) |
| S02 | 10, 11 | `:feature:studio` |
| S03 | 10, 12 | `:feature:studio` |
| S04 | 6, 7, 8, 9, 13 | `:core:domain`, `:core:data`, `:feature:studio`, `:feature:studio-generation-service` |
| S05 | 3, 4, 5 | `:core:domain` |
| S06 | 15 | `:feature:studio` |
| S07 | 2 | `:core:domain` |
| S08 | 10, 16 | `:feature:studio` |
| S09 | 10, 16 | `:feature:studio` |
| S10 | 10, 12 | `:feature:studio` |
| E01–E04 | 10, 16 | `:feature:studio` |
| E05 | 2, 16 | `:core:domain`, `:feature:studio` |
| E06 | 8, 9 | `:core:data`, `:feature:studio-generation-service` |
| MD01 | 3, 4, 5, 6 | `:core:domain` |

## 6. Naming & Signature Parity (cross-check with spec.md)

- `StudioModelOverride` sealed interface — variants `Local(configId: LocalModelConfigurationId, displayName: String)`, `Api(configId: ApiModelConfigurationId, displayName: String)`, `MediaProvider(providerId: MediaProviderId, displayName: String)`. ✅ matches spec §3.1.
- `GetStudioCapablePresetsUseCase(modelType: ModelType): Flow<StudioCapablePresets>`. ✅ matches §3.2.
- `MediaGenerationRequest(..., override: StudioModelOverride? = null)`. ✅ §3.3.
- `ActiveStudioGeneration(..., override: StudioModelOverride? = null)`. ✅ §3.4.
- Resolution overrides: `ResolveAssignedImageGenerationTargetUseCase.invoke(modelType, override)`, `ResolveAssignedMediaProviderUseCase.invoke(modelType, override)`, `GenerateMusicUseCase.invoke(prompt, settings, override)`. ✅ §3.5.
- `StudioModelOverrideCoordinator`: `override`, `isModelPickerVisible`, `pendingSelection`, `pickerModelType`; methods `openPicker`, `stageSelection`, `confirmSelection`, `cancelPicker`, `clearForCapabilityChange`, `clearSession`. ✅ §3.6.
- `StudioUiState` fields: `modelOverride`, `modelPickerAssets`, `isModelPickerVisible`, `selectedModelIdInPicker`. ✅ §3.7.

---

*Every `Then` clause above names a literal expected value (string, enum case, id equality, or specific exception type/message). No placeholder assertions.*
