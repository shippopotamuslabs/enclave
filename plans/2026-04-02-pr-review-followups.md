# PR Review Follow-Up Plan

## Goal
Address the current PR feedback without starting implementation yet, with the model-update/download lifecycle treated as the primary issue. The main design recommendation is to stop switching the active registry/default assignment to a new remote model before that model has finished downloading successfully.

## Confirmed Findings
- `InitializeModelsUseCase` currently registers remote configs immediately, even when the SHA changed and the new file is still missing. That repoints the active default to the new config before download success.
- `ModelRegistryImpl.setRegisteredModel()` marks `OLD` by looking up the incoming SHA, not the slot’s previous assignment. For a true model swap, that misses the old row entirely.
- `getAssetsPreferringOld()` does not currently provide a real fallback path. It just follows `default_models`, so once the default is repointed there is no old active assignment left to “prefer”.
- `clearOld()` is effectively a stub, so even the cleanup path after successful download cannot converge fully.
- The current soft-delete startup logic in `InitializeModelsUseCase` derives soft-deleted entries from `getAssetsPreferringOld()`, but soft-deleted models are no longer reachable from defaults. The implementation and the intended behavior are out of sync.
- `ConversationManagerImpl` can throw on cold start because it relies on async flow collection to fill `cachedAsset`/`cachedConfig`.
- `InferenceFactoryImpl` caches by extension only, so `.task` to `.task` swaps can keep using the stale model path.
- New BYOK credentials can still start with `credentialAlias = ""`, and the database does not enforce alias uniqueness yet.

## Recommended Design Direction
Do not mutate the active local-model assignment for a slot until the replacement file is present and verified.

That means:
- If remote config changes only tuning and the SHA/file stays the same, update the active config in place immediately.
- If remote config changes to a different SHA/file, keep the existing local assignment active during the download.
- Only after the download succeeds should the app:
  - register the new asset/config as `CURRENT`
  - demote the prior slot assignment to `OLD` when it is no longer referenced by any slot
  - delete `OLD` rows and orphaned files
- If the download fails and a previous local model existed, keep that previous assignment active and surface a user-facing fallback message.
- If the download fails on a pristine install, keep the current behavior of staying on the download screen because there is no fallback model to use.

This is simpler and safer than trying to pre-write a “pending current” registry state and then restore it later on failure.

## Planned Changes

### 1. Rework the model upgrade lifecycle
Files:
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/download/InitializeModelsUseCase.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/download/DownloadModelsResult.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/download/ModelDownloadOrchestratorImpl.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/download/ModelDownloadWorker.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/repository/ModelRegistryPort.kt`

Plan:
- Keep startup scanning based on the currently active local assignments, not on pre-registered remote replacements.
- Extend the startup/download result so the download pipeline knows which slot updates must be committed after a successful download.
- Move slot-switching for changed SHA/file cases out of startup registration and into the post-success path.
- Keep config-only updates on the current asset path immediate.
- Add explicit failure handling for “remote update failed but existing local model is still valid”, with a snackbar/message hook instead of trapping the user on a broken pending assignment.
- Either remove `getAssetsPreferringOld()` entirely or redefine it to reflect the new semantics, because the current name and behavior are misleading.

### 2. Fix registry demotion and cleanup semantics
Files:
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ModelRegistryImpl.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/LocalModelsDao.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/LocalModelConfigurationsDao.kt`

Plan:
- Change `setRegisteredModel()` so “mark existing as old” targets the slot’s previous active model/config, not a row found by the incoming SHA.
- Ensure demotion respects shared-file cases, so a row is not demoted/deleted while another slot still depends on the same model file.
- Implement `clearOld()` for real DB cleanup by deleting `OLD` local model rows and letting configuration rows cascade.
- Add DAO support for deleting `OLD` rows directly.
- Verify the success cleanup order is: commit new active assignment, demote obsolete row(s), clear `OLD`, then clean orphaned files.

### 3. Fix soft-delete startup handling
Files:
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/download/InitializeModelsUseCase.kt`
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/repository/ModelRegistryPort.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ModelRegistryImpl.kt`

Plan:
- Stop inferring soft-deleted models from the active/default assignment map.
- Use `getSoftDeletedModels()` as the source of truth for “available to re-download”.
- Keep those models out of `CheckModelsUseCase` input so they are not silently re-downloaded on startup.
- Update related tests so they model real soft-delete state instead of injecting empty-config assets through the active assignment path.

### 4. Fix inference read-through and cache invalidation
Files:
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/ConversationManagerImpl.kt`
- `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/InferenceFactoryImpl.kt`

Plan:
- Add a synchronous read-through in `ConversationManagerImpl.getConversation()` before throwing “No registered asset”.
- Make `InferenceFactoryImpl` cache by concrete asset identity, not only file extension. The cache key should include enough information to distinguish one `.task` file from another.
- Ensure service recreation happens when the active file path changes even if the implementation type stays the same.

### 5. Fix BYOK alias generation and persistence rules
Files:
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/ByokConfigureScreen.kt`
- `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsViewModel.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiCredentialsEntity.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiCredentialsDao.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/PocketCrewDatabase.kt`

Plan:
- Generate a non-empty alias for new credentials before the first save instead of relying on the blank UI fallback object.
- Enforce unique aliases at the database level.
- Add a migration/backfill step for existing blank or duplicate aliases before adding the unique index, since this is user data and destructive reset is the wrong default here.
- Keep delete/update behavior aligned with `ApiKeyManager`, which now uses `credentialAlias` as the stable key.

## Test Plan
- `InitializeModelsUseCaseTest`: cover config-only updates, changed-SHA deferred activation, soft-deleted source-of-truth via `getSoftDeletedModels()`, and pristine-install failure.
- `ModelRegistryImplTest`: cover demoting the previous slot model, shared-SHA no-demotion, and real `clearOld()` cleanup.
- `ModelDownloadOrchestratorImpl` tests: cover successful post-download registry commit and failed-download no-op/rollback behavior.
- `ConversationManagerImplTest`: cover cold-start synchronous fallback before first flow emission.
- `InferenceFactoryImplTest`: cover same-extension model swaps forcing service recreation.
- `SettingsViewModelTest` and DAO tests: cover generated alias behavior and DB uniqueness.

## Implementation Order
1. Lock the lifecycle decision: defer changed-SHA activation until download success.
2. Update the download result/orchestrator contract to carry post-success registry work.
3. Fix registry demotion and cleanup.
4. Fix soft-delete discovery to use the correct source.
5. Fix inference bugs.
6. Fix BYOK alias generation and schema constraints.
7. Add or update tests around each regression before merge.

## Notes
- I do not think the current “keep old config in the registry while also repointing defaults to the new one” model is buying us anything. If we adopt deferred activation, fallback becomes the natural default state instead of something we have to reconstruct after failure.
- If you want the fallback snackbar, the cleanest trigger point is the download-state failure path when there was an existing active local asset for the affected slot.
