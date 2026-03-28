# BYOK Settings UI + InferenceFactoryPort Stub — Final Implementation Ticket

## Overview

Add BYOK (Bring Your Own Key) configuration UI to Settings and wire up the `InferenceFactoryPort` abstraction that **always returns the on-device model** for now. Actual API inference implementation is a follow-up ticket.

**In scope:**
- New database tables (`api_models`, `default_models`) + Room migration v1 → v2
- `ApiKeyManager` backed by `EncryptedSharedPreferences` for secure key storage
- Domain models, ports, and use cases for BYOK config CRUD
- BYOK Setup bottom sheet in Settings (add/edit/delete API model configs) — **production-grade, S-Tier quality**
- Model Configuration display name refactor (remove persona aliases, show real model names, read-only label)
- `model_config.json` cleanup (real names, strip persona names from system prompts)
- `InferenceFactoryPort` interface + stub impl that always delegates to on-device engines
- Refactor `GenerateChatResponseUseCase` to use `InferenceFactoryPort` instead of direct qualifier injection

**Out of scope (future tickets):**
- `ApiInferenceServiceImpl` (actual HTTP calls to Anthropic/OpenAI/Google)
- Provider-specific request builders / SSE streaming
- Real API key validation (test connection)
- Crew mode hybrid (mixed on-device + API)
- Cost tracking / usage warnings
- `ModelType.CODE` addition

---

## Proposed Changes

### Phase 1: Database & Domain Foundation

---

#### Core Data Layer (`core/data`)

##### [NEW] `ApiModelEntity.kt`
`core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiModelEntity.kt`

```kotlin
@Entity(tableName = "api_models")
data class ApiModelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "display_name")
    val displayName: String,           // User-given name, e.g. "My Claude Sonnet"

    @ColumnInfo(name = "provider")
    val provider: ApiProvider,          // ANTHROPIC, OPENAI, GOOGLE

    @ColumnInfo(name = "base_url")
    val baseUrl: String? = null,        // Custom endpoint for compatible providers

    @ColumnInfo(name = "model_id")
    val modelId: String,               // e.g. "claude-sonnet-4-20250514"

    @ColumnInfo(name = "is_reasoning")
    val isReasoning: Boolean = false,

    @ColumnInfo(name = "is_vision")
    val isVision: Boolean = false,

    @ColumnInfo(name = "max_tokens")
    val maxTokens: Int = 4096,

    @ColumnInfo(name = "temperature")
    val temperature: Double = 0.7,

    @ColumnInfo(name = "top_p")
    val topP: Double = 0.95,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
```

**No `api_key` column.** Keys are stored exclusively in `EncryptedSharedPreferences` via `ApiKeyManager`, keyed by `"api_key_${entity.id}"`. Room never touches secrets.

##### [NEW] `DefaultModelEntity.kt`
`core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/DefaultModelEntity.kt`

```kotlin
@Entity(
    tableName = "default_models",
    foreignKeys = [
        ForeignKey(
            entity = ApiModelEntity::class,
            parentColumns = ["id"],
            childColumns = ["api_model_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["api_model_id"])]
)
data class DefaultModelEntity(
    @PrimaryKey
    @ColumnInfo(name = "model_type")
    val modelType: ModelType,

    @ColumnInfo(name = "source")
    val source: ModelSource,           // ON_DEVICE or API

    @ColumnInfo(name = "api_model_id")
    val apiModelId: Long? = null,      // FK to api_models, null = on-device

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
```

No FK to `ModelEntity` — when `source = ON_DEVICE`, `modelType` is the implicit join key used by `ModelRegistryPort.getRegisteredModel(modelType)`.

##### [NEW] `ApiModelsDao.kt`
`core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiModelsDao.kt`

```kotlin
@Dao
interface ApiModelsDao {
    @Query("SELECT * FROM api_models ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<ApiModelEntity>>

    @Query("SELECT * FROM api_models ORDER BY updated_at DESC")
    suspend fun getAll(): List<ApiModelEntity>

    @Query("SELECT * FROM api_models WHERE id = :id")
    suspend fun getById(id: Long): ApiModelEntity?

    @Upsert
    suspend fun upsert(entity: ApiModelEntity): Long

    @Query("DELETE FROM api_models WHERE id = :id")
    suspend fun deleteById(id: Long)
}
```

##### [NEW] `DefaultModelsDao.kt`
`core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/DefaultModelsDao.kt`

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

    @Query("UPDATE default_models SET source = 'ON_DEVICE', api_model_id = NULL WHERE api_model_id = :apiModelId")
    suspend fun resetAssignmentsForApiModel(apiModelId: Long)
}
```

##### [NEW] `ApiProviderConverters.kt` + `ModelSourceConverters.kt`
Room `@TypeConverter` classes for `ApiProvider` and `ModelSource` enums (string ↔ enum).

##### [NEW] `ApiKeyManager.kt`
`core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/ApiKeyManager.kt`

```kotlin
/**
 * Manages API key storage using EncryptedSharedPreferences.
 * Keys are AES-256 GCM encrypted via Android Keystore MasterKey.
 * Room stores everything EXCEPT the raw key — this is the only place keys exist.
 */
class ApiKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            "byok_api_keys",
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(apiModelId: Long, apiKey: String) {
        prefs.edit().putString("api_key_$apiModelId", apiKey).apply()
    }

    fun get(apiModelId: Long): String? {
        return prefs.getString("api_key_$apiModelId", null)
    }

    fun delete(apiModelId: Long) {
        prefs.edit().remove("api_key_$apiModelId").apply()
    }
}
```

##### [MODIFY] `PocketCrewDatabase.kt`
- `version = 1` → `version = 2`
- Add `ApiModelEntity::class`, `DefaultModelEntity::class` to `entities` array
- Add `abstract fun apiModelsDao(): ApiModelsDao`
- Add `abstract fun defaultModelsDao(): DefaultModelsDao`
- Add `ApiProviderConverters::class`, `ModelSourceConverters::class` to `@TypeConverters`
- Add `MIGRATION_1_2`:
  ```sql
  CREATE TABLE api_models (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, ...)
  CREATE TABLE default_models (model_type TEXT NOT NULL, source TEXT NOT NULL, api_model_id INTEGER, ..., PRIMARY KEY(model_type), FOREIGN KEY(api_model_id) REFERENCES api_models(id) ON DELETE SET NULL)
  CREATE INDEX index_default_models_api_model_id ON default_models(api_model_id)
  -- Seed defaults for each known ModelType as ON_DEVICE
  INSERT INTO default_models (model_type, source, api_model_id, updated_at) VALUES ('FAST', 'ON_DEVICE', NULL, ...)
  INSERT INTO default_models (model_type, source, api_model_id, updated_at) VALUES ('THINKING', 'ON_DEVICE', NULL, ...)
  -- ... etc for MAIN, DRAFT_ONE, DRAFT_TWO, FINAL_SYNTHESIS, VISION
  ```

##### [MODIFY] `DataModule.kt`
Add Hilt `@Provides` for `ApiModelsDao`, `DefaultModelsDao`, and `ApiKeyManager`.

##### [NEW] `ApiModelRepositoryImpl.kt`
`core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ApiModelRepositoryImpl.kt`

Implements `ApiModelRepositoryPort`. Injects `ApiModelsDao` + `ApiKeyManager`. Maps `ApiModelEntity` ↔ `ApiModelConfig`. The `save()` method persists entity to Room and key to ESP in a single operation. The `delete()` method removes from both.

##### [NEW] `DefaultModelRepositoryImpl.kt`
`core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/DefaultModelRepositoryImpl.kt`

Implements `DefaultModelRepositoryPort`. Injects `DefaultModelsDao` + `ApiModelsDao` + `ModelsDao`. When building `DefaultModelAssignment`, joins with either `ApiModelsDao.getById()` or `ModelsDao.getModelEntity()` depending on `source`.

---

#### Core Domain Layer (`core/domain`)

##### [NEW] `ApiProvider.kt`
`core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/ApiProvider.kt`

```kotlin
enum class ApiProvider(val displayName: String) {
    ANTHROPIC("Anthropic"),
    OPENAI("OpenAI"),
    GOOGLE("Google");
}
```

##### [NEW] `ModelSource.kt`
`core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/inference/ModelSource.kt`

```kotlin
enum class ModelSource {
    ON_DEVICE,
    API
}
```

##### [NEW] `ApiModelConfig.kt`
`core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/ApiModelConfig.kt`

```kotlin
/**
 * Domain model for a configured API model. Pure Kotlin — no framework deps.
 * API keys are NEVER stored here — managed exclusively by ApiKeyManager in :data.
 */
data class ApiModelConfig(
    val id: Long = 0,
    val displayName: String,
    val provider: ApiProvider,
    val modelId: String,
    val baseUrl: String? = null,
    val isReasoning: Boolean = false,
    val isVision: Boolean = false,
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
    val topP: Double = 0.95,
)
```

##### [NEW] `DefaultModelAssignment.kt`
`core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/DefaultModelAssignment.kt`

```kotlin
data class DefaultModelAssignment(
    val modelType: ModelType,
    val source: ModelSource,
    val apiModelConfig: ApiModelConfig? = null,   // Set when source = API
    val onDeviceDisplayName: String? = null,       // Set when source = ON_DEVICE
)
```

##### [NEW] `ApiModelRepositoryPort.kt`
`core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/repository/ApiModelRepositoryPort.kt`

```kotlin
interface ApiModelRepositoryPort {
    fun observeAll(): Flow<List<ApiModelConfig>>
    suspend fun getAll(): List<ApiModelConfig>
    suspend fun getById(id: Long): ApiModelConfig?
    suspend fun save(config: ApiModelConfig, apiKey: String): Long
    suspend fun delete(id: Long)
}
```

##### [NEW] `DefaultModelRepositoryPort.kt`
`core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/repository/DefaultModelRepositoryPort.kt`

```kotlin
interface DefaultModelRepositoryPort {
    suspend fun getDefault(modelType: ModelType): DefaultModelAssignment?
    fun observeDefaults(): Flow<List<DefaultModelAssignment>>
    suspend fun setDefault(modelType: ModelType, source: ModelSource, apiModelId: Long? = null)
    suspend fun clearDefault(modelType: ModelType)
}
```

##### [NEW] `InferenceFactoryPort.kt`
`core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/inference/InferenceFactoryPort.kt`

```kotlin
/**
 * Resolves the correct LlmInferencePort for a given ModelType at runtime.
 * Replaces static @FastModelEngine/@ThinkingModelEngine qualifier injection.
 */
interface InferenceFactoryPort {
    suspend fun getInferenceService(modelType: ModelType): LlmInferencePort
}
```

##### [NEW] Use Cases

| Use Case | File | Purpose |
|---|---|---|
| `SaveApiModelUseCase` | `domain/usecase/byok/SaveApiModelUseCase.kt` | Validates input (non-blank name, model ID, key) + delegates to `ApiModelRepositoryPort.save()` |
| `DeleteApiModelUseCase` | `domain/usecase/byok/DeleteApiModelUseCase.kt` | Deletes from `ApiModelRepositoryPort`, then calls `DefaultModelsDao.resetAssignmentsForApiModel()` to revert any defaults referencing it to `ON_DEVICE` |
| `GetApiModelsUseCase` | `domain/usecase/byok/GetApiModelsUseCase.kt` | Returns `ApiModelRepositoryPort.observeAll()` |
| `SetDefaultModelUseCase` | `domain/usecase/byok/SetDefaultModelUseCase.kt` | Updates `DefaultModelRepositoryPort.setDefault()` |
| `GetDefaultModelsUseCase` | `domain/usecase/byok/GetDefaultModelsUseCase.kt` | Returns `DefaultModelRepositoryPort.observeDefaults()` |

##### [MODIFY] `GenerateChatResponseUseCase.kt`

Remove `@FastModelEngine` and `@ThinkingModelEngine` qualifier injections. Replace with `InferenceFactoryPort`:

```diff
 class GenerateChatResponseUseCase @Inject constructor(
-    @param:FastModelEngine private val fastModelService: LlmInferencePort,
-    @param:ThinkingModelEngine private val thinkingModelService: LlmInferencePort,
+    private val inferenceFactory: InferenceFactoryPort,
     private val pipelineExecutor: PipelineExecutorPort,
     ...
 ) {
     // In invoke():
     val baseFlow: Flow<MessageGenerationState> = when (mode) {
-        Mode.FAST -> generateWithService(prompt, ..., fastModelService, ModelType.FAST)
+        Mode.FAST -> {
+            val service = inferenceFactory.getInferenceService(ModelType.FAST)
+            generateWithService(prompt, ..., service, ModelType.FAST)
+        }
-        Mode.THINKING -> generateWithService(prompt, ..., thinkingModelService, ModelType.THINKING)
+        Mode.THINKING -> {
+            val service = inferenceFactory.getInferenceService(ModelType.THINKING)
+            generateWithService(prompt, ..., service, ModelType.THINKING)
+        }
         Mode.CREW -> pipelineExecutor.executePipeline(...)
     }
 }
```

---

### Phase 2: InferenceFactoryPort Stub

---

##### [NEW] `InferenceFactoryImpl.kt`
`feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/InferenceFactoryImpl.kt`

**Stub — always returns on-device engine.** Future ticket adds API branch.

```kotlin
class InferenceFactoryImpl @Inject constructor(
    @FastModelEngine private val fastOnDevice: LlmInferencePort,
    @ThinkingModelEngine private val thinkingOnDevice: LlmInferencePort,
    @MainModelEngine private val mainOnDevice: LlmInferencePort,
    @DraftOneModelEngine private val draftOneOnDevice: LlmInferencePort,
    @DraftTwoModelEngine private val draftTwoOnDevice: LlmInferencePort,
    @FinalSynthesizerModelEngine private val finalSynthOnDevice: LlmInferencePort,
    @VisionModelEngine private val visionOnDevice: LlmInferencePort,
    private val loggingPort: LoggingPort,
) : InferenceFactoryPort {

    companion object { private const val TAG = "InferenceFactory" }

    override suspend fun getInferenceService(modelType: ModelType): LlmInferencePort {
        // STUB: Always returns on-device. Future ticket checks DefaultModelRepositoryPort
        // and constructs ApiInferenceServiceImpl when source == API.
        loggingPort.debug(TAG, "Resolving $modelType → ON_DEVICE (stub)")
        return when (modelType) {
            ModelType.FAST -> fastOnDevice
            ModelType.THINKING -> thinkingOnDevice
            ModelType.MAIN -> mainOnDevice
            ModelType.DRAFT_ONE -> draftOneOnDevice
            ModelType.DRAFT_TWO -> draftTwoOnDevice
            ModelType.FINAL_SYNTHESIS -> finalSynthOnDevice
            ModelType.VISION -> visionOnDevice
        }
    }
}
```

**`EngineModule.kt` is completely unchanged.** The existing `@Provides` methods continue to create on-device engines. This stub just wraps them.

##### [NEW] DI Binding Module

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class InferenceFactoryModule {
    @Binds
    @Singleton
    abstract fun bindInferenceFactory(impl: InferenceFactoryImpl): InferenceFactoryPort
}
```

---

### Phase 3: Settings UI (S-Tier Quality)

---

#### Feature Settings (`feature/settings`)

##### [MODIFY] `SettingsScreen.kt` — Add BYOK Setup Row

New row in the `Configuration` section, below "Model Configuration":

```kotlin
item { SectionHeader("Configuration") }
item {
    SettingsClickableRow(
        icon = Icons.Default.Settings,
        title = "Model Configuration",
        onClick = { onShowModelConfigSheet(true) }
    )
}
item {
    SettingsClickableRow(
        icon = Icons.Default.VpnKey,
        title = "BYOK Setup",
        onClick = { onShowByokSheet(true) }
    )
}
```

##### [NEW] BYOK Bottom Sheet — `ByokBottomSheet` composable

Full-height `ModalBottomSheet(skipPartiallyExpanded = true)`. Two-state navigation:

**State 1 — List View:**
```
┌─────────────────────────────────────────────────────┐
│  API Model Configurations                           │
├─────────────────────────────────────────────────────┤
│  ┌─ Info Card ─────────────────────────────────┐    │
│  │ ℹ  Configured API models can be set as      │    │
│  │    your default in Model Configuration.     │    │
│  └─────────────────────────────────────────────┘    │
│                                                     │
│  ┌─ Card ──────────────────────────────────────┐    │
│  │  [A] My Claude Sonnet              ▸        │    │
│  │      claude-sonnet-4 · Anthropic            │    │
│  └─────────────────────────────────────────────┘    │
│  ┌─ Card ──────────────────────────────────────┐    │
│  │  [G] Gemini Flash                   ▸        │    │
│  │      gemini-2.5-flash · Google              │    │
│  └─────────────────────────────────────────────┘    │
│                                                     │
│  ┌─ Empty State (if no models) ────────────────┐    │
│  │  No API models configured yet.              │    │
│  │  Tap "Add New" to get started.              │    │
│  └─────────────────────────────────────────────┘    │
│                                                     │
│           [ ＋ Add New ]  (FilledTonalButton)       │
└─────────────────────────────────────────────────────┘
```

- Each card: `surfaceVariant` background, `RoundedCornerShape(12.dp)`, provider icon (first letter in a `primaryContainer` circle), display name (bodyLarge), model ID + provider as subtitle (bodySmall, onSurfaceVariant)
- Swipe-to-delete with `SwipeToDismissBox` + red background + trash icon + confirmation dialog
- Tap card → navigate to edit view (state 2) with data pre-filled

**State 2 — Add/Edit View:**
```
┌─────────────────────────────────────────────────────┐
│  ◁  New API Model                       [Save]      │
├─────────────────────────────────────────────────────┤
│                                                     │
│  Provider                                           │
│  ┌─ Dropdown ──────────────────────────────────┐    │
│  │  Anthropic                            ▾     │    │
│  └─────────────────────────────────────────────┘    │
│                                                     │
│  Display Name                                       │
│  ┌─────────────────────────────────────────────┐    │
│  │  My Claude Sonnet                           │    │
│  └─────────────────────────────────────────────┘    │
│                                                     │
│  API Key                                            │
│  ┌─────────────────────────────────────────────┐    │
│  │  ••••••••••••••••••••                       │    │
│  └─────────────────────────────────────────────┘    │
│                                                     │
│  Model ID                                           │
│  ┌─────────────────────────────────────────────┐    │
│  │  claude-sonnet-4-20250514                   │    │
│  └─────────────────────────────────────────────┘    │
│  hint: "e.g. claude-sonnet-4-20250514"             │
│                                                     │
│  Base URL (Optional)                                │
│  ┌─────────────────────────────────────────────┐    │
│  │                                             │    │
│  └─────────────────────────────────────────────┘    │
│  hint: "Leave blank for default endpoint"          │
│                                                     │
│  ── Capabilities ──────────────────────────────     │
│  Reasoning capable                    [  toggle  ]  │
│  Vision capable                       [  toggle  ]  │
│                                                     │
│  ── Tunings ───────────── ▸ (expandable) ──────     │
│  Temperature: 0.70        ═══════●═══════           │
│  Top P: 0.95              ═════════════●═           │
│  Max Tokens               [ 4096         ]          │
│                                                     │
│       [ Save ]  (Button, full width)                │
└─────────────────────────────────────────────────────┘
```

- **Provider dropdown**: Changes hint text on Model ID field dynamically
- **API Key**: `PasswordVisualTransformation`, with trailing eye toggle for reveal
- **Base URL**: Only shown for Anthropic and OpenAI (Google uses fixed endpoint); hidden via `AnimatedVisibility`
- **Capabilities toggles**: M3 `Switch` components
- **Tunings section**: Collapsed by default with expandable header (`AnimatedVisibility`), matches the slider pattern from existing Model Config sheet
- **Validation**: Save button disabled until display name, API key, and model ID are all non-blank. Provider-specific hint text updates on provider change.
- **All text fields**: `RoundedCornerShape(12.dp)` to match existing app style

##### [MODIFY] Model Configuration Bottom Sheet — Display Name Refactor

**List view changes:**
- Currently: shows `config.displayName` (e.g., "Overclocked Truth Nuke")
- New: shows the real model name (e.g., "Gemma 3n E2B") from the updated `model_config.json`
- BYOK models also appear in this list with a provider badge chip (e.g., pill-shaped `SuggestionChip` with "Anthropic" in `tertiaryContainer` color)

**Detail view changes:**
- Display name shown as a **read-only `Text` label** at the top — not an editable `OutlinedTextField`
- Existing tuning sliders (temperature, top K, top P) remain editable for on-device models
- For BYOK entries in the list, tapping navigates to the BYOK edit view instead of the on-device config view

**Default assignment integration:**
- Below the model list, a new section "Default Assignments" shows each model type slot (Fast, Thinking, Draft One, etc.)
- Each slot displays its current assignment (on-device model name or API model name + provider)
- Tapping a slot opens a selection list of all available models (on-device + configured API models)
- Selection persists via `SetDefaultModelUseCase`

##### [MODIFY] `SettingsModels.kt`

```kotlin
data class SettingsUiState(
    // ... all existing fields unchanged ...

    // BYOK Sheet
    val showByokSheet: Boolean = false,
    val apiModels: List<ApiModelConfigUi> = emptyList(),
    val selectedApiModel: ApiModelConfigUi? = null,
    val isEditingApiModel: Boolean = false,

    // Default model assignments (for Model Config sheet)
    val defaultAssignments: List<DefaultModelAssignmentUi> = emptyList(),
)

data class ApiModelConfigUi(
    val id: Long = 0,
    val displayName: String = "",
    val provider: ApiProvider = ApiProvider.ANTHROPIC,
    val modelId: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",       // Only populated during editing, never persisted in UI state after save
    val isReasoning: Boolean = false,
    val isVision: Boolean = false,
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
    val topP: Double = 0.95,
)

data class DefaultModelAssignmentUi(
    val modelType: ModelType,
    val source: ModelSource,
    val currentModelName: String,     // Whichever model is currently assigned
    val providerName: String? = null, // Non-null when source = API
)
```

##### [MODIFY] `SettingsViewModel.kt`

- Inject: `SaveApiModelUseCase`, `DeleteApiModelUseCase`, `GetApiModelsUseCase`, `SetDefaultModelUseCase`, `GetDefaultModelsUseCase`
- Expand `TransientState` with BYOK fields
- Add to `combine()`: `getApiModelsUseCase()`, `getDefaultModelsUseCase()`
- New methods: `onShowByokSheet()`, `onSelectApiModel()`, `onSaveApiModel()`, `onDeleteApiModel()`, `onApiModelFieldChange()`, `onSetDefaultModel()`, `onBackToByokList()`

##### [MODIFY] `SettingsRoute.kt`

Pass all new BYOK and default assignment callbacks through to `SettingsScreen`.

---

### Phase 4: model_config.json Cleanup

---

##### [MODIFY] `model_config.json`

**Display name replacements:**

| Current (persona alias) | New (real model name) |
|---|---|
| "Iron Iris" | "Gemma 3n E2B" |
| "Machiavellian Mindstorm" | "LFM 2.5 1.2B Thinking" |
| "Insight Inferno" | "Qwen 3.5 2B" |
| "Overpowered Oracle" | "Jamba Reasoning 3B" |
| "The Last Word" | "Qwen 3.5 4B" |
| "Overclocked Truth Nuke" | "Gemma 3n E2B" |
| "The Synaptic Scalpel" | "LFM 2.5 1.2B Thinking" |
| "Straight to Prod" | "Qwen 2.5 Coder 1.5B" |

**System prompt changes** — remove self-referential names, preserve persona behavior:

| Before | After |
|---|---|
| `"You are Iron Iris, a precise visual analyst..."` | `"You are a precise visual analyst..."` |
| `"You are Machiavellian Mindstorm, the analytical drafter..."` | `"You are the analytical drafter..."` |
| `"You are Insight Inferno, the creative-analytical..."` | `"You are the creative-analytical thinking drafter..."` |
| `"You are Overpowered Oracle, a decisive synthesizer..."` | `"You are a decisive synthesizer..."` |
| `"...reviewer named Last Word who..."` | `"...critical reviewer who..."` |
| `"# Role: Overclocked Truth Nuke\nYou are a sharp-witted..."` | `"You are a sharp-witted, brutally honest..."` |
| `"# Role: The Synaptic Scalpel\nYou are a high-speed..."` | `"You are a high-speed reasoning engine..."` |
| `"You are Straight to Prod, a coding specialist..."` | `"You are a coding specialist..."` |

---

## Architecture Summary

| Decision | Rationale |
|---|---|
| Separate `api_models` table | `ModelEntity` is coupled to on-device fields (sha256, sizeInBytes, etc.). Clean separation; `DefaultModelEntity` is the union. |
| API keys in `EncryptedSharedPreferences` | Hardware-backed Keystore encryption. Room never holds secrets. Backups are safe. |
| `InferenceFactoryPort` wraps existing qualifiers | Zero changes to `EngineModule.kt`. Stub is trivial. Future ticket adds API branch with `DefaultModelRepositoryPort` check. |
| No FK from `DefaultModelEntity` → `ModelEntity` | `ModelEntity` has composite PK. `modelType` is the implicit join. |
| Display name → read-only label | Prevents user confusion when name doesn't match the model. Config JSON provides canonical name. |
| `ModelType.CODE` deferred | Future ticket. `model_config.json` entry cleaned up but not wired. |

---

## Verification Plan

### Automated Tests

```bash
./gradlew :core:domain:assemble           # Domain purity
./gradlew :core:data:kspDebugKotlin       # Room schema generation
./gradlew :feature:inference:assemble     # InferenceFactoryImpl compiles
./gradlew assembleDebug                   # Full build
./gradlew testDebugUnitTest               # All unit tests
./gradlew ktlintCheck                     # Code style
./gradlew detekt                          # Static analysis
```

### Unit Tests to Write

| Test | Verifies |
|---|---|
| `InferenceFactoryImplTest` | Stub returns correct on-device engine for every `ModelType` |
| `GenerateChatResponseUseCaseTest` | Works with `InferenceFactoryPort` (behavioral parity with old qualifier approach) |
| `SaveApiModelUseCaseTest` | Validates input, rejects blank fields, delegates to repository |
| `DeleteApiModelUseCaseTest` | Deletes model + resets default assignments referencing it |
| `DefaultModelRepositoryImplTest` | CRUD, FK cascade on API model delete, correct join logic |
| `ApiModelRepositoryImplTest` | Entity ↔ domain mapping, key delegation to `ApiKeyManager` |
| `Migration_1_2_Test` | Room migration creates tables, seeds defaults, preserves existing data |

### Manual Verification

1. Settings → BYOK Setup → verify empty state with guidance text
2. Add New → fill all fields → Save → verify appears in list
3. Tap existing entry → verify fields pre-populated → edit → Save → verify updates
4. Swipe-to-delete → confirm → verify removed from list
5. Settings → Model Configuration → verify real model names (not persona aliases)
6. Model Configuration → verify BYOK models appear with provider badge
7. Set BYOK model as default for Fast → verify assignment label updates
8. Delete a BYOK model → verify Fast reverts to on-device
9. Send a message in Fast mode → verify on-device model still works (stub)
10. Verify model_config.json changes display correctly after fresh install
