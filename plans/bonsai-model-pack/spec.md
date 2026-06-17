# Spec: Bonsai Model Pack (Download / Settings / Gating)

**Ticket ID:** `bonsai-model-pack`  
**Date:** 2026-06-04  
**Contracts read:** `ARCHITECTURE_RULES.md`, `DATA_LAYER_RULES.md`, `CODE_STYLE_RULES.md`, `UNIT_TESTING.md`  
**Prior art:** `plans/stable-diffusion-bonsai-t2i/spec.md` (inference/JNI context only; this ticket does not re-spec inference)  
**Scope:** Pack catalog, download gating, partial resume, settings UX, assign/delete/re-download. No Room schema migrations.

---

## 1. Feature Summary

### Problem

Bonsai local image generation requires **three** pinned artifacts treated as one product unit:

| Role | File | Catalog source |
|------|------|----------------|
| `diffusion` | `bonsai-ternary-4B-q5_K.gguf` | R2, `ModelType.IMAGE_GENERATION` slot asset |
| `vae` | `flux2-vae.safetensors` | HF utility `flux2_vae` |
| `text_encoder` | `Qwen3-4B-Q4_K_M.gguf` | HF utility `qwen3_text_encoder` |

Today they are three independent download targets. Utilities still enqueue when `IMAGE_GENERATION` is assigned to an API media provider (bug in `InitializeModelsUseCase` and `EnclaveNavGraph`). Settings shows three separate rows. Partial assign to local Bonsai is allowed without all files on disk. Delete only targets the diffusion row.

### Target behavior

1. **Catalog:** Root `modelPacks[]` groups the three members under stable pack id `bonsai-t2i`. Canonical file rows remain in root `assets[]`.
2. **Parser:** Build `ParsedModelCatalog` with packs + assets. **Exclude pack members from** free-floating `utilityAssets` and from slot download maps **unless** the pack is active for that slot (local `IMAGE_GENERATION` path, not API).
3. **API gating:** When `IMAGE_GENERATION` active config is API (`isLocal == false`), skip the **entire pack** on startup (`InitializeModelsUseCase`) and API onboarding download filter (`EnclaveNavGraph`) — zero pack files enqueued.
4. **Partial UX:** One pack row in Local AI Models with completeness `installedCount/totalCount` (e.g. `2/3`). Re-download/resume enqueues **only** missing or SHA+size-invalid members.
5. **Delete pack:** Soft-delete diffusion registry + utility registry for all members **and** delete physical files from the device models directory.
6. **Assign local:** Block `SetDefaultModelUseCase` for local `IMAGE_GENERATION` until pack is **fully verified on disk** (3/3). No auto-queue on assign. Exact user-facing error when blocked.
7. **Re-download eligibility:** Show download action when pack is soft-deleted **or** any member missing/invalid on disk (including “registry has presets but file gone” trap).
8. **Settings:** `LocalModelConfigureScreen` stays preset-only; pack management lives in Local AI Models sheet only.

### Non-goals

- JNI / `stable-diffusion.cpp` inference contract changes (see prior plan).
- New Room tables/columns (use existing `local_models`, `utility_models`, soft-delete flags).
- Pack id migration for shipped devices (feature not shipped).
- Auto-download on assign or on switching API → local.

### Pinned member metadata (shipping values)

| Member | SHA-256 | sizeInBytes |
|--------|---------|-------------|
| `bonsai-ternary-4B-q5_K.gguf` | `c415f811e4b39c1287ce529afbe99ff1ade0bb9ac0e5767019d9490a606b1983` | `2721233504` |
| `flux2-vae.safetensors` | `868fe7b343cc8f3a19dbcfcafbc3d5f888802be3f89bd81b65b3621a066ce8f3` | `336211292` |
| `Qwen3-4B-Q4_K_M.gguf` | `f6f851777709861056efcdad3af01da38b31223a3ba26e61a4f8bf3a2195813a` | `2497281312` |

---

## 2. File Structure Table

| Action | Path | Responsibility |
|--------|------|----------------|
| **Modify** | `model_config.json` | Add `modelPacks[]` for `bonsai-t2i`; keep existing `assets[]` rows unchanged |
| **Create** | `core/domain/.../model/download/ModelPackRole.kt` | Serializable roles: `diffusion`, `vae`, `text_encoder` |
| **Create** | `core/domain/.../model/download/ModelPackMemberRef.kt` | Member reference: `role`, `fileName`, optional `utilityType` |
| **Create** | `core/domain/.../model/download/ModelPack.kt` | Pack id, displayName, slot, members, resolved assets |
| **Create** | `core/domain/.../model/download/ParsedModelCatalog.kt` | Parsed catalog + filtering helpers for download/settings |
| **Create** | `core/domain/.../model/download/ModelPackCompleteness.kt` | `installedCount`, `totalCount`, `isComplete`, per-member status |
| **Create** | `core/domain/.../port/download/ModelPackVerifierPort.kt` | Disk verification for pack members (SHA + size) |
| **Create** | `core/domain/.../usecase/download/ResolveModelCatalogUseCase.kt` | Fetch remote config → `ParsedModelCatalog` |
| **Create** | `core/domain/.../usecase/download/VerifyModelPackCompletenessUseCase.kt` | Compute completeness for a pack id |
| **Create** | `core/domain/.../usecase/modelconfig/DeleteModelPackUseCase.kt` | Soft-delete all registries + delete all physical member files |
| **Create** | `core/domain/.../usecase/modelconfig/ReDownloadModelPackUseCase.kt` | Enqueue only missing/invalid pack members |
| **Create** | `core/domain/.../usecase/modelconfig/GetModelPacksForSettingsUseCase.kt` | Pack rows + completeness for Local AI Models UI |
| **Modify** | `core/domain/.../port/repository/ModelConfigFetcherPort.kt` | Return `ParsedModelCatalog` (or add `fetchParsedCatalog()`) |
| **Modify** | `core/data/.../download/ModelConfigFetcherImpl.kt` | Parse `modelPacks[]`; validate member refs; build `ParsedModelCatalog` |
| **Create** | `core/data/.../download/ModelPackVerifierImpl.kt` | Implements `ModelPackVerifierPort` using `ModelFileScannerPort` scan |
| **Modify** | `core/domain/.../usecase/download/InitializeModelsUseCase.kt` | Pack-aware slot/utility filtering; skip pack when `IMAGE_GENERATION` is API |
| **Modify** | `app/.../presentation/navigation/EnclaveNavGraph.kt` | API onboarding: exclude entire pack from `modelsToDownload` and `utilityAssets` |
| **Modify** | `core/domain/.../usecase/byok/SetDefaultModelUseCase.kt` | Block local `IMAGE_GENERATION` until pack `bonsai-t2i` is 3/3 verified |
| **Modify** | `core/domain/.../model/download/DownloadModelsResult.kt` | Optional `activeModelPacks` / pack download hints for orchestrator (no schema change) |
| **Modify** | `core/domain/.../port/download/ModelFileScannerPort.kt` | Add `deleteModelFileByLocalFileName(localFileName: String)` for utility members |
| **Modify** | `core/data/.../download/ModelFileScanner.kt` | Implement delete-by-filename for pack utility files |
| **Modify** | `core/data/.../download/DownloadFinalizeWorker.kt` | Finalize utility members downloaded as part of pack session (no new request kind required if SHA-deduped) |
| **Modify** | `feature/settings/.../SettingsModels.kt` | `ModelPackUi`, extend `LocalModelsSheetUiState` |
| **Modify** | `feature/settings/.../SettingsUiMappers.kt` | `ModelPackUiMapper` |
| **Modify** | `feature/settings/.../SettingsUiStateAssembler.kt` | Merge pack rows; hide pack-member utilities from per-asset lists |
| **Modify** | `feature/settings/.../LocalModelsBottomSheet.kt` | One card per pack with `2/3`, download/delete actions |
| **Modify** | `feature/settings/.../SettingsLocalModelsDelegate.kt` | Wire pack delete/re-download callbacks |
| **Modify** | `core/domain/.../usecase/settings/SettingsUseCases.kt` | Expose new use cases |
| **Modify** | `core/domain/.../usecase/settings/SettingsUseCasesImpl.kt` | DI wiring |
| **Modify** | `core/data/.../DataModule.kt` | Bind `ModelPackVerifierPort`, catalog fetch if split |
| **Create** | `core/domain/.../test/.../VerifyModelPackCompletenessUseCaseTest.kt` | Completeness matrix |
| **Create** | `core/domain/.../test/.../DeleteModelPackUseCaseTest.kt` | Delete all members |
| **Create** | `core/domain/.../test/.../ReDownloadModelPackUseCaseTest.kt` | Partial enqueue |
| **Modify** | `core/domain/.../test/.../InitializeModelsUseCaseTest.kt` | API pack skip scenarios |
| **Modify** | `core/data/.../test/.../ModelConfigFetcherImplTest.kt` | Pack JSON parse + member exclusion |
| **Modify** | `app/.../test/.../MainViewModelOnboardingTest.kt` or nav graph test | API onboarding zero pack downloads |
| **Modify** | `feature/settings/.../test/.../*` | Pack UI mapper/assembler tests |

---

## 3. API Contracts

### 3.1 `ModelPackRole` (enum)

**Path:** `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/download/ModelPackRole.kt`

```kotlin
enum class ModelPackRole(val apiValue: String) {
    DIFFUSION("diffusion"),
    VAE("vae"),
    TEXT_ENCODER("text_encoder"),
    ;
    companion object {
        fun fromApiValue(value: String): ModelPackRole?
    }
}
```

### 3.2 `ModelPackMemberRef` (catalog JSON → domain)

```kotlin
data class ModelPackMemberRef(
    val role: ModelPackRole,
    val fileName: String,
    val utilityType: UtilityType? = null, // required for vae + text_encoder; null for diffusion
)
```

Validation at parse time:

- `diffusion` member must reference a non-utility `assets[]` row with `defaultAssignments` containing `image_generation`.
- `vae` / `text_encoder` must reference utility rows; `utilityType` must match asset `utilityType`.
- All three `fileName` values must exist in `assets[]`.

### 3.3 `ModelPack` (resolved domain)

```kotlin
data class ModelPack(
    val packId: String,           // e.g. "bonsai-t2i"
    val displayName: String,      // e.g. "Bonsai Flux.2 Klein"
    val slot: ModelType,          // IMAGE_GENERATION for bonsai-t2i
    val members: List<ModelPackMember>,
)

data class ModelPackMember(
    val role: ModelPackRole,
    val asset: LocalModelAsset,   // resolved from assets[] by fileName
)
```

### 3.4 `ParsedModelCatalog`

```kotlin
data class ParsedModelCatalog(
    val assets: List<LocalModelAsset>,
    val packs: List<ModelPack>,
) {
    fun packById(packId: String): ModelPack?
    fun memberFileNamesForPack(packId: String): Set<String>
    fun isPackMemberAsset(asset: LocalModelAsset): Boolean
}
```

**Filtering contract** (used by `InitializeModelsUseCase`, onboarding, settings):

```kotlin
/**
 * @param imageGenerationIsApi true when active IMAGE_GENERATION config exists and isLocal == false
 */
fun filterForDownload(
    catalog: ParsedModelCatalog,
    imageGenerationIsApi: Boolean,
): DownloadCatalogSlice

data class DownloadCatalogSlice(
    val slotAssignments: Map<ModelType, LocalModelAsset>,
    val utilityAssets: List<LocalModelAsset>,
    val suppressedPackIds: Set<String>,
)
```

Rules:

| Condition | Slot map | `utilityAssets` | Pack members in `modelsToDownload` |
|-----------|----------|-----------------|-----------------------------------|
| `imageGenerationIsApi == true` | Exclude `IMAGE_GENERATION` slot asset | Exclude **all** members of packs bound to `IMAGE_GENERATION` (for `bonsai-t2i`: diffusion + vae + text_encoder) | None from that pack |
| Local or unconfigured `IMAGE_GENERATION` | Include slot diffusion asset | Include utilities **except** pack-bound `flux2_vae` and `qwen3_text_encoder` | Pack handled as unit in UI; download queue built from per-member scan |
| Other utilities (whisper, onnx, …) | N/A | Always included when not soft-deleted | Unchanged |

### 3.5 `ModelPackCompleteness`

```kotlin
data class ModelPackMemberStatus(
    val role: ModelPackRole,
    val fileName: String,
    val isVerifiedOnDisk: Boolean,
)

data class ModelPackCompleteness(
    val packId: String,
    val installedCount: Int,
    val totalCount: Int,
    val isComplete: Boolean, // installedCount == totalCount && all verified
    val members: List<ModelPackMemberStatus>,
)
```

Verification uses the same rules as `ModelFileScanner` / `CheckModelEligibilityUseCase`: file exists, length equals `sizeInBytes`, SHA-256 matches metadata (invalid hash ⇒ not installed).

### 3.6 Ports

**`ModelConfigFetcherPort`** — extend (backward-compatible default impl delegates):

```kotlin
suspend fun fetchParsedCatalog(): Result<ParsedModelCatalog>
// Existing fetchRemoteConfig() may delegate to fetchParsedCatalog().assets for callers not yet migrated
```

**`ModelPackVerifierPort`**

```kotlin
interface ModelPackVerifierPort {
    suspend fun verifyPack(pack: ModelPack): ModelPackCompleteness
    suspend fun verifyPackById(catalog: ParsedModelCatalog, packId: String): ModelPackCompleteness?
}
```

**`ModelFileScannerPort`** — add:

```kotlin
suspend fun deleteModelFileByLocalFileName(localFileName: String)
```

Deletes the file and related `.tmp` / meta temp siblings under the models directory (mirror safety rules in `ModelFileScanner`).

### 3.7 Use cases (signatures)

**`ResolveModelCatalogUseCase`**

```kotlin
class ResolveModelCatalogUseCase @Inject constructor(
    private val modelConfigFetcher: ModelConfigFetcherPort,
) {
    suspend operator fun invoke(): Result<ParsedModelCatalog>
}
```

**`VerifyModelPackCompletenessUseCase`**

```kotlin
class VerifyModelPackCompletenessUseCase @Inject constructor(
    private val modelPackVerifier: ModelPackVerifierPort,
    private val resolveModelCatalog: ResolveModelCatalogUseCase,
) {
    suspend operator fun invoke(packId: String): Result<ModelPackCompleteness>
}
```

**`SetDefaultModelUseCase`** — add gate (constants in companion):

```kotlin
// When modelType == IMAGE_GENERATION && localConfigId != null && asset is Bonsai diffusion:
private const val PACK_NOT_COMPLETE_MESSAGE =
    "Bonsai image pack is not fully downloaded. Download all pack files from Local AI Models before assigning."

require(verifyModelPackCompleteness("bonsai-t2i").isComplete) { PACK_NOT_COMPLETE_MESSAGE }
// No download scheduler call in this use case
```

Pack id `bonsai-t2i` is read from catalog pack definition tied to the assigned `localConfigId` (diffusion member), not hard-coded only by SHA, so future packs can reuse the same gate pattern.

**`DeleteModelPackUseCase`**

```kotlin
class DeleteModelPackUseCase @Inject constructor(
    private val deleteLocalModel: DeleteLocalModelUseCase,
    private val utilityModelRepository: UtilityModelRepositoryPort,
    private val modelFileScanner: ModelFileScannerPort,
    private val resolveModelCatalog: ResolveModelCatalogUseCase,
    private val localModelRepository: LocalModelRepositoryPort,
) {
    /**
     * Soft-deletes diffusion (configs cleared, entity preserved) and utilities (softDelete).
     * Deletes physical files for every member fileName.
     * @param replacement* passed through when diffusion deletion needs slot reassignment.
     */
    suspend operator fun invoke(
        packId: String,
        replacementLocalConfigId: LocalModelConfigurationId? = null,
        replacementApiConfigId: ApiModelConfigurationId? = null,
        replacementMediaProviderId: MediaProviderId? = null,
    ): Result<Unit>
}
```

For `IMAGE_GENERATION` reassignment when deleting an assigned pack: caller (`SettingsLocalModelsDelegate`) must supply API/media replacement if diffusion was default — same contract as `DeleteLocalModelUseCase`.

**`ReDownloadModelPackUseCase`**

```kotlin
class ReDownloadModelPackUseCase @Inject constructor(
    private val downloadWorkScheduler: DownloadWorkSchedulerPort,
    private val verifyModelPackCompleteness: VerifyModelPackCompletenessUseCase,
    private val resolveModelCatalog: ResolveModelCatalogUseCase,
) {
    /**
     * Enqueues one DownloadWorkRequest per member that is NOT verified on disk.
     * Returns session ids for UI progress (existing per-file download UI).
     */
    suspend operator fun invoke(packId: String): Result<List<ScheduledDownload>>
}
```

**`GetModelPacksForSettingsUseCase`**

```kotlin
data class ModelPackSettingsRow(
    val pack: ModelPack,
    val completeness: ModelPackCompleteness,
    val isSoftDeleted: Boolean,
    val showDownloadAction: Boolean,
)

class GetModelPacksForSettingsUseCase @Inject constructor(...) {
    suspend operator fun invoke(): List<ModelPackSettingsRow>
}
```

`showDownloadAction = isSoftDeleted || !completeness.isComplete`

### 3.8 Presentation (`feature/settings`)

```kotlin
data class ModelPackUi(
    val packId: String,
    val displayName: String,
    val completenessLabel: String, // e.g. "2/3"
    val installedCount: Int,
    val totalCount: Int,
    val showDownloadAction: Boolean,
    val isSoftDeleted: Boolean,
)

data class LocalModelsSheetUiState(
    val isVisible: Boolean = false,
    val modelPacks: List<ModelPackUi> = emptyList(),
    val models: List<LocalModelAssetUi> = emptyList(), // excludes pack-member utilities + diffusion when shown as pack
    val availableDownloads: List<LocalModelAssetUi> = emptyList(),
    val selectedAsset: LocalModelAssetUi? = null,
)
```

During active download, reuse existing download progress UI: pack display name as group label, per-member file progress unchanged.

---

## 4. `model_config.json` Schema

Root document shape:

```json
{
  "assets": [ /* existing rows unchanged */ ],
  "modelPacks": [
    {
      "packId": "bonsai-t2i",
      "displayName": "Bonsai Flux.2 Klein",
      "slot": "image_generation",
      "members": [
        { "role": "diffusion", "fileName": "bonsai-ternary-4B-q5_K.gguf" },
        { "role": "vae", "fileName": "flux2-vae.safetensors", "utilityType": "flux2_vae" },
        { "role": "text_encoder", "fileName": "Qwen3-4B-Q4_K_M.gguf", "utilityType": "qwen3_text_encoder" }
      ]
    }
  ]
}
```

Parser rules:

- Unknown keys ignored (`ignoreUnknownKeys = true` already used).
- `modelPacks` optional; empty list ⇒ legacy behavior (no packs).
- `slot` serializes to `ModelType.IMAGE_GENERATION` via existing enum JSON names.
- Duplicate `packId` or duplicate `fileName` across packs ⇒ parse failure with explicit error message.
- Member `fileName` not found in `assets[]` ⇒ parse failure.

`ModelConfigFetcherImpl` continues to validate Bonsai companion HF metadata via existing `BONSAI_COMPANION_UTILITY_TYPES` check.

---

## 5. Ordered Implementation Tasks

| Task ID | Description | Scenario IDs satisfied |
|---------|-------------|------------------------|
| **MPK-T01** | Add domain types: `ModelPackRole`, `ModelPackMemberRef`, `ModelPack`, `ModelPackMember`, `ParsedModelCatalog`, `ModelPackCompleteness`, `DownloadCatalogSlice` | MPK-001, MPK-002 |
| **MPK-T02** | Extend `model_config.json` with `modelPacks[]` for `bonsai-t2i` | MPK-001 |
| **MPK-T03** | Implement `fetchParsedCatalog()` in `ModelConfigFetcherImpl` with validation and asset resolution | MPK-001, MPK-002, MPK-003 |
| **MPK-T04** | Implement `ParsedModelCatalog.filterForDownload()` and unit tests | MPK-004, MPK-005, MPK-006 |
| **MPK-T05** | Refactor `InitializeModelsUseCase` to use catalog filter; skip full pack when `IMAGE_GENERATION` is API | MPK-004, MPK-005, MPK-016 |
| **MPK-T06** | Fix `EnclaveNavGraph` API onboarding filter: zero pack downloads (not utility-only whitelist) | MPK-005, MPK-016 |
| **MPK-T07** | Add `ModelPackVerifierPort` + `ModelPackVerifierImpl` | MPK-007, MPK-008, MPK-009 |
| **MPK-T08** | Add `VerifyModelPackCompletenessUseCase` | MPK-007, MPK-008, MPK-009, MPK-014 |
| **MPK-T09** | Gate `SetDefaultModelUseCase` local `IMAGE_GENERATION` on full pack; no auto-download | MPK-010, MPK-011, MPK-012 |
| **MPK-T10** | Add `ModelFileScannerPort.deleteModelFileByLocalFileName` + implementation | MPK-013 |
| **MPK-T11** | Implement `DeleteModelPackUseCase` | MPK-013 |
| **MPK-T12** | Implement `ReDownloadModelPackUseCase` (partial member enqueue only) | MPK-009, MPK-015 |
| **MPK-T13** | Implement `GetModelPacksForSettingsUseCase` + `ModelPackUiMapper` + assembler filters | MPK-007, MPK-014, MPK-015 |
| **MPK-T14** | Update `LocalModelsBottomSheet` pack card UI (`2/3`, download, delete) | MPK-007, MPK-014 |
| **MPK-T15** | Wire `SettingsUseCases`, delegate callbacks, DI | MPK-010–MPK-015 |
| **MPK-T16** | Adjust `DownloadFinalizeWorker` if pack utility finalize ordering regresses in tests | MPK-009 |
| **MPK-T17** | Full test suite per `test_spec.md`; ktlint/detekt on touched modules | All |

---

## 6. Drift / Escalation Rules

| Trigger | Action |
|---------|--------|
| Parser must add Room columns for pack state | **Stop.** Escalate for Schema Lock waiver; prefer disk+registry derived state only. |
| Product requests auto-download on assign | **Stop.** Requires ticket amendment; current lock forbids it. |
| New pack besides `bonsai-t2i` | Extend `modelPacks[]` only; reuse same code paths; do not fork Bonsai-only hacks in UI. |
| `ModelConfigFetcherImpl` validation fails in CI | Fix JSON pins; do not relax SHA/size checks. |
| Inference/JNI changes requested | Route to `stable-diffusion-bonsai-t2i` plan; out of scope here. |
| Assign gate message copy changes | Update **both** `spec.md` and `test_spec.md` Then clauses in same PR. |
| Contract conflict with `AGENTS.md` / `/contracts` | Reply: `Contract violation detected in [file]. Request explicit 'Contract Waiver' from user.` and stop. |

---

## 7. Validation Commands (implementation phase)

```bash
./gradlew :core:domain:testDebugUnitTest --tests "*ModelPack*" --tests "*InitializeModelsUseCase*" --tests "*SetDefaultModelUseCase*" --no-daemon
./gradlew :core:data:testDebugUnitTest --tests "*ModelConfigFetcherImpl*" --no-daemon
./gradlew :feature:settings:testDebugUnitTest --tests "*LocalModels*" --tests "*ModelPack*" --no-daemon
./gradlew :app:testDebugUnitTest --tests "*Onboarding*" --no-daemon
./gradlew :core:domain:assembleDebug :core:data:assembleDebug :app:assembleDebug --no-daemon
./gradlew ktlintCheck detekt --no-daemon
```

---

## 8. Self-Review Checklist (plan author)

- [x] All three files named and pinned in catalog section
- [x] API skip covers init + onboarding (bug called out)
- [x] Pack members excluded from free-floating utilities when pack active
- [x] Assign block message literal locked
- [x] Delete includes physical files for utilities
- [x] No Room migration, no inference re-spec
- [x] Task IDs map to scenario IDs
- [x] No TBD/TODO placeholders