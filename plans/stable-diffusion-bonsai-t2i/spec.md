# stable-diffusion-bonsai-t2i

## Feature summary

Add local text-to-image generation backed by `stable-diffusion.cpp` for the Bonsai Flux.2 Klein GGUF model, while keeping dependency direction inward toward `:core:domain` and reusing the existing download, assignment, Studio, and media-generation flows wherever they already fit.

The plan intentionally avoids new Room columns for transient generation state. The diffusion GGUF remains a normal local model asset assigned to `ModelType.IMAGE_GENERATION`. The Flux.2 VAE and Qwen3 text encoder are modeled as separately downloaded utility assets so they can be fetched, validated, persisted, and resolved using the existing utility-asset path instead of inventing arbitrary extra companion-file columns on local model entities. Those companion utility assets should be cataloged with `source: "HF"` and resolved through the existing Hugging Face URL provider rather than requiring mirrored Cloudflare R2 URLs.

## Hard constraints and blockers

- Do not edit `AGENTS.md` or any file under `/contracts`.
- Clean architecture rules remain binding: domain owns contracts, features map domain to presentation, no `NavController` in composables, no domain models passed directly to composables.
- This feature must not bypass the existing `GenerateMediaUseCase` / Studio service boundary by injecting JNI or inference ports directly into `feature/studio-generation-service`.
- Production logic changes require matching tests.
- Integrity-pinned asset metadata is a hard gate. Implementation must not invent final file names, checksums, or byte sizes for companion assets that are not already pinned in repo artifacts. Companion utility assets do not require Enclave-hosted R2 URLs if they are cataloged through the existing Hugging Face source contract.

### Known diffusion metadata

These values are already available for the Bonsai diffusion GGUF and can be carried into later implementation work:

| Artifact | Hosted/public metadata |
|---|---|
| Bonsai diffusion GGUF | URL `https://config.enclave-app.com/bonsai-ternary-4B-q5_K.gguf`, SHA-256 `c415f811e4b39c1287ce529afbe99ff1ade0bb9ac0e5767019d9490a606b1983`, byte size `2721233504` |

Known verification facts already observed locally:

- Local file: `bonsai-ternary-4B-q5_K.gguf`
- Local SHA-256: `c415f811e4b39c1287ce529afbe99ff1ade0bb9ac0e5767019d9490a606b1983`
- Local byte size: `2721233504`

### Required companion metadata before implementation can be completed

These companion assets can come directly from Hugging Face through the existing catalog contract, but their exact pinned metadata still must be supplied before the production `model_config.json` rows can ship:

| Artifact | Selected source candidate | Values still required before shipping |
|---|---|---|
| Flux.2 VAE | `Comfy-Org/vae-text-encorder-for-flux-klein-4b`, path `split_files/vae`, file `flux2-vae.safetensors` | Exact `sha256` and exact `sizeInBytes` for the selected file |
| Qwen3 text encoder | `unsloth/Qwen3-4B-GGUF`, file `Qwen3-4B-Q4_K_M.gguf` | Exact `sha256`, exact `sizeInBytes`, and confirmation that this GGUF variant is the selected shipping file |

### Companion asset source candidates and rationale

- Qwen3 4B text encoder:
  - Preferred Android packaging is a quantized GGUF, starting with a `Q4_K_M`-class variant or another stable `Q*_K*` GGUF that `stable-diffusion.cpp` accepts.
  - Source candidate: `unsloth/Qwen3-4B-GGUF`
  - Current observed file candidate: `Qwen3-4B-Q4_K_M.gguf` at about `2.5 GB`
  - Rationale: Bonsai / Flux.2 Klein conditions on Qwen3 4B hidden states, so the app’s existing LLM assets are not compatible substitutes.
- Flux.2 VAE:
  - Preferred file is `flux2-vae.safetensors`
  - Source candidate: `Comfy-Org/vae-text-encorder-for-flux-klein-4b`, path `split_files/vae/flux2-vae.safetensors`
  - Current observed file candidate size: about `336 MB`
  - Rationale: the diffusion transformer emits Flux.2 latents, and this VAE decodes those latents into final images.

Implementation may document these candidates and use them for human sourcing decisions, but it must not invent final checksums, exact byte sizes, or a final text-encoder file variant until those values are explicitly pinned. No companion Enclave-hosted URL is required as long as the catalog entry uses the existing Hugging Face source fields.

### AndroJack gates for implementation phase

Every later Kotlin, Compose, or Gradle code change must pass the repo’s Android gate:

- Pre-generation grounding:
  - architecture-sensitive Android changes: `mcp_androjack_architecture_reference`
  - new Android component/API usage: `mcp_androjack_android_component_status`
  - dependency/module changes: `mcp_androjack_gradle_dependency_checker`
  - Compose/UI work: `mcp_androjack_material3_expressive`
  - API-level questions: `mcp_androjack_android_api_level_check`
- Post-generation validation:
  - `mcp_androjack_android_code_validator` with `minSdk 34` and `targetSdk 36`
- Any FAIL verdict must be fixed before code is handed off.

## Proposed file structure and ownership

| Path | Change | Responsibility boundary |
|---|---|---|
| `settings.gradle.kts` | modify | Register a dedicated native Android wrapper module for stable diffusion. |
| `third_party/stable-diffusion.cpp/` | add vendor source | Pinned native upstream source; no app logic. |
| `stable-diffusion-android/build.gradle.kts` | add | Android wrapper module parallel to `:llama-android` and `:whisper-android`. |
| `stable-diffusion-android/src/main/cpp/CMakeLists.txt` | add | Native build wiring only. |
| `stable-diffusion-android/src/main/cpp/stable_diffusion_jni.cpp` | add | JNI boundary to `stable-diffusion.cpp`; no business rules. |
| `stable-diffusion-android/src/main/kotlin/com/browntowndev/enclave/stablediffusion/StableDiffusionBridge.kt` | add | Kotlin-native bridge contract for inference adapters. |
| `stable-diffusion-android/src/main/kotlin/com/browntowndev/enclave/stablediffusion/JniStableDiffusionBridge.kt` | add | JNI-backed implementation. |
| `stable-diffusion-android/src/main/kotlin/com/browntowndev/enclave/stablediffusion/di/StableDiffusionModule.kt` | add | Hilt provision of the bridge into inference. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/inference/ModelFileFormat.kt` | modify | Add `SAFETENSORS` for the Flux.2 VAE, and for the text encoder if GGUF is not available. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/config/UtilityType.kt` | modify | Add utility types for Flux.2 VAE and Qwen3 text encoder. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/media/ImageGenerationTarget.kt` | add | Domain contract describing either remote-provider or local-stable-diffusion image generation. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/port/media/ImageGenerationPort.kt` | modify | Accept a resolved image-generation target instead of only `MediaProviderAsset`. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/media/ResolveAssignedImageGenerationTargetUseCase.kt` | add | Resolve the current image-generation assignment to either a local or remote target. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/media/GenerateImageUseCase.kt` | modify | Validate assigned target, required local artifacts, and route through the unified generation port. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/media/GetImageGenerationCapabilitiesUseCase.kt` | add | Return `ProviderCapabilities` for either local or remote image generation. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/chat/ChatInferenceRequestPreparer.kt` | modify | Keep the image tool available when the assigned image-generation target is local. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/byok/SetDefaultModelUseCase.kt` | modify | Allow `ModelType.IMAGE_GENERATION` to use `localConfigId`. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/settings/AutoAssignLocalModelsUseCase.kt` | modify | Auto-assign the Bonsai local model to the image-generation slot when appropriate. |
| `model_config.json` | modify | Register the Bonsai diffusion asset metadata plus separately downloaded Flux.2 VAE and Qwen3 utility assets. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/download/remote/DynamicModelUrlProvider.kt` | verify/reuse | Reuse the existing Hugging Face URL builder for companion utility assets instead of adding bespoke Bonsai URL logic. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/download/ModelConfigFetcherImpl.kt` | modify | Parse and validate the new utility types and `SAFETENSORS` format without changing the existing companion-file contract for local models. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/download/DownloadFinalizeWorker.kt` | verify/extend | Ensure the new utility assets finalize into the utility registry and local availability scan. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/download/UtilityModelFileResolver.kt` | verify/extend | Resolve Flux.2 VAE and Qwen3 text encoder file paths by utility type. |
| `feature/inference/build.gradle.kts` | modify | Depend on `:stable-diffusion-android`. |
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/media/ImageGenerationPortImpl.kt` | modify | Branch local text-to-image requests to the stable-diffusion bridge while preserving remote providers. |
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/media/local/LocalStableDiffusionImageGenerator.kt` | add | Compose the local CLI-equivalent request, progress callbacks, temp/output handling, and off-main-thread execution. |
| `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/ModelConfigurationScreen.kt` | modify | Supply local assets for the image-generation slot instead of forcing media-provider-only selection. |
| `feature/studio/src/main/kotlin/com/browntowndev/enclave/feature/studio/MultimodalViewModel.kt` | modify | Resolve local or remote image targets, expose capabilities, and surface generation state. |
| `feature/studio/src/main/kotlin/com/browntowndev/enclave/feature/studio/StudioUiState.kt` | modify | Carry local image-generation progress/loading/error/success state to presentation. |
| `feature/studio/src/main/kotlin/com/browntowndev/enclave/feature/studio/coordinators/StudioCapabilitiesCoordinator.kt` | reuse/verify | Continue driving the bottom-sheet aspect-ratio and resolution pickers from `ProviderCapabilities`. |
| `feature/studio-generation-service/src/main/kotlin/com/browntowndev/enclave/feature/studio/service/MultimediaGenerationService.kt` | modify | Continue using domain use cases only, while supporting the new local image-generation path. |
| `feature/studio-generation-service/src/main/kotlin/com/browntowndev/enclave/feature/studio/service/StudioGenerationIntentCodec.kt` | verify/extend | Preserve image settings round-trip for local generation jobs. |

## Data and schema changes

### `model_config.json` contract

Planned shape:

- One non-utility asset for the diffusion model:
  - `displayName`: stable human-facing Bonsai model name
  - `fileName`: `bonsai-ternary-4B-q5_K.gguf`
  - `modelFileFormat`: `GGUF`
  - one configuration assigned to `ModelType.IMAGE_GENERATION`
  - hosted metadata already known:
    - `url`: `https://config.enclave-app.com/bonsai-ternary-4B-q5_K.gguf`
    - `sha256`: `c415f811e4b39c1287ce529afbe99ff1ade0bb9ac0e5767019d9490a606b1983`
    - `byteSize`: `2721233504`
  - backend/runtime metadata captured through existing naming conventions
- Two utility assets:
  - Flux.2 VAE, downloaded separately from the diffusion model
  - Qwen3 text encoder, downloaded separately from the diffusion model
  - both cataloged with the existing Hugging Face fields:
    - `source`: `HF`
    - `huggingFaceModelName`
    - optional `huggingFacePath`
    - `fileName`
    - `sha256`
    - `sizeInBytes`

The existing `DynamicModelUrlProvider` already resolves `source: HF` assets to `https://huggingface.co/{owner}/{repo}/resolve/main/{huggingFacePath?}/{fileName}`. No additional companion `url` field or Enclave R2 mirror is required if the selected Hugging Face file metadata is pinned exactly in the catalog.

The plan deliberately does not add arbitrary companion-file arrays to the local model schema. Existing contracts already support:

- normal local model assets with one primary file and optional `mmproj`
- utility assets persisted and downloaded separately

For the Qwen3 utility asset, the preferred first implementation target is the quantized GGUF file `Qwen3-4B-Q4_K_M.gguf` sourced from `unsloth/Qwen3-4B-GGUF`. If later sourcing proves only a different supported GGUF quant is viable, implementation may switch variants without changing the overall contract. If only non-GGUF packaging is available, stop and re-check runtime compatibility before proceeding.

The downloader and verification flow currently rely on non-null `sha256` and `sizeInBytes` values for every `RemoteModelAsset`. For that reason, the companion utility assets must pin exact `fileName`, `sha256`, and `sizeInBytes` values before shipping; changing that schema or relaxing those checks is not the preferred implementation path.

That existing split is enough for Bonsai if runtime resolution combines:

1. assigned local diffusion model
2. required Flux.2 VAE utility asset
3. required Qwen3 text encoder utility asset

### Room persistence impact

No Room migration is planned if implementation keeps the companion VAE and text encoder in the utility-asset registry.

Persisted state already covers what this feature needs:

- download state
- local path
- checksum
- size
- availability
- default assignment through `DefaultModelEntity.localConfigId`

Room migration becomes necessary only if implementation later attempts to store arbitrary companion-file references directly on `LocalModelEntity`. That approach is explicitly out of scope for this plan because it is unnecessary and would create avoidable schema drift.

### New enum/data additions

- `ModelFileFormat.SAFETENSORS`
- `UtilityType.FLUX2_VAE`
- `UtilityType.QWEN3_TEXT_ENCODER`

## Domain and native API contracts

### Resolved image-generation target

Add a domain-level target that insulates Studio and service layers from backend details:

```text
ImageGenerationTarget
  - Remote(providerAssetId, providerDisplayName, providerCapabilities?)
  - LocalStableDiffusion(
      localConfigId,
      displayName,
      diffusionModelPath,
      diffusionSha256,
      vaePath,
      textEncoderPath,
      backend = STABLE_DIFFUSION_CPP,
      capabilities
    )
```

Rules:

- The resolver owns artifact completeness checks.
- Callers do not manually stitch together model paths.
- Local targets are valid only when all required files are present on disk and checksums match persisted metadata.

### Image-generation port contract

`ImageGenerationPort` should accept:

- prompt
- resolved `ImageGenerationTarget`
- `ImageGenerationSettings`
- optional progress callback or progress event sink

Expected progress contract:

```text
ImageGenerationProgress
  - PreparingModel
  - LoadingModel
  - Generating(step: Int, totalSteps: Int)
  - SavingOutput
```

The exact transport can be callback-based or flow-based, but it must stay out of Room and off the main thread.

### Local stable-diffusion bridge request

Bridge-facing request must carry:

- diffusion model absolute path
- VAE absolute path
- text encoder absolute path
- prompt
- width and height derived from the selected Studio aspect ratio and resolution
- fixed initial runtime flags from the accepted CLI reference:
  - `cfgScale = 1.0`
  - `steps = 4`
  - `samplingMethod = "euler"`
  - `diffusionFa = true`
  - `offloadToCpu = true`

The wrapper should return:

- generated image file path or `Uri`-resolvable output reference
- image dimensions
- elapsed time, if readily available
- structured failure reason when generation cannot start

### User-visible error contract

Lock these user-facing strings for the first implementation so tests can assert them exactly:

- `No image generation model assigned.`
- `Image generation model is not fully downloaded.`
- `Flux.2 VAE is missing.`
- `Qwen3 text encoder is missing.`
- `Local image generation failed.`

Blank or unknown low-level failures should still fall back through the existing Studio generic message path where appropriate.

## Ordered implementation tasks

1. Vendor and pin `stable-diffusion.cpp`.
   - Add `third_party/stable-diffusion.cpp/`.
   - Record upstream commit/version in module documentation.

2. Create the Android wrapper module.
   - Add `:stable-diffusion-android`.
   - Mirror the narrow JNI boundary pattern used by `:whisper-android`.

3. Extend domain enums and utility types.
   - Add `ModelFileFormat.SAFETENSORS`.
   - Add utility types for Flux.2 VAE and Qwen3 text encoder.

4. Register cataloged assets in `model_config.json`.
   - Add the Bonsai diffusion model as a local `IMAGE_GENERATION` asset.
   - Add VAE and text encoder as separately downloaded utility assets.
   - Catalog the companion utility assets with `source: "HF"` and the existing fields `huggingFaceModelName`, optional `huggingFacePath`, `fileName`, `sha256`, and `sizeInBytes`.
   - Reuse the current Hugging Face URL provider instead of introducing new Bonsai-specific R2 URL handling for companion assets.
   - Use the known diffusion URL, checksum, and byte-size values already captured in this plan.
   - Block shipping companion utility-asset entries until exact pinned `fileName`, `sha256`, and `sizeInBytes` values are recorded for the selected Hugging Face files, along with the final compatible text-encoder packaging.

5. Preserve existing persistence model.
   - Reuse current local-model and utility-asset repositories.
   - Do not add Room columns unless a later implementation proves the utility-asset split cannot satisfy runtime lookup.

6. Introduce a unified domain image-generation target.
   - Resolve either a local stable-diffusion target or a remote provider target from the default assignment slot.
   - Move local artifact validation into the resolver/use case layer.

7. Allow local assignment for `ModelType.IMAGE_GENERATION`.
   - Update domain assignment rules.
   - Update settings selection screens to surface compatible local assets for the image-generation slot.
   - Preserve current provider-only behavior for the other media slots unless separately expanded.

8. Keep auto-assignment and chat tools coherent.
   - Update local auto-assignment so the Bonsai model can populate the image-generation slot.
   - Keep `generate_image` tool availability when the assigned target is local.

9. Implement the local inference adapter.
   - Add a local stable-diffusion path in `feature/inference`.
   - Use the JNI bridge from `:stable-diffusion-android`.
   - Keep remote providers unchanged.

10. Surface local capabilities in Studio.
   - Feed `ProviderCapabilities` for the local backend into the existing bottom-sheet controls.
   - Supported aspect ratios and resolutions must come from capabilities, not hard-coded instructional copy in the screen.

11. Add Studio generation state and output handling.
   - Expose loading/progress/error/success state for local image generation.
   - Keep long-running work in the service/use-case path.
   - Save or expose outputs through the existing media/storage pattern.

12. Validate and harden.
   - Run module-scoped tests and compile checks first.
   - Run `detekt` for changed Kotlin modules and explicit KSP smoke for data changes.

## Scenario mapping

| Scenario IDs | Covered by tasks |
|---|---|
| `SDT2I-001` to `SDT2I-003` | 3, 4, 5 |
| `SDT2I-004` to `SDT2I-007` | 6, 7, 8 |
| `SDT2I-008` to `SDT2I-011` | 9 |
| `SDT2I-012` to `SDT2I-016` | 10, 11, 12 |

## Validation plan

Narrow checks during implementation:

- `./gradlew :core:data:assembleDebug`
- `./gradlew :app:kspDebugKotlin`
- `./gradlew :feature:inference:assembleDebug`
- `./gradlew :feature:studio:assembleDebug`
- `./gradlew :feature:settings:assembleDebug`
- `./gradlew :core:data:testDebugUnitTest`
- `./gradlew :feature:studio:testDebugUnitTest`
- `./gradlew :feature:settings:testDebugUnitTest`
- `./gradlew :feature:inference:testDebugUnitTest`
- `./gradlew detekt`

Use root-level `./gradlew testDebugUnitTest` only if the change set spills across enough modules that narrow module tests no longer give reliable coverage.

## Drift and escalation points

- Companion Hugging Face file metadata not yet pinned:
  - The diffusion GGUF metadata is already known and should not be re-discovered.
  - The Flux.2 VAE and Qwen3 text encoder do not need Enclave-hosted R2 URLs.
  - Escalate to the user for the exact selected Hugging Face file metadata that must be pinned in `model_config.json`: `fileName`, `sha256`, and `sizeInBytes`.
- Text encoder packaging unresolved:
  - Prefer `Qwen3-4B-Q4_K_M.gguf` from `unsloth/Qwen3-4B-GGUF` for Android as requested.
  - If the final sourced Qwen3 artifact is not GGUF, confirm `stable-diffusion.cpp` Android integration supports that exact text-encoder packaging before implementation.
- Hugging Face revision immutability concerns:
  - The current source contract and URL provider resolve `main`.
  - If maintainers require revision-specific Hugging Face URLs instead of the current `resolve/main` behavior, stop and obtain explicit approval before proposing schema or URL-provider changes.
- Native module placement:
  - Preferred plan is a dedicated `:stable-diffusion-android` wrapper module because it matches existing `:llama-android` and `:whisper-android` patterns.
  - If architecture owners reject a new wrapper module, stop and obtain explicit direction before implementation.
- Capability values for the local backend:
  - Exact supported resolution/aspect-ratio sets should be defined once runtime constraints are benchmarked or otherwise approved.
  - Until then, write tests against explicit fixture capability payloads rather than assuming product constants.
