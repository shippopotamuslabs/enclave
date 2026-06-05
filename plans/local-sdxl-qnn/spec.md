# Local SDXL QNN

## Feature Summary

Add a second local `TextToImage` pack, `sdxl-t2i`, on the existing Local Dream-style QNN path. The change must:

- keep `sd15-t2i` as the default and recommended local image model
- gate downloads and assignment by device policy, not by UI-only checks
- expose Studio settings from the active assigned local image model
- replace hardcoded local generation parameters with model policy
- pass scheduler through Kotlin, JNI, and C++ instead of always emitting `"dpm"`
- fail or clamp unsupported local settings before native launch

Verified SDXL pack metadata for the first implementation target:

| Field | Value |
|---|---|
| Hugging Face repo | `xororz/sdxl-qnn` |
| Preferred file | `realvis_xl_v5_dmd2_qnn2.28_8gen3.zip` |
| HF path | `realvis_xl_v5_dmd2_qnn2.28_8gen3.zip` |
| SHA256 | `5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83` |
| Size | `3504485672` bytes |
| Verified repo commit | `a9ca5ebe019d4c00af6e7ba478d0d58fa7fa09ff` |

Observed source constraints from discovery:

- `model_config.json` already models `sd15-t2i` as a diffusion member plus a utility zip member.
- Native QNN readiness currently requires exactly `clip_v2.mnn`, `token_emb.bin`, `pos_emb.bin`, `unet.bin`, `vae_decoder.bin`, and `tokenizer.json`.
- The repo does not expose a checked-in SDXL model card or README with a conflicting scheduler, CFG, or step range.
- The requested `realvis_xl_v5_dmd2_qnn2.28_8gen2.zip` file does not exist in `xororz/sdxl-qnn`; the verified published file is the `8gen3` archive above.

## Boundaries

- `:core:domain` owns Snapdragon tiering, model descriptors, capability policy, assignment policy, and local request validation.
- `:core:data` owns catalog parsing, startup download filtering, pack extraction, installed-pack verification, and finalize-time registry writes.
- `:feature:settings` only renders pack state and assignable local model options from domain/data outputs.
- `:feature:studio` only renders the active model capability surface and clamps stale UI state to that surface.
- `:feature:inference` only converts validated domain policy into a native request.
- `:local-image-gen-android` only forwards native request fields and builds the backend JSON body.
- No Room tables or columns change.
- No Vulkan, GGML, or SDXL CPU fallback work is allowed.

## File Structure

The implementation phase is limited to these files. If another runtime file becomes necessary, stop and amend this plan before editing.

| Path | Action | Responsibility |
|---|---|---|
| `model_config.json` | Modify | Add the verified SDXL diffusion asset and the `sdxl-t2i` pack definition with utility membership. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/config/UtilityType.kt` | Modify | Add `SDXL_QNN_PACK` and its display label. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/device/SnapdragonTier.kt` | Modify | Split the tier enum into `UNKNOWN`, `GEN1`, `GEN2`, `GEN3_PLUS`. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/device/SnapdragonSocClassifier.kt` | Modify | Map `SM8450` to `GEN1`, `SM8550` / `kalama` to `GEN2`, and `SM8650` / `SM8750` / `pineapple` to `GEN3_PLUS`. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/media/GenerationSettings.kt` | Modify | Add local diffusion step settings and provider capability fields for step options and default step. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/media/LocalImageGenerationCapabilities.kt` | Modify | Add tier-aware local image download and assignment eligibility state plus disabled reasons. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/media/LocalImageGenerationModelRegistry.kt` | Modify | Replace the placeholder SDXL descriptor metadata with the verified SDXL pack metadata and explicit pack IDs. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/media/LocalImageGenerationPaths.kt` | Modify | Add `sdxl-qnn` extraction path support and generic QNN artifact lookup for both local packs. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/media/ImageGenerationTarget.kt` | Modify | Remove the single shared local capability constant and route local policy lookups through explicit model policy. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/media/LocalImageGenerationModelPolicy.kt` | Create | Central domain contract for model-specific capabilities, native policy, and resolution / step normalization. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/download/Sd15QnnPackVariant.kt` | Modify | Update tier mapping for the split `GEN1` / `GEN2` enum without changing verified SD1.5 metadata. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/download/SdxlQnnPackVariant.kt` | Create | Pin the verified SDXL zip metadata and expose lookup by SHA / default variant. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/download/InitializeModelsUseCase.kt` | Modify | Include or suppress `sd15-t2i` and `sdxl-t2i` startup downloads from domain policy. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/download/ResolveSd15QnnPackCatalogAssetUseCase.kt` | Modify | Keep SD1.5 catalog utility resolution aligned to the split tier enum. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/modelconfig/GetModelPacksForSettingsUseCase.kt` | Modify | Surface pack completeness together with download-disabled reasons for settings UI. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/byok/SetDefaultModelUseCase.kt` | Modify | Reject blocked or incomplete SDXL assignment in domain code with exact error messages. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/media/GetImageGenerationCapabilitiesUseCase.kt` | Modify | Return capabilities from the active local model policy instead of one shared SD policy. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/media/ResolveAssignedImageGenerationTargetUseCase.kt` | Modify | Resolve local target capabilities and pack paths from the assigned model descriptor and policy. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/settings/SettingsUseCases.kt` | Modify | Add the local image capability read needed by settings UI assembly. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/settings/SettingsUseCasesImpl.kt` | Modify | Wire the added settings capability use case. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/download/ModelConfigFetcherImpl.kt` | Modify | Parse the added SDXL asset and pack members from `model_config.json`. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/download/ModelFileScanner.kt` | Modify | Count `sdxl-qnn` extracted artifacts as installed and incomplete when missing. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/download/ModelPackVerifierImpl.kt` | Modify | Verify both extracted QNN pack directories by pack member utility type. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/download/DownloadFinalizeWorker.kt` | Modify | Finalize SDXL downloads, install the extracted QNN pack, and register the SDXL local asset without replacing the SD1.5 default. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/download/UtilityModelFileResolver.kt` | Modify | Resolve installed SDXL QNN pack directories for completeness and native launch. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/download/Sd15QnnPackInstaller.kt` | Modify | Generalize the existing pack installer logic so both SD1.5 and SDXL QNN zips are extracted, verified, and only then deleted. |
| `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/SettingsModels.kt` | Modify | Add pack-disabled reason and local-image assignment eligibility fields to UI models. |
| `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/SettingsUiMappers.kt` | Modify | Map local assets and pack rows with capability-aware assignment and download state. |
| `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/ModelPackUiMapper.kt` | Modify | Surface the disabled reason and action availability for pack cards. |
| `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/SettingsUiStateAssembler.kt` | Modify | Merge local assets, pack rows, and capability state into the settings UI state. |
| `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/SettingsViewModel.kt` | Modify | Observe local image capability state so assignment and pack UI stay policy-driven. |
| `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/ModelConfigurationScreen.kt` | Modify | Hide blocked SDXL local assignments and show only policy-allowed local image models. |
| `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/LocalModelsBottomSheet.kt` | Modify | Disable blocked SDXL downloads and render the exact Gen2 disabled reason in pack UI. |
| `feature/studio/src/main/kotlin/com/browntowndev/enclave/feature/studio/MultimodalViewModel.kt` | Modify | Load capabilities from the assigned local model instead of the shared local constant. |
| `feature/studio/src/main/kotlin/com/browntowndev/enclave/feature/studio/coordinators/StudioCapabilitiesCoordinator.kt` | Modify | Clamp stale resolution and diffusion-step values to the active model policy. |
| `feature/studio/src/main/kotlin/com/browntowndev/enclave/feature/studio/components/StudioComponents.kt` | Modify | Render SDXL-only diffusion-step controls and SD1.5 fixed-resolution behavior. |
| `feature/studio-generation-service/src/main/kotlin/com/browntowndev/enclave/feature/studio/service/StudioGenerationIntentCodec.kt` | Modify | Serialize and deserialize diffusion steps for foreground Studio generation. |
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/media/LocalImageGenerationCapabilityPortImpl.kt` | Modify | Implement the split tier policy and exact disabled reasons for SDXL gating. |
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/media/local/LocalImageGenerationBackend.kt` | Modify | Add scheduler to the native request contract. |
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/media/local/LocalStableDiffusionImageGenerator.kt` | Modify | Replace hardcoded steps / CFG with model policy, validate or clamp settings, and pass scheduler. |
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/media/local/QnnStableDiffusionBackend.kt` | Modify | Pass scheduler through the runtime call. |
| `local-image-gen-android/src/main/kotlin/com/browntowndev/enclave/localimagegen/NativeLocalImageGenRuntime.kt` | Modify | Add scheduler to the runtime bridge signature. |
| `local-image-gen-android/src/main/kotlin/com/browntowndev/enclave/localimagegen/LocalImageGenNative.kt` | Modify | Add scheduler to the QNN and MNN JNI entry points. |
| `local-image-gen-android/src/main/cpp/local_image_gen_jni.cpp` | Modify | Accept scheduler from JNI and forward it to native backends. |
| `local-image-gen-android/src/main/cpp/qnn_bridge.cpp` | Modify | Forward scheduler into the shared backend request. |
| `local-image-gen-android/src/main/cpp/mnn_bridge.cpp` | Modify | Keep signature parity with the shared request contract. |
| `local-image-gen-android/src/main/cpp/local_image_gen_backend_client.h` | Modify | Add scheduler to `GenerateRequest` and expose the inline pure `build_generate_request_body` JSON-body seam. |
| `local-image-gen-android/src/main/cpp/local_image_gen_backend_client.cpp` | Modify | Emit the caller-provided scheduler instead of always hardcoding `"dpm"` (delegating to `build_generate_request_body`). |
| `local-image-gen-android/src/test/cpp/CMakeLists.txt` | Create | Host-side (non-NDK, no gtest) CMake project + ctest target for the native JSON-body seam. |
| `local-image-gen-android/src/test/cpp/generate_request_body_test.cpp` | Create | Native unit test asserting the `/generate` JSON body honours the caller scheduler (INF-005, MUT-004). |
| `core/domain/src/testFixtures/kotlin/com/browntowndev/enclave/domain/model/download/ModelPackTestFixtures.kt` | Modify | Add SDXL pack fixtures and explicit utility-member coverage. |
| `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/model/device/SnapdragonSocClassifierTest.kt` | Modify | Cover the split Gen1 / Gen2 / Gen3+ classification literals. |
| `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/model/media/ImageGenerationSettingsTest.kt` | Modify | Cover diffusion-step normalization for local image settings. |
| `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/model/download/SdxlQnnPackCatalogTest.kt` | Create | Lock the verified SDXL zip metadata and SHA lookup. |
| `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/media/SelectLocalImageGenerationBackendUseCaseTest.kt` | Modify | Cover Gen2 block and Gen3+ allow behavior for SDXL. |
| `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/media/ResolveAssignedImageGenerationTargetUseCaseTest.kt` | Modify | Cover SDXL target resolution, capabilities, and pack-path selection. |
| `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/model/media/LocalImageGenerationCapabilityPolicyTest.kt` | Create | Cover the pure tier+QNN eligibility resolution (CAP-005/006/007, MUT-002) against `LocalImageGenerationCapabilityPolicy.resolve(...)`. |
| `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/byok/SetDefaultModelUseCaseTest.kt` | Modify | Cover exact Gen2 rejection, Gen3+ success, and incomplete SDXL pack rejection. |
| `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/download/InitializeModelsUseCaseTest.kt` | Modify | Cover startup download inclusion and suppression for both packs. |
| `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/modelconfig/GetModelPacksForSettingsUseCaseTest.kt` | Modify | Cover disabled pack reason and non-default SDXL registration. |
| `core/domain/src/test/kotlin/com/browntowndev/enclave/domain/usecase/download/ResolveSd15QnnPackCatalogAssetUseCaseTest.kt` | Modify | Keep SD1.5 tier mapping stable after the enum split. |
| `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/download/ModelConfigFetcherImplTest.kt` | Modify | Cover parsing `sd15-t2i` and `sdxl-t2i` from `model_config.json`. |
| `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/download/Sd15QnnPackInstallerTest.kt` | Modify | Cover SDXL extraction path and delayed zip deletion. |
| `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/download/ModelPackVerifierImplTest.kt` | Modify | Cover installed SD1.5 and SDXL extracted directories. |
| `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/download/DownloadFinalizeWorkerTest.kt` | Modify | Cover SDXL registration without replacing an existing SD1.5 default. |
| `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/download/UtilityModelFileResolverTest.kt` | Modify | Cover resolving `files/models/sdxl-qnn`. |
| `feature/settings/src/test/kotlin/com/browntowndev/enclave/feature/settings/StableDiffusionSd15T2iRedTest.kt` | Modify | Cover capability-aware local assignment filtering for SD1.5 vs. SDXL. |
| `feature/settings/src/test/kotlin/com/browntowndev/enclave/feature/settings/SettingsMediaTasksRedTest.kt` | Modify | Cover `SET-001` via assembled `ModelConfigurationScreen` assignment options. |
| `feature/settings/src/test/kotlin/com/browntowndev/enclave/feature/settings/ModelPackUiMapperTest.kt` | Modify | Cover disabled reason mapping for blocked SDXL pack rows. |
| `feature/settings/src/test/kotlin/com/browntowndev/enclave/feature/settings/ModelPackSettingsUiTest.kt` | Modify | Cover blocked Gen2 SDXL pack UI state. |
| `feature/settings/src/test/kotlin/com/browntowndev/enclave/feature/settings/SettingsViewModelTest.kt` | Modify | Cover capability-driven pack row and assignment-option updates in settings state. |
| `feature/studio/src/test/kotlin/com/browntowndev/enclave/feature/studio/MultimodalViewModelTest.kt` | Modify | Cover per-model capability exposure and stale-setting clamps. |
| `feature/studio/src/test/kotlin/com/browntowndev/enclave/feature/studio/coordinators/StudioCapabilitiesCoordinatorTest.kt` | Create | Cover direct stale-resolution and stale-step clamping in the coordinator. |
| `feature/studio/src/test/kotlin/com/browntowndev/enclave/feature/studio/StudioImageSettingsUiTest.kt` | Create | Compose UI tests for Resolution and Diffusion Steps visibility in the Studio sheet. |
| `feature/studio-generation-service/src/test/kotlin/com/browntowndev/enclave/feature/studio/service/StudioGenerationIntentCodecTest.kt` | Modify | Cover diffusion-step serialization. |
| `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/media/LocalImageGenerationDevicePolicyTest.kt` | Modify | Cover capability diagnostics and SDXL block/allow behavior by tier. |
| `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/media/local/LocalStableDiffusionImageGeneratorFailureTest.kt` | Modify | Cover pre-native validation error and SDXL step clamping. |
| `feature/inference/src/test/kotlin/com/browntowndev/enclave/feature/inference/ImageGenerationPortImplTest.kt` | Modify | Cover local request propagation for SD1.5 and SDXL. |

## API Contracts And Interfaces

### Snapdragon tier split

`SnapdragonTier` must become:

- `UNKNOWN`
- `GEN1`
- `GEN2`
- `GEN3_PLUS`

Classifier contract:

- `SM8450` -> `GEN1`
- `SM8550` and `kalama` -> `GEN2`
- `SM8650`, `SM8750`, and `pineapple` -> `GEN3_PLUS`
- blank or unrelated hardware -> `UNKNOWN`

### Local image model descriptor contract

The implementation phase must make the descriptor layer explicit enough to answer download, assignment, Studio capability, and native policy questions without UI branching.

Required descriptor fields:

- `id: LocalImageGenerationModelId`
- `family: LocalImageGenerationModelFamily`
- `displayName: String`
- `packId: String`
- `catalogSha256: String`
- `utilityType: UtilityType?`
- `relativeModelDir: String`
- `preferredBackend: LocalImageGenerationBackendKind`
- `minInstallBytes: Long`
- `version: String`

Pinned descriptors:

- `SD15_QNN`
  - `packId = "sd15-t2i"`
  - `relativeModelDir = "sd15-qnn"`
  - `catalogSha256 = "c2f6e92f9d08d69cc673a1003528ac8199274b3c0eaec88d5fbefe5af67bd42b"`
- `SDXL_QNN`
  - `packId = "sdxl-t2i"`
  - `relativeModelDir = "sdxl-qnn"`
  - `catalogSha256 = "5250591bc45e58490c2fd3c0e0e7d4d166ba1aa6f443d5ebdb962c95a9e99f83"`

### Local image model policy contract

Create a domain-owned policy contract that resolves by `LocalImageGenerationModelId` and returns:

- `supportedImageResolutions: List<String>`
- `defaultImageResolution: String`
- `supportedDiffusionSteps: List<Int>`
- `defaultDiffusionSteps: Int`
- `fixedDiffusionSteps: Int?`
- `cfgScale: Float`
- `scheduler: String`

Pinned policy literals:

| Model | Resolutions | Steps | Default | Fixed | CFG | Scheduler |
|---|---|---|---|---|---|---|
| `SD15_QNN` | `["512x512"]` | `[]` | `20` | `20` | `7.5` | `dpm` |
| `SDXL_QNN` | `["1024x1024"]` | `[8, 12, 16, 20, 24, 30]` | `16` | `null` | `1.5` | `lcm` |

Behavior rules:

- SD1.5 hides step selection in Studio and always uses `20`.
- SDXL exposes the exact step options above.
- SDXL stale step values clamp to `16`.
- Unsupported resolution never clamps across model families; it fails with a domain validation error before native launch.

### Provider capability contract

Extend `ProviderCapabilities` so Studio can render local model step controls without special UI branches outside capability data.

Required added fields:

- `supportedImageSteps: List<Int> = emptyList()`
- `defaultImageStep: Int? = null`

Local capability outputs:

- active SD1.5 -> `supportedImageResolutions == ["512x512"]`, `supportedImageSteps == []`, `defaultImageStep == 20`
- active SDXL -> `supportedImageResolutions == ["1024x1024"]`, `supportedImageSteps == [8, 12, 16, 20, 24, 30]`, `defaultImageStep == 16`

### Hardware eligibility contract

`LocalImageGenerationCapabilities` must expose:

- resolved `snapdragonTier`
- QNN probe result
- SD1.5 eligibility
- SDXL eligibility
- exact disabled reason per blocked local image model
- diagnostics list

Pinned eligibility outcomes:

- Non-arm64 -> block all local image packs.
- `UNKNOWN` Snapdragon tier -> block all local image packs.
- QNN probe failure -> block all local image packs.
- RAM below `8 GiB` -> block all local image packs with diagnostic `Insufficient RAM for local diffusion (need >= 8 GiB).`
- `GEN1` + QNN -> allow SD1.5 only.
- `GEN2` + QNN -> allow SD1.5 only and block SDXL with `Requires Snapdragon 8 Gen 3+ with QNN.`
- `GEN3_PLUS` + QNN -> allow SD1.5 and SDXL.

### Assignment contract

`SetDefaultModelUseCase` must reject blocked local image assignments in domain code, not UI code.

Pinned outcomes:

- blocked SDXL assignment on Gen2 throws `IllegalArgumentException("Requires Snapdragon 8 Gen 3+ with QNN.")`
- incomplete SDXL pack throws the existing pack incomplete message
- successful SDXL assignment on Gen3+ preserves existing remote / local slot semantics

### Local request validation contract

Local request normalization must happen before `LocalImageGenerationEngine.generate(...)`.

Required inputs:

- assigned `LocalImageGenerationModelId`
- raw `ImageGenerationSettings`
- model policy

Required outputs:

- normalized `imageResolution`
- normalized `diffusionSteps`
- derived native `width`
- derived native `height`
- derived `cfgScale`
- derived `scheduler`

Pinned validation error for unsupported SD1.5 resolution:

- `Unsupported local image generation resolution '1024x1024' for model SD15_QNN. Supported resolutions: [512x512].`

## Data Model And Schema Changes

- No Room schema changes.
- No DataStore schema changes.
- `model_config.json` changes only:
  - add the SDXL diffusion asset
  - add the SDXL QNN utility asset
  - add the `sdxl-t2i` pack with diffusion + utility members
- `UtilityType` gains one new enum entry: `SDXL_QNN_PACK`

## Download And Catalog Strategy

- Keep two independent image-generation packs:
  - `sd15-t2i`
  - `sdxl-t2i`
- Each pack must carry:
  - one assignable diffusion asset
  - one QNN utility zip member
- `InitializeModelsUseCase` must compute startup download slices from pack membership plus capability policy:
  - API image-generation assignment suppresses both packs
  - blocked hardware suppresses only the blocked pack(s)
  - Gen2 suppresses `sdxl-t2i` only
  - Gen3+ includes both packs
- Extracted SDXL pack target directory is `files/models/sdxl-qnn`
- Zip deletion must happen only after the extracted directory satisfies the native QNN artifact contract
- Finalization must register the SDXL local model asset and its utility row without overwriting the existing SD1.5 default assignment

## Assignment And UI Gating Strategy

- Settings pack UI consumes pack-level availability and disabled reason from `GetModelPacksForSettingsUseCase`.
- `LocalModelsBottomSheet` renders disabled SDXL download state on Gen2 with the exact reason `Requires Snapdragon 8 Gen 3+ with QNN.`
- `ModelConfigurationScreen` surfaces only local image models that are both:
  - installed / complete
  - assignment-eligible under domain policy
- UI filtering is advisory only; `SetDefaultModelUseCase` remains the final guardrail.

## Native Scheduler And Step Propagation Strategy

- `LocalStableDiffusionImageGenerator` must derive `steps`, `cfgScale`, `width`, `height`, and `scheduler` from model policy.
- `LocalImageGenerationNativeRequest` must add `scheduler: String`.
- `QnnStableDiffusionBackend`, `NativeLocalImageGenRuntime`, `LocalImageGenNative`, JNI, and the C++ request builder must forward the scheduler unchanged.
- `local_image_gen_backend_client.cpp` must emit the provided scheduler string into the JSON body instead of always emitting `"scheduler":"dpm"`.
- `mnn_bridge.cpp` must keep signature parity even though SDXL CPU fallback remains disabled by policy.

### Native test seam for `INF-005` / `MUT-004` (plan amendment)

The previous draft asserted `INF-005`/`MUT-004` against the Kotlin policy `scheduler`
field, which does not exercise the C++ JSON body where `"dpm"` is hardcoded. Per the
Drift/Escalation rule, the smallest viable deterministic seam is option (b): the JSON
`/generate` body construction is extracted from `run_stable_diffusion_generate(...)` into
an inline, side-effect-free `build_generate_request_body(const GenerateRequest&)` declared
in `local_image_gen_backend_client.h`. Because it is an inline header function with no
Android/NDK dependencies, the production translation unit and a host-compiled unit test
share one implementation. A standalone host CMake project under
`local-image-gen-android/src/test/cpp/` (no gtest dependency, plain assertions + ctest)
asserts the emitted body for a `scheduler = "lcm"` request. The seam preserves the current
hardcoded `"dpm"` behaviour verbatim (Red); the Green phase emits `request.scheduler`.

## Ordered Implementation Tasks

1. Update the catalog contract and pinned metadata in `model_config.json`, `UtilityType.kt`, `LocalImageGenerationModelRegistry.kt`, and `SdxlQnnPackVariant.kt`.
   Scenarios: `DL-001`, `DL-005`, `DL-009`

2. Split `SnapdragonTier` into `UNKNOWN`, `GEN1`, `GEN2`, and `GEN3_PLUS`, and update classifier literals.
   Scenarios: `CAP-001`, `CAP-002`, `CAP-003`, `CAP-004`

3. Extend local capability policy so SD1.5 and SDXL each expose exact eligibility and disabled reasons.
   Scenarios: `CAP-005`, `CAP-006`, `CAP-007`, `CAP-008`, `SET-002`, `MUT-002`

4. Introduce explicit local image model policy for per-model capabilities and native generation rules, and extend `GenerationSettings` / `ProviderCapabilities` to carry diffusion steps.
   Scenarios: `STU-001`, `STU-002`, `STU-003`, `INF-001`, `INF-002`, `INF-003`, `INF-004`, `MUT-001`, `MUT-003`

5. Update startup download slicing so `sd15-t2i` and `sdxl-t2i` are independently included or suppressed from `InitializeModelsUseCase`.
   Scenarios: `DL-002`, `DL-003`, `DL-004`

6. Generalize extracted QNN pack install, scan, verify, resolve, and finalize behavior for both SD1.5 and SDXL.
   Scenarios: `DL-005`, `DL-006`, `DL-007`, `DL-008`, `DL-009`

7. Enforce domain-side assignment gating for SDXL completeness and Gen2 hardware blocks.
   Scenarios: `SET-001`, `SET-003`, `SET-004`, `SET-005`

8. Surface pack-disabled reason and assignment-eligible local image options in Settings UI without moving policy into Compose.
   Scenarios: `SET-001`, `SET-002`

9. Drive Studio resolution and step controls from the active local model capability surface, and clamp stale values on model switches.
   Scenarios: `STU-004`, `STU-005`, `STU-006`, `STU-007`, `STU-008`

10. Extend Studio foreground intent serialization to preserve diffusion steps.
    Scenarios: `STU-005`, `STU-007`, `STU-008`, `INF-002`

11. Replace hardcoded local generation parameters with policy-derived values and thread scheduler through Kotlin, JNI, and C++.
    Scenarios: `INF-001`, `INF-002`, `INF-003`, `INF-004`, `INF-005`, `MUT-004`

12. Add or extend every listed test file, then run targeted test tasks and the required validation sweep.
    Scenarios: `CAP-001` through `MUT-004`

## Validation Commands

Targeted tests for changed classes:

- `./gradlew :core:domain:testDebugUnitTest --tests "*SnapdragonSocClassifierTest" --tests "*ImageGenerationSettingsTest" --tests "*SdxlQnnPackCatalogTest" --tests "*SelectLocalImageGenerationBackendUseCaseTest" --tests "*ResolveAssignedImageGenerationTargetUseCaseTest" --tests "*SetDefaultModelUseCaseTest" --tests "*InitializeModelsUseCaseTest" --tests "*GetModelPacksForSettingsUseCaseTest" --tests "*ResolveSd15QnnPackCatalogAssetUseCaseTest"`
- `./gradlew :core:data:testDebugUnitTest --tests "*ModelConfigFetcherImplTest" --tests "*Sd15QnnPackInstallerTest" --tests "*ModelPackVerifierImplTest" --tests "*DownloadFinalizeWorkerTest" --tests "*UtilityModelFileResolverTest"`
- `./gradlew :feature:settings:testDebugUnitTest --tests "*StableDiffusionSd15T2iRedTest" --tests "*SettingsMediaTasksRedTest" --tests "*ModelPackUiMapperTest" --tests "*ModelPackSettingsUiTest" --tests "*SettingsViewModelTest"`
- `./gradlew :feature:studio:testDebugUnitTest --tests "*MultimodalViewModelTest" --tests "*StudioCapabilitiesCoordinatorTest" --tests "*StudioImageSettingsUiTest"`
- `./gradlew :feature:studio-generation-service:testDebugUnitTest --tests "*StudioGenerationIntentCodecTest"`
- `./gradlew :feature:inference:testDebugUnitTest --tests "*LocalImageGenerationDevicePolicyTest" --tests "*LocalStableDiffusionImageGeneratorFailureTest" --tests "*ImageGenerationPortImplTest"`
- `./gradlew :core:domain:testDebugUnitTest --tests "*LocalImageGenerationCapabilityPolicyTest"`
- Native JSON-body seam (`INF-005`, `MUT-004`): `cmake -S local-image-gen-android/src/test/cpp -B <build-dir> && cmake --build <build-dir> && ctest --test-dir <build-dir> --output-on-failure`

Scenario home notes:
- `CAP-005`, `CAP-006`, `CAP-007`, and `MUT-002` are asserted once against the pure domain seam
  `LocalImageGenerationCapabilityPolicy.resolve(...)` (`LocalImageGenerationCapabilityPolicyTest`).
- `CAP-008` is asserted once against the Android port `LocalImageGenerationCapabilityPortImpl.getCapabilities()`
  (`LocalImageGenerationDevicePolicyTest`), matching its test_spec "When" clause.
- `INF-001`, `INF-002`, `INF-003`, and `INF-004` are asserted in `LocalStableDiffusionImageGeneratorFailureTest`
  against `LocalStableDiffusionImageGenerator.generate(...)`.
- `INT-SVC-001` is asserted in `StudioGenerationIntentCodecTest` for foreground diffusion-step intent serialization.
- `SET-001` is asserted in `SettingsMediaTasksRedTest` through assembled `ModelConfigurationScreen` state.

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

- If the exact preferred SDXL QNN zip metadata stops matching the verified Hugging Face repo values above, stop and amend this plan before implementation.
- If implementation requires modifying a runtime file not listed in the File Structure table, stop and amend this plan before editing.
- If the selected SDXL pack later documents a different scheduler, CFG, or recommended step range, update the domain policy literals and the matching scenario literals before writing code.
- If native Local Dream rejects `lcm` as a scheduler token, stop and report the supported scheduler tokens with source or log evidence.
- If no deterministic automated seam can assert `INF-005` against the generated backend JSON body, stop and amend the plan with the smallest viable native test seam before editing runtime code.
- If the extracted SDXL archive does not satisfy the existing QNN artifact contract, stop and report the actual extracted filenames before changing verifier rules.
- Do not add SDXL CPU fallback.
- Do not reintroduce Vulkan or GGML.
- Do not change `AGENTS.md` or any file under `contracts/`.
- During implementation, use the required AndroJack pre-generation and post-generation validation flow for every Kotlin, XML, or Gradle edit.
