# Test Specification: Room Database Schema Refactoring — Hybrid LLM Architecture
---
## 1. Happy Path Scenarios
### Entity CRUD — LocalModelEntity
* **Scenario:** Insert and retrieve a local model asset
  * **Given:** An empty `local_models` table
  * **When:** A `LocalModelEntity` is upserted with `sha256 = "abc123"`, `displayName = "Qwen3-4B"`, `modelStatus = CURRENT`
  * **Then:** `LocalModelsDao.getById()` returns the entity with a non-zero auto-generated ID, and all fields match the inserted values
* **Scenario:** Retrieve local model by SHA256
  * **Given:** A `LocalModelEntity` with `sha256 = "deadbeef"` exists in the database
  * **When:** `LocalModelsDao.getBySha256("deadbeef")` is called
  * **Then:** The returned entity has `sha256 = "deadbeef"` and matches the inserted entity exactly
* **Scenario:** Observe only CURRENT models
  * **Given:** Two `LocalModelEntity` records exist — one with `modelStatus = CURRENT`, one with `modelStatus = OLD`
  * **When:** `LocalModelsDao.observeAllCurrent()` emits its first value
  * **Then:** The emitted list contains exactly 1 entity, and it has `modelStatus = CURRENT`
* **Scenario:** Delete a local model by ID
  * **Given:** A `LocalModelEntity` exists with `id = 1`
  * **When:** `LocalModelsDao.deleteById(1)` is called
  * **Then:** `LocalModelsDao.getById(1)` returns `null`
---
### Entity CRUD — LocalModelConfigurationEntity
* **Scenario:** Insert and retrieve a tuning preset for a local model
  * **Given:** A `LocalModelEntity` with `id = 1` exists in the database
  * **When:** A `LocalModelConfigurationEntity` is upserted with `localModelId = 1`, `displayName = "Creative"`, `temperature = 0.9`, `systemPrompt = "You are a creative writer"`
  * **Then:** `LocalModelConfigurationsDao.getById()` returns the entity with the correct values, and `LocalModelConfigurationsDao.getAllForAsset(1)` returns a list containing exactly this one entity
* **Scenario:** Multiple presets per local model
  * **Given:** A `LocalModelEntity` with `id = 1` exists
  * **When:** Two `LocalModelConfigurationEntity` records are upserted: `displayName = "Precise"` and `displayName = "Creative"`, both with `localModelId = 1`
  * **Then:** `LocalModelConfigurationsDao.getAllForAsset(1)` returns a list of size 2
---
### Entity CRUD — ApiCredentialsEntity
* **Scenario:** Insert and retrieve API credentials
  * **Given:** An empty `api_credentials` table
  * **When:** An `ApiCredentialsEntity` is upserted with `provider = OPENAI`, `modelId = "gpt-4o"`, `credentialAlias = "my_openai_key"`, `displayName = "GPT-4o"`
  * **Then:** `ApiCredentialsDao.getById()` returns the entity with a non-zero id, and all fields match
* **Scenario:** Observe all credentials ordered by updated_at DESC
  * **Given:** Two `ApiCredentialsEntity` records exist: one with `updatedAt = 1000`, another with `updatedAt = 2000`
  * **When:** `ApiCredentialsDao.observeAll()` emits its first value
  * **Then:** The emitted list has the entity with `updatedAt = 2000` first
---
### Entity CRUD — ApiModelConfigurationEntity
* **Scenario:** Insert and retrieve an API tuning preset
  * **Given:** An `ApiCredentialsEntity` with `id = 1` exists
  * **When:** An `ApiModelConfigurationEntity` is upserted with `apiCredentialsId = 1`, `displayName = "Default"`, `temperature = 0.7`, `maxTokens = 4096`
  * **Then:** `ApiModelConfigurationsDao.getById()` returns the entity with correct values
* **Scenario:** Multiple presets per API credential
  * **Given:** An `ApiCredentialsEntity` with `id = 1` exists
  * **When:** Two `ApiModelConfigurationEntity` records are upserted with `displayName = "Fast"` and `displayName = "Thorough"`, both with `apiCredentialsId = 1`
  * **Then:** `ApiModelConfigurationsDao.getAllForCredentials(1)` returns a list of size 2
---
### Entity CRUD — DefaultModelEntity (XOR Pointer)
* **Scenario:** Assign a local config as default for a ModelType
  * **Given:** A `LocalModelConfigurationEntity` with `id = 5` exists
  * **When:** A `DefaultModelEntity` is upserted with `modelType = MAIN`, `localConfigId = 5`, `apiConfigId = null`
  * **Then:** `DefaultModelsDao.getDefault(MAIN)` returns the entity with `localConfigId = 5` and `apiConfigId = null`
* **Scenario:** Assign an API config as default for a ModelType
  * **Given:** An `ApiModelConfigurationEntity` with `id = 3` exists
  * **When:** A `DefaultModelEntity` is upserted with `modelType = VISION`, `localConfigId = null`, `apiConfigId = 3`
  * **Then:** `DefaultModelsDao.getDefault(VISION)` returns the entity with `apiConfigId = 3` and `localConfigId = null`
* **Scenario:** Switch default from local to API
  * **Given:** `DefaultModelEntity` for `MAIN` has `localConfigId = 5`, `apiConfigId = null`
  * **When:** The default is upserted with `localConfigId = null`, `apiConfigId = 3`
  * **Then:** `DefaultModelsDao.getDefault(MAIN)` returns `apiConfigId = 3` and `localConfigId = null`
* **Scenario:** Observe all defaults
  * **Given:** Defaults exist for `MAIN` (localConfigId = 5) and `VISION` (apiConfigId = 3)
  * **When:** `DefaultModelsDao.observeAll()` emits its first value
  * **Then:** The emitted list contains exactly 2 entries matching the above assignments
---
### CASCADE Behavior
* **Scenario:** Deleting a local model cascades to its configurations
  * **Given:** A `LocalModelEntity` with `id = 1` exists, and two `LocalModelConfigurationEntity` records with `localModelId = 1` exist
  * **When:** `LocalModelsDao.deleteById(1)` is called
  * **Then:** `LocalModelConfigurationsDao.getAllForAsset(1)` returns an empty list
* **Scenario:** Deleting API credentials cascades to its configurations
  * **Given:** An `ApiCredentialsEntity` with `id = 1` exists, and two `ApiModelConfigurationEntity` records with `apiCredentialsId = 1` exist
  * **When:** `ApiCredentialsDao.deleteById(1)` is called
  * **Then:** `ApiModelConfigurationsDao.getAllForCredentials(1)` returns an empty list
---
### ApiKeyManager (Refactored)
* **Scenario:** Save and retrieve key by credentialAlias
  * **Given:** No key exists for alias `"my_openai_key"`
  * **When:** `ApiKeyManager.save("my_openai_key", "sk-abc123")` is called
  * **Then:** `ApiKeyManager.get("my_openai_key")` returns `"sk-abc123"`
* **Scenario:** Delete key by credentialAlias
  * **Given:** A key exists for alias `"my_openai_key"`
  * **When:** `ApiKeyManager.delete("my_openai_key")` is called
  * **Then:** `ApiKeyManager.get("my_openai_key")` returns `null`
---
### Repository Layer — ApiModelRepositoryImpl
* **Scenario:** Save credentials with API key stores key via credentialAlias
  * **Given:** An empty database and ApiKeyManager
  * **When:** `saveCredentials(credentials, apiKey = "sk-test")` is called with an `ApiCredentials` having `credentialAlias = "my_key"`
  * **Then:** The credential entity is persisted in Room, and `ApiKeyManager.get("my_key")` returns `"sk-test"`
* **Scenario:** Delete credentials removes both entity and key
  * **Given:** An `ApiCredentialsEntity` with `id = 1` and `credentialAlias = "my_key"` exists, and a key is stored for `"my_key"`
  * **When:** `deleteCredentials(1)` is called
  * **Then:** `ApiCredentialsDao.getById(1)` returns `null`, and `ApiKeyManager.get("my_key")` returns `null`
---
### Repository Layer — DefaultModelRepositoryImpl
* **Scenario:** Get default with resolved display data for local model
  * **Given:** A `DefaultModelEntity` for `MAIN` has `localConfigId = 5` pointing to a config with `displayName = "Precise"` linked to a local model with `displayName = "Qwen3-4B"`
  * **When:** `getDefault(MAIN)` is called
  * **Then:** The returned `DefaultModelAssignment` has `localConfigId = 5`, `displayName = "Precise"`, and `apiConfigId = null`
* **Scenario:** Get default with resolved display data for API model
  * **Given:** A `DefaultModelEntity` for `VISION` has `apiConfigId = 3` pointing to a config with `displayName = "Default"` linked to credentials with `provider = OPENAI`, `displayName = "GPT-4o"`
  * **When:** `getDefault(VISION)` is called
  * **Then:** The returned `DefaultModelAssignment` has `apiConfigId = 3`, `displayName = "Default"`, `providerName = "OpenAI"`, and `localConfigId = null`
---
### Database Setup
* **Scenario:** Destructive migration from version 2 to 3
  * **Given:** A database at version 2 with `models` and `api_models` tables containing data
  * **When:** The app opens with version 3 schema and `fallbackToDestructiveMigration()`
  * **Then:** The database is recreated with all new tables (`local_models`, `local_model_configurations`, `api_credentials`, `api_model_configurations`, `default_models`) and old tables are gone, and all new tables are empty
---
## 2. Error Path & Edge Case Scenarios
### XOR Enforcement on DefaultModelEntity
* **Scenario:** Both FKs null throws IllegalArgumentException
  * **Given:** An attempt to construct a `DefaultModelEntity` with `localConfigId = null` and `apiConfigId = null`
  * **When:** The constructor is invoked
  * **Then:** An `IllegalArgumentException` is thrown with message containing "Exactly one of localConfigId or apiConfigId must be non-null"
* **Scenario:** Both FKs non-null throws IllegalArgumentException
  * **Given:** An attempt to construct a `DefaultModelEntity` with `localConfigId = 5` and `apiConfigId = 3`
  * **When:** The constructor is invoked
  * **Then:** An `IllegalArgumentException` is thrown with message containing "Exactly one of localConfigId or apiConfigId must be non-null"
---
### RESTRICT FK — Cannot Delete Config That Is a Default
* **Scenario:** Deleting a local config that is currently a default is blocked
  * **Given:** A `LocalModelConfigurationEntity` with `id = 5` exists, and a `DefaultModelEntity` for `MAIN` has `localConfigId = 5`
  * **When:** An attempt is made to delete the config (either directly or via `deleteAllForAsset()`)
  * **Then:** A `SQLiteConstraintException` (or equivalent Room exception) is thrown, and both the config and the default remain intact
* **Scenario:** Deleting an API config that is currently a default is blocked
  * **Given:** An `ApiModelConfigurationEntity` with `id = 3` exists, and a `DefaultModelEntity` for `VISION` has `apiConfigId = 3`
  * **When:** An attempt is made to delete the config (or its parent credential which would CASCADE to the config)
  * **Then:** A `SQLiteConstraintException` is thrown. The credential, its config, and the default all remain intact
---
### Unique Constraint Violations
* **Scenario:** Duplicate display name per local model asset is rejected
  * **Given:** A `LocalModelConfigurationEntity` with `localModelId = 1` and `displayName = "Creative"` exists
  * **When:** Another `LocalModelConfigurationEntity` with `localModelId = 1` and `displayName = "Creative"` (different `id = 0`) is inserted
  * **Then:** The insert replaces the existing entity (via `@Upsert` on the unique index) — the result is exactly one config with `displayName = "Creative"` for that asset
* **Scenario:** Same display name on different assets is allowed
  * **Given:** `LocalModelConfigurationEntity` with `localModelId = 1` and `displayName = "Creative"` exists
  * **When:** Another `LocalModelConfigurationEntity` with `localModelId = 2` and `displayName = "Creative"` is inserted
  * **Then:** Both configs exist — the unique constraint is scoped to `(localModelId, displayName)`
* **Scenario:** Duplicate API credential identity is rejected
  * **Given:** An `ApiCredentialsEntity` with `provider = OPENAI`, `modelId = "gpt-4o"`, `baseUrl = null` exists
  * **When:** Another `ApiCredentialsEntity` with the same `provider`, `modelId`, and `baseUrl` is inserted (different `id = 0`)
  * **Then:** The `@Upsert` replaces the existing entity on the unique index — exactly one credential remains
---
### Dangling References
* **Scenario:** DefaultModelEntity FK references a non-existent local config
  * **Given:** No `LocalModelConfigurationEntity` with `id = 999` exists
  * **When:** An attempt is made to upsert a `DefaultModelEntity` with `localConfigId = 999`
  * **Then:** A `SQLiteConstraintException` is thrown (FK constraint violation)
* **Scenario:** DefaultModelEntity FK references a non-existent API config
  * **Given:** No `ApiModelConfigurationEntity` with `id = 999` exists
  * **When:** An attempt is made to upsert a `DefaultModelEntity` with `apiConfigId = 999`
  * **Then:** A `SQLiteConstraintException` is thrown (FK constraint violation)
---
### ApiKeyManager Edge Cases
* **Scenario:** Retrieve key for non-existent alias returns null
  * **Given:** No key has been stored for alias `"nonexistent_alias"`
  * **When:** `ApiKeyManager.get("nonexistent_alias")` is called
  * **Then:** `null` is returned
* **Scenario:** Overwrite existing key for same alias
  * **Given:** A key `"old_key"` exists for alias `"my_key"`
  * **When:** `ApiKeyManager.save("my_key", "new_key")` is called
  * **Then:** `ApiKeyManager.get("my_key")` returns `"new_key"` — the old key is replaced
---
### JSON Serialization Edge Case — customHeadersAndParams
* **Scenario:** Empty map serializes to empty JSON object
  * **Given:** A repository mapper function that serializes `Map<String, String>` to JSON string
  * **When:** An empty map `emptyMap()` is serialized
  * **Then:** The resulting string is `"{}"`
* **Scenario:** Malformed JSON string deserializes to empty map
  * **Given:** A repository mapper function that deserializes JSON string to `Map<String, String>`
  * **When:** The input string is `"not_valid_json"`
  * **Then:** The resulting map is `emptyMap()` (graceful fallback, no exception thrown)
---
### Repository — Transaction Atomicity
* **Scenario:** Credential save with blank API key skips ApiKeyManager
  * **Given:** An empty database
  * **When:** `saveCredentials(credentials, apiKey = "")` is called
  * **Then:** The credential entity is persisted, but no key is stored in `ApiKeyManager`
---
## 3. Mutation Defense
### Lazy Implementation Risk
The most likely broken/lazy implementation would:
1. Accept any combination of `localConfigId`/`apiConfigId` in `DefaultModelEntity` without the `init` XOR check — allowing both-null (orphaned slot) or both-non-null (ambiguous source), and
2. Use `CASCADE` instead of `RESTRICT` on config→default FKs — silently deleting a user's default assignment when they delete a config preset, leading to mysteriously empty model slots
### Defense Scenario 1: XOR enforcement prevents silent both-null state
* **Scenario:** Constructing DefaultModelEntity with both FKs null must crash
  * **Given:** A function that creates `DefaultModelEntity(modelType = MAIN, localConfigId = null, apiConfigId = null)`
  * **When:** This constructor is invoked
  * **Then:** An `IllegalArgumentException` is thrown — NOT a successful insert with a silently orphaned model slot
### Defense Scenario 2: RESTRICT prevents silent cascade deletion of defaults
* **Scenario:** Deleting a local model with configs that are actively assigned as defaults must fail
  * **Given:** A `LocalModelEntity` (id=1) with a `LocalModelConfigurationEntity` (id=5, localModelId=1). A `DefaultModelEntity` for `MAIN` has `localConfigId = 5`.
  * **When:** `LocalModelsDao.deleteById(1)` is called (which CASCADEs to delete config id=5)
  * **Then:** The delete is BLOCKED by the RESTRICT FK on `default_models.local_config_id → local_model_configurations.id`. A `SQLiteConstraintException` is thrown. The local model, its config, and the default assignment all remain intact. The user must reassign the default before the model can be deleted.
### Defense Scenario 3: credentialAlias is used for key lookup, not entity ID
* **Scenario:** ApiKeyManager must use credentialAlias string, not numeric ID
  * **Given:** An `ApiCredentialsEntity` with `id = 42` and `credentialAlias = "my_key"` is saved, along with API key `"sk-secret"`
  * **When:** `ApiKeyManager.get("my_key")` is called
  * **Then:** `"sk-secret"` is returned. Calling `ApiKeyManager.get("42")` returns `null` — proving the key is stored by alias, not by numeric ID.
