# FEAT-local-model-asset-display-name Test Spec

**Ticket ID:** `FEAT-local-model-asset-display-name`

## Test Framework And Run Commands

**Frameworks:**

- JUnit 5 (`@Test`, `@BeforeEach`, `@ExtendWith`)
- MockK for port/DAO fakes
- Robolectric + `ApplicationProvider` for Room DAO/repository tests (`@ExtendWith(RobolectricExtension::class)`, `@Config(sdk = [34])`)
- Turbine where `observeDefaults()` Flow is asserted

**Focused commands during iteration:**

```bash
./gradlew :core:data:testDebugUnitTest --tests "*ModelConfigFetcherImplTest*"
./gradlew :core:data:testDebugUnitTest --tests "*LocalModelsDaoTest*"
./gradlew :core:data:testDebugUnitTest --tests "*DefaultModelsDaoTest*"
./gradlew :core:data:testDebugUnitTest --tests "*DefaultModelRepositoryImplTest*"
./gradlew :core:data:testDebugUnitTest --tests "*ActiveModelProviderImplTest*"
./gradlew :core:data:testDebugUnitTest --tests "*LocalModelRepositoryImplTest*"
./gradlew :core:data:testDebugUnitTest --tests "*LocalModelSyncHandlerTest*"
./gradlew :core:data:testDebugUnitTest --tests "*DownloadFinalizeWorkerTest*"
./gradlew :core:data:testDebugUnitTest --tests "*EnclaveDatabaseMigrationTest*"
./gradlew :core:domain:testDebugUnitTest --tests "*GetModelDisplayNameUseCaseTest*"
./gradlew :core:domain:testDebugUnitTest --tests "*PrepareModelDeletionUseCaseTest*"
./gradlew :feature:settings:testDebugUnitTest --tests "*SettingsUiStateFactoryTest*"
```

**Required validation before TASK_STATUS COMPLETE:**

```bash
./gradlew :core:data:testDebugUnitTest
./gradlew :core:domain:testDebugUnitTest
./gradlew :feature:settings:testDebugUnitTest
./gradlew :core:data:assembleDebug
./gradlew :app:kspDebugKotlin
./gradlew :core:data:ktlintCheck
./gradlew detekt
```

---

## Scenario Index

| Scenario ID | Area | Summary |
| --- | --- | --- |
| SCN-JSON-001 | Catalog | Shipped `model_config.json` LLM assets declare asset `displayName` and shortened preset labels. |
| SCN-DOMAIN-001 | Domain | `LocalModelMetadata` carries `displayName` distinct from `huggingFaceModelName`. |
| SCN-FETCH-001 | Fetcher | `toLocalModelAssets` copies asset `displayName` into metadata. |
| SCN-FETCH-002 | Fetcher | Non-utility asset without `displayName` fails validation. |
| SCN-FETCH-003 | Fetcher | Utility asset without `displayName` still maps successfully. |
| SCN-SCHEMA-001 | Schema | Fresh version-8 DB creates `local_models.display_name`. |
| SCN-MIGRATION-001 | Migration | `MIGRATION_7_8` backfills `display_name` from `huggingface_model_name`. |
| SCN-DAO-001 | DAO | `LocalModelsDao` round-trips `display_name`. |
| SCN-DAO-002 | DAO | `observeAllWithDetails` exposes `lm.display_name` as `localAssetName`. |
| SCN-REPO-001 | Repository | `LocalModelRepositoryImpl` upsert/read preserves `displayName`. |
| SCN-REPO-DEFAULT-001 | Repository | `getDefault` local assignment `displayName` is asset label. |
| SCN-REPO-DEFAULT-002 | Repository | `observeDefaults` local row uses asset label, preset unchanged. |
| SCN-ACTIVE-001 | Provider | `ActiveModelProviderImpl` local `name` is asset `displayName`. |
| SCN-USECASE-001 | Domain | `GetModelDisplayNameUseCase` returns asset `displayName`. |
| SCN-SYNC-001 | Firestore | `toFirestore` includes `display_name`. |
| SCN-SYNC-002 | Firestore | `fromFirestore` restores `display_name`. |
| SCN-SYNC-003 | Firestore | Remote newer `updated_at` overwrites local `display_name`. |
| SCN-UI-001 | Settings | `LocalModelAssetUiMapper` sets `friendlyName` from metadata `displayName`. |
| SCN-UI-002 | Settings | `SettingsUiStateFactory` surfaces `friendlyName` `"Gemma 4 E4B"`. |
| SCN-DELETION-001 | Domain | `PrepareModelDeletionUseCase` reassignment uses asset `displayName`. |
| SCN-MAPPER-001 | Domain | `ModelConfigMapper` uses asset `displayName` for `ModelFile`. |
| SCN-FINALIZE-001 | Worker | `DownloadFinalizeWorker` registry path stores asset `displayName`. |
| SCN-MUT-001 | Mutation defense | Fetcher that omits metadata `displayName` fails SCN-FETCH-001. |
| SCN-MUT-002 | Mutation defense | `resolveAssignment` using `huggingFaceModelName` fails SCN-REPO-DEFAULT-001. |
| SCN-MUT-003 | Mutation defense | `ActiveModelProviderImpl` using config preset name fails SCN-ACTIVE-001. |

---

## Happy Path Scenarios

### SCN-JSON-001: Shipped Catalog Declares Asset And Preset Labels

Given the repository file `model_config.json` at the project root
When the file is parsed as JSON
Then the asset with `"sha256": "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0"` has `"displayName": "Gemma 4 E4B"`
And that asset's configuration `"configId": "550e8400-e29b-41d4-a716-446655440006"` has `"displayName": "Fast"`
And that asset's configuration `"configId": "550e8400-e29b-41d4-a716-446655440007"` has `"displayName": "Thinking"`
And that asset's configuration `"configId": "550e8400-e29b-41d4-a716-446655440005"` has `"displayName": "Final Synthesis"`
And the asset with `"sha256": "1cfadbb76a841c6a448870dda3f2f46209ae6d552a848ee4ab3205de76b5adfb"` has `"displayName": "MiniCPM-V-4.6 1.3B"`
And its configuration `"configId": "550e8400-e29b-41d4-a716-446655440002"` has `"displayName": "Analytic Drafter"`

### SCN-DOMAIN-001: LocalModelMetadata Holds Asset Display Name

Given `LocalModelMetadata` built with `displayName = "Qwen 3.5 2B"` and `huggingFaceModelName = "Jackrong/Qwen3.5-2B-Claude-4.6-Opus-Reasoning-Distilled-GGUF"`
When the metadata instance is read
Then `displayName` equals `"Qwen 3.5 2B"`
And `huggingFaceModelName` equals `"Jackrong/Qwen3.5-2B-Claude-4.6-Opus-Reasoning-Distilled-GGUF"`

### SCN-FETCH-001: Fetcher Maps Asset DisplayName Into Metadata

Given `RemoteModelAsset` with `displayName = "Gemma 4 E4B"`, `huggingFaceModelName = "litert-community/gemma-4-E4B-it-litert-lm"`, `fileName = "gemma-4-E4B-it.litertlm"`, `sha256 = "abc123"`, and one configuration with `displayName = "Fast"`
When `ModelConfigFetcherImpl.toLocalModelAssets(listOf(asset))` runs
Then `modelAssets[0].metadata.displayName` equals `"Gemma 4 E4B"`
And `modelAssets[0].configurations[0].displayName` equals `"Fast"`

### SCN-FETCH-003: Utility Asset Maps Without Asset DisplayName

Given `RemoteModelAsset` with `utilityType = UtilityType.WHISPER`, `displayName = ""`, `configurations = emptyList()`, `fileName = "ggml-base.en.bin"`, `sha256 = "whisper-sha"`
When `ModelConfigFetcherImpl.toLocalModelAssets(listOf(asset))` runs
Then the result list size equals `1`
And `modelAssets[0].metadata.displayName` equals `""`

### SCN-SCHEMA-001: Version 8 Database Creates display_name Column

Given a fresh in-memory `EnclaveDatabase` at version `8`
When `localModelsDao` inserts `LocalModelEntity` with `displayName = "Gemma 4 E4B"` and `huggingFaceModelName = "litert-community/gemma-4-E4B-it-litert-lm"`
And the row is read by primary key
Then `retrieved.displayName` equals `"Gemma 4 E4B"`

### SCN-MIGRATION-001: Migration 7 To 8 Backfills From Hugging Face Name

Given an on-disk SQLite database at version `7` with one `local_models` row where `huggingface_model_name = "litert-community/gemma-4-E4B-it-litert-lm"` and no `display_name` column
When `EnclaveDatabaseMigrations.MIGRATION_7_8.migrate(db)` runs
And the row is queried for `display_name`
Then the column value equals `"litert-community/gemma-4-E4B-it-litert-lm"`

### SCN-DAO-001: LocalModelsDao Round-Trips display_name

Given Robolectric Room database version `8`
And `LocalModelEntity` with `id = LocalModelId("model-asset-1")`, `displayName = "Jamba Reasoning 3B"`, `huggingFaceModelName = "bartowski/ai21labs_AI21-Jamba-Reasoning-3B-GGUF"`, `sha256 = "jamba-sha"`, `sizeInBytes = 100L`
When `localModelsDao.upsert(entity)` and `getById(LocalModelId("model-asset-1"))` run
Then `retrieved.displayName` equals `"Jamba Reasoning 3B"`

### SCN-DAO-002: observeAllWithDetails Returns Asset Display Name

Given Robolectric Room database version `8`
And `local_models` row with `display_name = "Gemma 4 E4B"` joined to a default on `local_model_configurations.display_name = "Fast"`
When `defaultModelsDao.observeAllWithDetails().first()` emits
Then the `ModelType.FAST` view `localAssetName` equals `"Gemma 4 E4B"`
And the same view `localPresetName` decrypts to `"Fast"`

### SCN-REPO-001: LocalModelRepositoryImpl Preserves displayName

Given empty version-8 database
And `LocalModelAsset` metadata with `displayName = "MiniCPM-V-4.6 1.3B"`, `sha256 = "minicpm-sha"`, `huggingFaceModelName = "openbmb/MiniCPM-V-4.6-Thinking-gguf"`
When `LocalModelRepositoryImpl.upsertLocalAsset(asset)` runs
And `getAssetById` loads the stored id
Then `loaded.metadata.displayName` equals `"MiniCPM-V-4.6 1.3B"`

### SCN-REPO-DEFAULT-001: getDefault Uses Asset DisplayName For Local Assignment

Given `DefaultModelEntity` for `ModelType.MAIN` with `localConfigId = LocalModelConfigurationId("cfg-main")`
And `LocalModelConfigurationEntity` with `displayName` encrypted as `"Synthesis"` and `localModelId = LocalModelId("model-1")`
And `LocalModelEntity` with `displayName = "Jamba Reasoning 3B"`, `huggingFaceModelName = "bartowski/ai21labs_AI21-Jamba-Reasoning-3B-GGUF"`
When `DefaultModelRepositoryImpl.getDefault(ModelType.MAIN)` runs
Then `result.displayName` equals `"Jamba Reasoning 3B"`
And `result.presetName` equals `"Synthesis"`
And `result.providerName` equals `"Local"`

### SCN-REPO-DEFAULT-002: observeDefaults Uses Asset DisplayName From SQL View

Given `DefaultModelAssignmentView` with `modelType = ModelType.THINKING`, `localAssetName = "Gemma 4 E4B"`, `localPresetName` encrypted `"Thinking"`
When `DefaultModelRepositoryImpl.observeDefaults()` is collected once
Then the `ModelType.THINKING` assignment `displayName` equals `"Gemma 4 E4B"`
And `presetName` equals `"Thinking"`

### SCN-ACTIVE-001: ActiveModelProvider Uses Asset DisplayName For Local Config

Given `DefaultModelAssignment` for `ModelType.FAST` with `localConfigId = LocalModelConfigurationId("cfg-fast")`
And `LocalModelConfigurationEntity` with encrypted `displayName = "Fast"`
And `LocalModelEntity` with `displayName = "Gemma 4 E4B"`, `localFilename = "gemma-4-E4B-it.litertlm"`
When `ActiveModelProviderImpl.getActiveConfiguration(ModelType.FAST)` runs
Then `result.name` equals `"Gemma 4 E4B"`
And `result.modelId` equals `"gemma-4-E4B-it.litertlm"`

### SCN-USECASE-001: GetModelDisplayNameUseCase Returns Asset Label

Given `ActiveModelProviderPort` returns `ActiveModelConfiguration` with `name = "Gemma 4 E4B"` for `ModelType.FAST`
When `GetModelDisplayNameUseCase.invoke(ModelType.FAST)` runs
Then the result equals `"Gemma 4 E4B"`

### SCN-SYNC-001: toFirestore Writes display_name

Given `LocalModelEntity` with `displayName = "Qwen 3.5 2B"`, `huggingFaceModelName = "Jackrong/Qwen3.5-2B-Claude-4.6-Opus-Reasoning-Distilled-GGUF"`, `sha256 = "qwen-sha"`, `updatedAt = 1700000000000`, `deleted = false`
When `LocalModelSyncHandler.toFirestore(entity)` runs
Then the map entry `"display_name"` equals `"Qwen 3.5 2B"`

### SCN-SYNC-002: fromFirestore Reads display_name

Given Firestore document fields `display_name = "Gemma 4 E4B"`, `huggingface_model_name = "litert-community/gemma-4-E4B-it-litert-lm"`, `sha256 = "gemma-sha"`, `model_file_format = "LITERTLM"`, `remote_filename = "gemma-4-E4B-it.litertlm"`, `local_filename = "gemma-4-E4B-it.litertlm"`, `size_in_bytes = 3659530240`, `updated_at = 1700000000000`, `deleted = false`
When `LocalModelSyncHandler.fromFirestore(doc)` parses
Then `entity.displayName` equals `"Gemma 4 E4B"`

### SCN-SYNC-003: Remote Newer updated_at Overwrites display_name

Given local row `displayName = "Old Label"`, `updatedAt = 1000`, `pendingSync = 0`
And remote document `display_name = "Gemma 4 E4B"`, `updated_at = 2000`
When `LocalModelSyncHandler.upsertLocal(entity, doc, overwrite = false)` runs
Then DAO row `displayName` equals `"Gemma 4 E4B"`

### SCN-UI-001: LocalModelAssetUiMapper Uses Metadata displayName

Given `LocalModelAsset` with `metadata.displayName = "Gemma 4 E4B"` and `metadata.huggingFaceModelName = "litert-community/gemma-4-E4B-it-litert-lm"`
When `LocalModelAssetUiMapper.map(asset)` runs
Then `ui.friendlyName` equals `"Gemma 4 E4B"`

### SCN-UI-002: SettingsUiStateFactory Exposes Friendly Asset Name

Given `SettingsUiStateFactory` with real `LocalModelAssetUiMapper`
And `localAsset` metadata `displayName = "Gemma 4 E4B"`, `huggingFaceModelName = "litert-community/gemma-4-E4B-it-litert-lm"`, with at least one configuration
When `factory.create(localAssets = listOf(localAsset), defaults = defaultFactoryArgs())` runs
Then `state.localModelsSheet.models[0].friendlyName` equals `"Gemma 4 E4B"`

### SCN-DELETION-001: PrepareModelDeletion Reassignment Uses Asset DisplayName

Given local assets list containing asset `metadata.displayName = "Qwen 3.5 2B"`, `metadata.id = LocalModelId("other-id")`, with configuration `LocalModelConfigurationId("other-config-id")`
And deleting `LocalModelId("test-id")` requires reassignment for `ModelType.MAIN`
When `PrepareModelDeletionUseCase(ModelDeletionTarget.LocalModelAsset(LocalModelId("test-id")))` returns `PreparedModelDeletion.Ready`
Then `reassignmentCandidates[0].assetDisplayName` equals `"Qwen 3.5 2B"`

### SCN-MAPPER-001: ModelConfigMapper Uses Asset DisplayName

Given `RemoteModelAsset` with `displayName = "Gemma 4 E4B"`, `fileName = "gemma-4-E4B-it.litertlm"`, and configuration `displayName = "Fast"`
When `ModelConfigMapper.toModelFiles(listOf(asset))` runs
Then `files[0].displayName` equals `"Gemma 4 E4B"`

### SCN-FINALIZE-001: DownloadFinalize Persists Asset DisplayName From Catalog

Given `ModelConfigFetcherPort.fetchRemoteConfig()` returns asset with `metadata.displayName = "Gemma 4 E4B"`, `sha256 = "gemma-sha"`, and downloaded SHA list includes `"gemma-sha"`
And `SyncLocalModelRegistryUseCase` captures upserted `LocalModelAsset`
When `DownloadFinalizeWorker` finalize path runs for that SHA
Then captured `LocalModelAsset.metadata.displayName` equals `"Gemma 4 E4B"`

---

## Error And Edge Case Scenarios

### SCN-FETCH-002: Non-Utility Asset Without displayName Fails Validation

Given `RemoteModelAsset` with `utilityType = null`, `displayName = ""`, `fileName = "model.gguf"`, and one non-empty configuration
When `ModelConfigFetcherImpl.toLocalModelAssets(listOf(asset))` runs
Then the call throws `IllegalArgumentException`
And `exception.message` equals `"Non-utility asset model.gguf must declare displayName."`

---

## Mutation-Defense Scenarios

### SCN-MUT-001: Lazy Fetcher Leaving Metadata displayName Empty

**Risk:** `toLocalModelAssets` maps configurations but leaves `LocalModelMetadata.displayName` default empty while tests only assert preset names.

**Scenario:** Reuses SCN-FETCH-001 Given/When/Then.

**Failure condition:** If metadata `displayName` is `""`, SCN-FETCH-001 Then clause `equals "Gemma 4 E4B"` fails.

### SCN-MUT-002: Default Repository Still Using huggingFaceModelName

**Risk:** `DefaultModelRepositoryImpl` line 89 left as `model?.huggingFaceModelName`.

**Scenario:** Reuses SCN-REPO-DEFAULT-001 with `LocalModelEntity.huggingFaceModelName = "bartowski/ai21labs_AI21-Jamba-Reasoning-3B-GGUF"` and `displayName = "Jamba Reasoning 3B"`.

**Failure condition:** If `result.displayName` equals `"bartowski/ai21labs_AI21-Jamba-Reasoning-3B-GGUF"`, SCN-REPO-DEFAULT-001 fails.

### SCN-MUT-003: Active Provider Still Using Config Preset Name

**Risk:** `ActiveModelProviderImpl` still decrypts `config.displayName` for `name`.

**Scenario:** Reuses SCN-ACTIVE-001.

**Failure condition:** If `result.name` equals `"Fast"` instead of `"Gemma 4 E4B"`, SCN-ACTIVE-001 fails.

---

## Task To Scenario Mapping

| Task (spec.md) | Scenario IDs |
| --- | --- |
| Task 1 — Catalog and domain types | SCN-JSON-001, SCN-DOMAIN-001, SCN-FETCH-001, SCN-FETCH-002, SCN-MUT-001 |
| Task 2 — Room schema v8 | SCN-SCHEMA-001, SCN-MIGRATION-001, SCN-DAO-001 |
| Task 3 — Fetcher and repository persistence | SCN-FETCH-001, SCN-FETCH-002, SCN-FETCH-003, SCN-REPO-001, SCN-FINALIZE-001, SCN-MUT-001 |
| Task 4 — Default model assignment resolution | SCN-DAO-002, SCN-REPO-DEFAULT-001, SCN-REPO-DEFAULT-002, SCN-MUT-002 |
| Task 5 — Active model display name (chat loading) | SCN-ACTIVE-001, SCN-USECASE-001, SCN-MUT-003 |
| Task 6 — Firestore sync | SCN-SYNC-001, SCN-SYNC-002, SCN-SYNC-003 |
| Task 7 — Settings and deletion UI mappers | SCN-UI-001, SCN-UI-002, SCN-DELETION-001, SCN-MAPPER-001 |
| Task 8 — Test fixtures and fakes | All scenario IDs |
| Task 9 — Validation gate | (command execution) |

---

## Implementation Notes For TDD Red Phase

- Add **new** test methods; do not weaken existing assertions unrelated to display names.
- `ActiveModelProviderImplTest` test `` `getActiveConfiguration decrypts local config fields` `` must rename expected `name` from `"Balanced"` to a literal asset `displayName` such as `"Gemma 4 E4B"` once entity fixture includes `displayName`.
- `ModelConfigFetcherImplTest` `` `toLocalModelAssets_handlesMultipleConfigsPerAsset` `` must assert `metadata.displayName == "Gemma 4 E4B"` and configuration names `"Fast"` / `"Thinking"` (not combined strings).
- `DefaultModelRepositoryImplTest` `` `get default with resolved display data for local model` `` must expect `displayName == "Jamba Reasoning 3B"` (not `"qwen"`).
- Robolectric tests must use database version `8` after schema bump (in-memory builder picks up entity change automatically when version matches).
- `LocalModelSyncHandlerTest` is a **new** file; no existing test to extend.
