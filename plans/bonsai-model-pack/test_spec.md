# Test Specification: Bonsai Model Pack

**Ticket ID:** `bonsai-model-pack`  
**Companion spec:** `plans/bonsai-model-pack/spec.md`

---

## Run Commands (AGENTS.md / Gradle modules)

Per AGENTS.md §4 — use module-scoped `testDebugUnitTest` during implementation; full regression before merge.

| Phase | Command |
|-------|---------|
| Catalog parse (data) | `./gradlew :core:data:testDebugUnitTest --tests "com.browntowndev.enclave.core.data.download.ModelConfigFetcherImplTest" --no-daemon` |
| Catalog filter + init (domain) | `./gradlew :core:domain:testDebugUnitTest --tests "com.browntowndev.enclave.domain.usecase.download.InitializeModelsUseCaseTest" --tests "*ParsedModelCatalog*" --tests "*VerifyModelPackCompleteness*" --no-daemon` |
| Assign gate (domain) | `./gradlew :core:domain:testDebugUnitTest --tests "com.browntowndev.enclave.domain.usecase.byok.SetDefaultModelUseCaseTest" --no-daemon` |
| Pack delete / re-download (domain) | `./gradlew :core:domain:testDebugUnitTest --tests "*DeleteModelPackUseCaseTest" --tests "*ReDownloadModelPackUseCaseTest" --no-daemon` |
| Settings UI (feature) | `./gradlew :feature:settings:testDebugUnitTest --tests "*ModelPack*" --tests "*SettingsUiState*" --no-daemon` |
| Onboarding filter (app) | `./gradlew :app:testDebugUnitTest --tests "*Onboarding*" --tests "*EnclaveNavGraph*" --no-daemon` |
| Ticket regression | `./gradlew :core:data:testDebugUnitTest :core:domain:testDebugUnitTest :feature:settings:testDebugUnitTest :app:testDebugUnitTest --tests "*ModelPack*" --tests "*bonsai*" --tests "*InitializeModelsUseCase*" --tests "*SetDefaultModelUseCase*" --no-daemon` |
| Compile smoke | `./gradlew :core:domain:assembleDebug :core:data:assembleDebug :feature:settings:assembleDebug :app:assembleDebug --no-daemon` |
| Style | `./gradlew ktlintCheck detekt --no-daemon` |

---

## Fixture Constants (shared across scenarios)

| Symbol | Value |
|--------|-------|
| `PACK_ID` | `bonsai-t2i` |
| `PACK_DISPLAY` | `Bonsai Flux.2 Klein` |
| `DIFFUSION_FILE` | `bonsai-ternary-4B-q5_K.gguf` |
| `VAE_FILE` | `flux2-vae.safetensors` |
| `ENCODER_FILE` | `Qwen3-4B-Q4_K_M.gguf` |
| `DIFFUSION_SHA` | `c415f811e4b39c1287ce529afbe99ff1ade0bb9ac0e5767019d9490a606b1983` |
| `VAE_SHA` | `868fe7b343cc8f3a19dbcfcafbc3d5f888802be3f89bd81b65b3621a066ce8f3` |
| `ENCODER_SHA` | `f6f851777709861056efcdad3af01da38b31223a3ba26e61a4f8bf3a2195813a` |
| `BONSAI_CONFIG_ID` | `bonsai-local-config` |
| `PACK_INCOMPLETE_MESSAGE` | `Bonsai image pack is not fully downloaded. Download all pack files from Local AI Models before assigning.` |
| `TOTAL_MEMBERS` | `3` |

---

## Happy Path Scenarios

### MPK-001 — Parse modelPacks from model_config.json

```gherkin
Scenario: MPK-001 Parse modelPacks from model_config.json
  Given model_config.json root contains modelPacks with packId "bonsai-t2i"
  And pack displayName is "Bonsai Flux.2 Klein"
  And pack slot is "image_generation"
  And pack members reference fileNames "bonsai-ternary-4B-q5_K.gguf", "flux2-vae.safetensors", "Qwen3-4B-Q4_K_M.gguf"
  When ModelConfigFetcherImpl.fetchParsedCatalog() runs
  Then ParsedModelCatalog.packs has size 1
  And ParsedModelCatalog.packs[0].packId equals "bonsai-t2i"
  And ParsedModelCatalog.packs[0].displayName equals "Bonsai Flux.2 Klein"
  And ParsedModelCatalog.packs[0].members has size 3
```

### MPK-002 — Pack member assets resolve from assets[]

```gherkin
Scenario: MPK-002 Pack member assets resolve from assets[]
  Given a valid ParsedModelCatalog for pack "bonsai-t2i"
  When pack members are resolved
  Then diffusion member asset metadata sha256 equals "c415f811e4b39c1287ce529afbe99ff1ade0bb9ac0e5767019d9490a606b1983"
  And vae member asset metadata utilityType equals FLUX2_VAE
  And text_encoder member asset metadata utilityType equals QWEN3_TEXT_ENCODER
```

### MPK-003 — Invalid pack member fileName fails parse

```gherkin
Scenario: MPK-003 Invalid pack member fileName fails parse
  Given model_config.json modelPacks references fileName "missing-file.gguf"
  When ModelConfigFetcherImpl.fetchParsedCatalog() runs
  Then the result is failure
  And the exception message contains "Pack member file not found in assets"
```

### MPK-004 — Local IMAGE_GENERATION excludes pack utilities from utilityAssets

```gherkin
Scenario: MPK-004 Local IMAGE_GENERATION excludes pack utilities from utilityAssets
  Given ParsedModelCatalog contains pack "bonsai-t2i"
  And active IMAGE_GENERATION configuration is local or unset
  When ParsedModelCatalog.filterForDownload(imageGenerationIsApi = false) runs
  Then utilityAssets contains no asset with utilityType FLUX2_VAE
  And utilityAssets contains no asset with utilityType QWEN3_TEXT_ENCODER
  And slotAssignments[IMAGE_GENERATION] metadata localFileName equals "bonsai-ternary-4B-q5_K.gguf"
```

### MPK-005 — API IMAGE_GENERATION skips entire pack on InitializeModelsUseCase

```gherkin
Scenario: MPK-005 API IMAGE_GENERATION skips entire pack on InitializeModelsUseCase
  Given remote catalog includes pack "bonsai-t2i" and whisper utility
  And activeModelProvider.getActiveConfiguration(IMAGE_GENERATION) returns API assignment isLocal false
  When InitializeModelsUseCase.invoke() runs
  Then checkModelsUseCase is called with expectedModels that exclude IMAGE_GENERATION
  And checkModelsUseCase is called with utilityAssets that exclude sha256 "868fe7b343cc8f3a19dbcfcafbc3d5f888802be3f89bd81b65b3621a066ce8f3"
  And checkModelsUseCase is called with utilityAssets that exclude sha256 "f6f851777709861056efcdad3af01da38b31223a3ba26e61a4f8bf3a2195813a"
  And modelsToDownload contains zero assets whose metadata sha256 is in pack member sha set
```

### MPK-006 — API onboarding download screen enqueues zero pack files

```gherkin
Scenario: MPK-006 API onboarding download screen enqueues zero pack files
  Given DownloadModelsResult.modelsToDownload includes diffusion, vae, and encoder before filter
  And user completes onboarding with ModelChoice.API
  When EnclaveNavGraph applies API onboarding filter to effectiveModelsResult
  Then effectiveModelsResult.modelsToDownload has size 0
  And effectiveModelsResult.allModels is empty
```

### MPK-007 — Settings shows one pack row with completeness 2/3

```gherkin
Scenario: MPK-007 Settings shows one pack row with completeness 2/3
  Given disk has verified files for "bonsai-ternary-4B-q5_K.gguf" and "flux2-vae.safetensors"
  And disk is missing "Qwen3-4B-Q4_K_M.gguf"
  When GetModelPacksForSettingsUseCase.invoke() runs
  Then rows has size 1
  And rows[0].completeness.installedCount equals 2
  And rows[0].completeness.totalCount equals 3
  And ModelPackUiMapper maps completenessLabel to "2/3"
```

### MPK-008 — Full pack completeness is 3/3

```gherkin
Scenario: MPK-008 Full pack completeness is 3/3
  Given all three pack member files exist with matching sizeInBytes and sha256
  When VerifyModelPackCompletenessUseCase.invoke("bonsai-t2i") runs
  Then result.isComplete is true
  And result.installedCount equals 3
  And result.totalCount equals 3
```

### MPK-009 — Re-download enqueues only missing pack member

```gherkin
Scenario: MPK-009 Re-download enqueues only missing pack member
  Given pack "bonsai-t2i" has installedCount 2 and missing member fileName "Qwen3-4B-Q4_K_M.gguf"
  When ReDownloadModelPackUseCase.invoke("bonsai-t2i") runs
  Then scheduled download file count equals 1
  And scheduled download includes remoteFileName "Qwen3-4B-Q4_K_M.gguf"
  And scheduled download excludes remoteFileName "bonsai-ternary-4B-q5_K.gguf"
  And scheduled download excludes remoteFileName "flux2-vae.safetensors"
```

### MPK-010 — Local IMAGE_GENERATION assign allowed when pack complete

```gherkin
Scenario: MPK-010 Local IMAGE_GENERATION assign allowed when pack complete
  Given VerifyModelPackCompletenessUseCase returns isComplete true for "bonsai-t2i"
  And localModelRepository.getAssetByConfigId("bonsai-local-config") returns Bonsai diffusion asset
  When SetDefaultModelUseCase.invoke(IMAGE_GENERATION, localConfigId = "bonsai-local-config", apiConfigId = null) runs
  Then default assignment localConfigId equals "bonsai-local-config"
  And downloadWorkScheduler.enqueue is never called
```

### MPK-011 — Settings hides pack member utilities from asset list

```gherkin
Scenario: MPK-011 Settings hides pack member utilities from asset list
  Given catalog defines pack "bonsai-t2i"
  When SettingsUiStateAssembler builds localModelsSheet
  Then localModelsSheet.modelPacks has size 1
  And localModelsSheet.models contains no asset with remoteFileName "flux2-vae.safetensors"
  And localModelsSheet.models contains no asset with remoteFileName "Qwen3-4B-Q4_K_M.gguf"
```

### MPK-012 — Whisper utility still downloads when IMAGE_GENERATION is API

```gherkin
Scenario: MPK-012 Whisper utility still downloads when IMAGE_GENERATION is API
  Given remote catalog includes whisper utility sha "a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002"
  And IMAGE_GENERATION slot is API
  When InitializeModelsUseCase.invoke() runs
  Then checkModelsUseCase utilityAssets includes whisper utility
  And utilityAssets size equals 1
```

---

## Error Path Scenarios

### MPK-013 — Delete pack removes DB soft-delete and physical files for all members

```gherkin
Scenario: MPK-013 Delete pack removes DB soft-delete and physical files for all members
  Given pack "bonsai-t2i" is installed with registry rows for diffusion and both utilities
  When DeleteModelPackUseCase.invoke("bonsai-t2i") runs successfully
  Then DeleteLocalModelUseCase ran for diffusion metadata id
  And utilityModelRepository.softDelete ran for FLUX2_VAE and QWEN3_TEXT_ENCODER
  And modelFileScanner.deleteModelFileByLocalFileName was called for "bonsai-ternary-4B-q5_K.gguf"
  And modelFileScanner.deleteModelFileByLocalFileName was called for "flux2-vae.safetensors"
  And modelFileScanner.deleteModelFileByLocalFileName was called for "Qwen3-4B-Q4_K_M.gguf"
```

### MPK-014 — Registry-without-file shows download action

```gherkin
Scenario: MPK-014 Registry-without-file shows download action
  Given local_models row exists for Bonsai diffusion with configs present
  And physical file "bonsai-ternary-4B-q5_K.gguf" is absent on disk
  When GetModelPacksForSettingsUseCase.invoke() runs
  Then rows[0].showDownloadAction is true
  And rows[0].completeness.installedCount equals 0
  And rows[0].completeness.totalCount equals 3
```

### MPK-015 — Soft-deleted pack shows download action

```gherkin
Scenario: MPK-015 Soft-deleted pack shows download action
  Given diffusion model is soft-deleted configurations empty
  And utility models for pack are soft-deleted
  When GetModelPacksForSettingsUseCase.invoke() runs
  Then rows[0].isSoftDeleted is true
  And rows[0].showDownloadAction is true
```

---

## Edge Scenarios

### MPK-016 — Partial assign blocked with exact message

```gherkin
Scenario: MPK-016 Partial assign blocked with exact message
  Given VerifyModelPackCompletenessUseCase returns installedCount 2 totalCount 3 isComplete false
  And localModelRepository.getAssetByConfigId("bonsai-local-config") returns Bonsai diffusion asset
  When SetDefaultModelUseCase.invoke(IMAGE_GENERATION, localConfigId = "bonsai-local-config", apiConfigId = null) is attempted
  Then IllegalArgumentException message equals "Bonsai image pack is not fully downloaded. Download all pack files from Local AI Models before assigning."
  And default assignment for IMAGE_GENERATION is unchanged
```

### MPK-017 — Invalid on-disk hash counts as not installed

```gherkin
Scenario: MPK-017 Invalid on-disk hash counts as not installed
  Given file "flux2-vae.safetensors" exists with wrong sha256
  When VerifyModelPackCompletenessUseCase.invoke("bonsai-t2i") runs
  Then installedCount equals 0
  Or installedCount equals 1
  And isComplete is false
  And vae member isVerifiedOnDisk is false
```

### MPK-018 — Assign does not auto-queue downloads (mutation trap)

```gherkin
Scenario: MPK-018 Assign does not auto-queue downloads
  Given pack completeness is incomplete
  When SetDefaultModelUseCase.invoke(IMAGE_GENERATION, localConfigId = "bonsai-local-config", apiConfigId = null) throws
  Then downloadWorkScheduler.enqueue call count equals 0
```

---

## Mutation Defense Scenarios

### MPK-019 — Lazy impl must not download Bonsai utilities when IMAGE_GENERATION is API

```gherkin
Scenario: MPK-019 Lazy impl must not download Bonsai utilities when IMAGE_GENERATION is API
  Given IMAGE_GENERATION active config is API isLocal false
  And remote catalog contains flux2_vae and qwen3_text_encoder utilities
  When InitializeModelsUseCase.invoke() runs
  Then modelsToDownload.filter { it.metadata.sha256 == "868fe7b343cc8f3a19dbcfcafbc3d5f888802be3f89bd81b65b3621a066ce8f3" } has size 0
  And modelsToDownload.filter { it.metadata.sha256 == "f6f851777709861056efcdad3af01da38b31223a3ba26e61a4f8bf3a2195813a" } has size 0
  And modelsToDownload.filter { it.metadata.sha256 == "c415f811e4b39c1287ce529afbe99ff1ade0bb9ac0e5767019d9490a606b1983" } has size 0
```

**Defense:** A lazy fix that only filters slot maps but leaves `utilityAssets` untouched must fail this scenario. Assert `checkModelsUseCase` **second argument** `utilityAssets` excludes both companion SHAs, not only `expectedModels`.

### MPK-020 — Onboarding must not whitelist pack utilities for API users

```gherkin
Scenario: MPK-020 Onboarding must not whitelist pack utilities for API users
  Given modelsResult.utilityAssets includes flux2 and qwen3 encoders
  And modelsResult.modelsToDownload includes all three pack member assets
  When onboarding completes with ModelChoice.API
  Then filtered modelsToDownload has size 0
  And filtered utilityAssets is not passed to download queue with any pack member sha
```

**Defense:** Replacing `utilityShas` whitelist (current bug) with pack-aware exclusion must be tested; a regression that keeps `utilityShas` filtering will still download companions.

### MPK-021 — Re-download must not enqueue verified members

```gherkin
Scenario: MPK-021 Re-download must not enqueue verified members
  Given installedCount equals 3 and isComplete true
  When ReDownloadModelPackUseCase.invoke("bonsai-t2i") runs
  Then scheduled downloads list is empty
```

**Defense:** Prevents “re-download pack” from always scheduling three WorkManager jobs.

### MPK-022 — Pack utilities must not appear in standalone utility download list

```gherkin
Scenario: MPK-022 Pack utilities must not appear in standalone utility download list
  Given IMAGE_GENERATION is local assignment
  When ParsedModelCatalog.filterForDownload(imageGenerationIsApi = false) runs
  Then utilityAssets.map { it.metadata.utilityType } equals list without FLUX2_VAE and without QWEN3_TEXT_ENCODER
```

**Defense:** Catches duplicate download paths (utility queue + pack queue) for the same files.

---

## Test File Placement (implementation)

| Scenario IDs | Suggested test class |
|--------------|---------------------|
| MPK-001–003 | `core/data/.../ModelConfigFetcherImplTest.kt` |
| MPK-004 | `core/domain/.../ParsedModelCatalogTest.kt` (new) |
| MPK-005, MPK-012, MPK-019 | `core/domain/.../InitializeModelsUseCaseTest.kt` |
| MPK-006, MPK-020 | `app/.../MainViewModelOnboardingTest.kt` or dedicated `EnclaveNavGraphPackFilterTest.kt` |
| MPK-007–008, MPK-014–017, MPK-021–022 | `core/domain/.../VerifyModelPackCompletenessUseCaseTest.kt` |
| MPK-009, MPK-021 | `core/domain/.../ReDownloadModelPackUseCaseTest.kt` |
| MPK-010, MPK-016, MPK-018 | `core/domain/.../SetDefaultModelUseCaseTest.kt` |
| MPK-011 | `feature/settings/.../SettingsUiStateFactoryTest.kt` or `ModelPackUiMapperTest.kt` |
| MPK-013 | `core/domain/.../DeleteModelPackUseCaseTest.kt` |
| MPK-015 | `core/domain/.../GetModelPacksForSettingsUseCaseTest.kt` |

---

## Scenario Index

| ID | Category | Summary |
|----|----------|---------|
| MPK-001 | Happy | Parse `modelPacks[]` |
| MPK-002 | Happy | Resolve members from `assets[]` |
| MPK-003 | Error | Invalid member fileName |
| MPK-004 | Happy | Exclude pack utilities when local |
| MPK-005 | Happy | Init skips pack when API |
| MPK-006 | Happy | Onboarding zero pack downloads |
| MPK-007 | Happy | UI `2/3` label |
| MPK-008 | Happy | Full `3/3` verify |
| MPK-009 | Happy | Partial re-download one file |
| MPK-010 | Happy | Assign when complete |
| MPK-011 | Happy | Hide member rows in settings |
| MPK-012 | Happy | Whisper still downloads |
| MPK-013 | Error | Delete all members |
| MPK-014 | Edge | Registry without file |
| MPK-015 | Edge | Soft-deleted pack |
| MPK-016 | Error | Partial assign message |
| MPK-017 | Edge | Bad hash not installed |
| MPK-018 | Edge | No auto-queue on assign |
| MPK-019 | Mutation | API utility download trap |
| MPK-020 | Mutation | Onboarding whitelist trap |
| MPK-021 | Mutation | Full pack re-download noop |
| MPK-022 | Mutation | Duplicate utility path |

**Total scenarios: 22**

---

## Pre-Merge Gate

1. All commands in **Run Commands** pass for touched modules.  
2. Every scenario ID has at least one automated test with `@DisplayName` containing the scenario id (e.g. `MPK-005`).  
3. Literal strings in Then clauses match `spec.md` §3.7 constants exactly.  
4. `./gradlew detekt` reports `maxIssues: 0`.