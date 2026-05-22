# utility-models-persistence Test Spec

**Ticket ID:** `utility-models-persistence`

## Test Framework And Run Commands

**Frameworks:**

- JUnit 5 (`@Test`, `@BeforeEach`, `@ExtendWith`)
- MockK for port fakes and call verification
- Robolectric + `androidx.test.core.app.ApplicationProvider` for Room DAO/repository tests (`@ExtendWith(RobolectricExtension::class)`, `@Config(sdk = [34])`)
- Turbine only if `observeAllActive()` Flow tests are added

**Focused commands during iteration:**

```bash
./gradlew :core:domain:testDebugUnitTest --tests "*SyncUtilityModelRegistryUseCaseTest*"
./gradlew :core:domain:testDebugUnitTest --tests "*InitializeModelsUseCaseTest*"
./gradlew :core:data:testDebugUnitTest --tests "*UtilityModelRepositoryImplTest*"
./gradlew :core:data:testDebugUnitTest --tests "*UtilityModelsDaoTest*"
./gradlew :core:data:testDebugUnitTest --tests "*UtilityModelSyncHandlerTest*"
./gradlew :core:data:testDebugUnitTest --tests "*UtilityModelFileResolverTest*"
./gradlew :core:data:testDebugUnitTest --tests "*DownloadFinalizeWorkerTest*"
./gradlew :core:data:testDebugUnitTest --tests "*FirestoreSyncAdapterRegistryTest*"
./gradlew :core:data:testDebugUnitTest --tests "*FirestoreSyncDependencyGraphTest*"
./gradlew :core:data:testDebugUnitTest --tests "*CleanupDaoTest*"
npm --prefix functions test -- --grep "utility_models"
```

**Required validation before TASK_STATUS COMPLETE:**

```bash
./gradlew :core:domain:testDebugUnitTest
./gradlew :core:data:testDebugUnitTest
./gradlew :core:data:assembleDebug
./gradlew :app:kspDebugKotlin
./gradlew :core:data:ktlintCheck
./gradlew detekt
npm --prefix functions test
```

## Scenario Index

| Scenario ID | Area | Summary |
| --- | --- | --- |
| SCN-DOMAIN-001 | Domain | Sync utility asset upserts metadata with non-empty id. |
| SCN-DOMAIN-002 | Domain | Port rejects upsert when utilityType would duplicate active row. |
| SCN-DOMAIN-003 | Domain | Sync rejects non-utility asset. |
| SCN-DAO-001 | DAO | Unique index on utility_type prevents duplicate active types. |
| SCN-DAO-002 | DAO | Soft delete sets deleted=1 and pending_sync=1. |
| SCN-DAO-003 | DAO | claimPending/markSynced cycle matches local_models semantics. |
| SCN-SCHEMA-001 | Schema | Fresh version-7 database creates utility_models with expected columns. |
| SCN-REPO-001 | Repository | upsert stores row and triggers CloudSyncTrigger. |
| SCN-REPO-002 | Repository | getByUtilityType returns active metadata. |
| SCN-REPO-003 | Repository | Re-upsert same utility_type reuses existing id. |
| SCN-WIRE-001 | DI | DataModule binds UtilityModelRepositoryPort to impl. |
| SCN-SYNC-001 | Firestore | toFirestore/fromFirestore round-trip preserves fields. |
| SCN-SYNC-002 | Firestore | Remote newer updated_at overwrites local row. |
| SCN-SYNC-003 | Firestore | Local newer updated_at skips remote overwrite. |
| SCN-SYNC-004 | Firestore | Dependency graph lists utility_models with no parents. |
| SCN-SYNC-005 | Firestore | PendingSyncLookup returns pending_sync for utility_models doc. |
| SCN-FUNC-001 | Functions | SYNC_FEED_ALLOWLIST contains utility_models. |
| SCN-RESOLVE-001 | Resolver | Returns absolute path when Room row and file match size. |
| SCN-RESOLVE-002 | Resolver | Returns null when Room row missing. |
| SCN-RESOLVE-003 | Resolver | Returns null when file size mismatches metadata. |
| SCN-INIT-001 | Init | InitializeModelsUseCase persists valid on-disk utility. |
| SCN-INIT-002 | Init | InitializeModelsUseCase skips utility still in modelsToDownload. |
| SCN-FINALIZE-001 | Finalize | DownloadFinalizeWorker persists downloaded utility asset. |
| SCN-FINALIZE-002 | Finalize | DownloadFinalizeWorker enqueues embedding regen for ONNX_MODEL. |
| SCN-CLEANUP-001 | Cleanup | clearUserData deletes all utility_models rows. |
| SCN-SEC-001 | Security | UtilityModelEntity has no sensitive encrypted fields. |
| SCN-MUT-001 | Mutation defense | Resolver with fetchRemoteConfig call fails (zero network). |
| SCN-MUT-002 | Mutation defense | Utility routed through SyncLocalModelRegistryUseCase fails. |
| SCN-MUT-003 | Mutation defense | Resolver returning wrong utility_type path fails. |

## Happy Path Scenarios

### SCN-DOMAIN-002: Port Rejects Duplicate Active Utility Type At DAO Layer

Given Room contains active row with `utilityType = WHISPER` and `id = "aaaa-bbbb"`
When `UtilityModelRepositoryImpl.upsert` inserts a **new** id with the same `utilityType = WHISPER` bypassing reuse logic (mutation test)
Then SQLite constraint on `index_utility_models_utility_type` fails
And row count for `utilityType = WHISPER` remains `1`

### SCN-DOMAIN-001: Sync Utility Asset Upserts Metadata With Non-Empty Id

Given `LocalModelAsset` with `metadata.utilityType = UtilityType.WHISPER`, `sha256 = "whisper-sha"`, `localFileName = "ggml-base.en.bin"`, and `configurations = emptyList()`
And `FakeUtilityModelRepository` starts empty
When `SyncUtilityModelRegistryUseCase.invoke(asset)` runs
Then the returned `UtilityModelId.value` matches `"^[0-9a-f-]{36}$"`
And `FakeUtilityModelRepository.lastUpserted?.utilityType` equals `UtilityType.WHISPER`
And `FakeUtilityModelRepository.lastUpserted?.sha256` equals `"whisper-sha"`

### SCN-REPO-002: getByUtilityType Returns Active Metadata

Given Room row `utilityType = ONNX_VOCAB`, `sha256 = "vocab-sha"`, `deleted = 0`
When `UtilityModelRepositoryImpl.getByUtilityType(UtilityType.ONNX_VOCAB)` runs
Then result `utilityType` equals `UtilityType.ONNX_VOCAB`
And result `sha256` equals `"vocab-sha"`

### SCN-REPO-001: Upsert Stores Row And Triggers CloudSyncTrigger

Given an empty in-memory Room database at version 7
And `UtilityModelMetadata` with `utilityType = UtilityType.ONNX_MODEL`, `sha256 = "onnx-sha"`, `sizeInBytes = 90439680`, `localFileName = "model.onnx"`
When `UtilityModelRepositoryImpl.upsert(metadata)` runs
Then `utilityModelsDao.getByUtilityType(UtilityType.ONNX_MODEL)?.sha256` equals `"onnx-sha"`
And `utilityModelsDao.getByUtilityType(UtilityType.ONNX_MODEL)?.pendingSync` equals `1`
And `CloudSyncTrigger.trigger()` invocation count equals `1`

### SCN-REPO-003: Re-Upsert Same Utility Type Reuses Existing Id

Given Room contains active row `id = "11111111-1111-1111-1111-111111111111"` with `utilityType = UtilityType.WHISPER`
When `UtilityModelRepositoryImpl.upsert` is called with `UtilityModelMetadata(id = UtilityModelId(""), utilityType = UtilityType.WHISPER, sha256 = "new-sha", ...)`
Then returned id equals `UtilityModelId("11111111-1111-1111-1111-111111111111")`
And stored `sha256` equals `"new-sha"`

### SCN-SYNC-001: Firestore Round-Trip Preserves Fields

Given local `UtilityModelEntity` with `utilityType = UtilityType.ONNX_VOCAB`, `sha256 = "vocab-sha"`, `sizeInBytes = 231508`, `updatedAt = 1700000001000`, `deleted = false`
When `UtilityModelSyncHandler.toFirestore(entity)` is mapped and `fromFirestore(doc)` parses the result
Then parsed entity `utilityType` stored as `"onnx_vocab"`
And parsed entity `sha256` equals `"vocab-sha"`
And parsed entity `sizeInBytes` equals `231508`

### SCN-SYNC-002: Remote Newer updated_at Overwrites Local Row

Given local row `utilityType = WHISPER`, `localFileName = "old.bin"`, `updatedAt = 1000`, `pendingSync = 0`
And remote Firestore document has `local_filename = "ggml-base.en.bin"`, `updated_at = 2000`, `deleted = false`
When `UtilityModelSyncHandler.upsertLocal(entity, doc, overwrite = false)` runs
Then DAO row `localFilename` equals `"ggml-base.en.bin"`
And DAO row `pendingSync` equals `0`

### SCN-RESOLVE-001: Resolver Returns Path When Room Row And File Match Size

Given Room row `utilityType = WHISPER`, `localFileName = "ggml-base.en.bin"`, `sizeInBytes = 4`
And filesystem file `{modelsDir}/ggml-base.en.bin` exists with length `4`
When `UtilityModelFileResolver.resolveUtilityModelPath(UtilityType.WHISPER)` runs
Then result equals `"{modelsDir}/ggml-base.en.bin"`
And `ModelConfigFetcherPort.fetchRemoteConfig()` call count equals `0`

### SCN-INIT-001: InitializeModelsUseCase Persists Valid On-Disk Utility

Given remote catalog contains utility asset `utilityType = WHISPER`, `sha256 = "whisper-sha"`, `sizeInBytes = 4`
And filesystem has valid file for `"whisper-sha"`
And `CheckModelsUseCase` returns `modelsToDownload = emptyList()`
When `InitializeModelsUseCase.invoke()` runs
Then `UtilityModelRepositoryPort.getByUtilityType(UtilityType.WHISPER)?.sha256` equals `"whisper-sha"`
And `SyncLocalModelRegistryUseCase` invocation count equals `0`

### SCN-FINALIZE-001: DownloadFinalizeWorker Persists Downloaded Utility Asset

Given downloaded SHAs include `"onnx-sha"`
And remote catalog utility asset has `utilityType = ONNX_MODEL`, `sha256 = "onnx-sha"`, `configurations = emptyList()`
When `DownloadFinalizeWorker` handles `REQUEST_INITIALIZE` with `downloadedShas = ["onnx-sha"]`
Then `UtilityModelRepositoryPort.getByUtilityType(UtilityType.ONNX_MODEL)?.sha256` equals `"onnx-sha"`
And `SyncLocalModelRegistryUseCase` invocation count equals `0`

### SCN-FINALIZE-002: DownloadFinalizeWorker Enqueues Embedding Regen For ONNX_MODEL

Given downloaded utility asset has `utilityType = ONNX_MODEL`
When finalize completes successfully
Then `EmbeddingRegenerationCoordinator.enqueueFullRegeneration()` invocation count equals `1`

### SCN-FUNC-001: Functions Allowlist Contains utility_models

Given `functions/src/syncEventFeed.ts` exports `SYNC_FEED_ALLOWLIST`
When the allowlist is read in `syncEventFeed.test.ts`
Then `SYNC_FEED_ALLOWLIST` includes `"utility_models"`

## Error And Edge Scenarios

### SCN-DAO-002: Soft Delete Sets deleted=1 And pending_sync=1

Given active row `id = "util-1"`, `utilityType = WHISPER`, `pendingSync = 0`, `deleted = 0`
When `UtilityModelsDao.softDeleteById(UtilityModelId("util-1"))` runs
Then row `deleted` equals `1`
And row `pendingSync` equals `1`
And `getByUtilityType(WHISPER)` returns `null`

### SCN-DAO-003: claimPending And markSynced Match local_models Semantics

Given row `pendingSync = 1`
When `claimPending()` then `getClaimedForSync()` then `markSynced(listOf(id))` run
Then after claim `pendingSync` equals `2`
And after mark synced `pendingSync` equals `0`

### SCN-SCHEMA-001: Fresh Version-7 Database Creates utility_models Table

Given empty database store
When `Room.databaseBuilder` opens `EnclaveDatabase` at version `7` with `fallbackToDestructiveMigration(dropAllTables = true)` and no incremental migrations for `utility_models`
Then `PRAGMA table_info(utility_models)` includes columns `utility_type`, `sha256`, `pending_sync`, `deleted`
And index `index_utility_models_utility_type` exists with `unique = 1`

### SCN-SYNC-004: Dependency Graph Lists utility_models With No Parents

Given `FirestoreSyncDependencyGraph.collectionPaths()`
When required parents for `"utility_models"` and empty source map are resolved
Then result is empty list
And `"utility_models"` is in `collectionPaths()`

### SCN-SYNC-005: PendingSyncLookup Returns pending_sync For utility_models

Given Room row `id = "sync-id"`, `pendingSync = 2`
When `FirestoreSyncPendingSyncLookup.pendingSync("utility_models", "sync-id")` runs
Then result equals `2`

### SCN-WIRE-001: DataModule Binds UtilityModelRepositoryPort

Given `:core:data` compiles with Hilt modules
When `UtilityModelRepositoryImpl` is injected as `UtilityModelRepositoryPort`
Then compile succeeds without manual `@Provides` duplication errors

### SCN-DOMAIN-003: Sync Rejects Non-Utility Asset

Given `LocalModelAsset` with `metadata.utilityType = null` and one configuration assigned to `ModelType.FAST`
When `SyncUtilityModelRegistryUseCase.invoke(asset)` runs
Then thrown exception message contains `"Not a utility asset"`

### SCN-DAO-001: Unique Index On utility_type Prevents Duplicate Active Types

Given Room contains active row with `utilityType = WHISPER`
When inserting second active row with `utilityType = WHISPER` and different `id`
Then SQLite throws constraint failure on `index_utility_models_utility_type`

### SCN-RESOLVE-002: Resolver Returns Null When Room Row Missing

Given `utility_models` table has zero rows
When `UtilityModelFileResolver.resolveUtilityModelPath(UtilityType.WHISPER)` runs
Then result is `null`
And `ModelConfigFetcherPort.fetchRemoteConfig()` call count equals `0`

### SCN-RESOLVE-003: Resolver Returns Null When File Size Mismatches Metadata

Given Room row `utilityType = WHISPER`, `localFileName = "ggml-base.en.bin"`, `sizeInBytes = 4`
And filesystem file length equals `2`
When `UtilityModelFileResolver.resolveUtilityModelPath(UtilityType.WHISPER)` runs
Then result is `null`

### SCN-INIT-002: InitializeModelsUseCase Skips Utility Still In modelsToDownload

Given remote utility asset `sha256 = "missing-sha"`
And `CheckModelsUseCase` returns that asset in `modelsToDownload`
When `InitializeModelsUseCase.invoke()` runs
Then `UtilityModelRepositoryPort.getByUtilityType(UtilityType.WHISPER)` is `null`

### SCN-SYNC-003: Local Newer updated_at Skips Remote Overwrite

Given local row `utilityType = WHISPER`, `localFileName = "keep.bin"`, `updatedAt = 5000`, `pendingSync = 0`
And remote document has `local_filename = "remote.bin"`, `updated_at = 2000`
When `UtilityModelSyncHandler.upsertLocal(entity, doc, overwrite = false)` runs
Then DAO row `localFilename` equals `"keep.bin"`

### SCN-CLEANUP-001: clearUserData Deletes All utility_models Rows

Given Room contains 2 active utility rows
When `CleanupDao.clearUserData()` runs
Then `utilityModelsDao.getAll()` returns empty list

### SCN-SEC-001: UtilityModelEntity Has No Sensitive Encrypted Fields

Given `StaticSensitiveFieldCatalog.sensitiveFields("UtilityModelEntity")`
When the field set is inspected
Then size equals `0`
And `StaticSensitiveFieldCatalog.firestoreFields("utility_models")` size equals `0`

## Mutation-Defense Scenarios

These scenarios **must fail** a lazy implementation that keeps network fetch in the resolver, stores utilities in `local_models`, or resolves the wrong type.

### SCN-MUT-001: Resolver With fetchRemoteConfig Call Fails

Given `UtilityModelFileResolver` is constructed with a strict mock `ModelConfigFetcherPort` where `fetchRemoteConfig()` throws `AssertionError("network forbidden")`
And Room contains valid Whisper row and matching filesystem file
When `resolveUtilityModelPath(UtilityType.WHISPER)` runs
Then result equals the expected absolute path
And `fetchRemoteConfig()` call count equals `0`

**Failure mode:** Any implementation that still calls `fetchRemoteConfig()` throws or records call count ≥ 1 → test fails.

### SCN-MUT-002: Utility Routed Through SyncLocalModelRegistryUseCase Fails

Given downloaded utility asset `utilityType = WHISPER`, `configurations = emptyList()`
When `DownloadFinalizeWorker` finalize runs
Then `SyncLocalModelRegistryUseCase.invoke` call count equals `0`
And `SyncUtilityModelRegistryUseCase.invoke` call count equals `1`

**Failure mode:** Calling slot sync for utilities triggers `"Asset must contain at least one configuration"` or nonzero slot sync count → test fails.

### SCN-MUT-003: Resolver Returning Wrong Utility Type Path Fails

Given Room row for `UtilityType.ONNX_MODEL` with `localFileName = "model.onnx"`, `sizeInBytes = 100`
And Room row for `UtilityType.WHISPER` with `localFileName = "ggml-base.en.bin"`, `sizeInBytes = 4`
And both files exist with matching sizes
When `resolveUtilityModelPath(UtilityType.WHISPER)` runs
Then result ends with `"/ggml-base.en.bin"`
And result does not end with `"/model.onnx"`

**Failure mode:** Resolver matches first catalog row or ignores `utilityType` filter → test fails.

## Test File Mapping

| Scenario IDs | Test class |
| --- | --- |
| SCN-DOMAIN-001, SCN-DOMAIN-003 | `SyncUtilityModelRegistryUseCaseTest` |
| SCN-INIT-001, SCN-INIT-002 | `InitializeModelsUseCaseTest` |
| SCN-DAO-001, SCN-DAO-002, SCN-DAO-003 | `UtilityModelsDaoTest` |
| SCN-SCHEMA-001 | `EnclaveDatabaseMigrationTest` |
| SCN-REPO-001, SCN-REPO-002, SCN-REPO-003 | `UtilityModelRepositoryImplTest` |
| SCN-SYNC-001, SCN-SYNC-002, SCN-SYNC-003 | `UtilityModelSyncHandlerTest` |
| SCN-SYNC-004 | `FirestoreSyncDependencyGraphTest` |
| SCN-SYNC-005 | `FirestoreSyncPendingSyncLookupTest` (new or extend existing integration support) |
| SCN-FUNC-001 | `functions/src/syncEventFeed.test.ts` |
| SCN-RESOLVE-001, SCN-RESOLVE-002, SCN-RESOLVE-003, SCN-MUT-001, SCN-MUT-003 | `UtilityModelFileResolverTest` |
| SCN-FINALIZE-001, SCN-FINALIZE-002, SCN-MUT-002 | `DownloadFinalizeWorkerTest` |
| SCN-CLEANUP-001 | `CleanupDaoTest` |
| SCN-SEC-001 | `SensitiveFieldCatalogTest` |
| SCN-WIRE-001 | `DataModule` compile-time + optional Hilt smoke (covered by `:core:data:assembleDebug`) |

## Requirement Traceability

| Requirement | Tasks | Scenarios |
| --- | --- | --- |
| Dedicated Room table UUID PK | T3, T4 | SCN-SCHEMA-001, SCN-DAO-001 |
| Unique utility_type | T3 | SCN-DAO-001, SCN-REPO-003 |
| Separate DAO + repository | T3, T5, T6 | SCN-REPO-001, SCN-REPO-002 |
| Firestore upload pending | T5, T7 | SCN-REPO-001, SCN-DAO-003 |
| Firestore download apply | T7, T8 | SCN-SYNC-001, SCN-SYNC-002, SCN-SYNC-003 |
| Resolver offline (no network) | T10 | SCN-RESOLVE-001–003, SCN-MUT-001 |
| Init persist valid utilities (post-reinstall) | T11 | SCN-INIT-001, SCN-INIT-002 |
| Reinstall-only (no migration/backfill) | T4 | SCN-SCHEMA-001 |
| Download finalize persist | T12 | SCN-FINALIZE-001, SCN-FINALIZE-002 |
| No local_models shoehorn | T11, T12 | SCN-MUT-002 |
| Account cleanup | T13 | SCN-CLEANUP-001 |
| Functions allowlist | T9 | SCN-FUNC-001 |

## Self-Review Checklist (Plan Author)

- [x] Every spec task T1–T16 has at least one scenario
- [x] Literal expected values in Then clauses (paths, counts, sha256 strings, enum names)
- [x] SCN-MUT-001 fails lazy "still fetch remote" resolver implementation
- [x] No TBD/TODO placeholders
- [x] Run commands documented for `:core:domain` and `:core:data`
