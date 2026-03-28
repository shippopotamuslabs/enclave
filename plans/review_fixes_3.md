# Review Fixes Plan 3

## 1. Room Database Versioning Crash
**Problem:** Added new tables (`api_models`, `default_models`) but left `version = 1` in `PocketCrewDatabase.kt`, which will crash on startup.
**Fix:** Bump `version` to `2` in `PocketCrewDatabase.kt` and add `fallbackToDestructiveMigration()` in the database builder.

## 2. Unscoped ViewModels & Broken BYOK Edit Flow
**Problem:** `SettingsNavigation.kt` instantiates a new `SettingsViewModel` for each screen. `onNavigateToByokConfigure` doesn't pass the `apiModelId`, so editing existing models opens a blank config.
**Fix:** Scope the `SettingsViewModel` to the navigation graph (`SettingsDestination.GRAPH`) using `val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(SettingsDestination.GRAPH) }` and `hiltViewModel(parentEntry)`. This shares the `_transientState` across all settings screens, naturally fixing the edit flow without needing string-based ID passing.

## 3. `requireNotNull` Crash on Empty API Models
**Problem:** In `ModelConfigurationScreen.kt`, clicking "API (BYOK)" when there are no API models passes a `null` ID, crashing the app.
**Fix:** Disable the `SegmentedButton` if `uiState.apiModels.isEmpty()`.

## 4. Main Thread Block via EncryptedSharedPreferences
**Problem:** `ApiKeyManager.save()` and `delete()` perform synchronous disk I/O, but `SettingsViewModel` launches them on `Dispatchers.Main`.
**Fix:** Wrap the inner calls of `save()` and `delete()` in `ApiModelRepositoryImpl.kt` with `withContext(Dispatchers.IO)`. Also, change `apply()` to `commit()` in `ApiKeyManager.kt` to ensure confirmation of the critical security write.

## 5. Missing `topK` in UI Domain Mapping
**Problem:** `ApiModelConfig.toUi()` in `SettingsViewModel.kt` misses the `topK` mapping, causing it to reset.
**Fix:** Add `topK = topK` to the `toUi()` mapper.

## 6. Redundant collect in Flow Builder
**Problem:** `GenerateChatResponseUseCase.kt` uses `.collect { emit(it) }` inside a flow.
**Fix:** Use `emitAll(generateWithService(...))` for better performance and idiomatic Kotlin.

## 7. Minor Code Quality Fixes
**Fix:**
*   Remove dead code lambda (`draftOneServiceProvider`) in `InferenceService.kt`.
*   Remove stale comment (`// removed isEditingApiModel = true`) in `SettingsViewModel.kt`.
*   Add `// TODO` for API routing in `InferenceFactoryImpl.kt`.
*   Fix `post_github_comment` in `.github/scripts/gemini_review.py` to raise an exception or exit 1 on failure.