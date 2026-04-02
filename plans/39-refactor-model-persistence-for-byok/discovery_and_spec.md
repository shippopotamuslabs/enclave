# Final Spec: Room Database Schema Refactoring — Hybrid LLM Architecture

## Status: FINAL — Ready for Implementation

---

## Architecture: "Asset → Config → Default" Pattern

```
Physical Asset              Tuning Preset                    Active Pointer
─────────────────          ──────────────────────           ─────────────────
LocalModelEntity      →    LocalModelConfigurationEntity   →  DefaultModelEntity
ApiCredentialsEntity  →    ApiModelConfigurationEntity    →  DefaultModelEntity

Key Invariants:
• Assets = immutable identity (file path / API credentials)
• Configs = slot-agnostic named presets (temperature, system_prompt, etc.)
• DefaultModelEntity = maps ModelType → active config ID (XOR: exactly one FK non-null)
• Configs are reusable across ANY ModelType slot
• ModelSource enum DELETED — source is implicit from which FK is populated
```

---

## Review Corrections (Post-Review)

> [!IMPORTANT]
> These corrections were identified during the architectural review and MUST be applied during implementation.

1. **No Gson dependency** — The project uses `org.json.JSONObject`/`JSONArray` (Android built-in), NOT Gson. The `CustomHeadersTypeConverter` using Gson is invalid. Since `customHeadersAndParams` is stored as a raw `String` column (default `"{}"`), no `@TypeConverter` is needed. Serialization/deserialization to `Map<String, String>` happens in the repository mapper layer using `org.json.JSONObject`.

2. **No SQLite CHECK constraint** — Room does not support `CHECK` constraints via entity annotations. XOR enforcement is single-level: Kotlin `init` block only. A database-level CHECK can optionally be added via `RoomDatabase.Callback.onCreate()`.

3. **Database version must be 3** — The current `PocketCrewDatabase` is already at `version = 2`. The refactored schema must use `version = 3`.

4. **ApiKeyManager refactor** — The current `ApiKeyManager` uses `apiModelId: Long` as the ESP key (`api_key_$apiModelId`). The new schema introduces `credentialAlias: String` on `ApiCredentialsEntity`. `ApiKeyManager` must be refactored to use `credentialAlias` as the ESP key instead of the numeric ID, enabling stable key references that survive database rebuilds.

5. **CustomHeadersTypeConverter REMOVED** — Since `customHeadersAndParams` is a `String` column, the `@TypeConverters` annotation on `PocketCrewDatabase` should NOT include `CustomHeadersTypeConverter`. The `ModelSourceConverters` TypeConverter is also removed (since `ModelSource` enum is deleted).

---

## Entity Definitions

### 1. LocalModelEntity (Physical File Asset)

```kotlin
@Entity(
    tableName = "local_models",
    indices = [Index(value = ["sha256"])]
)
data class LocalModelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "model_file_format")
    val modelFileFormat: ModelFileFormat,

    @ColumnInfo(name = "huggingface_model_name")
    val huggingFaceModelName: String,

    @ColumnInfo(name = "remote_filename")
    val remoteFilename: String,

    @ColumnInfo(name = "local_filename")
    val localFilename: String,

    @ColumnInfo(name = "sha256")
    val sha256: String,

    @ColumnInfo(name = "size_in_bytes")
    val sizeInBytes: Long,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "model_status")
    val modelStatus: ModelStatus,

    @ColumnInfo(name = "thinking_enabled")
    val thinkingEnabled: Boolean = false,

    @ColumnInfo(name = "is_vision")
    val isVision: Boolean = false,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
```

**Note:** `isVision` is NEW — added to track vision capability. `thinkingEnabled` already exists in current schema.

---

### 2. LocalModelConfigurationEntity (Slot-Agnostic Tuning Preset)

```kotlin
@Entity(
    tableName = "local_model_configurations",
    foreignKeys = [
        ForeignKey(
            entity = LocalModelEntity::class,
            parentColumns = ["id"],
            childColumns = ["local_model_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["local_model_id", "display_name"], unique = true)
    ]
)
data class LocalModelConfigurationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "local_model_id")
    val localModelId: Long,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "temperature")
    val temperature: Double = 0.7,

    @ColumnInfo(name = "top_k")
    val topK: Int = 40,

    @ColumnInfo(name = "top_p")
    val topP: Double = 0.95,

    @ColumnInfo(name = "min_p")
    val minP: Double = 0.05,

    @ColumnInfo(name = "repetition_penalty")
    val repetitionPenalty: Double = 1.1,

    @ColumnInfo(name = "max_tokens")
    val maxTokens: Int = 4096,

    @ColumnInfo(name = "context_window")
    val contextWindow: Int = 4096,

    @ColumnInfo(name = "thinking_enabled")
    val thinkingEnabled: Boolean = false,

    @ColumnInfo(name = "system_prompt")
    val systemPrompt: String? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
```

**Key decisions:**
- **No `modelType` on config** — configs are slot-agnostic, assignable to any MoA slot
- **Unique index on `(local_model_id, display_name)`** — prevents duplicate named presets per file
- **System prompt on config** — user picks a preset and it brings its own persona
- `CASCADE` delete — removing a file removes all its configs

---

### 3. ApiCredentialsEntity (API Credential Asset)

```kotlin
@Entity(
    tableName = "api_credentials",
    indices = [
        Index(value = ["provider", "model_id", "base_url"], unique = true)
    ]
)
data class ApiCredentialsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "provider")
    val provider: ApiProvider,

    @ColumnInfo(name = "base_url")
    val baseUrl: String? = null,

    @ColumnInfo(name = "model_id")
    val modelId: String,

    @ColumnInfo(name = "is_vision")
    val isVision: Boolean = false,

    @ColumnInfo(name = "credential_alias")
    val credentialAlias: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
```

**Key decisions:**
- `credentialAlias` is the lookup key in `EncryptedSharedPreferences`
- `"SUBSCRIPTION_TOKEN"` → use JWT auth (subscription proxy)
- Any other string → lookup API key in ESP via `ApiKeyManager`
- API key itself **never stored in Room**

---

### 4. ApiModelConfigurationEntity (Slot-Agnostic Tuning Preset for API)

```kotlin
@Entity(
    tableName = "api_model_configurations",
    foreignKeys = [
        ForeignKey(
            entity = ApiCredentialsEntity::class,
            parentColumns = ["id"],
            childColumns = ["api_credentials_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["api_credentials_id", "display_name"], unique = true)
    ]
)
data class ApiModelConfigurationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "api_credentials_id")
    val apiCredentialsId: Long,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "custom_headers_and_params")
    val customHeadersAndParams: String = "{}",

    @ColumnInfo(name = "max_tokens")
    val maxTokens: Int = 4096,

    @ColumnInfo(name = "context_window")
    val contextWindow: Int = 4096,

    @ColumnInfo(name = "temperature")
    val temperature: Double = 0.7,

    @ColumnInfo(name = "top_p")
    val topP: Double = 0.95,

    @ColumnInfo(name = "top_k")
    val topK: Int? = null,

    @ColumnInfo(name = "frequency_penalty")
    val frequencyPenalty: Double = 0.0,

    @ColumnInfo(name = "presence_penalty")
    val presencePenalty: Double = 0.0,

    @ColumnInfo(name = "stop_sequences")
    val stopSequences: String = "",

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
```

---

### 5. DefaultModelEntity (Active Pointer per ModelType Slot)

```kotlin
@Entity(
    tableName = "default_models",
    foreignKeys = [
        ForeignKey(
            entity = LocalModelConfigurationEntity::class,
            parentColumns = ["id"],
            childColumns = ["local_config_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ApiModelConfigurationEntity::class,
            parentColumns = ["id"],
            childColumns = ["api_config_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["local_config_id"]),
        Index(value = ["api_config_id"])
    ]
)
data class DefaultModelEntity(
    @PrimaryKey
    @ColumnInfo(name = "model_type")
    val modelType: ModelType,

    @ColumnInfo(name = "local_config_id")
    val localConfigId: Long? = null,

    @ColumnInfo(name = "api_config_id")
    val apiConfigId: Long? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    init {
        require((localConfigId == null) xor (apiConfigId == null)) {
            "Exactly one of localConfigId or apiConfigId must be non-null"
        }
    }
}
```

**Key decisions:**
- `RESTRICT` cascade — blocks deletion of a config that is currently a default
- **XOR enforced via Kotlin `init` block** — Room does not support `CHECK` constraints via annotations. The `init { require(...) }` block prevents invalid construction at the Kotlin level. A `CHECK` constraint can optionally be added via `RoomDatabase.Callback.onCreate()` for defense in depth.
- **No `ModelSource` enum** — source is implicit from which FK is populated

---

## DAOs

### LocalModelsDao
```kotlin
@Dao
interface LocalModelsDao {
    @Query("SELECT * FROM local_models WHERE id = :id")
    suspend fun getById(id: Long): LocalModelEntity?

    @Query("SELECT * FROM local_models WHERE sha256 = :sha256")
    suspend fun getBySha256(sha256: String): LocalModelEntity?

    @Query("SELECT * FROM local_models WHERE model_status = 'CURRENT'")
    suspend fun getAllCurrent(): List<LocalModelEntity>

    @Query("SELECT * FROM local_models WHERE model_status = 'CURRENT'")
    fun observeAllCurrent(): Flow<List<LocalModelEntity>>

    @Upsert
    suspend fun upsert(entity: LocalModelEntity): Long

    @Query("DELETE FROM local_models WHERE id = :id")
    suspend fun deleteById(id: Long)
}
```

### LocalModelConfigurationsDao
```kotlin
@Dao
interface LocalModelConfigurationsDao {
    @Query("SELECT * FROM local_model_configurations WHERE local_model_id = :localModelId")
    suspend fun getAllForAsset(localModelId: Long): List<LocalModelConfigurationEntity>

    @Query("SELECT * FROM local_model_configurations WHERE id = :id")
    suspend fun getById(id: Long): LocalModelConfigurationEntity?

    @Upsert
    suspend fun upsert(entity: LocalModelConfigurationEntity): Long

    @Query("DELETE FROM local_model_configurations WHERE local_model_id = :localModelId")
    suspend fun deleteAllForAsset(localModelId: Long)
}
```

### ApiCredentialsDao
```kotlin
@Dao
interface ApiCredentialsDao {
    @Query("SELECT * FROM api_credentials ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<ApiCredentialsEntity>>

    @Query("SELECT * FROM api_credentials ORDER BY updated_at DESC")
    suspend fun getAll(): List<ApiCredentialsEntity>

    @Query("SELECT * FROM api_credentials WHERE id = :id")
    suspend fun getById(id: Long): ApiCredentialsEntity?

    @Upsert
    suspend fun upsert(entity: ApiCredentialsEntity): Long

    @Query("DELETE FROM api_credentials WHERE id = :id")
    suspend fun deleteById(id: Long)
}
```

### ApiModelConfigurationsDao
```kotlin
@Dao
interface ApiModelConfigurationsDao {
    @Query("SELECT * FROM api_model_configurations WHERE api_credentials_id = :credentialsId")
    suspend fun getAllForCredentials(credentialsId: Long): List<ApiModelConfigurationEntity>

    @Query("SELECT * FROM api_model_configurations WHERE id = :id")
    suspend fun getById(id: Long): ApiModelConfigurationEntity?

    @Upsert
    suspend fun upsert(entity: ApiModelConfigurationEntity): Long

    @Query("DELETE FROM api_model_configurations WHERE api_credentials_id = :credentialsId")
    suspend fun deleteAllForCredentials(credentialsId: Long)
}
```

### DefaultModelsDao
```kotlin
@Dao
interface DefaultModelsDao {
    @Query("SELECT * FROM default_models WHERE model_type = :modelType")
    suspend fun getDefault(modelType: ModelType): DefaultModelEntity?

    @Query("SELECT * FROM default_models")
    fun observeAll(): Flow<List<DefaultModelEntity>>

    @Query("SELECT * FROM default_models")
    suspend fun getAll(): List<DefaultModelEntity>

    @Upsert
    suspend fun upsert(entity: DefaultModelEntity)

    @Query("DELETE FROM default_models WHERE model_type = :modelType")
    suspend fun delete(modelType: ModelType)
}
```

---

## Migration Strategy

Since the app is **unreleased** (no production users), use destructive migration:

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
)
abstract class PocketCrewDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun localModelsDao(): LocalModelsDao
    abstract fun localModelConfigurationsDao(): LocalModelConfigurationsDao
    abstract fun apiCredentialsDao(): ApiCredentialsDao
    abstract fun apiModelConfigurationsDao(): ApiModelConfigurationsDao
    abstract fun defaultModelsDao(): DefaultModelsDao
}

// In database builder:
.databaseBuilder(...)
    .fallbackToDestructiveMigration()
    .build()
```

**Old tables dropped:** `models` (ModelEntity), `api_models` (old ApiModelEntity)
**New tables created:** `local_models`, `local_model_configurations`, `api_credentials`, `api_model_configurations`, `default_models`

---

## Complete Ripple Effects

| Layer | File | Change |
|---|---|---|
| **Domain** | `ApiProvider.kt` | Add `SELF_HOSTED`, `SUBSCRIPTION` |
| **Domain** | `ApiModelConfig.kt` | Split into `ApiCredentials` + `ApiModelConfiguration` domain models |
| **Domain** | `DefaultModelAssignment.kt` | Update to reference config IDs |
| **Domain** | `ModelSource.kt` | **DELETE** — redundant, source implicit from FK |
| **Data** | `LocalModelEntity.kt` | New file (file asset) |
| **Data** | `LocalModelConfigurationEntity.kt` | New file (tuning preset) |
| **Data** | `ApiCredentialsEntity.kt` | New file (replaces old `ApiModelEntity`) |
| **Data** | `ApiModelConfigurationEntity.kt` | New file (tuning preset for API) |
| **Data** | `DefaultModelEntity.kt` | Refactor with dual FK, RESTRICT cascade, no `source` column |
| **Data** | `LocalModelsDao.kt` | New DAO |
| **Data** | `LocalModelConfigurationsDao.kt` | New DAO |
| **Data** | `ApiCredentialsDao.kt` | New DAO |
| **Data** | `ApiModelConfigurationsDao.kt` | New DAO |
| **Data** | `DefaultModelsDao.kt` | Update for new schema |
| **Data** | `PocketCrewDatabase.kt` | Version 3, destructive migration, new entities + DAOs |
| **Data** | `ApiModelRepositoryImpl.kt` | Rewrite for `ApiCredentialsDao` + `ApiModelConfigurationsDao` |
| **Data** | `DefaultModelRepositoryImpl.kt` | Update joins for new config tables |
| **Data** | `ModelRegistryImpl.kt` | Major refactor — use new asset/config tables |
| **Feature** | `SettingsViewModel.kt` | Handle slot-agnostic config creation + assignment |
| **Feature** | `SettingsModels.kt` | Update UI state |
| **model_config.json** | — | Add `isVision` field to each model entry |

---

## Decision Summary

| Decision | Choice | Rationale |
|---|---|---|
| Config pattern | Asset → Config → Default | Unified for local and API |
| Config slot association | Slot-agnostic | Same preset assignable to any MoA slot |
| `modelType` on config | **NOT needed** | Configs are just named presets; assignment is in DefaultModelEntity |
| Unique constraint | `(assetId, displayName)` | Multiple presets per asset, named uniquely |
| `ModelSource` enum | **DELETED** | XOR in DefaultModelEntity makes it redundant |
| FK cascade (asset → config) | `CASCADE` | Deleting a file/API cred removes all its configs |
| FK cascade (config → default) | `RESTRICT` | Must change default before deleting a config |
| XOR enforcement | Kotlin `init` block | Room lacks CHECK annotation support; init block provides runtime safety |
| Migration | Destructive (unreleased app) | `fallbackToDestructiveMigration()` + version bump |
| BYOK migration | Not needed | Unreleased app |

