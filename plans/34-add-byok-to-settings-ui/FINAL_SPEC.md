# Final Spec: Room Database Schema Refactoring for BYOK & Local Models

## Status: Proposed
## Architect: Subagent
## Context
The Pocket Crew database schema is undergoing a major refactoring to support Bring Your Own Key (BYOK) configurations alongside a more flexible and slot-agnostic local model setup. 
The current database uses `ModelEntity` to associate configs tightly with files and `ApiModelEntity` which doesn't support multiple configurations for the same credentials.
We need to introduce a generalized "Asset -> Config -> Default" pattern to support both API and Local Models smoothly across any inference slot (`ModelType`).

## Answers to Open Questions

### Question 1: Does LocalModelConfigurationEntity need a modelType field?
**A1:** No. Configs are designed to be slot-agnostic named presets. What prevents the same config from being assigned to multiple slots simultaneously is nothing—a user can absolutely assign the same config (e.g., "Casual Llama") to both the `DRAFT_ONE` and `DRAFT_TWO` slots. The `DefaultModelEntity` will merely have multiple rows (one for each `ModelType`) pointing to the same `localConfigId`. The unique constraint on the config table will be `(localModelId, displayName)`.

**A2:** The UX flow should present creating a config as "creating a named preset" for an asset. Assigning that preset to a slot (FAST, THINKING, MAIN) is a separate step managed by `DefaultModelEntity`. 

### Question 2: ApiModelConfigurationEntity also slot-agnostic?
**Yes.** Similar to local models, API configurations are slot-agnostic named presets. A user can create "Claude Coding" and "Claude Casual" and assign them to whatever slot they choose. 

### Question 3: Does the unique index need to be (assetId) alone or (assetId, something)?
**A3:** The unique index must be `(assetId, displayName)`. The user's goal is to have multiple configs per asset (e.g., Temperature 0.2 for coding vs Temperature 1.0 for casual). `displayName` makes the preset unique per underlying asset.

### Question 4: What about the current ModelEntity?
**A4:** Since the app is unreleased and there are no existing production users, we will NOT write a Room migration strategy for `ModelEntity` and `ApiModelEntity`. We will do a fresh setup. The current tables (`models`, `api_models`, `default_models`) should be dropped/re-created in `PocketCrewDatabase.kt` and version incremented or tables replaced. However, since we are using Room and doing a destructive change on an unreleased app, we should provide the drop/create SQL in a migration step or just bump the schema version and advise clearing app data if local development requires it. 

### Question 5: Where does the MoA pipeline actually read from?
**A5:** The MoA pipeline queries `DefaultModelEntity` for a specific `ModelType`. Since `DefaultModelEntity` uses a CHECK constraint / XOR structure, it will know whether to fetch the associated Local Config or API Config based on which FK (`localConfigId` or `apiConfigId`) is non-null. The pipeline layer (domain/data) will resolve the join to the appropriate Config table and Asset table.

### Question 6: ApiProvider enum expansion
**A6:** Yes. `SELF_HOSTED` requires `baseUrl`. `SUBSCRIPTION` uses a JWT-based proxy where `credentialAlias = "SUBSCRIPTION_TOKEN"`.

---

## 1. Entity Definitions (Kotlin)

### Local Model Asset
```kotlin
package com.browntowndev.pocketcrew.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus

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

### Local Model Configuration
```kotlin
package com.browntowndev.pocketcrew.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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

### API Credentials Asset
```kotlin
package com.browntowndev.pocketcrew.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider

@Entity(tableName = "api_credentials")
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

### API Model Configuration
```kotlin
package com.browntowndev.pocketcrew.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val customHeadersAndParams: String = "{}", // Stored as JSON string

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

### Default Model Pointer
```kotlin
package com.browntowndev.pocketcrew.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

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

## 2. Updated Enums

```kotlin
// In com.browntowndev.pocketcrew.domain.model.inference.ApiProvider.kt
package com.browntowndev.pocketcrew.domain.model.inference

enum class ApiProvider(val displayName: String) {
    ANTHROPIC("Anthropic"),
    OPENAI("OpenAI"),
    GOOGLE("Google"),
    SELF_HOSTED("Self-Hosted"),
    SUBSCRIPTION("Subscription")
}
```
**Important:** `ModelSource` enum should be DELETED entirely as it's implicit now via the XOR logic in `DefaultModelEntity`.

## 3. Room Type Converters

```kotlin
package com.browntowndev.pocketcrew.core.data.local

import androidx.room.TypeConverter
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ByokConverters {
    @TypeConverter
    fun fromApiProvider(provider: ApiProvider): String = provider.name

    @TypeConverter
    fun toApiProvider(value: String): ApiProvider = ApiProvider.valueOf(value)
    
    // For mapping custom headers and params Map<String, String> to JSON string
    @TypeConverter
    fun fromMap(map: Map<String, String>): String {
        return Json.encodeToString(map)
    }

    @TypeConverter
    fun toMap(value: String): Map<String, String> {
        return if (value.isBlank()) emptyMap() else Json.decodeFromString(value)
    }
}
```
*(Note: You will also want to ensure kotlinx-serialization is available, or use `JSONObject` or Moshi/Gson if that's standard for this app).*

## 4. DAOs

The full DAO implementations will follow the standard Room structure, e.g., 
- `LocalModelsDao` (Insert/Update/Delete LocalModelEntity + Configurations)
- `ApiCredentialsDao` (Insert/Update/Delete ApiCredentialsEntity + Configurations)
- `DefaultModelsDao` (Upsert/Delete DefaultModelEntity).

*Omitting full repetitive Dao interfaces here for brevity as they are standard boilerplate.*

## 5. Migration SQL
Since the app is unreleased, we will do a destructive drop/re-create by bumping the schema version to 2 (if it was 1). No detailed migration SQL needed. Just replace the old tables with the new ones. If strictly requested by Room:
```sql
DROP TABLE IF EXISTS default_models;
DROP TABLE IF EXISTS api_models;
DROP TABLE IF EXISTS models;

-- Room will then run its create queries for the new tables based on annotations.
```
*Wait, standard Room unreleased behavior is simply to update the version and `fallbackToDestructiveMigration()`. We will enable that in the `PocketCrewDatabase` builder.*

## 6. Ripple Effects

| Component | Impact | Action Required |
| :--- | :--- | :--- |
| `PocketCrewDatabase` | Model changes require new Dao references and updated Entity list. | Add new DAOs, update `@Database(entities = [...])`. Add `@TypeConverters(ByokConverters::class)`. |
| `ModelTypeConverters` | `ModelSourceConverters` is obsolete. | Delete `ModelSourceConverters`. |
| `ModelRegistryImpl` | Needs a rewrite to use the Config tables and properly map the pipeline to the active config via `DefaultModelsDao`. | Refactor repository logic to use XOR pattern. |
| `ApiModelRepositoryImpl` | Must be updated to read from `ApiCredentialsDao` and handle configuration mapping. | Refactor entirely. |
| Repositories | Many downstream usages of `ModelEntity` will break. | Map new domain models (`LocalModel`, `LocalModelConfig`) correctly. |

## 7. Decision Summary

| Problem | Decision | Rationale |
| :--- | :--- | :--- |
| Config reusability | Configs are slot-agnostic. | Allows one tuned file or API endpoint to be reused across `DRAFT_ONE` or `MAIN` without duplication. |
| Uniqueness Constraint | `(assetId, displayName)` | Enables multiple diverse configurations per single downloaded model or API endpoint. |
| Entity Structure | "Asset -> Config -> Default" | Decouples the physical file/API key from the inference slot pointer. |
| Enum Management | Added `SELF_HOSTED` & `SUBSCRIPTION`. Deleted `ModelSource`. | ModelSource was redundant as XOR logic manages this dynamically now. |
| Default Deletion Guard | `RESTRICT` on DefaultModelEntity FKs. | Prevents deleting a config if it is actively in use by a pipeline slot. |

## Delegation Plan
This spec is fully complete and ready to be turned over to the coder droid.
1. The **Coder** droid should apply the entity and converter changes.
2. The **TDD** droid should update repository tests given the new domain models and DAO boundaries.
3. The **Coder** droid will then refactor `ModelRegistryImpl` and others to match the new DAOs.

Architecture proposal complete.
