# Local SDXL QNN Test Spec

## Framework And Commands

- Frameworks: JUnit 5, Turbine, MockK, Robolectric, and Compose UI tests where UI behavior changes.
- Primary targeted commands:
  - `./gradlew :core:domain:testDebugUnitTest --tests "*SnapdragonSocClassifierTest" --tests "*ImageGenerationSettingsTest" --tests "*SdxlQnnPackCatalogTest" --tests "*SelectLocalImageGenerationBackendUseCaseTest" --tests "*ResolveAssignedImageGenerationTargetUseCaseTest" --tests "*SetDefaultModelUseCaseTest" --tests "*InitializeModelsUseCaseTest" --tests "*GetModelPacksForSettingsUseCaseTest" --tests "*ResolveSd15QnnPackCatalogAssetUseCaseTest"`
  - `./gradlew :core:data:testDebugUnitTest --tests "*ModelConfigFetcherImplTest" --tests "*Sd15QnnPackInstallerTest" --tests "*ModelPackVerifierImplTest" --tests "*DownloadFinalizeWorkerTest" --tests "*UtilityModelFileResolverTest"`
  - `./gradlew :feature:settings:testDebugUnitTest --tests "*StableDiffusionSd15T2iRedTest" --tests "*SettingsMediaTasksRedTest" --tests "*ModelPackUiMapperTest" --tests "*ModelPackSettingsUiTest" --tests "*SettingsViewModelTest"`
  - `./gradlew :feature:studio:testDebugUnitTest --tests "*MultimodalViewModelTest" --tests "*StudioCapabilitiesCoordinatorTest" --tests "*StudioImageSettingsUiTest"`
  - `./gradlew :feature:studio-generation-service:testDebugUnitTest --tests "*StudioGenerationIntentCodecTest"`
  - `./gradlew :feature:inference:testDebugUnitTest --tests "*LocalImageGenerationDevicePolicyTest" --tests "*LocalStableDiffusionImageGeneratorFailureTest" --tests "*ImageGenerationPortImplTest"`

## Scenario Index

| ID | Title |
|---|---|
| `CAP-001` | `SM8450` classifies as `GEN1` |
| `CAP-002` | `SM8550` / `kalama` classifies as `GEN2` |
| `CAP-003` | `SM8650`, `SM8750`, or `pineapple` classifies as `GEN3_PLUS` |
| `CAP-004` | blank or unrelated hardware classifies as `UNKNOWN` |
| `CAP-005` | Gen2 + QNN supports SD1.5 and blocks SDXL with the exact reason |
| `CAP-006` | Gen3+ + QNN supports SD1.5 and SDXL |
| `CAP-007` | QNN probe failure blocks all local image pack downloads |
| `CAP-008` | insufficient RAM blocks all local image pack downloads with the literal diagnostic |
| `DL-001` | catalog parses `sd15-t2i` and `sdxl-t2i` |
| `DL-002` | Gen2 startup result includes SD1.5 pack files and excludes SDXL pack files |
| `DL-003` | Gen3+ startup result includes SD1.5 and SDXL eligible pack files |
| `DL-004` | API image-generation assignment suppresses local image pack downloads |
| `DL-005` | SDXL extraction installs under `files/models/sdxl-qnn` |
| `DL-006` | SDXL zip deletes only after required artifacts verify |
| `DL-007` | incomplete SDXL extraction stays incomplete and does not register as ready |
| `DL-008` | pack verifier counts extracted SD1.5 and SDXL QNN directories as installed |
| `DL-009` | finalizer registers SDXL without overriding the SD1.5 default |
| `SET-001` | Gen3+ installed SDXL appears in local `TextToImage` assignment options |
| `SET-002` | Gen2 hides SDXL from assignment and shows the disabled pack reason |
| `SET-003` | direct Gen2 SDXL assignment throws the exact `IllegalArgumentException` |
| `SET-004` | direct Gen3+ SDXL assignment succeeds when the pack is complete |
| `SET-005` | incomplete SDXL assignment throws the existing pack incomplete message |
| `STU-001` | active SD1.5 exposes exactly `["512x512"]` |
| `STU-002` | active SDXL exposes exactly `["1024x1024"]` |
| `STU-003` | active SDXL exposes exact step options and default `16` |
| `STU-004` | Studio bottom sheet shows Resolution for SDXL as `1024x1024` |
| `STU-005` | Studio bottom sheet shows Diffusion Steps for SDXL |
| `STU-006` | Studio bottom sheet hides Diffusion Steps for SD1.5 |
| `STU-007` | switching SDXL -> SD1.5 clamps stale resolution and clears SDXL step selection |
| `STU-008` | switching SD1.5 -> SDXL clamps stale resolution and steps to the SDXL defaults |
| `INF-001` | SD1.5 native request uses `512x512`, `20`, and `7.5` |
| `INF-002` | SDXL native request uses `1024x1024`, selected steps `8`, CFG `1.5`, and scheduler `lcm` |
| `INF-003` | stale SD1.5 `1024x1024` request fails before native launch with the exact validation error |
| `INF-004` | stale SDXL step `13` clamps to `16` before native launch |
| `INF-005` | C++ backend JSON body contains `"scheduler":"lcm"` for SDXL and is no longer always `"dpm"` |
| `MUT-001` | a hardcoded dual-resolution implementation fails the SD1.5 exact-resolution check |
| `MUT-002` | a QNN-only SDXL gate fails because Gen2 + QNN must still block SDXL |
| `MUT-003` | a lazy any-step implementation fails because step `13` must clamp to `16` |
| `MUT-004` | a hardcoded native `"dpm"` scheduler implementation fails for SDXL |
| `INT-SVC-001` | foreground Studio service intent round-trips diffusion steps |

Scenario home notes:
- `INF-001`, `INF-002`, `INF-003`, and `INF-004` are asserted in `LocalStableDiffusionImageGeneratorFailureTest` against `LocalStableDiffusionImageGenerator.generate(...)`.
- `INT-SVC-001` is asserted in `StudioGenerationIntentCodecTest` (foreground intent serialization for diffusion steps).
- `SET-001` is asserted in `SettingsMediaTasksRedTest` via assembled `ModelConfigurationScreen` state.

## Capability And Hardware

Scenario: `CAP-001` `SM8450` classifies as `GEN1`
  Given the hardware haystack `qcom sm8450`
  When `SnapdragonSocClassifier.tier(...)` is evaluated
  Then the returned tier equals `GEN1`

Scenario: `CAP-002` `SM8550` / `kalama` classifies as `GEN2`
  Given the hardware haystack `qcom sm8550 kalama`
  When `SnapdragonSocClassifier.tier(...)` is evaluated
  Then the returned tier equals `GEN2`

Scenario: `CAP-003` `SM8650`, `SM8750`, or `pineapple` classifies as `GEN3_PLUS`
  Given the hardware haystack `motorola sm8750 pineapple`
  When `SnapdragonSocClassifier.tier(...)` is evaluated
  Then the returned tier equals `GEN3_PLUS`

Scenario: `CAP-004` blank or unrelated hardware classifies as `UNKNOWN`
  Given the hardware haystack `""`
  When `SnapdragonSocClassifier.tier(...)` is evaluated
  Then the returned tier equals `UNKNOWN`

Scenario: `CAP-005` Gen2 + QNN supports SD1.5 and blocks SDXL with the exact reason
  Given local image capabilities with `snapdragonTier = GEN2` and `npuAvailable = YES`
  When SD1.5 and SDXL eligibility are resolved
  Then `SD15_QNN` is marked eligible
  And `SDXL_QNN` is marked blocked with reason `Requires Snapdragon 8 Gen 3+ with QNN.`

Scenario: `CAP-006` Gen3+ + QNN supports SD1.5 and SDXL
  Given local image capabilities with `snapdragonTier = GEN3_PLUS` and `npuAvailable = YES`
  When SD1.5 and SDXL eligibility are resolved
  Then `SD15_QNN` is marked eligible
  And `SDXL_QNN` is marked eligible

Scenario: `CAP-007` QNN probe failure blocks all local image pack downloads
  Given local image capabilities with `npuAvailable = NO`
  When download eligibility is resolved
  Then `SD15_QNN` is marked blocked
  And `SDXL_QNN` is marked blocked

Scenario: `CAP-008` insufficient RAM blocks all local image pack downloads with the literal diagnostic
  Given a device with total memory below `8589934592`
  When `LocalImageGenerationCapabilityPortImpl.getCapabilities()` runs
  Then `diagnostics` contains `Insufficient RAM for local diffusion (need >= 8 GiB).`
  And `SD15_QNN` is marked blocked
  And `SDXL_QNN` is marked blocked

## Catalog And Download

Scenario: `DL-001` catalog parses `sd15-t2i` and `sdxl-t2i`
  Given `model_config.json` defines `sd15-t2i` and `sdxl-t2i`
  When `ModelConfigFetcherImpl.fetchParsedCatalog()` parses the file
  Then `ParsedModelCatalog.packs.map { it.packId }` equals `["sd15-t2i", "sdxl-t2i"]`

Scenario: `DL-002` Gen2 startup result includes SD1.5 pack files and excludes SDXL pack files
  Given startup uses `snapdragonTier = GEN2` with QNN ready
  When `InitializeModelsUseCase` computes the startup download result
  Then `modelsToDownload` contains the `sd15-t2i` diffusion member
  And `utilityAssets` contains the `SD15_QNN_PACK` member
  And neither `modelsToDownload` nor `utilityAssets` contains any `sdxl-t2i` member

Scenario: `DL-003` Gen3+ startup result includes SD1.5 and SDXL eligible pack files
  Given startup uses `snapdragonTier = GEN3_PLUS` with QNN ready
  When `InitializeModelsUseCase` computes the startup download result
  Then `modelsToDownload` contains the `sd15-t2i` diffusion member
  And `modelsToDownload` contains the `sdxl-t2i` diffusion member
  And `utilityAssets` contains both `SD15_QNN_PACK` and `SDXL_QNN_PACK`

Scenario: `DL-004` API image-generation assignment suppresses local image pack downloads
  Given the active `IMAGE_GENERATION` assignment is API-backed
  When `InitializeModelsUseCase` computes the startup download result
  Then `modelsToDownload` contains no `sd15-t2i` member
  And `modelsToDownload` contains no `sdxl-t2i` member
  And `utilityAssets` contains no `SD15_QNN_PACK` or `SDXL_QNN_PACK`

Scenario: `DL-005` SDXL extraction installs under `files/models/sdxl-qnn`
  Given a verified `realvis_xl_v5_dmd2_qnn2.28_8gen3.zip` is present in `files/models`
  When the QNN pack installer finalizes the SDXL utility asset
  Then the extracted model directory equals `files/models/sdxl-qnn`

Scenario: `DL-006` SDXL zip deletes only after required artifacts verify
  Given the SDXL zip extracts all required QNN artifacts
  When the QNN pack installer completes
  Then `files/models/realvis_xl_v5_dmd2_qnn2.28_8gen3.zip` does not exist
  And `LocalImageGenerationPaths.hasQnnArtifacts(files/models/sdxl-qnn)` equals `true`

Scenario: `DL-007` incomplete SDXL extraction stays incomplete and does not register as ready
  Given the SDXL zip extracts without `tokenizer.json`
  When finalize attempts to install the SDXL utility asset
  Then `LocalImageGenerationPaths.hasQnnArtifacts(files/models/sdxl-qnn)` equals `false`
  And `ModelPackCompleteness.isComplete` equals `false`
  And the utility registry row for `SDXL_QNN_PACK` is absent

Scenario: `DL-008` pack verifier counts extracted SD1.5 and SDXL QNN directories as installed
  Given `files/models/sd15-qnn` and `files/models/sdxl-qnn` each contain the required QNN files
  When `ModelPackVerifierImpl.verifyPack(...)` runs for `sd15-t2i` and `sdxl-t2i`
  Then each returned `ModelPackCompleteness.isComplete` equals `true`

Scenario: `DL-009` finalizer registers SDXL without overriding the SD1.5 default
  Given a Gen3+ device already has `IMAGE_GENERATION -> sd15-local-config`
  And an SDXL pack download finalizes successfully
  When `DownloadFinalizeWorker` syncs the downloaded SDXL assets
  Then the SDXL local asset is registered as assignable
  And the persisted default assignment remains `sd15-local-config`

## Assignment And Settings

Scenario: `SET-001` Gen3+ installed SDXL appears in local `TextToImage` assignment options
  Given the device policy allows `SDXL_QNN`
  And the `sdxl-t2i` pack completeness is `true`
  When `ModelConfigurationScreen` resolves local `IMAGE_GENERATION` options
  Then the options include the local configuration `sdxl-local-config`

Scenario: `SET-002` Gen2 hides SDXL from assignment and shows the disabled pack reason
  Given the device policy blocks `SDXL_QNN` on `GEN2`
  When Settings renders local image packs and assignment options
  Then no `IMAGE_GENERATION` local option references `sdxl-local-config`
  And the pack UI shows `Requires Snapdragon 8 Gen 3+ with QNN.`

Scenario: `SET-003` direct Gen2 SDXL assignment throws the exact `IllegalArgumentException`
  Given `SetDefaultModelUseCase` receives `ModelType.IMAGE_GENERATION` and `localConfigId = sdxl-local-config`
  And the device policy is `GEN2` with QNN ready
  When the use case runs
  Then it throws `IllegalArgumentException`
  And the exception message equals `Requires Snapdragon 8 Gen 3+ with QNN.`

Scenario: `SET-004` direct Gen3+ SDXL assignment succeeds when the pack is complete
  Given `SetDefaultModelUseCase` receives `ModelType.IMAGE_GENERATION` and `localConfigId = sdxl-local-config`
  And the device policy is `GEN3_PLUS` with QNN ready
  And `ModelPackCompleteness.isComplete` equals `true`
  When the use case runs
  Then the persisted default local config equals `sdxl-local-config`

Scenario: `SET-005` incomplete SDXL assignment throws the existing pack incomplete message
  Given `SetDefaultModelUseCase` receives `localConfigId = sdxl-local-config`
  And the matching pack completeness is `false`
  When the use case runs
  Then it throws `IllegalArgumentException`
  And the exception message equals `Local image pack is not fully downloaded. Download all pack files from Local AI Models before assigning.`

## Studio

Scenario: `STU-001` active SD1.5 exposes exactly `["512x512"]`
  Given the assigned local image model is `SD15_QNN`
  When Studio resolves active image capabilities
  Then `supportedImageResolutions` equals `["512x512"]`

Scenario: `STU-002` active SDXL exposes exactly `["1024x1024"]`
  Given the assigned local image model is `SDXL_QNN`
  When Studio resolves active image capabilities
  Then `supportedImageResolutions` equals `["1024x1024"]`

Scenario: `STU-003` active SDXL exposes exact step options and default `16`
  Given the assigned local image model is `SDXL_QNN`
  When Studio resolves active image capabilities
  Then `supportedImageSteps` equals `[8, 12, 16, 20, 24, 30]`
  And `defaultImageStep` equals `16`

Scenario: `STU-004` Studio bottom sheet shows Resolution for SDXL as `1024x1024`
  Given Studio settings are open for an assigned `SDXL_QNN` target
  When the image settings section renders
  Then the `Resolution` control shows the single option `1024x1024`

Scenario: `STU-005` Studio bottom sheet shows Diffusion Steps for SDXL
  Given Studio settings are open for an assigned `SDXL_QNN` target
  When the image settings section renders
  Then the sheet contains a control labeled `Diffusion Steps`
  And the control options equal `8`, `12`, `16`, `20`, `24`, and `30`

Scenario: `STU-006` Studio bottom sheet hides Diffusion Steps for SD1.5
  Given Studio settings are open for an assigned `SD15_QNN` target
  When the image settings section renders
  Then the sheet does not contain a control labeled `Diffusion Steps`

Scenario: `STU-007` switching SDXL -> SD1.5 clamps stale resolution and clears SDXL step selection
  Given the current image settings are `imageResolution = "1024x1024"` and `diffusionSteps = 24`
  And the active assigned model changes from `SDXL_QNN` to `SD15_QNN`
  When `StudioCapabilitiesCoordinator` applies the new capabilities
  Then `imageResolution` equals `"512x512"`
  And `diffusionSteps` equals `null`

Scenario: `STU-008` switching SD1.5 -> SDXL clamps stale resolution and steps to the SDXL defaults
  Given the current image settings are `imageResolution = "512x512"` and `diffusionSteps = null`
  And the active assigned model changes from `SD15_QNN` to `SDXL_QNN`
  When `StudioCapabilitiesCoordinator` applies the new capabilities
  Then `imageResolution` equals `"1024x1024"`
  And `diffusionSteps` equals `16`

## Inference And Native

Scenario: `INF-001` SD1.5 native request uses `512x512`, `20`, and `7.5`
  Given the assigned local image model is `SD15_QNN`
  When `LocalStableDiffusionImageGenerator.generate(...)` builds the native request
  Then `width` equals `512`
  And `height` equals `512`
  And `steps` equals `20`
  And `cfgScale` equals `7.5`

Scenario: `INF-002` SDXL native request uses `1024x1024`, selected steps `8`, CFG `1.5`, and scheduler `lcm`
  Given the assigned local image model is `SDXL_QNN`
  And Studio settings provide `imageResolution = "1024x1024"` and `diffusionSteps = 8`
  When `LocalStableDiffusionImageGenerator.generate(...)` builds the native request
  Then `width` equals `1024`
  And `height` equals `1024`
  And `steps` equals `8`
  And `cfgScale` equals `1.5`
  And `scheduler` equals `lcm`

Scenario: `INF-003` stale SD1.5 `1024x1024` request fails before native launch with the exact validation error
  Given the assigned local image model is `SD15_QNN`
  And Studio settings provide `imageResolution = "1024x1024"`
  When `LocalStableDiffusionImageGenerator.generate(...)` validates the local request
  Then it fails before `LocalImageGenerationEngine.generate(...)` is invoked
  And the exception message equals `Unsupported local image generation resolution '1024x1024' for model SD15_QNN. Supported resolutions: [512x512].`

Scenario: `INF-004` stale SDXL step `13` clamps to `16` before native launch
  Given the assigned local image model is `SDXL_QNN`
  And Studio settings provide `diffusionSteps = 13`
  When `LocalStableDiffusionImageGenerator.generate(...)` normalizes the local request
  Then the native request `steps` equals `16`

Scenario: `INF-005` C++ backend JSON body contains `"scheduler":"lcm"` for SDXL and is no longer always `"dpm"`
  Given the native generate request contains `scheduler = "lcm"`
  When the native seam `local_image_gen::build_generate_request_body(...)`
    (extracted from `local_image_gen_backend_client.cpp`, declared inline in
    `local_image_gen_backend_client.h`) builds the `/generate` JSON body, exercised by the
    host C++ test `local-image-gen-android/src/test/cpp/generate_request_body_test.cpp`
  Then the JSON contains the literal `"scheduler":"lcm"`
  And the JSON does not contain the literal `"scheduler":"dpm"`

## Mutation Defense

Scenario: `MUT-001` a hardcoded dual-resolution implementation fails the SD1.5 exact-resolution check
  Given a lazy implementation returns `["512x512", "1024x1024"]` for every local model
  When `STU-001` runs against `SD15_QNN`
  Then the assertion fails because the expected value is exactly `["512x512"]`

Scenario: `MUT-002` a QNN-only SDXL gate fails because Gen2 + QNN must still block SDXL
  Given a lazy implementation enables SDXL whenever `npuAvailable = YES`
  When `CAP-005` runs with `snapdragonTier = GEN2`
  Then the assertion fails because the expected blocked reason is `Requires Snapdragon 8 Gen 3+ with QNN.`

Scenario: `MUT-003` a lazy any-step implementation fails because step `13` must clamp to `16`
  Given a lazy implementation forwards every requested SDXL step count unchanged
  When `INF-004` runs with `diffusionSteps = 13`
  Then the assertion fails because the expected native step count is `16`

Scenario: `MUT-004` a hardcoded native `"dpm"` scheduler implementation fails for SDXL
  Given a lazy implementation always emits `"scheduler":"dpm"`
  When `INF-005` runs for an SDXL request against the native seam
    `local_image_gen::build_generate_request_body(...)` (host C++ test
    `local-image-gen-android/src/test/cpp/generate_request_body_test.cpp`)
  Then the assertion fails because the expected scheduler literal is `"scheduler":"lcm"`

## Foreground Service Intent

Scenario: `INT-SVC-001` foreground Studio service intent round-trips diffusion steps
  Given `ImageGenerationSettings` with `diffusionSteps = 24`
  When `StudioGenerationIntentCodec.writeSettings(...)` and `readSettings(...)` run
  Then the decoded settings equal the original `diffusionSteps`
