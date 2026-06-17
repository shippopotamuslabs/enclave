# Juggernaut XL QNN

## Feature Summary

Add a third local on-device image model pack, `juggernaut-xl-t2i`, on the existing Local Dream-style QNN SDXL-family path. The change must:

- keep `sd15-t2i` as the default and recommended local `IMAGE_GENERATION` model
- keep `sdxl-t2i` (RealVis XL DMD2) as the Gen3+ local `IMAGE_GENERATION` option
- add `juggernaut-xl-t2i` as the default local `IMAGE_EDIT` model on Gen3+ devices
- allow all three packs to be installed and complete on the same device at the same time
- extract Juggernaut into its own directory `juggernaut-qnn/` (never share `sdxl-qnn/`)
- gate Juggernaut downloads and assignment with the same Gen3+ + QNN policy as RealVis SDXL
- apply Juggernaut-specific generation policy (non-DMD2): scheduler `dpm`, steps `20`–`30` default `24`, CFG `4.0`, resolution `1024x1024`
- require `vae_encoder.bin` before assigning Juggernaut to `IMAGE_EDIT`
- expose Studio settings from the active assigned local image model policy
- fail or clamp unsupported local settings before native launch

Verified Juggernaut pack metadata for the implementation target:

| Field | Value |
|---|---|
| Hugging Face repo | `xororz/sdxl-qnn` |
| Preferred file | `juggernaut_qnn2.28_8gen3.zip` |
| HF path | `juggernaut_qnn2.28_8gen3.zip` |
| SHA256 | `b27231823434c4e4184a61f77401227264a5308c6846c6fc628c37583e76413c` |
| Size | `3747687306` bytes |

Observed source constraints from discovery:

- `model_config.json` already models `sd15-t2i` and `sdxl-t2i` as diffusion members plus QNN utility zip members.
- `ParsedModelCatalog.packMemberSha256sForSlot(ModelType)` already supports arbitrary pack slots; Juggernaut must use `ModelType.IMAGE_EDIT`.
- `filterForDownload(...)` currently suppresses only `IMAGE_GENERATION` packs when that slot is API-backed; it must gain parallel `IMAGE_EDIT` suppression without blocking the other slot's packs.
- `LocalImageGenerationPaths.REQUIRED_SDXL_QNN_FILES` already matches the SDXL-family QNN artifact contract Juggernaut ships.
- `SetDefaultModelUseCase` already validates `IMAGE_EDIT` assignments with `requireImg2ImgEncoder = true` and `LocalImageGenerationPaths.hasImg2ImgCapability(...)`.
- `AutoAssignLocalModelsUseCase` currently falls back to media providers for `IMAGE_EDIT`; it must prefer an installed local config whose `defaultAssignments` contains `IMAGE_EDIT`.
- Native scheduler plumbing already exists end-to-end; Juggernaut must emit `dpm`, not inherit RealVis `lcm`.

## Boundaries

- `:core:domain` owns Snapdragon tiering, model descriptors, capability policy, assignment policy, catalog download slicing, and local request validation.
- `:core:data` owns catalog parsing, startup download filtering, pack extraction, installed-pack verification, and finalize-time registry writes.
- `:feature:settings` only renders pack state and assignable local model options from domain/data outputs.
- `:feature:studio` only renders the active model capability surface and clamps stale UI state to that surface.
- `:feature:inference` only converts validated domain policy into a native request.
- `:local-image-gen-android` only forwards native request fields and builds the backend JSON body.
- Harness scripts under `scripts/run-enclave-diffusion-harness.sh` and `scripts/build-enclave-diffusion-harness.sh` remain available for manual native validation; no harness code change is required unless Red tests expose a scheduler regression.
- No Room tables or columns change.
- No Vulkan, GGML, or SDXL CPU fallback work is allowed.

## File Structure

The implementation phase is limited to these files. If another runtime file becomes necessary, stop and amend this plan before editing.

| Path | Action | Responsibility |
|---|---|---|
| `model_config.json` | Modify | Add the verified Juggernaut diffusion asset, utility asset, and `juggernaut-xl-t2i` pack with `slot = image_edit` and `defaultAssignments = ["image_edit"]`. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/config/UtilityType.kt` | Modify | Add `JUGGERNAUT_XL_QNN_PACK` and its display label. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/media/LocalImageGenerationModelRegistry.kt` | Modify | Add `JUGGERNAUT_XL_QNN` descriptor metadata and SHA constant. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/media/LocalImageGenerationPaths.kt` | Modify | Add `juggernaut-qnn` subdir constant and `hasJuggernautXlQnnArtifacts(...)` using `REQUIRED_SDXL_QNN_FILES`. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/media/LocalImageGenerationModelPolicy.kt` | Modify | Add Juggernaut policy literals (`dpm`, CFG `4.0`, steps `20`–`30`, default `24`). |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/media/LocalImageGenerationCapabilities.kt` | Modify | Add `juggernautXlNpuSupported: LocalImageGenTriState` and include it in `isAnyBackendUsable`. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/media/LocalImageGenerationCapabilityPolicy.kt` | Modify | Resolve Juggernaut eligibility with the same Gen3+ gate as SDXL and populate `disabledReasonsByModelId`. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/download/JuggernautXlQnnPackVariant.kt` | Create | Pin the verified Juggernaut zip metadata and expose lookup by SHA / default variant. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/download/ParsedModelCatalog.kt` | Modify | Extend `filterForDownload(...)` to suppress `IMAGE_EDIT` slot packs independently and union local-image pack-member SHA sets. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/download/ImageGenerationPackMemberFilter.kt` | Modify | Extend exclusion helpers to cover `IMAGE_EDIT` pack members via `imageEditPackMemberSha256s`. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/download/DownloadModelsResult.kt` | Modify | Add `imageEditPackMemberSha256s: Set<String>`. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/download/InitializeModelsUseCase.kt` | Modify | Include or suppress `juggernaut-xl-t2i` startup downloads from domain policy; union IMAGE_EDIT pack utilities; filter Gen3+-only packs on Gen2. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/modelconfig/GetModelPacksForSettingsUseCase.kt` | Modify | Surface Juggernaut pack completeness with Gen3+ disabled reason. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/byok/SetDefaultModelUseCase.kt` | Modify | Gate `JUGGERNAUT_XL_QNN` assignment on Gen3+ with the exact blocked reason. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/settings/AutoAssignLocalModelsUseCase.kt` | Modify | Prefer installed local configs with `IMAGE_EDIT` in `defaultAssignments` for the edit slot. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/media/ResolveAssignedImageGenerationTargetUseCase.kt` | Modify | No logic change expected; keep IMAGE_EDIT slot routing. Covered by tests for Juggernaut path resolution. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/download/ModelConfigFetcherImpl.kt` | Modify | Parse the added Juggernaut asset and pack members from `model_config.json`. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/download/ModelFileScanner.kt` | Modify | Count `juggernaut-qnn` extracted artifacts as installed and incomplete when missing. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/download/ModelPackVerifierImpl.kt` | Modify | Verify the extracted Juggernaut QNN pack directory by `JUGGERNAUT_XL_QNN_PACK`. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/download/DownloadFinalizeWorker.kt` | Modify | Finalize Juggernaut downloads, install the extracted QNN pack, and register the local asset without replacing existing slot defaults. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/download/UtilityModelFileResolver.kt` | Modify | Resolve installed Juggernaut QNN pack directories and local diffusion paths by SHA. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/download/Sd15QnnPackInstaller.kt` | Modify | Add `JUGGERNAUT_XL_QNN_PACK` install branch targeting `juggernaut-qnn/`. |
| `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/ModelConfigurationScreen.kt` | Modify | Map `juggernaut-local-config` to `juggernaut-xl-t2i` for assignment eligibility filtering. |
| `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/LocalModelsBottomSheet.kt` | Modify | Render blocked Juggernaut download state on Gen2 with the exact Gen3+ disabled reason. |
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/media/LocalImageGenerationCapabilityPortImpl.kt` | Modify | Populate `juggernautXlNpuSupported` and disabled reasons for Gen2 hardware. |
| `core/domain/src/testFixtures/kotlin/com/browntowndev/enclave/domain/model/download/ModelPackTestFixtures.kt` | Modify | Add Juggernaut pack fixtures and three-pack catalog helper. |
| `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/model/download/JuggernautXlQnnPackCatalogTest.kt` | Create | Lock the verified Juggernaut zip metadata and SHA lookup. |
| `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/model/download/ParsedModelCatalogTest.kt` | Modify | Cover `IMAGE_EDIT` slot SHA derivation and independent slot suppression. |
| `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/model/media/LocalImageGenerationCapabilityPolicyTest.kt` | Modify | Cover Gen2 block and Gen3+ allow for `JUGGERNAUT_XL_QNN`. |
| `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/model/media/LocalImageGenerationModelPolicyTest.kt` | Create | Cover Juggernaut policy literals and step normalization. |
| `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/model/media/LocalImageGenerationPathsTest.kt` | Modify | Cover `juggernaut-qnn` artifact detection separate from `sdxl-qnn`. |
| `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/download/InitializeModelsUseCaseTest.kt` | Modify | Cover three-pack startup inclusion/suppression and IMAGE_EDIT API suppression. |
| `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/byok/SetDefaultModelUseCaseTest.kt` | Modify | Cover Gen2 rejection, Gen3+ success, incomplete pack, and missing `vae_encoder.bin` for Juggernaut IMAGE_EDIT. |
| `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/modelconfig/GetModelPacksForSettingsUseCaseTest.kt` | Modify | Cover Juggernaut disabled pack reason and eligibility on Gen2 vs Gen3+. |
| `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/settings/AutoAssignLocalModelsUseCaseTest.kt` | Modify | Cover auto-assignment of `juggernaut-local-config` to `IMAGE_EDIT`. |
| `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/media/ResolveAssignedImageGenerationTargetUseCaseTest.kt` | Modify | Cover Juggernaut IMAGE_EDIT target resolution to `files/models/juggernaut-qnn`. |
| `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/download/ModelConfigFetcherImplTest.kt` | Modify | Cover parsing `juggernaut-xl-t2i` from `model_config.json`. |
| `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/download/Sd15QnnPackInstallerTest.kt` | Modify | Cover Juggernaut extraction path and delayed zip deletion. |
| `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/download/ModelPackVerifierImplTest.kt` | Modify | Cover installed SD1.5, SDXL, and Juggernaut extracted directories simultaneously. |
| `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/download/DownloadFinalizeWorkerTest.kt` | Modify | Cover Juggernaut registration without overriding existing `IMAGE_EDIT` default. |
| `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/download/UtilityModelFileResolverTest.kt` | Modify | Cover resolving `files/models/juggernaut-qnn`. |
| `feature/settings/src/test/kotlin/com/browntowndev/enclave/feature/settings/SettingsMediaTasksRedTest.kt` | Modify | Cover `SET-006` via assembled `ModelConfigurationScreen` IMAGE_EDIT assignment options. |
| `feature/settings/src/test/kotlin/com/browntowndev/enclave/feature/settings/ModelPackUiMapperTest.kt` | Modify | Cover disabled reason mapping for blocked Juggernaut pack rows. |
| `feature/settings/src/test/kotlin/com/browntowndev/enclave/feature/settings/SettingsViewModelTest.kt` | Modify | Cover capability-driven Juggernaut pack row updates. |
| `feature/studio/src/test/kotlin/com/browntowndev/enclave/feature/studio/MultimodalViewModelTest.kt` | Modify | Cover per-model capability exposure when IMAGE_EDIT uses Juggernaut. |
| `feature/studio/src/test/kotlin/com/browntowndev/enclave/feature/studio/coordinators/StudioCapabilitiesCoordinatorTest.kt` | Modify | Cover stale Juggernaut step clamping on model switch. |
| `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/media/LocalImageGenerationDevicePolicyTest.kt` | Modify | Cover `juggernautXlNpuSupported` diagnostics on Gen2 vs Gen3+. |
| `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/media/local/LocalStableDiffusionImageGeneratorFailureTest.kt` | Modify | Cover Juggernaut native request literals and step clamping. |
| `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/ImageGenerationPortImplTest.kt` | Modify | Cover local IMAGE_EDIT request propagation for Juggernaut. |
| `local-image-gen-android/src/test/cpp/generate_request_body_test.cpp` | Modify | Add assertion that `scheduler = "dpm"` is emitted for Juggernaut requests (`INF-006`, `MUT-005`). |

## API Contracts And Interfaces

### Local image model descriptor contract

Add `LocalImageGenerationModelId.JUGGERNAUT_XL_QNN`.

Required descriptor fields remain unchanged. Pinned descriptors after implementation:

- `SD15_QNN`
  - `packId = "sd15-t2i"`
  - `relativeModelDir = "sd15-qnn"`
  - `catalogSha256 = "c2f6e92f9d08d69cc673a1003528ac8199274b3c0eaec88d5fbefe5af67bd42b"`
- `SDXL_QNN` (RealVis XL DMD2)
  - `packId = "sdxl-t2i"`
  - `relativeModelDir = "sdxl-qnn"`
  - `catalogSha256 = "5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83"`
- `JUGGERNAUT_XL_QNN`
  - `packId = "juggernaut-xl-t2i"`
  - `relativeModelDir = "juggernaut-qnn"`
  - `catalogSha256 = "b27231823434c4e4184a61f77401227264a5308c6846c6fc628c37583e76413c"`
  - `displayName = "Juggernaut XL (NPU)"`
  - `utilityType = UtilityType.JUGGERNAUT_XL_QNN_PACK`
  - `minInstallBytes = 3_700_000_000L`

Catalog configuration contract for Juggernaut:

- `configId = "juggernaut-local-config"`
- `defaultAssignments = [ModelType.IMAGE_EDIT]`

RealVis SDXL configuration remains:

- `configId = "sdxl-local-config"`
- `defaultAssignments = [ModelType.IMAGE_GENERATION]`

### Local image model policy contract

Extend `LocalImageGenerationModelPolicy.policyFor(...)` with Juggernaut literals:

| Model | Resolutions | Steps | Default | Fixed | CFG | Scheduler |
|---|---|---|---|---|---|---|
| `SD15_QNN` | `["512x512"]` | `[]` | `20` | `20` | `7.5` | `dpm` |
| `SDXL_QNN` | `["1024x1024"]` | `[8, 12, 16, 20, 24, 30]` | `8` | `null` | `1.5` | `lcm` |
| `JUGGERNAUT_XL_QNN` | `["1024x1024"]` | `[20, 22, 24, 26, 28, 30]` | `24` | `null` | `4.0` | `dpm` |

Behavior rules:

- Juggernaut exposes the exact step options above in Studio.
- Juggernaut stale step values clamp to `24`.
- Juggernaut must not reuse SDXL `lcm` / CFG `1.5` / default step `8`.
- Unsupported resolution never clamps across model families; it fails with a domain validation error before native launch.

Provider capability outputs:

- active Juggernaut -> `supportedImageResolutions == ["1024x1024"]`, `supportedImageSteps == [20, 22, 24, 26, 28, 30]`, `defaultImageStep == 24`

### Hardware eligibility contract

Extend `LocalImageGenerationCapabilities`:

- `juggernautXlNpuSupported: LocalImageGenTriState`

Pinned eligibility outcomes (unchanged global rules, extended per-model map):

- Non-arm64 -> block all local image packs.
- `UNKNOWN` Snapdragon tier -> block all local image packs.
- QNN probe failure -> block all local image packs.
- RAM below `8 GiB` -> block all local image packs with diagnostic `Insufficient RAM for local diffusion (need >= 8 GiB).`
- `GEN1` + QNN -> allow SD1.5 only.
- `GEN2` + QNN -> allow SD1.5 only and block SDXL and Juggernaut with `Requires Snapdragon 8 Gen 3+ with QNN.`
- `GEN3_PLUS` + QNN -> allow SD1.5, SDXL, and Juggernaut.

`disabledReasonsByModelId` must contain:

- `LocalImageGenerationModelId.SDXL_QNN -> "Requires Snapdragon 8 Gen 3+ with QNN."` on Gen2
- `LocalImageGenerationModelId.JUGGERNAUT_XL_QNN -> "Requires Snapdragon 8 Gen 3+ with QNN."` on Gen2

### Assignment contract

`SetDefaultModelUseCase` must reject blocked Juggernaut assignments in domain code.

Pinned outcomes:

- blocked Juggernaut assignment on Gen2 throws `IllegalArgumentException("Requires Snapdragon 8 Gen 3+ with QNN.")`
- incomplete Juggernaut pack throws `Local image pack is not fully downloaded. Download all pack files from Local AI Models before assigning.`
- Juggernaut `IMAGE_EDIT` assignment without `vae_encoder.bin` throws `Local image edit requires vae_encoder.bin in the model pack.`
- successful Juggernaut assignment on Gen3+ preserves existing remote / local slot semantics for other slots

`AutoAssignLocalModelsUseCase` contract for `ModelType.IMAGE_EDIT`:

- when a local asset configuration lists `IMAGE_EDIT` in `defaultAssignments` and the asset is registered, pick that `localConfigId` before any media provider
- preferred config is `juggernaut-local-config` once the Juggernaut pack is registered

### Catalog download slice contract

`filterForDownload(catalog, suppressImageGenerationSlot, suppressImageEditSlot)` must:

- place the Juggernaut diffusion asset in `slotAssignments[ModelType.IMAGE_EDIT]` when `suppressImageEditSlot == false`
- suppress only `slot == image_edit` pack members when `suppressImageEditSlot == true`
- suppress only `slot == image_generation` pack members when `suppressImageGenerationSlot == true`
- never suppress SD1.5 / SDXL members because `IMAGE_EDIT` is API-backed, and never suppress Juggernaut because `IMAGE_GENERATION` is API-backed

`DownloadModelsResult` must carry:

- `imageGenerationPackMemberSha256s` (existing)
- `imageEditPackMemberSha256s` (new)

### Local request validation contract

Pinned validation error for unsupported Juggernaut resolution:

- `Unsupported local image generation resolution '512x512' for model JUGGERNAUT_XL_QNN. Supported resolutions: [1024x1024].`

Pinned native request for Juggernaut with default settings:

- `width = 1024`, `height = 1024`, `steps = 24`, `cfgScale = 4.0`, `scheduler = "dpm"`

Pinned stale-step normalization:

- requested step `23` clamps to `24`

### Extraction contract

- SDXL RealVis extracted directory: `files/models/sdxl-qnn`
- Juggernaut extracted directory: `files/models/juggernaut-qnn`
- Zip `juggernaut_qnn2.28_8gen3.zip` deletes only after `LocalImageGenerationPaths.hasJuggernautXlQnnArtifacts(files/models/juggernaut-qnn)` is `true`
- Installing Juggernaut must not mutate files under `files/models/sdxl-qnn`

## Data Model And Schema Changes

- No Room schema changes.
- No DataStore schema changes.
- `model_config.json` changes only:
  - add the Juggernaut diffusion asset with `juggernaut-local-config`
  - add the Juggernaut QNN utility asset
  - add the `juggernaut-xl-t2i` pack with `slot = "image_edit"` and diffusion + utility members
- `UtilityType` gains one new enum entry: `JUGGERNAUT_XL_QNN_PACK` (`apiValue = "juggernaut_xl_qnn_pack"`)

## Download And Catalog Strategy

- Keep three independent image packs:
  - `sd15-t2i` (`IMAGE_GENERATION`)
  - `sdxl-t2i` (`IMAGE_GENERATION`)
  - `juggernaut-xl-t2i` (`IMAGE_EDIT`)
- Each pack carries one assignable diffusion asset and one QNN utility zip member (Juggernaut uses the same zip for both roles, matching the SDXL pattern).
- `InitializeModelsUseCase` startup download slices:
  - API `IMAGE_GENERATION` assignment suppresses `sd15-t2i` and `sdxl-t2i` only
  - API `IMAGE_EDIT` assignment suppresses `juggernaut-xl-t2i` only
  - blocked hardware suppresses only the blocked pack(s)
  - Gen2 suppresses `sdxl-t2i` and `juggernaut-xl-t2i` only
  - Gen3+ includes all three packs when their slots are local
- All three extracted directories may coexist; completeness is evaluated per pack ID.

## Assignment And UI Gating Strategy

- Settings pack UI consumes pack-level availability and disabled reason from `GetModelPacksForSettingsUseCase`.
- `LocalModelsBottomSheet` renders disabled Juggernaut download state on Gen2 with `Requires Snapdragon 8 Gen 3+ with QNN.`
- `ModelConfigurationScreen` surfaces Juggernaut only under `ModelType.IMAGE_EDIT` local options when installed, complete, and assignment-eligible.
- RealVis SDXL remains under `ModelType.IMAGE_GENERATION` local options only.
- UI filtering is advisory only; `SetDefaultModelUseCase` remains the final guardrail.

## Native Scheduler Propagation Strategy

- `LocalStableDiffusionImageGenerator` must derive Juggernaut `scheduler = "dpm"` from model policy.
- Existing JNI / C++ scheduler forwarding remains unchanged.
- Host C++ test `generate_request_body_test.cpp` must assert `"scheduler":"dpm"` for a Juggernaut-shaped request so `MUT-005` can fail a hardcoded `"lcm"` implementation.

## Ordered Implementation Tasks

1. Update the catalog contract and pinned metadata in `model_config.json`, `UtilityType.kt`, `LocalImageGenerationModelRegistry.kt`, and `JuggernautXlQnnPackVariant.kt`.
   Scenarios: `DL-001`, `DL-010`, `DL-011`

2. Extend `LocalImageGenerationPaths`, pack catalog tests, and path tests for the separate `juggernaut-qnn` extraction directory.
   Scenarios: `DL-010`, `DL-011`, `DL-012`, `MUT-006`

3. Extend capability policy, capabilities model, and inference port for `juggernautXlNpuSupported` with Gen3+ gating identical to SDXL.
   Scenarios: `CAP-005`, `CAP-006`, `CAP-009`, `CAP-010`, `MUT-002`

4. Add Juggernaut generation policy literals and provider capability mapping in `LocalImageGenerationModelPolicy.kt`.
   Scenarios: `STU-009`, `STU-010`, `STU-011`, `INF-006`, `INF-007`, `MUT-005`

5. Extend catalog download slicing (`ParsedModelCatalog.kt`, `ImageGenerationPackMemberFilter.kt`, `DownloadModelsResult.kt`) for independent `IMAGE_EDIT` suppression and pack-member SHA tracking.
   Scenarios: `DL-002`, `DL-003`, `DL-004`, `DL-014`

6. Update `InitializeModelsUseCase` tier-aware filters so Gen2 excludes both Gen3+ packs and Gen3+ includes all three when eligible.
   Scenarios: `DL-002`, `DL-003`, `DL-004`, `DL-014`

7. Generalize QNN pack install, scan, verify, resolve, and finalize behavior for Juggernaut alongside SD1.5 and SDXL.
   Scenarios: `DL-008`, `DL-009`, `DL-010`, `DL-011`, `DL-012`, `DL-013`, `ASG-001`

8. Enforce domain-side assignment gating, img2img encoder checks, and auto-assignment for Juggernaut on `IMAGE_EDIT`.
   Scenarios: `SET-006`, `SET-007`, `SET-008`, `SET-009`, `SET-010`, `SET-011`

9. Surface Juggernaut pack-disabled reason and IMAGE_EDIT assignment options in Settings UI without moving policy into Compose.
   Scenarios: `SET-006`, `SET-007`

10. Drive Studio capability exposure and stale-setting clamps for active Juggernaut IMAGE_EDIT assignments.
    Scenarios: `STU-009`, `STU-010`, `STU-011`

11. Propagate Juggernaut `dpm` scheduler through inference and extend the host C++ JSON-body test.
    Scenarios: `INF-006`, `MUT-005`

12. Add or extend every listed test file, then run targeted test tasks and the required validation sweep.
    Scenarios: `CAP-005` through `MUT-006`

## Validation Commands

Targeted tests for changed classes:

- `./gradlew :core:domain:testDebugUnitTest --tests "*JuggernautXlQnnPackCatalogTest" --tests "*ParsedModelCatalogTest" --tests "*LocalImageGenerationCapabilityPolicyTest" --tests "*LocalImageGenerationModelPolicyTest" --tests "*LocalImageGenerationPathsTest" --tests "*InitializeModelsUseCaseTest" --tests "*SetDefaultModelUseCaseTest" --tests "*GetModelPacksForSettingsUseCaseTest" --tests "*AutoAssignLocalModelsUseCaseTest" --tests "*ResolveAssignedImageGenerationTargetUseCaseTest"`
- `./gradlew :core:data:testDebugUnitTest --tests "*ModelConfigFetcherImplTest" --tests "*Sd15QnnPackInstallerTest" --tests "*ModelPackVerifierImplTest" --tests "*DownloadFinalizeWorkerTest" --tests "*UtilityModelFileResolverTest"`
- `./gradlew :feature:settings:testDebugUnitTest --tests "*SettingsMediaTasksRedTest" --tests "*ModelPackUiMapperTest" --tests "*SettingsViewModelTest"`
- `./gradlew :feature:studio:testDebugUnitTest --tests "*MultimodalViewModelTest" --tests "*StudioCapabilitiesCoordinatorTest"`
- `./gradlew :feature:inference:testDebugUnitTest --tests "*LocalImageGenerationDevicePolicyTest" --tests "*LocalStableDiffusionImageGeneratorFailureTest" --tests "*ImageGenerationPortImplTest"`
- Native JSON-body seam (`INF-006`, `MUT-005`): `cmake -S local-image-gen-android/src/test/cpp -B build/juggernaut-cpp-test && cmake --build build/juggernaut-cpp-test && ctest --test-dir build/juggernaut-cpp-test --output-on-failure`
- Optional manual harness (non-CI): `./scripts/run-enclave-diffusion-harness.sh --target host -- --scheduler dpm --steps 24 --cfg 4.0 --width 1024 --height 1024`

Scenario home notes:

- `CAP-005`, `CAP-006`, `CAP-009`, and `MUT-002` are asserted in `LocalImageGenerationCapabilityPolicyTest`.
- `CAP-010` is asserted in `LocalImageGenerationDevicePolicyTest`.
- `INF-006` and `INF-007` are asserted in `LocalStableDiffusionImageGeneratorFailureTest`.
- `SET-006` is asserted in `SettingsMediaTasksRedTest` through assembled `ModelConfigurationScreen` state.
- `ASG-001` is asserted in `ModelPackVerifierImplTest`.

Required validation sweep:

- `./gradlew :core:domain:assembleDebug`
- `./gradlew :core:data:assembleDebug`
- `./gradlew :feature:inference:assembleDebug`
- `./gradlew :feature:settings:assembleDebug`
- `./gradlew :feature:studio:assembleDebug`
- `./gradlew ktlintCheck`
- `./gradlew detekt`
- `./gradlew :app:assembleRelease`

## Drift And Escalation Rules

- If the exact preferred Juggernaut QNN zip metadata stops matching the verified values above, stop and amend this plan before implementation.
- If implementation requires modifying a runtime file not listed in the File Structure table, stop and amend this plan before editing.
- If the extracted Juggernaut archive does not satisfy `REQUIRED_SDXL_QNN_FILES`, stop and report the actual extracted filenames before changing verifier rules.
- If Juggernaut ships without `vae_encoder.bin`, stop and report the omission; do not weaken the `IMAGE_EDIT` encoder requirement.
- If native Local Dream rejects `dpm` as a scheduler token for this pack, stop and report supported scheduler tokens with source or log evidence.
- Do not extract Juggernaut into `sdxl-qnn/` or share utility registry rows between RealVis and Juggernaut.
- Do not add SDXL CPU fallback.
- Do not reintroduce Vulkan or GGML.
- Do not change `AGENTS.md` or any file under `contracts/`.
- During implementation, use the required AndroJack pre-generation and post-generation validation flow for every Kotlin, XML, or Gradle edit.
