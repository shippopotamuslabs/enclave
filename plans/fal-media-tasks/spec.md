# Spec — Fine-Grained Media Tasks (`fal-media-tasks`)

**Ticket:** `fal-media-tasks`
**Branch base:** `main`
**Source audit:** `scratch/fal-capability-audit.md` (verified file-by-file against the current codebase on 2026-06-02; drift notes are at the end of this document).
**Contracts honored:** ARCHITECTURE_RULES, DATA_LAYER_RULES, INFERENCE_RULES, UI_DESIGN_SPEC, CODE_STYLE_RULES, UNIT_TESTING.

This document contains **no runnable code**. Type shapes, enum entries, method signatures, the bitmask-derivation table, and the 4-slot routing table are declarations and contracts, not implementations.

---

## 1. Feature Summary

Today the app conflates "media kind" (`MediaCapability` = `IMAGE | VIDEO | MUSIC`) with "media task." Every image model is assumed to support image-edit and every video model is assumed to support image-to-video. This produces requests that 4xx against text-only Fal endpoints and shows controls (reference-image picker, Animate) that the resolved model cannot satisfy.

This feature introduces a fine-grained, per-model **`MediaTask`** flag set (`TEXT_TO_IMAGE`, `EDIT_IMAGE`, `TEXT_TO_VIDEO`, `IMAGE_TO_VIDEO`) that:

- is modeled as an immutable `Set<MediaTask>` in `:core:domain` and persisted as an `Int` bitmask (`tasks_mask`) in Room and Firestore;
- is derived for Fal models from the endpoint `category`/id and the OpenAPI 3.0 input schema's `required[]` array (optional-image endpoints advertise BOTH the text task and the image task);
- adds two new `ModelType` default-model slots (`IMAGE_EDIT`, `IMAGE_TO_VIDEO`) so each task has an independent default model (Option A);
- deletes the brittle `isImageToVideoModel` string heuristic in `FalVideoGenerationAdapter` and replaces it with task-driven behavior;
- gates Studio UI and Settings provider filtering on real task support, **failing closed** (a control appears only when its task's default model exists);
- preserves existing behavior for OpenAI/Google/xAI media adapters by seeding them with all-tasks-for-their-kind.

No database migration code is written. `EnclaveDatabase` version is reset `8 → 1`, `fallbackToDestructiveMigration(dropAllTables = true)` is kept, and the developer reinstalls for a clean DB. Wiping the `media_providers` and `default_models` Firestore collections is a **manual out-of-band step**, not a code task. Music/TTS, chat tool-call generation, and the non-Fal adapter classification logic are out of scope except where behavior must be preserved.

---

## 2. API Contracts and Interfaces

### 2.1 `MediaTask` enum (NEW, `:core:domain`)

`core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/config/MediaTask.kt`

```
enum class MediaTask(val kind: MediaCapability) {
    TEXT_TO_IMAGE(MediaCapability.IMAGE)
    EDIT_IMAGE(MediaCapability.IMAGE)
    TEXT_TO_VIDEO(MediaCapability.VIDEO)
    IMAGE_TO_VIDEO(MediaCapability.VIDEO)
}
```

- `kind` is the parent coarse `MediaCapability`. `MediaCapability` (`IMAGE | VIDEO | MUSIC`, in `MediaProviderAsset.kt:16`) is **kept**; `MediaTask` refines within it.
- The bitmask is keyed on **declaration order (ordinal)**: bit `(1 shl ordinal)`. The mask helpers below are the single source of truth — no other code performs bit math.

### 2.2 Mask helpers (NEW, pure functions, `:core:domain`)

Same file as the enum (or a sibling `MediaTaskMask.kt` in the same package). Framework-free, pure:

```
fun Set<MediaTask>.toMask(): Int
fun Int.toMediaTasks(): Set<MediaTask>
```

Contract:
- `emptySet<MediaTask>().toMask() == 0` and `0.toMediaTasks() == emptySet()`.
- Round-trip is total over valid masks: for any `Set<MediaTask> s`, `s.toMask().toMediaTasks() == s`.
- All-bits set: `MediaTask.entries.toSet().toMask() == 15` (bits 0..3 = `1 + 2 + 4 + 8`), and `15.toMediaTasks() == MediaTask.entries.toSet()`.
- Unknown high bits in an `Int` are ignored by `toMediaTasks()` (only bits `0..(entries.size - 1)` are read), so a forward-compat mask never throws.

### 2.3 `MediaTask` ↔ `ModelType` mapping (NEW, single home, `:core:domain`)

A single bidirectional mapping object/extension lives in `:core:domain` (sibling to `MediaTask`, e.g. `MediaTaskModelType.kt`). It is the only place that maps a task to a default-model slot and back:

```
fun MediaTask.toModelType(): ModelType
fun ModelType.toMediaTaskOrNull(): MediaTask?
```

The 4-slot routing table (see §2.7) defines the exact pairs. `ModelType` values not in the table return `null` from `toMediaTaskOrNull()`.

### 2.4 New `ModelType` entries (MODIFY, `:core:domain`)

`core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/inference/ModelType.kt`

The enum constructor takes only `apiValue: String`; `displayName()` and `description` are `when`-based (verified lines 89-123), and `isTextModel` is `when`-based (lines 125-130). Add two entries plus their branches in each `when`:

| New entry | `apiValue` (`@SerialName`) | `displayName()` | `description` | `isTextModel` |
|---|---|---|---|---|
| `IMAGE_EDIT` | `"image_edit"` | `"Image Edit"` | `"Edit or transform images using a reference image."` | `false` |
| `IMAGE_TO_VIDEO` | `"image_to_video"` | `"Image to Video"` | `"Animate a reference image into video."` | `false` |

`IMAGE_GENERATION` (= text-to-image) and `VIDEO_GENERATION` (= text-to-video) are unchanged (lines 64-71). `fromApiValue` (line 137) already tolerates new values. The two new entries fall into the `else -> false` arm of `isTextModel` automatically, but they MUST also be covered explicitly in `displayName()` and `description` `when` blocks (those `when`s are exhaustive over the enum and will not compile otherwise).

### 2.5 `MediaProviderAsset` addition (MODIFY, `:core:domain`)

`core/domain/.../model/config/MediaProviderAsset.kt` — add a field to the data class (lines 27-35), keeping `capability`:

```
val tasks: Set<MediaTask> = emptySet()
```

`emptySet()` default means "unknown" and is treated as fail-closed by all consumers (see §2.9, §6). No framework types are introduced; `Set` satisfies CODE_STYLE immutable-collection preference.

### 2.6 `DiscoveredApiModel` addition (MODIFY, `:core:domain`)

`core/domain/.../model/inference/DiscoveredApiModel.kt` (lines 5-20) — add:

```
val tasks: Set<MediaTask> = emptySet()
```

### 2.7 4-Slot Routing Table (contract for Studio + Settings)

| Studio situation | resolved `MediaTask` | default-model slot (`ModelType`) |
|---|---|---|
| IMAGE mode, no reference image | `TEXT_TO_IMAGE` | `IMAGE_GENERATION` |
| IMAGE mode, reference image attached | `EDIT_IMAGE` | `IMAGE_EDIT` |
| VIDEO mode, no reference image | `TEXT_TO_VIDEO` | `VIDEO_GENERATION` |
| VIDEO mode, animate / reference image | `IMAGE_TO_VIDEO` | `IMAGE_TO_VIDEO` |

`MediaTask.toModelType()` / `ModelType.toMediaTaskOrNull()` implement exactly these four pairs.

### 2.8 Image-input requirement enum (NEW + MODIFY, `:core:domain` + `:feature:inference`)

**Owner decision (2026-06-02):** the conflated reference-image boolean is removed, not extended. Both image booleans collapse into a single three-state enum so the illegal state (`supports == false && required == true`) is unrepresentable.

New enum (`:core:domain`, colocated in `ModelGenerationInputSchema.kt`):

```
enum class ImageInputRequirement { NONE, OPTIONAL, REQUIRED }
```

`ModelGenerationInputSchema` (`core/domain/.../model/media/ModelGenerationInputSchema.kt`, lines 8-19) — **replace** `supportsReferenceImage: Boolean = false` (line 18) with:

```
val imageInput: ImageInputRequirement = ImageInputRequirement.NONE
```

`FalOpenApiInputSchemaParser.parse` (`feature/inference/.../fal/FalOpenApiInputSchemaParser.kt:13`) sets `imageInput`:
- `NONE` when no image field (`image_url` / `image` / `reference_image` / `start_image_url`) is present;
- `REQUIRED` when an image field is present AND named in the OpenAPI input schema's `required` array;
- `OPTIONAL` when an image field is present but absent from `required`.

**Parser refactor note (verified gap):** `findInputProperties` (lines 40-49) currently returns only the input schema's `properties` object and discards the schema-level `required` array (a sibling of `properties`). To read required-ness, the parser must locate the same input schema (the one whose `properties` contains `"prompt"`) and read its `required` array — `inputProperties` alone is insufficient. The presence check stays property-based; only the `REQUIRED` vs `OPTIONAL` distinction needs the `required` array.

**`ProviderCapabilities.supportsReferenceImage` is removed entirely** (`GenerationSettings.kt:95`). It had zero terminal production consumers (verified by grep: every reference is an assignment, a field declaration, the `@Preview` fake, or a test). Gating now keys off default-slot existence (§6), not this boolean. Consequently:
- `ModelGenerationInputSchema.toProviderCapabilities` (lines 21-64) drops both `supportsReferenceImage = supportsReferenceImage || fallback.supportsReferenceImage` lines (37, 54); it no longer reads any image signal.
- `GetProviderCapabilitiesUseCase` drops every `supportsReferenceImage = true/false` assignment (lines 19, 40, 51, 71, 89, 100).
- The `StudioComponents.kt` `@Preview` fake (`:feature:studio`, line 1030) drops its `supportsReferenceImage = true` argument.

### 2.9 Bitmask-Derivation Table (Fal task derivation; Resolved Decision 5)

Inputs: the coarse kind (image vs video) and the schema's `imageInput: ImageInputRequirement`.

| Schema signal | Image kind → tasks | Video kind → tasks |
|---|---|---|
| `imageInput == NONE` | `{TEXT_TO_IMAGE}` | `{TEXT_TO_VIDEO}` |
| `imageInput == REQUIRED` | `{EDIT_IMAGE}` | `{IMAGE_TO_VIDEO}` |
| `imageInput == OPTIONAL` | `{TEXT_TO_IMAGE, EDIT_IMAGE}` | `{TEXT_TO_VIDEO, IMAGE_TO_VIDEO}` |

`ApiModelCatalogRepositoryImpl` derives tasks in this precedence order when classifying a Fal model:

1. **`category` / endpoint-id** (cheap, no schema needed):
   - `category == "text-to-image"` → `{TEXT_TO_IMAGE}`
   - `category == "image-to-image"` → `{EDIT_IMAGE}`
   - `category == "text-to-video"` → `{TEXT_TO_VIDEO}`
   - `category == "image-to-video"` → `{IMAGE_TO_VIDEO}`
   - endpoint-id substring backup: `/image-to-video`, `/i2v`, `img2vid`, `img-to-video`, `image2video` → `{IMAGE_TO_VIDEO}`; `/text-to-video` → `{TEXT_TO_VIDEO}`; `/edit`, `/kontext` → image, schema-disambiguated below; `/text-to-image` → `{TEXT_TO_IMAGE}`.
   - **Unrecognized `category` = no signal.** A non-null `category` that matches none of the known values (e.g. a display name) must be treated as absent and fall through to the id substring, then the schema, then the fallback map. This matters because the featured-fallback path constructs `FalModel(... category = it.name)` (`ApiModelCatalogRepositoryImpl.kt:279`), passing a display name as `category`; without fall-through the fallback map (step 3) would never run for featured ids like `fal-ai/recraft/v3` and `fal-ai/minimax/video-01`.
2. **OpenAPI schema** (when available, applying the table above using the coarse kind already computed from `isImageGenerationCapable` / `isVideoGenerationCapable`). The optional-image row MUST yield BOTH tasks; it must not collapse to the image-only task.
3. **Hand-maintained fallback map** keyed by endpoint id, used only when neither category nor schema yields a task (offline / unauth / sparse metadata). Seed from `featuredFalModelIds` (lines 179-188):
   - `fal-ai/flux-pro/kontext` → `{TEXT_TO_IMAGE, EDIT_IMAGE}`
   - `fal-ai/recraft/v3` → `{TEXT_TO_IMAGE}`
   - `fal-ai/kling-video/v2.1/master/image-to-video` → `{IMAGE_TO_VIDEO}`
   - `fal-ai/minimax/video-01` → `{TEXT_TO_VIDEO}`

For OpenAI/Google/xAI media models, seed `tasks` to all-tasks-for-the-kind so existing behavior is preserved:
- image-capable → `{TEXT_TO_IMAGE, EDIT_IMAGE}`
- video-capable → `{TEXT_TO_VIDEO, IMAGE_TO_VIDEO}`

### 2.10 Adapter / port signature changes (MODIFY, `:feature:inference`)

**`FalImageGenerationAdapter.generateImage`** (signature at lines 25-32) gains one parameter so the adapter only attaches `image_url`/`image` when editing is intended:

```
suspend fun generateImage(
    prompt: String,
    apiKey: String,
    modelId: String,
    baseUrl: String?,
    settings: GenerationSettings,
    referenceImage: ByteArray? = null,
    supportsEdit: Boolean = false,
): Result<List<ByteArray>>
```

Behavior: the `image_url` + `image` object (lines 58-62) is attached **only when `referenceImage != null && supportsEdit`**. When `referenceImage != null && !supportsEdit`, no image fields are added (text-to-image request is sent unchanged).

**`FalVideoGenerationAdapter.generateVideo`** (signature at lines 22-29) gains one parameter; the `isImageToVideoModel(modelId)` string heuristic (lines 77-82) and its `require(...)` guard (lines 36-38) are **deleted**:

```
suspend fun generateVideo(
    prompt: String,
    apiKey: String,
    modelId: String,
    baseUrl: String?,
    settings: GenerationSettings,
    referenceImage: ByteArray? = null,
    supportsImageToVideo: Boolean = false,
): Result<ByteArray>
```

Behavior: `image_url` (lines 51-53) is attached **only when `referenceImage != null && supportsImageToVideo`**. When `supportsImageToVideo == true && referenceImage == null`, fail fast with `IllegalArgumentException` carrying the **exact** message:
`"Model '$modelId' requires a reference image but none was provided."` (this is the same literal as today's deleted heuristic message; the fail-fast for image-mandatory video models is now driven by the resolved task, not the model-id string).

**`ImageGenerationPortImpl.generateImage`** (lines 21-80) and **`VideoGenerationPortImpl.generateVideo`** (lines 22-77): both receive `provider: MediaProviderAsset` and therefore `provider.tasks`. They:
- compute the relevant task from (kind + whether a reference image is present) per §2.7;
- pass `supportsEdit = MediaTask.EDIT_IMAGE in provider.tasks` (image) / `supportsImageToVideo = MediaTask.IMAGE_TO_VIDEO in provider.tasks` (video) down to the Fal adapter;
- **fail fast** when a reference image is present (`(settings as? VisualGenerationSettings)?.referenceImageUri != null`) but the required task is absent from `provider.tasks`, returning `Result.failure(IllegalArgumentException(...))` **before** loading reference bytes (lines 30-33) and before calling any adapter. The exact messages:
  - Image port: `"Model '${provider.modelName}' does not support image editing."`
  - Video port: `"Model '${provider.modelName}' does not support image-to-video."`

Provider isolation (INFERENCE_RULES) is preserved: all Fal specifics remain in `:feature:inference`; ports read the domain `provider.tasks` only.

### 2.11 Persistence mapper helpers (`:core:data`)

The entity↔domain mapping in `MediaProviderRepositoryImpl` uses `Set<MediaTask>.toMask()` / `Int.toMediaTasks()` (from `:core:domain`). All mapping stays inside `:core:data` per DATA_LAYER_RULES §4.

---

## 3. Data Model / Schema Changes

### 3.1 Room entity — `tasks_mask` column (MODIFY, `:core:data`)

`core/data/.../local/MediaProviderEntity.kt` (entity at lines 8-24) — add:

```
@ColumnInfo(name = "tasks_mask") val tasksMask: Int = 0
```

`Int` is a Room-native type → **no new `@TypeConverter`**. Default `0` = unknown/fail-closed. This is an authorized schema change (Resolved Decision 1; DATA_LAYER_RULES §1 SCHEMA LOCK — this ticket is the explicit authorization; note it in the PR).

### 3.2 `EnclaveDatabase` version reset (MODIFY, `:core:data`)

`core/data/.../local/EnclaveDatabase.kt` — change `version = 8` (line 37) to `version = 1`. No `Migration` objects; keep `fallbackToDestructiveMigration(dropAllTables = true)` in `DataModule`. Reinstall produces a clean DB (Resolved Decision 1).

### 3.3 Firestore field (MODIFY, `:core:data`)

`core/data/.../remote/firestore/handlers/MediaProviderSyncHandler.kt`:
- `toFirestore` (lines 37-47) — add `"tasks_mask" to local.tasksMask`.
- `fromFirestore` (lines 49-64) — add `tasksMask = doc.getLong("tasks_mask")?.toInt() ?: 0`.

No Firebase Functions change (server has zero media-capability awareness). Wiping the `media_providers` and `default_models` Firestore collections is a **manual** step (Resolved Decision 2).

### 3.4 Default-model slot additions

The two new `ModelType` entries (`IMAGE_EDIT`, `IMAGE_TO_VIDEO`, §2.4) are stored in `default_models` (PK = `model_type`, `DefaultModelEntity.kt:49-52`). The init block (lines 68-73, "exactly one of local/api/tts/media id non-null") is unchanged. `DefaultModelSyncHandler` already serializes the slot via `ModelType.valueOf(doc.id)` (verified line 38), so the new slots sync with no handler change.

### 3.5 (Optional) `DefaultModelAssignmentView` task exposure

Not required for this feature. Studio resolves tasks from `provider.tasks` (via `MediaProviderRepository.getMediaProviders()`), not from the join. `DefaultModelsDao.observeAllWithDetails` (line 49) and `DefaultModelAssignmentView` (line 13, `mediaCapability` at 50-51) are left unchanged unless a later phase needs to surface the mask in the assignment UI.

---

## 4. File Structure Table (grouped by A–E module sequence)

### A. `:core:domain`

| File | Action | Responsibility |
|---|---|---|
| `core/domain/.../model/config/MediaTask.kt` | Create | `MediaTask` enum (with `kind`) + pure `Set<MediaTask>.toMask()` / `Int.toMediaTasks()` helpers. |
| `core/domain/.../model/config/MediaTaskModelType.kt` | Create | Single bidirectional `MediaTask.toModelType()` / `ModelType.toMediaTaskOrNull()` mapping (4-slot routing table). |
| `core/domain/.../model/config/MediaProviderAsset.kt` | Modify | Add `tasks: Set<MediaTask> = emptySet()`. Keep `capability`. |
| `core/domain/.../model/inference/ModelType.kt` | Modify | Add `IMAGE_EDIT`, `IMAGE_TO_VIDEO` entries + `displayName()`/`description` branches. |
| `core/domain/.../model/inference/DiscoveredApiModel.kt` | Modify | Add `tasks: Set<MediaTask> = emptySet()`. |
| `core/domain/.../model/media/ModelGenerationInputSchema.kt` | Modify | Add `ImageInputRequirement` enum; replace `supportsReferenceImage` with `imageInput: ImageInputRequirement = NONE`; drop the two `supportsReferenceImage` merge lines in `toProviderCapabilities`. |
| `core/domain/.../model/media/GenerationSettings.kt` | Modify | Remove dead `ProviderCapabilities.supportsReferenceImage` field. |
| `core/domain/.../usecase/media/GetProviderCapabilitiesUseCase.kt` | Modify | Drop all `supportsReferenceImage` assignments (now-removed field). |

### B. `:core:data`

| File | Action | Responsibility |
|---|---|---|
| `core/data/.../local/MediaProviderEntity.kt` | Modify | Add `@ColumnInfo(name = "tasks_mask") val tasksMask: Int = 0`. |
| `core/data/.../local/EnclaveDatabase.kt` | Modify | Reset `version = 8 → 1`. |
| `core/data/.../repository/MediaProviderRepositoryImpl.kt` | Modify | Mapper: `toDomain` reads `tasksMask.toMediaTasks()`; `saveMediaProvider` writes `tasksMask = asset.tasks.toMask()`. |
| `core/data/.../remote/firestore/handlers/MediaProviderSyncHandler.kt` | Modify | Add `tasks_mask` to `toFirestore` and `fromFirestore`. |

### C. `:feature:inference`

| File | Action | Responsibility |
|---|---|---|
| `feature/inference/.../fal/FalOpenApiInputSchemaParser.kt` | Modify | Read OpenAPI `required[]`; set `imageInput` (NONE/OPTIONAL/REQUIRED). |
| `feature/inference/.../repository/ApiModelCatalogRepositoryImpl.kt` | Modify | Derive `tasks` from `category`/endpoint-id + schema (§2.9); fallback map; seed non-Fal all-tasks-for-kind. |
| `feature/inference/.../media/FalImageGenerationAdapter.kt` | Modify | Add `supportsEdit`; attach image fields only when `referenceImage != null && supportsEdit`. |
| `feature/inference/.../media/FalVideoGenerationAdapter.kt` | Modify | Delete `isImageToVideoModel` + its `require`; add `supportsImageToVideo`; task-driven image attach + fail-fast. |
| `feature/inference/.../media/ImageGenerationPortImpl.kt` | Modify | Resolve task; fail fast on unsupported edit; pass `supportsEdit`. |
| `feature/inference/.../media/VideoGenerationPortImpl.kt` | Modify | Resolve task; fail fast on unsupported I2V; pass `supportsImageToVideo`. |

### D. `:feature:studio`

| File | Action | Responsibility |
|---|---|---|
| `feature/studio/.../MultimodalViewModel.kt` | Modify | Resolve 1-of-4 slots by (mediaType + reference-present); add `hasImageEditModel` / `hasImageToVideoModel`; guard `onAnimateMedia`. |
| `feature/studio/.../StudioUiState.kt` | Modify | Replace `hasVideoModel` with `hasImageEditModel` + `hasImageToVideoModel`. |
| `feature/studio/.../StudioInputDock.kt` | Modify | Reference picker visible only when the active task's model exists (fail-closed). |
| `feature/studio/.../StudioDetailScreen.kt` | Modify | Animate/Quick Animate `enabled = hasImageToVideoModel`; corrected tooltip. |
| `feature/studio/.../GalleryDetailScreen.kt` | Modify | Animate/Quick Animate `enabled = hasImageToVideoModel`; corrected tooltip. |
| `feature/studio/.../coordinators/StudioGenerationCoordinator.kt` | Modify | `startDetailVideoGeneration` (line 94) gains `hasImageToVideoModel: Boolean`; when false, set `VideoGenerationState.Error` and return before `startGeneration` (line 102). |
| `core/ui/.../component/FullscreenMediaDetail.kt` | Modify | Update `FULLSCREEN_MEDIA_ANIMATE_DISABLED_TOOLTIP` literal (see §7). |
| `feature/studio/.../components/StudioComponents.kt` | Modify | Drop `supportsReferenceImage = true` from the `@Preview` `ProviderCapabilities` fake (compile fallout of the field removal). |

### E. `:feature:settings`

| File | Action | Responsibility |
|---|---|---|
| `feature/settings/.../ModelConfigurationScreen.kt` | Modify | Expand `studioMediaTypes` (line 109) AND the `isMediaSlot` list (lines 153-157) to 4 slots; replace the slot→`MediaCapability` block + `filter { it.capability == capability }` (lines 168-175) with `slotTask in asset.tasks`. |
| `feature/settings/.../MediaConfigureScreen.kt` | Modify | Add read-only/editable `MediaTask` multi-select; per-task "Use as Default" mapping. |
| `feature/settings/.../MediaProvidersCoordinator.kt` | Modify | Map new slots in `setDefaultModel`; carry `tasks` on save. |
| `feature/settings/.../ApiProviderDiscoverySupport.kt` | Modify | `filterMediaModels` narrows by task once known. |
| `feature/settings/.../SettingsModels.kt` | Modify | `MediaProviderAssetUi` gains `tasks: Set<MediaTask>`. |
| `feature/settings/.../MediaProviderAssetUiMapper.kt` | Modify | Map `tasks` domain↔UI. |

---

## 5. Ordered Implementation Tasks

Sequence is A → B → C → D → E so each layer compiles (and Room schema is green via `:app:kspDebugKotlin`) before the next. Each task lists the `test_spec.md` scenario IDs it satisfies.

### Phase A — `:core:domain`

- **A1.** Create `MediaTask` enum with `kind` and the `Set<MediaTask>.toMask()` / `Int.toMediaTasks()` pure helpers. — `MASK-01, MASK-02, MASK-03, MASK-04, MASK-05`
- **A2.** Create the single `MediaTask.toModelType()` / `ModelType.toMediaTaskOrNull()` mapping per the 4-slot routing table. — `MAP-01, MAP-02, MAP-03`
- **A3.** Add `IMAGE_EDIT("image_edit")` and `IMAGE_TO_VIDEO("image_to_video")` to `ModelType`, with `displayName()`/`description` branches and `isTextModel == false`. — `MODELTYPE-01, MODELTYPE-02, MAP-01, MAP-02`
- **A4.** Add `tasks: Set<MediaTask> = emptySet()` to `MediaProviderAsset`; keep `capability`. — `MASK-06`
- **A5.** Add `tasks: Set<MediaTask> = emptySet()` to `DiscoveredApiModel`. — `CATALOG-01, CATALOG-02, CATALOG-03, CATALOG-04, CATALOG-05`
- **A6.** Add the `ImageInputRequirement` enum and replace `ModelGenerationInputSchema.supportsReferenceImage` with `imageInput: ImageInputRequirement = NONE`. Remove the dead `ProviderCapabilities.supportsReferenceImage`: drop its two `toProviderCapabilities` merge lines, every `GetProviderCapabilitiesUseCase` assignment, and the `StudioComponents` preview-fake argument. The removal is compile-enforced and covered by existing `toProviderCapabilities` mapping tests; the enum's behavior is covered by the PARSE scenarios. — `PARSE-01, PARSE-02, PARSE-03, PARSE-04, PARSE-05, PARSE-06`

### Phase B — `:core:data` (schema reset lands here)

- **B1.** Add `tasks_mask` column to `MediaProviderEntity` (default `0`). — `ENTITY-01, ENTITY-02`
- **B2.** Reset `EnclaveDatabase` `version` to `1`. — `DB-01`
- **B3.** Wire the mapper in `MediaProviderRepositoryImpl` (`toDomain` reads mask, `saveMediaProvider` writes mask). — `ENTITY-01, ENTITY-02`
- **B4.** Add `tasks_mask` to `MediaProviderSyncHandler.toFirestore` and `fromFirestore`. — `SYNC-01, SYNC-02, SYNC-03`

### Phase C — `:feature:inference`

- **C1.** Extend `FalOpenApiInputSchemaParser.parse` to read `required[]` and set `imageInput` (NONE/OPTIONAL/REQUIRED). — `PARSE-01, PARSE-02, PARSE-03`
- **C2.** Derive `tasks` in `ApiModelCatalogRepositoryImpl` from `category`/endpoint-id + schema (§2.9), with the hand-maintained fallback map and non-Fal all-tasks-for-kind seeding. — `CATALOG-01, CATALOG-02, CATALOG-03, CATALOG-04, CATALOG-05, CATALOG-06, MUT-02`
- **C3.** Add `supportsEdit` to `FalImageGenerationAdapter`; attach image fields only when `referenceImage != null && supportsEdit`. — `IMGADAPTER-01, IMGADAPTER-02, MUT-01`
- **C4.** Delete `isImageToVideoModel` + its `require`; add `supportsImageToVideo` to `FalVideoGenerationAdapter`; task-driven attach + fail-fast on image-mandatory video without reference. — `VIDADAPTER-01, VIDADAPTER-02, VIDADAPTER-03`
- **C5.** Make `ImageGenerationPortImpl` resolve the task, fail fast on unsupported edit, and pass `supportsEdit`. — `IMGPORT-01, IMGPORT-02, IMGPORT-03`
- **C6.** Make `VideoGenerationPortImpl` resolve the task, fail fast on unsupported I2V, and pass `supportsImageToVideo`. — `VIDPORT-01, VIDPORT-02, VIDPORT-03`

### Phase D — `:feature:studio`

- **D1.** In `MultimodalViewModel`, resolve the 1-of-4 slot by (mediaType + reference-present); add `hasImageEditModel` and `hasImageToVideoModel` flows; replace the coarse `hasVideoModel`. — `VM-01, VM-02, VM-03, VM-04`
- **D2.** Two-layer fail-closed guard for Animate (no `IMAGE_TO_VIDEO` default):
  - `MultimodalViewModel.onAnimateMedia` (line 454; `autoAnimate == true` branch at 487): when `hasImageToVideoModel` is false, do NOT call `startDetailVideoGeneration`; surface `VideoGenerationState.Error(assetId, STUDIO_ANIMATE_I2V_UNAVAILABLE)` (via a coordinator state setter) instead. In production the VM calls `startDetailVideoGeneration` only when the flag is true.
  - `StudioGenerationCoordinator.startDetailVideoGeneration` (line 94): gains a `hasImageToVideoModel: Boolean` parameter; when false, set `_videoGenerationState = VideoGenerationState.Error(asset.id, STUDIO_ANIMATE_I2V_UNAVAILABLE)` and return BEFORE `startGeneration` (line 102) — defensive second layer covered by COORD-01. — `VM-04, COORD-01`
- **D3.** Update `StudioUiState` to carry `hasImageEditModel` + `hasImageToVideoModel`. — `VM-01, VM-02`
- **D4.** Gate the `StudioInputDock` reference picker (fail-closed): IMAGE mode visible only when `hasImageEditModel`; VIDEO mode visible only when `hasImageToVideoModel`. — `UI-01, UI-02, UI-03`
- **D5.** Set Animate/Quick Animate `enabled = hasImageToVideoModel` in `StudioDetailScreen` and `GalleryDetailScreen`; wire the corrected disabled tooltip. — `UI-04, UI-05`
- **D6.** Update the `FULLSCREEN_MEDIA_ANIMATE_DISABLED_TOOLTIP` literal in `core/ui` per §7. — `UI-04, UI-05`

### Phase E — `:feature:settings`

- **E1.** Expand `studioMediaTypes` (line 109) to `[IMAGE_GENERATION, IMAGE_EDIT, VIDEO_GENERATION, IMAGE_TO_VIDEO]` AND add `IMAGE_EDIT` + `IMAGE_TO_VIDEO` to the `isMediaSlot` list (lines 153-157) so the new slots are treated as media slots and render four "Studio & Media" rows. (Both lists must change; the `isMediaSlot` list currently holds only `IMAGE_GENERATION, VIDEO_GENERATION, MUSIC_GENERATION`.) — `SETTINGS-01`
- **E2.** Replace the `when (assignmentSlot)`→`MediaCapability` block (lines 168-174) and the `filter { it.capability == capability }` (line 175) with task-based filtering: `val slotTask = assignmentSlot.toMediaTaskOrNull(); uiState.mediaProvidersSheet.assets.filter { slotTask != null && slotTask in it.tasks }`. — `SETTINGS-02, SETTINGS-03, MUT-03`
- **E3.** Add `tasks` to `MediaProviderAssetUi` + mapper; add the `MediaTask` multi-select to `MediaConfigureScreen` and per-task "Use as Default" mapping. — `SETTINGS-04`
- **E4.** Map the new slots in `MediaProvidersCoordinator.setDefaultModel` and narrow `ApiProviderDiscoverySupport.filterMediaModels` by task. — `SETTINGS-02, SETTINGS-05`

### Manual (non-code)

- Wipe `media_providers` + `default_models` Firestore collections, reinstall, re-add providers fresh (Resolved Decision 2). Documented in the PR; not a code task.

---

## 6. Behavior Specifications (literals and gating semantics)

- **Fail-closed (Resolved Decision 6):** A Studio control appears only when its task's default model exists. The IMAGE-mode reference picker shows iff `hasImageEditModel`. The VIDEO-mode reference picker and Animate/Quick Animate show/enable iff `hasImageToVideoModel`. An empty `tasks` set (mask `0`) never enables any task-gated control.
- **Fail-fast ports:** When a reference image is present but the resolved task is not in `provider.tasks`, the port returns `Result.failure` with the exact message in §2.10 before loading bytes or calling the adapter.
- **Fail-fast video adapter:** When `supportsImageToVideo == true` and `referenceImage == null`, fail with `IllegalArgumentException("Model '$modelId' requires a reference image but none was provided.")`.
- **T2I-only never attaches an image:** When `supportsEdit == false`, `FalImageGenerationAdapter` omits `image_url`/`image` even if `referenceImage` is non-null.
- **Optional-image = BOTH:** The §2.9 optional row yields a two-element set (`{TEXT_TO_IMAGE, EDIT_IMAGE}` or `{TEXT_TO_VIDEO, IMAGE_TO_VIDEO}`), never the image-only single-element set.

---

## 7. UI Copy Literals (source of truth)

The existing constant is verified: `FULLSCREEN_MEDIA_ANIMATE_DISABLED_TOOLTIP` in `core/ui/.../component/FullscreenMediaDetail.kt:44-45` currently equals `"Setup a video model in Settings to enable."`. This copy conflates "no video model" with "video model can't animate images." It is changed to a single literal that covers the fail-closed I2V case:

- **New literal (`FULLSCREEN_MEDIA_ANIMATE_DISABLED_TOOLTIP`):**
  `"Your video model only supports text-to-video. Set an image-to-video model in Settings to animate."`

Studio reference-picker hidden-state copy (used where a hint is shown; defined here as the source of truth, referenced verbatim in `test_spec.md`):

- **IMAGE mode, no edit model (`STUDIO_REFERENCE_IMAGE_EDIT_UNAVAILABLE`):**
  `"This image model generates from text only. Set an image-edit model in Settings to use a reference image."`
- **VIDEO mode, no I2V model (`STUDIO_REFERENCE_I2V_UNAVAILABLE`):**
  `"This video model generates from text only. Set an image-to-video model in Settings to use a reference image."`

Port error messages (source of truth for `test_spec.md`):

- Image port unsupported edit: `"Model '${provider.modelName}' does not support image editing."`
- Video port unsupported I2V: `"Model '${provider.modelName}' does not support image-to-video."`
- Video adapter image-mandatory without reference: `"Model '$modelId' requires a reference image but none was provided."`
- Animate blocked, no image-to-video default — surfaced as `VideoGenerationState.Error.message` (`STUDIO_ANIMATE_I2V_UNAVAILABLE`): `"No image-to-video model is set. Choose one in Settings to animate."`

---

## 8. Drift and Escalation Rules (later phases)

- **If a verified path/line differs at implementation time:** prefer the symbol over the line number (this spec cites symbols; line numbers are advisory). If a symbol named here no longer exists, STOP and escalate to the human approval gate with the exact symbol and file; do not invent a replacement.
- **Schema-lock authorization (DATA_LAYER_RULES §1):** the `tasks_mask` column and the `version 8 → 1` reset are authorized by this ticket. Record the authorization in the PR description. Do not add any other column or table.
- **Provider isolation (INFERENCE_RULES):** all Fal-specific derivation and request-shaping stays in `:feature:inference`. Ports and ViewModels read only the domain `MediaProviderAsset.tasks`. No Compose UI may enter `:core:data` or `:feature:inference`.
- **Clean Architecture (ARCHITECTURE_RULES §3.1):** `MediaTask`, mask helpers, and the task↔slot mapping live in `:core:domain` and must not import Room/Compose/Hilt. The bitmask `Int` is an implementation detail of `:core:data` mapping/Firestore only.
- **No migration code (Resolved Decision 1):** never add a `Migration` object or backfill routine; rely on destructive migration + reinstall.

---

## 9. Audit references that did NOT verify exactly (for the approval gate)

The audit's findings are substantively correct; the following are minor inaccuracies the implementer should be aware of:

- **`FULLSCREEN_MEDIA_ANIMATE_DISABLED_TOOLTIP` actual literal.** The audit treated this as a generic "no video model" tooltip and proposed new copy; the actual current literal (verified `FullscreenMediaDetail.kt:45`) is `"Setup a video model in Settings to enable."`. The new literal is defined in §7.
- **Animate buttons are `FullscreenMediaAction`, not `NeoActionButton`.** The audit (line 205-207) wrote `NeoActionButton(label = "Animate", enabled = hasVideoModel, ...)`. The actual code builds `FullscreenMediaAction(label = "Animate"/"Quick Animate", enabled = hasVideoModel, disabledTooltip = ...)` (`StudioDetailScreen.kt:264-289`, `GalleryDetailScreen.kt:135-160`). Gating semantics are identical; only the type name differs.
- **`onAnimateMedia` does not directly seed `referenceImageUri = asset.localUri` in the manual path.** The audit (line 213) said it does. Verified: the manual (`autoAnimate == false`) path (`MultimodalViewModel.kt:476-485`) sets `_mediaType = VIDEO` and a fresh `VideoGenerationSettings` without the URI; the URI is seeded only in the auto path via `StudioGenerationCoordinator.startDetailVideoGeneration` (`referenceImageUri = asset.localUri`, line 111). Both paths are still guarded by D2.
- **`ProviderCapabilities.supportsReferenceImage` (owner decision: remove).** Located at `GenerationSettings.kt:95` (audit §2 said 61, relative to the data class at 86). The audit flagged this as a possible-but-optional removal; the owner chose full removal (§2.8) after confirming zero production reads. The `ModelGenerationInputSchema` boolean it sourced from is likewise replaced by the `ImageInputRequirement` enum.
- **Line-number drift (symbols intact):** `applyCapabilities` is at `ApiModelCatalogRepositoryImpl.kt:855` (audit said 854). `FalVideoGenerationAdapter.isImageToVideoModel` ends at line 82 (audit said 83). `MediaConfigureScreen` "Use as Default" toggle is at line 286 (audit said 298) with the description copy at 291 (matches). `ModelConfigurationScreen` media-slot list is at lines 154-156 (audit §8 cited 132-138). All symbols exist; only line numbers drifted.
- **No `MediaProviderSyncHandlerTest.kt` exists yet.** The audit's Test Impact implies updating sync tests; the round-trip test is new (`SYNC-01..03`). Sibling handler tests (e.g. `MemoriesSyncHandlerTest.kt`) define the convention to follow.

---

## 10. Existing Tests Requiring Update (compile fallout)

Removing `ProviderCapabilities.supportsReferenceImage` and replacing `ModelGenerationInputSchema.supportsReferenceImage` with `imageInput` breaks compilation of existing tests that reference those symbols (verified by grep). The Implementation phase MUST update these (they are not net-new Red tests):

- `core/domain/.../usecase/media/GetProviderCapabilitiesUseCaseTest.kt` (lines 29, 51) — delete the `assertEquals(true, capabilities.supportsReferenceImage)` assertions.
- `core/domain/.../usecase/media/MediaToolDefinitionFactoryTest.kt` (lines 28, 60) — drop the `supportsReferenceImage = true` constructor argument.
- `feature/studio/.../MultimodalViewModelTest.kt` (lines 154, 250, 303) — drop the `supportsReferenceImage` argument from the `ProviderCapabilities` fakes.
- `feature/studio/.../MultimodalViewModelAnimateTest.kt` (line 104) — drop the `supportsReferenceImage` argument.
- `feature/inference/.../fal/FalOpenApiInputSchemaParserTest.kt` (line 57) — replace `assertTrue(schema.supportsReferenceImage)` with the `imageInput == ImageInputRequirement.*` assertion (covered by PARSE-01..03).

This is mechanical fallout, not behavior change; it does not introduce new scenarios.
