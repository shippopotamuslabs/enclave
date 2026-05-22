# BYOK-MODEL-ON-PRESET Spec

**Ticket ID:** `BYOK-MODEL-ON-PRESET`

## Feature Summary

Refactor BYOK persistence and settings UX so **credentials (assets)** store only connection identity (provider, base URL, encrypted API key, human display name) and **presets (`ApiModelConfiguration`)** store **model ID**, **vision/multimodal flag**, and tuning fields. The app is unreleased: **no Room migration**, **no Firestore backward compatibility** — bump schema version and rely on existing `fallbackToDestructiveMigration(dropAllTables = true)`; users reinstall and purge Firestore.

**Problems solved (verified in codebase):**

- `api_key_signature` includes `modelId`, so the same API key for DeepSeek vs Kimi dedupes incorrectly and shows the wrong linked display name (`ApiCredentialsIdentity.kt`, `ApiModelRepositoryImpl.findMatchingCredentials`).
- `ReusableApiCredentialUi` subtitles show `modelId` from credentials (`ByokConfigureForms.kt` `ReusableCredentialDropdown`).
- `ActiveModelProviderImpl` and `InferenceFactoryImpl` read `modelId` / `isMultimodal` from `ApiCredentials`, not the active preset (`ActiveModelProviderImpl.kt` lines 88–95, `InferenceFactoryImpl.kt` `createApiIdentity` / service constructors).
- Discover Models and Vision toggle live on the credential form (`CredentialsConfigurationForm` in `ByokConfigureForms.kt`), not on preset tuning.
- `ByokBottomSheet` asset list subtitle is `provider • modelId` from the asset (`ByokBottomSheet.kt` line 329).

**Target mental model:**

| Layer | Fields |
| --- | --- |
| **Asset** (`ApiCredentials`) | `displayName`, `provider`, `baseUrl`, `credentialAlias`, encrypted key |
| **Preset** (`ApiModelConfiguration`) | `modelId`, `isMultimodal`, preset `displayName`, tunings, headers, OpenRouter routing |

---

## File Structure Table

| File | Action | Responsibility |
| --- | --- | --- |
| `plans/BYOK-MODEL-ON-PRESET/spec.md` | Create | Implementation contract (this file). |
| `plans/BYOK-MODEL-ON-PRESET/test_spec.md` | Create | Gherkin scenarios and run commands. |
| `core/domain/.../model/config/ApiCredentials.kt` | Modify | Remove `modelId`, `isMultimodal` from domain credentials. |
| `core/domain/.../model/config/ApiModelConfiguration.kt` | Modify | Add `modelId: String`, `isMultimodal: Boolean`. |
| `core/domain/.../usecase/settings/SettingsWorkflowModels.kt` | Modify | `ApiProviderDraft` credential-only; `ApiPresetDraft` holds `modelId` / `isMultimodal`. |
| `core/domain/.../port/repository/ApiModelRepositoryPort.kt` | Modify | `findMatchingCredentials` drops `modelId` parameter. |
| `core/domain/.../usecase/settings/SaveApiProviderDraftUseCase.kt` | Modify | Match/save credentials without model; initial preset carries empty `modelId` until user configures. |
| `core/domain/.../usecase/settings/SaveApiPresetUseCase.kt` | Modify | Map `modelId` / `isMultimodal` from `ApiPresetDraft` into `ApiModelConfiguration`. |
| `core/domain/.../usecase/settings/TestApiProviderConnectionUseCase.kt` | Create | Verify provider connectivity via list-models fetch (no model required). |
| `core/domain/.../usecase/byok/SetDefaultModelUseCase.kt` | Modify | Vision slot checks `config.isMultimodal`, not credentials. |
| `core/domain/.../usecase/settings/PrepareModelDeletionUseCase.kt` | Modify | Vision reassignment filters per **preset** `isMultimodal`. |
| `core/domain/.../usecase/settings/AutoAssignLocalModelsUseCase.kt` | Modify | `firstMultimodalApi` selects first preset with `isMultimodal == true`. |
| `core/data/.../local/ApiCredentialsEntity.kt` | Modify | Drop `model_id`, `is_multimodal` columns. |
| `core/data/.../local/ApiModelConfigurationEntity.kt` | Modify | Add `model_id`, `is_multimodal` columns. |
| `core/data/.../local/ApiCredentialsIdentity.kt` | Modify | Signature input: `provider \| baseUrl \| apiKey` only. |
| `core/data/.../local/EnclaveDatabase.kt` | Modify | Version `7` → `8`. |
| `core/data/schemas/.../EnclaveDatabase/8.json` | Create | Exported Room schema (KSP after implementation). |
| `core/data/.../repository/ApiModelRepositoryImpl.kt` | Modify | Entity mapping, `saveCredentials`, `findMatchingCredentials`, `saveConfiguration` for new columns. |
| `core/data/.../remote/firestore/handlers/ApiCredentialsSyncHandler.kt` | Modify | Stop syncing `model_id` / `is_multimodal` on credentials docs. |
| `core/data/.../remote/firestore/handlers/ApiModelConfigSyncHandler.kt` | Modify | Sync `model_id` / `is_multimodal` on preset docs. |
| `core/data/.../repository/ActiveModelProviderImpl.kt` | Modify | API active config reads `modelId` / `isMultimodal` from **configuration** entity. |
| `feature/inference/.../InferenceFactoryImpl.kt` | Modify | Service construction and `ServiceIdentity.Api.modelId` from **configuration**, not credentials. |
| `feature/settings/.../SettingsModels.kt` | Modify | `ApiModelAssetUi` credential-only; `ApiModelConfigUi` adds `modelId` / `isMultimodal`; `ReusableApiCredentialUi` drops `modelId`; editor connection-test state. |
| `feature/settings/.../SettingsUiMappers.kt` | Modify | Map domain fields to/from new UI shapes. |
| `feature/settings/.../ApiProviderDiscoverySupport.kt` | Modify | Discovery/vision helpers keyed on **preset** `modelId`. |
| `feature/settings/.../ByokConfigureComponents.kt` | Modify | Host preset model + vision section (moved from credential form). |
| `feature/settings/.../ByokConfigureForms.kt` | Modify | Credential form: Test button only; `PresetConfigurationForm` embeds discovery; remove discovery from `CredentialsConfigurationForm`. |
| `feature/settings/.../ByokConfigureScreen.kt` | Modify | Save-enable rules: credentials vs preset; wire Test + discovery callbacks. |
| `feature/settings/.../ByokBottomSheet.kt` | Modify | Asset/preset-centric list subtitles (no asset `modelId`). |
| `feature/settings/.../ByokProvidersCoordinator.kt` | Modify | Save/test/discovery flows; connection-test reset on credential fingerprint change. |
| `feature/settings/.../SettingsUiStateFactory.kt` | Modify | Metadata defaults from `presetDraft.modelId`; map connection-test UI state. |
| `feature/settings/.../SettingsViewModel.kt` | Modify | Expose `onTestApiProviderConnection`. |
| `feature/settings/.../SettingsApiProvidersDelegate.kt` | Modify | Delegate test-connection to coordinator. |
| `feature/settings/.../ModelConfigurationScreen.kt` | Modify | Assignment subtitles use preset `modelId` where applicable. |
| `app/.../ByokUseCasesModule.kt` | Modify | Bind `TestApiProviderConnectionUseCase`. |
| `core/domain/.../usecase/settings/SettingsUseCases.kt` | Modify | Port `testApiProviderConnection` on settings API use-case bundle. |
| `core/domain/.../usecase/settings/SettingsUseCasesImpl.kt` | Modify | Wire new use case. |
| `core/domain/src/test/.../testing/Fakes.kt` | Modify | Fake repository signature and sample credentials/configs. |
| `core/domain/src/test/.../usecase/settings/SaveApiProviderDraftUseCaseTest.kt` | Modify | Dedupe without `modelId`; alias slug uses provider only. |
| `core/domain/src/test/.../usecase/settings/SaveApiPresetUseCaseTest.kt` | Modify | Persist `modelId` / `isMultimodal` on configuration. |
| `core/domain/src/test/.../usecase/settings/TestApiProviderConnectionUseCaseTest.kt` | Create | Success/failure paths. |
| `core/domain/src/test/.../usecase/byok/SetDefaultModelUseCaseTest.kt` | Modify | Vision guard uses config multimodal flag. |
| `core/domain/src/test/.../usecase/settings/PrepareModelDeletionUseCaseTest.kt` | Modify | Vision candidates per preset. |
| `core/domain/src/test/.../usecase/settings/AutoAssignLocalModelsUseCaseTest.kt` | Modify | Multimodal API pick uses preset flag. |
| `core/data/src/test/.../repository/ApiModelRepositoryImplTest.kt` | Modify | Signature, save, findMatching without model. |
| `core/data/src/test/.../local/ApiCredentialsDaoTest.kt` | Modify | Entity shape without model columns. |
| `core/data/src/test/.../local/ApiModelConfigurationsDaoTest.kt` | Modify | Round-trip `model_id` / `is_multimodal`. |
| `core/data/src/test/.../repository/ActiveModelProviderImplTest.kt` | Modify | Active API config from preset columns. |
| `core/data/src/test/.../remote/firestore/ApiCredentialsSyncHandlerTest.kt` | Create | Credentials Firestore payload excludes model fields. |
| `core/data/src/test/.../remote/firestore/ApiModelConfigSyncHandlerTest.kt` | Create | Preset Firestore payload includes model fields. |
| `feature/inference/src/test/.../InferenceFactoryImplTest.kt` | Modify | Fixtures: `modelId` on configuration, empty on credentials. |
| `feature/settings/src/test/.../ByokProvidersCoordinatorTest.kt` | Modify | Discovery + connection test state transitions. |
| `feature/settings/src/test/.../SettingsViewModelTest.kt` | Modify | Credential save without model; preset save with model. |
| `feature/settings/src/test/.../SettingsUiStateFactoryTest.kt` | Modify | Editor state mapping. |

**Out of scope (unchanged product surfaces):**

- TTS / Media provider drafts retain their own `modelId` on **media/TTS assets** (separate tables).
- `ApiModelCatalogPort.fetchModels` implementation (reuse as-is for Test + Discover).

---

## API Contracts

### Domain — `ApiCredentials`

```kotlin
data class ApiCredentials(
    val id: ApiCredentialsId,
    val displayName: String,
    val provider: ApiProvider,
    val baseUrl: String? = null,
    val credentialAlias: String,
    val encryptedApiKey: ByteArray? = null,
)
```

### Domain — `ApiModelConfiguration`

```kotlin
data class ApiModelConfiguration(
    val id: ApiModelConfigurationId = ApiModelConfigurationId(""),
    val apiCredentialsId: ApiCredentialsId,
    val modelId: String,
    val isMultimodal: Boolean = false,
    // ... existing tuning fields unchanged ...
)
```

### Drafts — `SettingsWorkflowModels.kt`

```kotlin
data class ApiProviderDraft(
    val id: ApiCredentialsId = ApiCredentialsId(""),
    val displayName: String,
    val provider: ApiProvider,
    val baseUrl: String?,
    val credentialAlias: String,
    val apiKey: String,
    val sourceCredentialAlias: String? = null,
)

data class ApiPresetDraft(
    // ... existing fields ...
    val modelId: String = "",
    val isMultimodal: Boolean = false,
)
```

### Repository — `findMatchingCredentials`

```kotlin
suspend fun findMatchingCredentials(
    provider: ApiProvider,
    baseUrl: String?,
    apiKey: String,
    sourceCredentialAlias: String? = null,
): ApiCredentials?
```

### Identity signature — `buildApiCredentialsIdentitySignature`

```kotlin
internal fun buildApiCredentialsIdentitySignature(
    provider: String,
    baseUrl: String?,
    apiKey: String,
): String
// SHA-256 hex of: "${provider.trim()}|${normalizedBaseUrl}|${apiKey}"
```

### Use case — `TestApiProviderConnectionUseCase`

```kotlin
class TestApiProviderConnectionUseCase @Inject constructor(
    private val fetchApiProviderModelsUseCase: FetchApiProviderModelsUseCase,
) {
    /**
     * Proves the provider accepts the API key by invoking the same list-models
     * catalog path as Discover Models. Does not require a selected model ID.
     * @return Result.success(Unit) when fetch completes without error (empty list allowed).
     */
    suspend operator fun invoke(
        provider: ApiProvider,
        currentApiKey: String,
        credentialAlias: String?,
        baseUrl: String?,
    ): Result<Unit>
}
```

### Presentation — connection test state

```kotlin
enum class ApiConnectionTestStatus { IDLE, TESTING, SUCCESS }

// In ApiProviderEditorUiState:
val connectionTestStatus: ApiConnectionTestStatus = IDLE
```

**Fingerprint** for reset (internal, settings layer): `"{provider}|{normalizedBaseUrl}|{apiKeyOrAlias}"` — when any component changes, set `connectionTestStatus = IDLE`.

### Presentation — `ApiModelConfigUi`

```kotlin
data class ApiModelConfigUi(
    // ... existing ...
    val modelId: String = "",
    val isMultimodal: Boolean = false,
)
```

### Presentation — `ReusableApiCredentialUi`

```kotlin
data class ReusableApiCredentialUi(
    val credentialsId: ApiCredentialsId,
    val displayName: String,
    val providerLabel: String,  // e.g. "OpenAI" — replaces modelId subtitle
    val credentialAlias: String,
)
```

---

## Schema (Room v8)

**`api_credentials`:** remove columns `model_id`, `is_multimodal`.

**`api_model_configurations`:** add columns:

| Column | Type | Default |
| --- | --- | --- |
| `model_id` | `TEXT NOT NULL` | `""` |
| `is_multimodal` | `INTEGER NOT NULL` | `0` |

**Migration policy:** Do **not** add `MIGRATION_7_8`. Bump `EnclaveDatabase` to version `8` only; existing `DataModule` `fallbackToDestructiveMigration(dropAllTables = true)` recreates tables on upgrade.

---

## Firestore Sync

| Collection | Change |
| --- | --- |
| `api_credentials` | Remove `model_id`, `is_multimodal` from `toFirestore` / `fromFirestore`. |
| `api_model_configs` | Add `model_id` (string), `is_multimodal` (boolean). |

No dual-read of legacy credential model fields.

---

## UI Behavior

### Credential screen (`Provider Credentials`)

Fields only: provider, base URL, API key, display name.

- **Test** `TextButton` directly under API key row.
  - Enabled when `apiKey.isNotBlank()` OR reusable credential selected OR editing saved asset (`credentialsId` non-empty).
  - While running: show small `CircularProgressIndicator` + label `"Testing…"`.
  - On success: label `"Success"` + `Icons.Default.CheckCircle` (same pattern as saved-key indicator).
  - On failure: snackbar via existing `snackbarMessages` channel with catalog error message.
  - Reset to `IDLE` when provider, base URL, API key text, or selected reusable credential changes.

**Save Credentials** enabled when `displayName.isNotBlank()` AND (`apiKey` OR reusable credential) — **no** `modelId` requirement.

### Preset screen (`Configure Preset`)

`PresetConfigurationForm` (`ByokConfigureForms.kt`) order:

1. Preset name
2. **`PresetModelAndVisionSection`** (new composable in `ByokConfigureComponents.kt`): `ModelSelectionField` + `VisionCapabilitySwitch` moved from credential form; wired to `config.modelId` / `config.isMultimodal`
3. System prompt, OpenRouter routing, context, tuning, headers (unchanged)

Discover Models / Refresh uses parent asset’s provider + base URL + key (same as today) but updates **`presetDraft`**, not `assetDraft`. `onFetchApiModels` only meaningful in preset mode.

After **Save Credentials** on new asset: still auto-create `"Default Preset"` with `modelId = ""`, `isMultimodal = false`, and keep user on configure flow when `createdPreset != null` (existing navigation).

### BYOK bottom sheet

| View | Subtitle rule |
| --- | --- |
| Asset list | `"{provider.displayName}"` only (drop `• modelId`); keep preset count line |
| Config list per asset | Each preset row shows `"{preset.displayName} • {preset.modelId}"` (or model-only secondary if name duplicates) |
| Asset detail header | Remove asset-level vision icon unless **any** preset is multimodal (optional aggregate) |

---

## Inference / Active Model

- `ActiveModelProviderImpl`: for API branch, `modelId = config.modelId`, `isMultimodal = config.isMultimodal`.
- `InferenceFactoryImpl`: all `modelId = apiCreds.modelId` → `modelId = apiConfig.modelId`; `createApiIdentity` uses `apiConfig.modelId`.
- `ServiceIdentity.Api.modelId` remains; source is configuration.

---

## Implementation Tasks

### T01 — Domain models and drafts

**Scenarios:** SCN-DOMAIN-001, SCN-DOMAIN-002, SCN-DRAFT-001

- Apply `ApiCredentials` / `ApiModelConfiguration` field moves.
- Update `ApiProviderDraft`, `ApiPresetDraft`, `ApiModelConfigUi` shapes per contracts.
- Fix compile errors in domain module from removed credential fields.

### T02 — Repository port and identity signature

**Scenarios:** SCN-SIGNATURE-001, SCN-SIGNATURE-002, SCN-PORT-001

- Change `ApiModelRepositoryPort.findMatchingCredentials` signature.
- Update `ApiCredentialsIdentity.kt` to omit `modelId` from hash input.
- Update `Fakes.kt` and domain test doubles.

### T03 — Room entities and database version 8

**Scenarios:** SCN-SCHEMA-001, SCN-SCHEMA-002, SCN-DAO-001, SCN-DAO-002

- Edit `ApiCredentialsEntity`, `ApiModelConfigurationEntity`.
- Bump `EnclaveDatabase` to version `8`; export `8.json` via KSP.
- Confirm destructive fallback recreates schema (no `MIGRATION_7_8`).

### T04 — ApiModelRepositoryImpl

**Scenarios:** SCN-REPO-001, SCN-REPO-002, SCN-REPO-003, SCN-REPO-004

- `saveCredentials` / `toDomain` without model fields on credentials.
- `saveConfiguration` / config `toDomain` with `modelId` / `isMultimodal`.
- `findMatchingCredentials` uses new signature only.
- `apiKeySignature` built without `modelId`.

### T05 — Firestore sync handlers

**Scenarios:** SCN-SYNC-001, SCN-SYNC-002, SCN-SYNC-003, SCN-SYNC-004

- `ApiCredentialsSyncHandler`: drop model fields.
- `ApiModelConfigSyncHandler`: add model fields.
- Add handler unit tests.

### T06 — SaveApiProviderDraftUseCase

**Scenarios:** SCN-USECASE-SAVE-ASSET-001, SCN-USECASE-SAVE-ASSET-002, SCN-USECASE-SAVE-ASSET-003

- Dedupe via `findMatchingCredentials(provider, baseUrl, apiKey)` only.
- `generateUniqueAlias(provider, existingAliases)` — slug from **provider** only (no model segment).
- Initial preset: `modelId = ""`, `isMultimodal = false`, `defaultReasoningEffort = null` until preset saved with model.

### T07 — SaveApiPresetUseCase

**Scenarios:** SCN-USECASE-SAVE-PRESET-001, SCN-USECASE-SAVE-PRESET-002

- Persist `draft.modelId` and `draft.isMultimodal` on `ApiModelConfiguration`.

### T08 — TestApiProviderConnectionUseCase

**Scenarios:** SCN-USECASE-TEST-001, SCN-USECASE-TEST-002, SCN-USECASE-TEST-003

- Implement use case delegating to `FetchApiProviderModelsUseCase`.
- Register in `SettingsUseCases`, `SettingsUseCasesImpl`, `ByokUseCasesModule`.
- Unit test success and failure propagation.

### T09 — Domain guards (vision / deletion / auto-assign)

**Scenarios:** SCN-USECASE-VISION-001, SCN-USECASE-DELETION-001, SCN-USECASE-AUTO-001

- `SetDefaultModelUseCase`: `require(config.isMultimodal)` (load config by id).
- `PrepareModelDeletionUseCase`: skip non-multimodal **configs** when `requireVisionCompatibility`.
- `AutoAssignLocalModelsUseCase.firstMultimodalApi`: any preset with `isMultimodal`.

### T10 — ActiveModelProviderImpl

**Scenarios:** SCN-ACTIVE-001, SCN-ACTIVE-002

- Read API `modelId` / `isMultimodal` from configuration entity.
- Update `ActiveModelProviderImplTest` fixtures.

### T11 — InferenceFactoryImpl

**Scenarios:** SCN-INFERENCE-001, SCN-INFERENCE-002

- Pass `apiConfig.modelId` into all provider service constructors and `createApiIdentity`.
- Update `InferenceFactoryImplTest` helpers: empty credential `modelId`, populated config `modelId`.

### T12 — Settings UI models and mappers

**Scenarios:** SCN-UI-MAPPER-001, SCN-UI-MAPPER-002

- `ApiModelAssetUi` without `modelId` / `isMultimodal`.
- `ApiModelConfigUi` with `modelId` / `isMultimodal`.
- `ReusableApiCredentialUi` with `providerLabel` only.
- Update `ApiModelAssetUiMapper`, mocks in `SettingsModels.kt`.

### T13 — PresetModelAndVisionSection (ByokConfigureComponents)

**Scenarios:** SCN-UI-DISCOVER-001, SCN-UI-DISCOVER-002, SCN-UI-VISION-001

- Move/adapt `ModelSelectionField` and `VisionCapabilitySwitch` to operate on `ApiModelConfigUi`.
- Export composable used by `PresetConfigurationForm`.

### T14 — Credential form and Test button (ByokConfigureForms)

**Scenarios:** SCN-UI-TEST-001, SCN-UI-TEST-002, SCN-UI-TEST-003, SCN-UI-CRED-001

- Strip discovery/vision/model fields from `CredentialsConfigurationForm`.
- Add Test button row under API key with success UI spec.
- Update `ReusableCredentialDropdown` subtitle to `providerLabel`.

### T15 — ByokConfigureScreen save rules

**Scenarios:** SCN-UI-SAVE-001, SCN-UI-SAVE-002

- Credential save: no `modelId` in `isSaveEnabled`.
- Preset save: require `presetDraft.modelId.isNotBlank()` plus existing preset validations.

### T16 — ByokProvidersCoordinator and discovery support

**Scenarios:** SCN-UI-COORD-001, SCN-UI-COORD-002, SCN-UI-COORD-003

- `onTestApiProviderConnection` with loading/success/error + fingerprint reset.
- `onFetchApiModels` / XAI detail / `normalizeVisionCapability` / `discoveredModelFor` use `presetDraft`.
- `onSaveApiCredentials` builds credential-only `ApiProviderDraft`.
- `onSaveApiModelConfig` passes `presetDraft.modelId` into `SaveApiPresetUseCase` / metadata defaults.

### T17 — SettingsUiStateFactory and ViewModel wiring

**Scenarios:** SCN-UI-STATE-001, SCN-UI-STATE-002

- Map `connectionTestStatus` into `ApiProviderEditorUiState`.
- `applyApiModelMetadataDefaults` uses `presetDraft.modelId` (not asset).
- `SettingsViewModel` / `SettingsApiProvidersDelegate` expose test handler.

### T18 — ByokBottomSheet and ModelConfigurationScreen labels

**Scenarios:** SCN-UI-SHEET-001, SCN-UI-SHEET-002, SCN-UI-ASSIGN-001

- Asset list subtitle provider-only.
- Config list shows per-preset model id.
- Model assignment picker uses preset model where applicable.

### T19 — Domain unit tests

**Scenarios:** SCN-USECASE-SAVE-ASSET-001 through SCN-USECASE-TEST-003, SCN-USECASE-VISION-001, SCN-USECASE-DELETION-001, SCN-USECASE-AUTO-001

- Update/create tests listed in file table for `:core:domain`.

### T20 — Data unit tests

**Scenarios:** SCN-SIGNATURE-001, SCN-REPO-001 through SCN-REPO-004, SCN-SYNC-001 through SCN-SYNC-004, SCN-ACTIVE-001, SCN-DAO-001, SCN-DAO-002

- Update/create tests listed in file table for `:core:data`.

### T21 — Inference unit tests

**Scenarios:** SCN-INFERENCE-001, SCN-INFERENCE-002

- `InferenceFactoryImplTest` identity/service uses config `modelId`.

### T22 — Settings feature unit tests

**Scenarios:** SCN-UI-TEST-001 through SCN-UI-COORD-003, SCN-UI-SAVE-001, SCN-UI-SAVE-002

- Coordinator and ViewModel tests for test connection reset, discovery on preset, credential save without model.

---

## Validation Commands

**Iteration:**

```bash
./gradlew :core:domain:testDebugUnitTest --tests "*SaveApiProviderDraftUseCaseTest*"
./gradlew :core:domain:testDebugUnitTest --tests "*TestApiProviderConnectionUseCaseTest*"
./gradlew :core:data:testDebugUnitTest --tests "*ApiModelRepositoryImplTest*"
./gradlew :core:data:testDebugUnitTest --tests "*ActiveModelProviderImplTest*"
./gradlew :feature:inference:testDebugUnitTest --tests "*InferenceFactoryImplTest*"
./gradlew :feature:settings:testDebugUnitTest --tests "*ByokProvidersCoordinatorTest*"
```

**Before TASK_STATUS COMPLETE:**

```bash
./gradlew :feature:settings:testDebugUnitTest :core:domain:testDebugUnitTest :core:data:testDebugUnitTest
./gradlew :feature:inference:testDebugUnitTest
./gradlew :core:data:assembleDebug :feature:inference:assembleDebug :feature:settings:assembleDebug
./gradlew :app:kspDebugKotlin
./gradlew ktlintCheck
./gradlew detekt
```

---

## Self-Review Checklist

- [x] Every changed layer has mapped files (domain, data, inference, settings).
- [x] Test Connection use case reuses `FetchApiProviderModelsUseCase` (no new HTTP client).
- [x] Discover Models placement: `ByokConfigureComponents` + `PresetConfigurationForm`.
- [x] No Room migration; version bump + destructive fallback documented.
- [x] All tasks reference scenario IDs from `test_spec.md`.
- [x] No TBD/TODO placeholders.
