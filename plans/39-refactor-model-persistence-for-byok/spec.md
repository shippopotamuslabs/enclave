# Technical Specification: Room Database Schema Refactoring — Hybrid LLM Architecture
## 1. System Architecture
### Target Files
**New Files (`:data`):**
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/LocalModelEntity.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/LocalModelConfigurationEntity.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiCredentialsEntity.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiModelConfigurationEntity.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/LocalModelsDao.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/LocalModelConfigurationsDao.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiCredentialsDao.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiModelConfigurationsDao.kt`
**Modify Files (`:data`):**
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/DefaultModelEntity.kt` — Replace FK to `ApiModelEntity` with dual FKs to config entities, remove `source` column, add XOR `init` block
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/DefaultModelsDao.kt` — Remove `resetAssignmentsForApiModel()`, add queries by config ID
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/PocketCrewDatabase.kt` — Version 3, replace entity/DAO registration, remove `ModelSourceConverters`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ModelRegistryImpl.kt` — Major refactor to use `LocalModelsDao` + `LocalModelConfigurationsDao`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ApiModelRepositoryImpl.kt` — Rewrite for `ApiCredentialsDao` + `ApiModelConfigurationsDao`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/DefaultModelRepositoryImpl.kt` — Update joins for dual FK config tables
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/ApiKeyManager.kt` — Refactor key lookup from `Long` to `String` (credentialAlias)
**Delete Files (`:data`):**
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ModelEntity.kt` — Replaced by `LocalModelEntity` + `LocalModelConfigurationEntity`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiModelEntity.kt` — Replaced by `ApiCredentialsEntity` + `ApiModelConfigurationEntity`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiModelsDao.kt` — Replaced by `ApiCredentialsDao` + `ApiModelConfigurationsDao`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ModelsDao.kt` — Replaced by `LocalModelsDao` + `LocalModelConfigurationsDao`
**Modify Files (`:domain`):**
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/ApiProvider.kt` — Add `SELF_HOSTED`, `SUBSCRIPTION` variants
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/DefaultModelAssignment.kt` — Refactor to reference config IDs, remove `ModelSource` dependency
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/ApiModelConfig.kt` — Split into `ApiCredentials` + `ApiModelConfiguration` domain models
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/repository/ApiModelRepositoryPort.kt` — Update to accept credential + config separately
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/repository/DefaultModelRepositoryPort.kt` — Update signature for dual-FK model
**Delete Files (`:domain`):**
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/ModelSource.kt` — Redundant; source is implicit from which FK is populated in `DefaultModelEntity`
### Component Boundaries
```
:domain (Pure Kotlin — no framework deps)
├── ApiProvider enum (+ SELF_HOSTED, SUBSCRIPTION)
├── ApiCredentials domain model (NEW — identity + auth)
├── ApiModelConfiguration domain model (NEW — tuning preset)
├── DefaultModelAssignment (refactored — config IDs, no ModelSource)
├── ModelRegistryPort (existing — updated for local asset/config)
├── ApiModelRepositoryPort (updated — credential + config pair)
└── DefaultModelRepositoryPort (updated — dual FK model)
:data (Room + Hilt — implements domain ports)
├── LocalModelEntity + LocalModelsDao (NEW — file asset)
├── LocalModelConfigurationEntity + LocalModelConfigurationsDao (NEW — local tuning preset)
├── ApiCredentialsEntity + ApiCredentialsDao (NEW — replaces ApiModelEntity)
├── ApiModelConfigurationEntity + ApiModelConfigurationsDao (NEW — API tuning preset)
├── DefaultModelEntity + DefaultModelsDao (REFACTORED — dual FK XOR)
├── ApiKeyManager (REFACTORED — credentialAlias-based lookup)
├── ModelRegistryImpl (REFACTORED — uses asset + config DAOs)
├── ApiModelRepositoryImpl (REWRITTEN — credentials + configs)
└── DefaultModelRepositoryImpl (REFACTORED — dual FK joins)
```
## 2. Data Models & Schemas
### Entity Relationship Diagram
```
local_models (1) ──CASCADE──→ (N) local_model_configurations
                                         │
                                     RESTRICT
                                         ↓
                               default_models ←── PK: model_type (ModelType)
                                         ↑
                                     RESTRICT
                                         │
api_credentials (1) ──CASCADE──→ (N) api_model_configurations
```
### New Entities (defined in discovery_and_spec.md)
| Entity | Table | PK | Key FKs | Cascade |
|--------|-------|----|---------|---------|
| `LocalModelEntity` | `local_models` | `id` (autoGenerate) | — | — |
| `LocalModelConfigurationEntity` | `local_model_configurations` | `id` (autoGenerate) | `local_model_id` → `local_models.id` | CASCADE |
| `ApiCredentialsEntity` | `api_credentials` | `id` (autoGenerate) | — | — |
| `ApiModelConfigurationEntity` | `api_model_configurations` | `id` (autoGenerate) | `api_credentials_id` → `api_credentials.id` | CASCADE |
| `DefaultModelEntity` | `default_models` | `model_type` (ModelType) | `local_config_id` → `local_model_configurations.id`, `api_config_id` → `api_model_configurations.id` | RESTRICT (both) |
### Reused Existing Models
- `ModelType` enum — unchanged, used as PK for `default_models`
- `ModelFileFormat` enum — unchanged, used in `LocalModelEntity`
- `ModelStatus` enum — unchanged, used in `LocalModelEntity`
- `ModelConfiguration` domain model — unchanged, still used for local model registry
- `TransactionProvider` port + `RoomTransactionProvider` impl — reused for multi-DAO transactions
- `LoggingPort` — reused in `ModelRegistryImpl` for debug logging
### Deleted Models
- `ModelSource` enum — redundant, source is implicit from which FK column is populated in `DefaultModelEntity`
- `ModelEntity` — replaced by `LocalModelEntity` (asset) + `LocalModelConfigurationEntity` (preset)
- `ApiModelEntity` — replaced by `ApiCredentialsEntity` (identity) + `ApiModelConfigurationEntity` (preset)
### New Domain Models (`:domain`)
```kotlin
// Replaces ApiModelConfig — split into identity + configuration
data class ApiCredentials(
    val id: Long = 0,
    val displayName: String,
    val provider: ApiProvider,
    val modelId: String,
    val baseUrl: String? = null,
    val isVision: Boolean = false,
    val credentialAlias: String,
)
data class ApiModelConfiguration(
    val id: Long = 0,
    val apiCredentialsId: Long,
    val displayName: String,
    val maxTokens: Int = 4096,
    val contextWindow: Int = 4096,
    val temperature: Double = 0.7,
    val topP: Double = 0.95,
    val topK: Int? = null,
    val frequencyPenalty: Double = 0.0,
    val presencePenalty: Double = 0.0,
    val stopSequences: List<String> = emptyList(),
    val customHeadersAndParams: Map<String, String> = emptyMap(),
)
```
### Refactored Domain Models
```kotlin
// DefaultModelAssignment — no longer uses ModelSource
data class DefaultModelAssignment(
    val modelType: ModelType,
    val localConfigId: Long? = null,
    val apiConfigId: Long? = null,
    // Resolved display data for the UI
    val displayName: String? = null,
    val providerName: String? = null,
)
```
## 3. API Contracts & Interfaces
### ApiModelRepositoryPort (Updated)
```kotlin
interface ApiModelRepositoryPort {
    fun observeAllCredentials(): Flow<List<ApiCredentials>>
    suspend fun getAllCredentials(): List<ApiCredentials>
    suspend fun getCredentialsById(id: Long): ApiCredentials?
    suspend fun saveCredentials(credentials: ApiCredentials, apiKey: String): Long
    suspend fun deleteCredentials(id: Long)
    suspend fun getConfigurationsForCredentials(credentialsId: Long): List<ApiModelConfiguration>
    suspend fun getConfigurationById(id: Long): ApiModelConfiguration?
    suspend fun saveConfiguration(config: ApiModelConfiguration): Long
    suspend fun deleteConfigurationsForCredentials(credentialsId: Long)
}
```
### DefaultModelRepositoryPort (Updated)
```kotlin
interface DefaultModelRepositoryPort {
    suspend fun getDefault(modelType: ModelType): DefaultModelAssignment?
    fun observeDefaults(): Flow<List<DefaultModelAssignment>>
    suspend fun setDefault(modelType: ModelType, localConfigId: Long?, apiConfigId: Long?)
    suspend fun clearDefault(modelType: ModelType)
}
```
### ApiKeyManager (Refactored)
```kotlin
class ApiKeyManager {
    fun save(credentialAlias: String, apiKey: String)
    fun get(credentialAlias: String): String?
    fun delete(credentialAlias: String)
}
```
### Error Handling
- **RESTRICT FK violation on delete:** When attempting to delete a configuration that is currently a default, SQLite will throw `SQLiteConstraintException`. The repository must catch this and surface a typed domain error (e.g., `ConfigInUseException`).
- **XOR init violation:** `IllegalArgumentException` thrown if both or neither FK is set on `DefaultModelEntity`. This is a programming error, not a user-facing error.
- **Dangling credential alias:** If `ApiKeyManager.get(alias)` returns `null`, the credential is treated as having an expired/missing key. UI should prompt re-entry.
## 4. Environment & Config
### build.gradle.kts
No new dependencies required. Uses existing:
- Room (KSP)
- `org.json.JSONObject` (Android built-in, already used)
- Hilt for DI
### PocketCrewDatabase Changes
```kotlin
@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        MessageSearch::class,
        LocalModelEntity::class,
        LocalModelConfigurationEntity::class,
        ApiCredentialsEntity::class,
        ApiModelConfigurationEntity::class,
        DefaultModelEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(
    DateConverters::class,
    RoleConverters::class,
    ModelTypeConverters::class,
    MessageStateConverters::class,
    PipelineStepConverters::class,
    ApiProviderConverters::class
    // ModelSourceConverters REMOVED — ModelSource enum deleted
    // CustomHeadersTypeConverter NOT NEEDED — stored as raw String
)
```
### Migration
- `fallbackToDestructiveMigration()` — App is unreleased, no production data to preserve
- Version bump: 2 → 3
### Manifest
No manifest changes required.
## 5. Constitution Audit
This design strictly adheres to the project's Clean Architecture rules:
- `:domain` contains only pure Kotlin models, interfaces, and enums — zero framework imports
- Entity ↔ Domain mapping is exclusively in `:data` (repository layer)
- All new entities live in `:data` only, never exposed to `:app` or `:domain`
- `TransactionProvider` port pattern is reused for multi-DAO atomicity
- No `@TypeConverter` proliferation — `customHeadersAndParams` mapped in the repository layer per DATA_LAYER_RULES.md §2 preference for normalized over TypeConverter
