# Architectural Review: 39-refactor-model-persistence-for-byok

## Overview
This document reviews the proposed changes in `plans/39-refactor-model-persistence-for-byok` (discovery.md, spec.md, and test_spec.md). The focus is on the impact on the existing codebase, specifically analyzing `InitializeModelsUseCase`, `isDefault` semantics, deletion behavior, and identifying any cross-feature gaps or holes in the design.

## 1. Deletion Behavior & Reassignment
The strategy correctly identifies the XOR constraint in `DefaultModelEntity` and appropriately relies on updating it rather than deleting it when reassigning a model. Hard-deleting configs while preserving `LocalModelEntity` for a soft-delete correctly ensures the file metadata is intact for redownloading without polluting the active configuration pool.

**Gaps & Recommendations:**
- **Reassignment Logic Holes:** In `spec.md`, the reassignment check looks for `modelConfigIds.intersect(defaultConfigIds.toSet())`. If there's an intersection, it requires a replacement config. However, the spec says "User picks replacementConfigId (from a DIFFERENT model)". If the user only has ONE local model downloaded, they cannot pick a replacement from a different model. The spec mentions a minimum model count constraint ("At least one BYOK or local model must always exist"), but if the only other models are API models, the reassignment dialog MUST allow picking an `apiConfigId` as the replacement. The current code snippet in `spec.md` (Step 3) assumes we are updating `localConfigId`:
  ```kotlin
  defaultModelsDao.upsert(DefaultModelEntity(
      modelType = modelTypeToUpdate,
      localConfigId = replacementConfigId, // What if it's an API model?
      apiConfigId = null
  ))
  ```
  **Fix:** The reassignment logic must handle `apiConfigId` if the replacement is an API model. `DeleteLocalModelUseCase` needs to accept either a `replacementLocalConfigId` or `replacementApiConfigId`.
- **Last Model Deletion:** The constraint "At least one BYOK or local model must always exist" is good. But the UI needs to be able to evaluate this synchronously. `SettingsViewModel` needs to know the total count of active models (local + api) to prevent the deletion. This is partially handled in `showCannotDeleteLastModelAlert`.

## 2. InitializeModelsUseCase & Eligibility Check
The spec proposes completely rewriting `InitializeModelsUseCase` to skip SHA256 checks and rely on `DefaultModelEntity`. However, the current codebase delegates the eligibility check to `CheckModelEligibilityUseCase`, which compares `originalModels` and `newModels` (remote configs), checks for format changes, and relies on `ModelScanResult` (from `ModelFileScannerPort`) to detect missing or invalid files.

**Gaps & Recommendations:**
- **Logic Placement:** `spec.md` shows the logic entirely inside `InitializeModelsUseCase`, but the current architecture heavily uses `CheckModelsUseCase` and `CheckModelEligibilityUseCase`. Putting all this logic back into `InitializeModelsUseCase` breaks the Single Responsibility Principle and separation of concerns recently established in the codebase.
- **Soft-Deleted Model Handling:** The spec says "If exists and has NO configs -> soft-deleted, add to Available for Download". This check (checking if `configs.isEmpty()`) should likely be done inside `CheckModelEligibilityUseCase` or `InitializeModelsUseCase` before calling `CheckModelsUseCase`, so that soft-deleted models are excluded from the `expectedModels` passed to the file scanner. If we pass soft-deleted models to the file scanner, the scanner will see the file exists (if we didn't delete it physically, though the spec says we delete the physical file), or it will see it's missing and flag it for download.
- **Physical File Deletion vs Soft-Delete:** The spec states: "Delete physical file... Preserve LocalModelEntity (soft-delete)". If the physical file is deleted, `ModelFileScannerPort` will flag it as missing. If we want it to be "available to redownload" but NOT auto-downloaded, `InitializeModelsUseCase` MUST intercept this before `CheckModelsUseCase` forces a download.
- **Data Model Update:** `DownloadModelsResult` needs to be updated to include `availableToRedownload: List<LocalModelAsset>`. Currently, it only has `allModels`, `modelsToDownload`, and `scanResult`.

## 3. The `isDefault` Property
The spec introduces `isDefault: Boolean = false` on `LocalModelConfigurationEntity` to distinguish R2-downloaded configs (read-only) from user-created configs (editable).

**Gaps & Recommendations:**
- **Naming Conflict/Confusion:** `isDefault` is highly confusing in a system that has a `DefaultModelEntity`. A config with `isDefault = true` might NOT be the active default model for a slot (e.g., if the user reassigns the slot to a user-created config). The property means "is system provided" or "is factory preset", NOT "is the currently active default".
  **Fix:** Rename `isDefault` to `isSystemProvided`, `isFactoryPreset`, or `isReadOnly` to avoid massive confusion with `DefaultModelEntity`.
- **Domain Model Update:** `LocalModelConfiguration` domain model needs this property added so the UI can read it. (Currently it is missing in `LocalModelConfiguration.kt`).
- **UI State Update:** `LocalModelConfigUi` in `SettingsModels.kt` needs this property added so the UI can disable editing fields.

## 4. Cross-Feature Gaps
- **ModelRegistryImpl Caching:** `ModelRegistryImpl` caches assets and configurations. If a model is soft-deleted, we must ensure `ModelRegistryImpl` clears its cache for that model, and `observeAssets()` only emits active models (which it seems to do, since `modelsDao.observeAllCurrent()` should probably filter out soft-deleted ones, but wait—soft deleted models still have `modelStatus = 'CURRENT'`).
  **Critical Bug:** The query for `observeAllCurrent()` in `LocalModelsDao` is `SELECT * FROM local_models WHERE model_status = 'CURRENT'`. Soft-deleted models retain `model_status = 'CURRENT'`. This means `ModelRegistryImpl.observeAssets()` will emit soft-deleted models! The UI will show soft-deleted models as if they are active, just without configs.
  **Fix:** Update `LocalModelsDao.observeAllCurrent()` and `getAllCurrent()` to filter out soft-deleted models:
  ```sql
  SELECT m.* FROM local_models m
  WHERE m.model_status = 'CURRENT'
  AND EXISTS (SELECT 1 FROM local_model_configurations c WHERE c.local_model_id = m.id)
  ```
- **Reuse on Redownload:** The spec mentions "Reuse existing LocalModelEntity row... Create new config with isDefault = true". When this happens, `ModelRegistryImpl.setRegisteredModel` needs to be aware of this. Currently, `setRegisteredModel` checks if the model exists by SHA256 and updates its status. It needs to handle the case where it's soft-deleted (no configs) and add the new config correctly.
- **API Model Consistency:** Does `ApiModelConfigurationEntity` need `isDefault` (or `isSystemPreset`) as well? If the user sets up an API model, can they have read-only presets? Probably not necessary right now, but worth noting for consistency.

## 5. Summary of Action Items Required Before Implementation
1. **Rename `isDefault`:** Change to `isSystemPreset` or `isReadOnly` on `LocalModelConfigurationEntity` and domain models to avoid confusion with `DefaultModelEntity`.
2. **Update Reassignment Logic:** Modify `DeleteLocalModelUseCase` to accept either a local or API replacement config ID, to handle the case where the only alternative is an API model.
3. **Fix LocalModelsDao Queries:** Update `getAllCurrent()` and `observeAllCurrent()` to only return models that have at least one configuration (i.e., not soft-deleted).
4. **Refine InitializeModelsUseCase:** Keep the file scanning logic in `CheckModelEligibilityUseCase` / `CheckModelsUseCase`. Add a pre-filter step to identify soft-deleted models (by checking if they have 0 configs) and place them in an `availableToRedownload` list inside `DownloadModelsResult` rather than passing them to the scanner.
5. **Update Domain Models:** Add `isSystemPreset: Boolean` to `LocalModelConfiguration`. Add `availableToRedownload` to `DownloadModelsResult`.
