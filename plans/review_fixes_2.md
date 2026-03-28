# Code Review Fixes Plan

## 1. DeleteApiModelUseCase Ignores resetAssignmentsForApiModel DAO Query
**Proposed Solution:**
*   Add `suspend fun resetDefaultsForApiModel(apiModelId: Long)` to `DefaultModelRepositoryPort`.
*   Implement this method in `DefaultModelRepositoryImpl` by calling `defaultModelsDao.resetAssignmentsForApiModel(apiModelId)`.
*   Update `DeleteApiModelUseCase` to simply call `defaultModelRepository.resetDefaultsForApiModel(id)` instead of the `ModelType.entries.forEach` loop.

## 2. Null Safety Gap in DefaultModelRepositoryImpl.toAssignment
**Proposed Solution:**
*   Update `DefaultModelRepositoryImpl.toAssignment` to handle the case where `apiConfig` is null.
*   If `source == ModelSource.API` but `apiConfig` resolves to `null`, log a warning and gracefully fall back to `ModelSource.ON_DEVICE`.

## 3. Race Condition on _currentApiKey in SettingsViewModel
**Proposed Solution:**
*   Capture the current API key in a local immutable variable (`val apiKeyToSave = _currentApiKey.value`) *before* launching the coroutine in `onSaveApiModel`.
*   Pass `apiKeyToSave` to the use case.

## 4. Suboptimal Deletion — Redundant Lookup in ApiModelRepositoryImpl.delete
**Proposed Solution:**
*   Reverse the deletion order in `ApiModelRepositoryImpl.delete`: call `apiKeyManager.delete(id)` first, then call `apiModelsDao.deleteById(id)`.

## 5. stopSequences Delimiter Is Fragile
**Proposed Solution:**
*   Given that the Android SDK includes `org.json.JSONArray`, we can use it to serialize and deserialize the `stopSequences` list safely, e.g., `JSONArray(stopSequences).toString()` and parsing it back using `JSONArray(jsonString)`.