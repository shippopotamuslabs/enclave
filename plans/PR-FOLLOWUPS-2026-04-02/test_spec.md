# Test Specification: PR Review Follow-ups

## 1. Happy Path Scenarios

### Scenario: Deferred Activation - Success
- **Given:** A remote config update with a different SHA256 for a model slot that already has a local asset.
- **When:** `InitializeModelsUseCase` is invoked.
- **Then:** The local registry should NOT be updated to the new SHA yet. The "current" asset for that slot should remain unchanged in the DB.
- **When:** `ModelDownloadOrchestrator` reports SUCCESS for the download.
- **Then:** The registry should be updated (set the new asset as `CURRENT`, demote the old one to `OLD`).

### Scenario: Deferred Activation - Failure
- **Given:** A remote config update with a different SHA256 for an existing model slot.
- **When:** `ModelDownloadOrchestrator` reports FAILURE for the download.
- **Then:** The registry should NOT be updated. The previous local asset should remain active.
- **And:** A fallback snackbar message should be emitted.

### Scenario: BYOK Alias Generation - New
- **Given:** A new API credential is being saved for "openai" provider and "gpt-4o" model.
- **When:** `onSaveApiCredentials` is called in `SettingsViewModel`.
- **Then:** The `credentialAlias` should be generated as `openai-gpt-4o`.
- **And:** If `openai-gpt-4o` already exists, it should append `-2`, `-3`, etc.

### Scenario: Inference Cache Invalidation
- **Given:** An active inference session with a model file.
- **When:** The model file for that slot is updated (different SHA, same extension).
- **Then:** `InferenceFactoryImpl` should return a NEW inference service instance, not the cached one.

### Scenario: Registry Cleanup
- **Given:** Multiple rows in `local_models` marked as `OLD`.
- **When:** `ModelRegistryImpl.clearOld()` is called.
- **Then:** Those `OLD` rows should be deleted from the database.

## 2. Error Path & Edge Case Scenarios

### Scenario: DB Uniqueness Violation
- **Given:** An existing credential with alias `openai-gpt-4o`.
- **When:** A new credential with the same alias is inserted via DAO (bypassing ViewModel check).
- **Then:** `SQLiteConstraintException` (Unique constraint failed: api_credentials.credential_alias) should be thrown.

### Scenario: Inference Cold Start Race
- **Given:** `ConversationManagerImpl` is queried for a conversation before the first flow emission.
- **When:** `getConversation()` is called.
- **Then:** It should perform a synchronous read from the registry instead of returning null or throwing.

## 3. Mutation Defense
### Lazy Implementation Risk
A lazy implementation might still use `getAssetsPreferringOld()` or a similar fallback mechanism instead of properly deferring activation until success, leading to race conditions where the UI shows the "new" model metadata but the "old" model file is still being used.

### Defense Scenario
- **Given:** `InitializeModelsUseCase` discovers a new remote SHA.
- **When:** `getRegisteredAsset()` is called immediately after initialization.
- **Then:** It MUST return the OLD asset metadata, not the new remote one.
- **And:** `ModelRegistryImpl.clearOld()` should NOT delete any `OLD` rows if they are still referenced by any slot's fallback state (though in this new design, they aren't "old" yet, they are "current").
