# Juggernaut XL QNN Test Spec

## Framework And Commands

- Frameworks: JUnit 5, Turbine, MockK, Robolectric, and Compose UI tests where UI behavior changes.
- Primary targeted commands:
  - `./gradlew :core:domain:testDebugUnitTest --tests "*JuggernautXlQnnPackCatalogTest" --tests "*ParsedModelCatalogTest" --tests "*LocalImageGenerationCapabilityPolicyTest" --tests "*LocalImageGenerationModelPolicyTest" --tests "*LocalImageGenerationPathsTest" --tests "*InitializeModelsUseCaseTest" --tests "*SetDefaultModelUseCaseTest" --tests "*GetModelPacksForSettingsUseCaseTest" --tests "*AutoAssignLocalModelsUseCaseTest" --tests "*ResolveAssignedImageGenerationTargetUseCaseTest"`
  - `./gradlew :core:data:testDebugUnitTest --tests "*ModelConfigFetcherImplTest" --tests "*Sd15QnnPackInstallerTest" --tests "*ModelPackVerifierImplTest" --tests "*DownloadFinalizeWorkerTest" --tests "*UtilityModelFileResolverTest"`
  - `./gradlew :feature:settings:testDebugUnitTest --tests "*SettingsMediaTasksRedTest" --tests "*ModelPackUiMapperTest" --tests "*SettingsViewModelTest"`
  - `./gradlew :feature:studio:testDebugUnitTest --tests "*MultimodalViewModelTest" --tests "*StudioCapabilitiesCoordinatorTest"`
  - `./gradlew :feature:inference:testDebugUnitTest --tests "*LocalImageGenerationDevicePolicyTest" --tests "*LocalStableDiffusionImageGeneratorFailureTest" --tests "*ImageGenerationPortImplTest"`
  - `cmake -S local-image-gen-android/src/test/cpp -B build/juggernaut-cpp-test && cmake --build build/juggernaut-cpp-test && ctest --test-dir build/juggernaut-cpp-test --output-on-failure`

## Scenario Index

| ID | Title |
|---|---|
| `CAP-005` | Gen2 + QNN supports SD1.5 and blocks SDXL with the exact reason |
| `CAP-006` | Gen3+ + QNN supports SD1.5, SDXL, and Juggernaut |
| `CAP-009` | Gen2 + QNN blocks Juggernaut with the exact reason |
| `CAP-010` | `LocalImageGenerationCapabilityPortImpl` exposes `juggernautXlNpuSupported = NO` on Gen2 |
| `DL-001` | catalog parses `sd15-t2i`, `sdxl-t2i`, and `juggernaut-xl-t2i` |
| `DL-002` | Gen2 startup includes SD1.5 and excludes SDXL and Juggernaut pack files |
| `DL-003` | Gen3+ startup includes SD1.5, SDXL, and Juggernaut eligible pack files |
| `DL-004` | API `IMAGE_GENERATION` suppresses SD1.5 and SDXL packs but not Juggernaut |
| `DL-008` | pack verifier counts SD1.5, SDXL, and Juggernaut QNN directories as installed |
| `DL-009` | finalizer registers SDXL without overriding the SD1.5 default |
| `DL-010` | Juggernaut extraction installs under `files/models/juggernaut-qnn` |
| `DL-011` | Juggernaut zip deletes only after required artifacts verify |
| `DL-012` | incomplete Juggernaut extraction stays incomplete and does not register as ready |
| `DL-013` | finalizer registers Juggernaut without overriding an existing `IMAGE_EDIT` default |
| `DL-014` | API `IMAGE_EDIT` suppresses Juggernaut pack files but not SD1.5 or SDXL |
| `SET-006` | Gen3+ installed Juggernaut appears in local `IMAGE_EDIT` assignment options |
| `SET-007` | Gen2 hides Juggernaut from assignment and shows the disabled pack reason |
| `SET-008` | direct Gen2 Juggernaut assignment throws the exact `IllegalArgumentException` |
| `SET-009` | direct Gen3+ Juggernaut `IMAGE_EDIT` assignment succeeds when the pack is complete |
| `SET-010` | Juggernaut `IMAGE_EDIT` assignment without `vae_encoder.bin` throws the encoder message |
| `SET-011` | `AutoAssignLocalModelsUseCase` assigns `juggernaut-local-config` to `IMAGE_EDIT` when installed |
| `STU-009` | active Juggernaut exposes exactly `["1024x1024"]` |
| `STU-010` | active Juggernaut exposes exact step options and default `24` |
| `STU-011` | switching RealVis SDXL -> Juggernaut clamps stale SDXL steps to Juggernaut defaults |
| `INF-006` | Juggernaut native request uses `1024x1024`, `24`, CFG `4.0`, and scheduler `dpm` |
| `INF-007` | stale Juggernaut step `23` clamps to `24` before native launch |
| `ASG-001` | SD1.5, SDXL, and Juggernaut packs may all be complete on one device |
| `MUT-002` | a QNN-only SDXL-family gate fails because Gen2 + QNN must still block Juggernaut |
| `MUT-005` | reusing SDXL `lcm` policy for Juggernaut fails native scheduler and CFG checks |
| `MUT-006` | extracting Juggernaut into `sdxl-qnn` fails the separate-directory check |

Scenario home notes:

- `CAP-005`, `CAP-006`, `CAP-009`, and `MUT-002` are asserted in `LocalImageGenerationCapabilityPolicyTest`.
- `CAP-010` is asserted in `LocalImageGenerationDevicePolicyTest`.
- `INF-006` and `INF-007` are asserted in `LocalStableDiffusionImageGeneratorFailureTest`.
- `SET-006` is asserted in `SettingsMediaTasksRedTest` via assembled `ModelConfigurationScreen` state.
- `ASG-001` is asserted in `ModelPackVerifierImplTest`.
- `INF-006` scheduler literal is also asserted in `local-image-gen-android/src/test/cpp/generate_request_body_test.cpp` for `MUT-005`.

## Capability And Hardware

Scenario: `CAP-005` Gen2 + QNN supports SD1.5 and blocks SDXL with the exact reason
  Given local image capabilities with `snapdragonTier = GEN2` and `npuAvailable = YES`
  When SD1.5 and SDXL eligibility are resolved
  Then `SD15_QNN` is marked eligible
  And `SDXL_QNN` is marked blocked with reason `Requires Snapdragon 8 Gen 3+ with QNN.`

Scenario: `CAP-006` Gen3+ + QNN supports SD1.5, SDXL, and Juggernaut
  Given local image capabilities with `snapdragonTier = GEN3_PLUS` and `npuAvailable = YES`
  When SD1.5, SDXL, and Juggernaut eligibility are resolved
  Then `SD15_QNN` is marked eligible
  And `SDXL_QNN` is marked eligible
  And `JUGGERNAUT_XL_QNN` is marked eligible

Scenario: `CAP-009` Gen2 + QNN blocks Juggernaut with the exact reason
  Given local image capabilities with `snapdragonTier = GEN2` and `npuAvailable = YES`
  When Juggernaut eligibility is resolved
  Then `JUGGERNAUT_XL_QNN` is marked blocked with reason `Requires Snapdragon 8 Gen 3+ with QNN.`

Scenario: `CAP-010` `LocalImageGenerationCapabilityPortImpl` exposes `juggernautXlNpuSupported = NO` on Gen2
  Given a device classified as `GEN2` with QNN probe success
  When `LocalImageGenerationCapabilityPortImpl.getCapabilities()` runs
  Then `juggernautXlNpuSupported` equals `NO`
  And `disabledReasonsByModelId[JUGGERNAUT_XL_QNN]` equals `Requires Snapdragon 8 Gen 3+ with QNN.`

## Catalog And Download

Scenario: `DL-001` catalog parses `sd15-t2i`, `sdxl-t2i`, and `juggernaut-xl-t2i`
  Given `model_config.json` defines `sd15-t2i`, `sdxl-t2i`, and `juggernaut-xl-t2i`
  When `ModelConfigFetcherImpl.fetchParsedCatalog()` parses the file
  Then `ParsedModelCatalog.packs.map { it.packId }` equals `["sd15-t2i", "sdxl-t2i", "juggernaut-xl-t2i"]`
  And the Juggernaut pack `slot` equals `IMAGE_EDIT`

Scenario: `DL-002` Gen2 startup includes SD1.5 and excludes SDXL and Juggernaut pack files
  Given startup uses `snapdragonTier = GEN2` with QNN ready
  When `InitializeModelsUseCase` computes the startup download result
  Then `modelsToDownload` contains the `sd15-t2i` diffusion member
  And `utilityAssets` contains the `SD15_QNN_PACK` member
  And neither `modelsToDownload` nor `utilityAssets` contains any `sdxl-t2i` member
  And neither `modelsToDownload` nor `utilityAssets` contains any `juggernaut-xl-t2i` member

Scenario: `DL-003` Gen3+ startup includes SD1.5, SDXL, and Juggernaut eligible pack files
  Given startup uses `snapdragonTier = GEN3_PLUS` with QNN ready
  And both `IMAGE_GENERATION` and `IMAGE_EDIT` slots are local-backed
  When `InitializeModelsUseCase` computes the startup download result
  Then `modelsToDownload` contains the `sd15-t2i` diffusion member
  And `modelsToDownload` contains the `sdxl-t2i` diffusion member
  And `modelsToDownload` contains the `juggernaut-xl-t2i` diffusion member
  And `utilityAssets` contains `SD15_QNN_PACK`, `SDXL_QNN_PACK`, and `JUGGERNAUT_XL_QNN_PACK`

Scenario: `DL-004` API `IMAGE_GENERATION` suppresses SD1.5 and SDXL packs but not Juggernaut
  Given the active `IMAGE_GENERATION` assignment is API-backed
  And the active `IMAGE_EDIT` assignment is local-backed
  When `InitializeModelsUseCase` computes the startup download result
  Then `modelsToDownload` contains no `sd15-t2i` member
  And `modelsToDownload` contains no `sdxl-t2i` member
  And `modelsToDownload` contains the `juggernaut-xl-t2i` diffusion member

Scenario: `DL-008` pack verifier counts SD1.5, SDXL, and Juggernaut QNN directories as installed
  Given `files/models/sd15-qnn`, `files/models/sdxl-qnn`, and `files/models/juggernaut-qnn` each contain the required QNN files
  When `ModelPackVerifierImpl.verifyPack(...)` runs for `sd15-t2i`, `sdxl-t2i`, and `juggernaut-xl-t2i`
  Then each returned `ModelPackCompleteness.isComplete` equals `true`

Scenario: `DL-009` finalizer registers SDXL without overriding the SD1.5 default
  Given a Gen3+ device already has `IMAGE_GENERATION -> sd15-local-config`
  And an SDXL pack download finalizes successfully
  When `DownloadFinalizeWorker` syncs the downloaded SDXL assets
  Then the SDXL local asset is registered as assignable
  And the persisted default assignment remains `sd15-local-config`

Scenario: `DL-010` Juggernaut extraction installs under `files/models/juggernaut-qnn`
  Given a verified `juggernaut_qnn2.28_8gen3.zip` is present in `files/models`
  When the QNN pack installer finalizes the Juggernaut utility asset
  Then the extracted model directory equals `files/models/juggernaut-qnn`
  And `files/models/sdxl-qnn` is not created or modified by the Juggernaut install

Scenario: `DL-011` Juggernaut zip deletes only after required artifacts verify
  Given the Juggernaut zip extracts all required QNN artifacts into `files/models/juggernaut-qnn`
  When the QNN pack installer completes
  Then `files/models/juggernaut_qnn2.28_8gen3.zip` does not exist
  And `LocalImageGenerationPaths.hasJuggernautXlQnnArtifacts(files/models/juggernaut-qnn)` equals `true`

Scenario: `DL-012` incomplete Juggernaut extraction stays incomplete and does not register as ready
  Given the Juggernaut zip extracts without `tokenizer.json` under `files/models/juggernaut-qnn`
  When finalize attempts to install the Juggernaut utility asset
  Then `LocalImageGenerationPaths.hasJuggernautXlQnnArtifacts(files/models/juggernaut-qnn)` equals `false`
  And `ModelPackCompleteness.isComplete` equals `false`
  And the utility registry row for `JUGGERNAUT_XL_QNN_PACK` is absent

Scenario: `DL-013` finalizer registers Juggernaut without overriding an existing `IMAGE_EDIT` default
  Given a Gen3+ device already has `IMAGE_EDIT -> sdxl-local-config`
  And a Juggernaut pack download finalizes successfully
  When `DownloadFinalizeWorker` syncs the downloaded Juggernaut assets
  Then the Juggernaut local asset is registered as assignable
  And the persisted default assignment remains `sdxl-local-config`

Scenario: `DL-014` API `IMAGE_EDIT` suppresses Juggernaut pack files but not SD1.5 or SDXL
  Given the active `IMAGE_EDIT` assignment is API-backed
  And the active `IMAGE_GENERATION` assignment is local-backed
  When `InitializeModelsUseCase` computes the startup download result
  Then `modelsToDownload` contains no `juggernaut-xl-t2i` member
  And `utilityAssets` contains no `JUGGERNAUT_XL_QNN_PACK` member
  And `modelsToDownload` contains the `sd15-t2i` diffusion member
  And `modelsToDownload` contains the `sdxl-t2i` diffusion member

## Assignment And Settings

Scenario: `SET-006` Gen3+ installed Juggernaut appears in local `IMAGE_EDIT` assignment options
  Given the device policy allows `JUGGERNAUT_XL_QNN`
  And the `juggernaut-xl-t2i` pack completeness is `true`
  When `ModelConfigurationScreen` resolves local `IMAGE_EDIT` options
  Then the options include the local configuration `juggernaut-local-config`
  And the options do not list `juggernaut-local-config` under `IMAGE_GENERATION`

Scenario: `SET-007` Gen2 hides Juggernaut from assignment and shows the disabled pack reason
  Given the device policy blocks `JUGGERNAUT_XL_QNN` on `GEN2`
  When Settings renders local image packs and `IMAGE_EDIT` assignment options
  Then no `IMAGE_EDIT` local option references `juggernaut-local-config`
  And the pack UI shows `Requires Snapdragon 8 Gen 3+ with QNN.`

Scenario: `SET-008` direct Gen2 Juggernaut assignment throws the exact `IllegalArgumentException`
  Given `SetDefaultModelUseCase` receives `ModelType.IMAGE_EDIT` and `localConfigId = juggernaut-local-config`
  And the device policy is `GEN2` with QNN ready
  When the use case runs
  Then it throws `IllegalArgumentException`
  And the exception message equals `Requires Snapdragon 8 Gen 3+ with QNN.`

Scenario: `SET-009` direct Gen3+ Juggernaut `IMAGE_EDIT` assignment succeeds when the pack is complete
  Given `SetDefaultModelUseCase` receives `ModelType.IMAGE_EDIT` and `localConfigId = juggernaut-local-config`
  And the device policy is `GEN3_PLUS` with QNN ready
  And `ModelPackCompleteness.isComplete` equals `true`
  And `files/models/juggernaut-qnn/vae_encoder.bin` exists
  When the use case runs
  Then the persisted default local config equals `juggernaut-local-config`

Scenario: `SET-010` Juggernaut `IMAGE_EDIT` assignment without `vae_encoder.bin` throws the encoder message
  Given `SetDefaultModelUseCase` receives `ModelType.IMAGE_EDIT` and `localConfigId = juggernaut-local-config`
  And the Juggernaut pack is complete except `vae_encoder.bin` is absent
  When the use case runs
  Then it throws `IllegalArgumentException`
  And the exception message equals `Local image edit requires vae_encoder.bin in the model pack.`

Scenario: `SET-011` `AutoAssignLocalModelsUseCase` assigns `juggernaut-local-config` to `IMAGE_EDIT` when installed
  Given the `IMAGE_EDIT` slot is empty
  And local assets include `juggernaut-local-config` with `defaultAssignments = [IMAGE_EDIT]`
  When `AutoAssignLocalModelsUseCase` runs
  Then `setDefaultModel` is called with `modelType = IMAGE_EDIT` and `localConfigId = juggernaut-local-config`

Scenario: `ASG-001` SD1.5, SDXL, and Juggernaut packs may all be complete on one device
  Given `files/models/sd15-qnn`, `files/models/sdxl-qnn`, and `files/models/juggernaut-qnn` are each fully populated
  When pack completeness is evaluated for all three pack IDs
  Then `sd15-t2i` completeness is `true`
  And `sdxl-t2i` completeness is `true`
  And `juggernaut-xl-t2i` completeness is `true`

## Studio

Scenario: `STU-009` active Juggernaut exposes exactly `["1024x1024"]`
  Given the assigned local image model is `JUGGERNAUT_XL_QNN`
  When Studio resolves active image capabilities
  Then `supportedImageResolutions` equals `["1024x1024"]`

Scenario: `STU-010` active Juggernaut exposes exact step options and default `24`
  Given the assigned local image model is `JUGGERNAUT_XL_QNN`
  When Studio resolves active image capabilities
  Then `supportedImageSteps` equals `[20, 22, 24, 26, 28, 30]`
  And `defaultImageStep` equals `24`

Scenario: `STU-011` switching RealVis SDXL -> Juggernaut clamps stale SDXL steps to Juggernaut defaults
  Given the current image settings are `imageResolution = "1024x1024"` and `diffusionSteps = 8`
  And the active assigned model changes from `SDXL_QNN` to `JUGGERNAUT_XL_QNN`
  When `StudioCapabilitiesCoordinator` applies the new capabilities
  Then `imageResolution` equals `"1024x1024"`
  And `diffusionSteps` equals `24`

## Inference And Native

Scenario: `INF-006` Juggernaut native request uses `1024x1024`, `24`, CFG `4.0`, and scheduler `dpm`
  Given the assigned local image model is `JUGGERNAUT_XL_QNN`
  And Studio settings provide `imageResolution = "1024x1024"` and `diffusionSteps = 24`
  When `LocalStableDiffusionImageGenerator.generate(...)` builds the native request
  Then `width` equals `1024`
  And `height` equals `1024`
  And `steps` equals `24`
  And `cfgScale` equals `4.0`
  And `scheduler` equals `dpm`

Scenario: `INF-007` stale Juggernaut step `23` clamps to `24` before native launch
  Given the assigned local image model is `JUGGERNAUT_XL_QNN`
  And Studio settings provide `diffusionSteps = 23`
  When `LocalStableDiffusionImageGenerator.generate(...)` normalizes the local request
  Then the native request `steps` equals `24`

## Mutation Defense

Scenario: `MUT-002` a QNN-only SDXL-family gate fails because Gen2 + QNN must still block Juggernaut
  Given a lazy implementation enables every SDXL-family model whenever `npuAvailable = YES`
  When `CAP-009` runs with `snapdragonTier = GEN2`
  Then the assertion fails because the expected blocked reason is `Requires Snapdragon 8 Gen 3+ with QNN.`

Scenario: `MUT-005` reusing SDXL `lcm` policy for Juggernaut fails native scheduler and CFG checks
  Given a lazy implementation maps `JUGGERNAUT_XL_QNN` to the `SDXL_QNN` policy literals (`scheduler = lcm`, `cfgScale = 1.5`, default step `8`)
  When `INF-006` runs for Juggernaut
  Then the assertion fails because the expected scheduler is `dpm`
  And the expected `cfgScale` is `4.0`
  And the expected `steps` is `24`

Scenario: `MUT-006` extracting Juggernaut into `sdxl-qnn` fails the separate-directory check
  Given a lazy implementation reuses `LocalImageGenerationPaths.SDXL_QNN_SUBDIR` for Juggernaut installs
  When `DL-010` runs
  Then the assertion fails because the expected extracted directory is `files/models/juggernaut-qnn`
