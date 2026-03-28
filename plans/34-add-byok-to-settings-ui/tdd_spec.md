# Test Specification: BYOK Settings UI + InferenceFactoryPort Stub

> **Source:** `plans/34-add-byok-to-settings-ui/discovery_and_spec.md`
> **Testing Stack:** JUnit Jupiter 5, MockK, kotlinx-coroutines-test
> **No Mockito.** Fakes preferred for port implementations; MockK for collaborator verification.

---

## 1. Test Plan Overview

| Test Class | Module | Layer | Purpose |
|---|---|---|---|
| `SaveApiModelUseCaseTest` | `:core:domain` | Domain | Input validation + delegation to `ApiModelRepositoryPort` |
| `DeleteApiModelUseCaseTest` | `:core:domain` | Domain | Delete model + cascade reset of default assignments |
| `GetApiModelsUseCaseTest` | `:core:domain` | Domain | Passthrough observeAll from repository |
| `SetDefaultModelUseCaseTest` | `:core:domain` | Domain | Set model type → source + apiModelId mapping |
| `GetDefaultModelsUseCaseTest` | `:core:domain` | Domain | Passthrough observeDefaults from repository |
| `InferenceFactoryImplTest` | `:feature:inference` | Infra | Stub returns correct on-device engine for every `ModelType` |
| `GenerateChatResponseUseCaseRefactorTest` | `:core:domain` | Domain | Behavioral parity after InferenceFactoryPort swap |
| `ApiModelRepositoryImplTest` | `:core:data` | Data | Entity ↔ domain mapping, key delegation to `ApiKeyManager` |
| `DefaultModelRepositoryImplTest` | `:core:data` | Data | CRUD, FK cascade, correct join logic |
| `ApiKeyManagerTest` | `:core:data` | Data | EncryptedSharedPreferences save/get/delete |
| `ApiModelConfigTest` | `:core:domain` | Domain | Domain model default values and exhaustive tuning fields |

---

## 2. Fake Definitions

### `FakeApiModelRepository` (`:core:domain` test)

```kotlin
class FakeApiModelRepository : ApiModelRepositoryPort {
    private val _models = MutableStateFlow<List<ApiModelConfig>>(emptyList())
    private var nextId = 1L
    var savedKeys = mutableMapOf<Long, String>()  // Track keys for assertion

    override fun observeAll(): Flow<List<ApiModelConfig>> = _models
    override suspend fun getAll(): List<ApiModelConfig> = _models.value
    override suspend fun getById(id: Long): ApiModelConfig? = _models.value.find { it.id == id }

    override suspend fun save(config: ApiModelConfig, apiKey: String): Long {
        val id = if (config.id == 0L) nextId++ else config.id
        val saved = config.copy(id = id)
        _models.value = _models.value.filter { it.id != id } + saved
        savedKeys[id] = apiKey
        return id
    }

    override suspend fun delete(id: Long) {
        _models.value = _models.value.filter { it.id != id }
        savedKeys.remove(id)
    }
}
```

### `FakeDefaultModelRepository` (`:core:domain` test)

```kotlin
class FakeDefaultModelRepository : DefaultModelRepositoryPort {
    private val _defaults = MutableStateFlow<List<DefaultModelAssignment>>(emptyList())
    var lastSetCall: Triple<ModelType, ModelSource, Long?>? = null

    override suspend fun getDefault(modelType: ModelType): DefaultModelAssignment? =
        _defaults.value.find { it.modelType == modelType }

    override fun observeDefaults(): Flow<List<DefaultModelAssignment>> = _defaults

    override suspend fun setDefault(modelType: ModelType, source: ModelSource, apiModelId: Long?) {
        lastSetCall = Triple(modelType, source, apiModelId)
        val assignment = DefaultModelAssignment(modelType, source)
        _defaults.value = _defaults.value.filter { it.modelType != modelType } + assignment
    }

    override suspend fun clearDefault(modelType: ModelType) {
        _defaults.value = _defaults.value.filter { it.modelType != modelType }
    }

    fun seed(assignments: List<DefaultModelAssignment>) {
        _defaults.value = assignments
    }
}
```

### `FakeInferenceFactory` (`:core:domain` test)

```kotlin
class FakeInferenceFactory : InferenceFactoryPort {
    val resolvedTypes = mutableListOf<ModelType>()
    var serviceToReturn: LlmInferencePort = mockk(relaxed = true)

    override suspend fun getInferenceService(modelType: ModelType): LlmInferencePort {
        resolvedTypes.add(modelType)
        return serviceToReturn
    }
}
```

---

## 3. Happy Path Scenarios

---

### `SaveApiModelUseCaseTest`

**Scenario: Save a valid Anthropic API model**
- **Given:** A `SaveApiModelUseCase` with a `FakeApiModelRepository`
- **When:** Invoked with `displayName = "My Claude"`, `provider = ANTHROPIC`, `modelId = "claude-sonnet-4-20250514"`, `apiKey = "sk-ant-xxxx"`, `maxTokens = 8192`, `temperature = 0.8`, `topP = 0.9`, `topK = 40`, `frequencyPenalty = 0.0`, `presencePenalty = 0.0`
- **Then:** `repository.getAll()` returns exactly 1 model with matching fields; `repository.savedKeys[1]` equals `"sk-ant-xxxx"`; returned ID is `1L`

**Scenario: Save a valid OpenAI API model with all tuning parameters populated**
- **Given:** A `SaveApiModelUseCase` with a `FakeApiModelRepository`
- **When:** Invoked with `displayName = "GPT-4o"`, `provider = OPENAI`, `modelId = "gpt-4o"`, `apiKey = "sk-xxxx"`, `maxTokens = 16384`, `temperature = 0.3`, `topP = 1.0`, `topK = null`, `frequencyPenalty = 0.5`, `presencePenalty = 0.3`, `baseUrl = "https://api.custom.com/v1"`
- **Then:** Saved model has `frequencyPenalty = 0.5`, `presencePenalty = 0.3`, `baseUrl = "https://api.custom.com/v1"`

**Scenario: Save a valid Google API model**
- **Given:** A `SaveApiModelUseCase` with a `FakeApiModelRepository`
- **When:** Invoked with `displayName = "Gemini Flash"`, `provider = GOOGLE`, `modelId = "gemini-2.5-flash"`, `apiKey = "AIza-xxxx"`, `topK = 40`, `topP = 0.95`, `temperature = 1.0`
- **Then:** Saved model has `topK = 40`, `provider = GOOGLE`, `baseUrl = null` (Google uses fixed endpoint)

**Scenario: Update an existing API model**
- **Given:** A model with `id = 1` already exists in repository
- **When:** `save()` invoked with `id = 1`, updated `displayName = "Claude Renamed"`, new `apiKey = "sk-ant-new"`
- **Then:** `repository.getAll()` still returns exactly 1 model; `displayName` is `"Claude Renamed"`; saved key is updated

**Scenario: Save model preserves default tuning values when not specified**
- **Given:** A `SaveApiModelUseCase`
- **When:** Invoked with only required fields (displayName, provider, modelId, apiKey) — no tuning overrides
- **Then:** Model defaults: `maxTokens = 4096`, `temperature = 0.7`, `topP = 0.95`, `topK = null`, `frequencyPenalty = 0.0`, `presencePenalty = 0.0`, `stopSequences = emptyList()`

---

### `DeleteApiModelUseCaseTest`

**Scenario: Delete an existing API model**
- **Given:** A model with `id = 1` exists in `FakeApiModelRepository`
- **When:** `delete(1L)` invoked
- **Then:** `repository.getAll()` returns empty list

**Scenario: Delete a model that is a default assignment resets the assignment to ON_DEVICE**
- **Given:** Model `id = 1` exists; default assignment for `FAST` is `source = API, apiModelId = 1`
- **When:** `delete(1L)` invoked
- **Then:** Default assignment for `FAST` has `source = ON_DEVICE`, `apiModelId = null`

**Scenario: Delete a model that is assigned to multiple slots resets all of them**
- **Given:** Model `id = 1` is default for both `FAST` and `THINKING`
- **When:** `delete(1L)` invoked
- **Then:** Both `FAST` and `THINKING` assignments revert to `ON_DEVICE`

---

### `GetApiModelsUseCaseTest`

**Scenario: Returns empty flow when no models configured**
- **Given:** Empty `FakeApiModelRepository`
- **When:** `observeAll()` collected
- **Then:** Emits `emptyList()`

**Scenario: Returns all configured models**
- **Given:** 3 models saved (Anthropic, OpenAI, Google)
- **When:** `observeAll()` collected
- **Then:** Emits list of size 3 with correct providers

---

### `SetDefaultModelUseCaseTest`

**Scenario: Set FAST slot to API source**
- **Given:** `FakeDefaultModelRepository` with all slots seeded as `ON_DEVICE`
- **When:** `setDefault(ModelType.FAST, ModelSource.API, apiModelId = 5L)`
- **Then:** `repository.lastSetCall` equals `Triple(FAST, API, 5L)`

**Scenario: Set THINKING slot back to ON_DEVICE**
- **Given:** `THINKING` is currently `API`
- **When:** `setDefault(ModelType.THINKING, ModelSource.ON_DEVICE)`
- **Then:** `repository.lastSetCall` equals `Triple(THINKING, ON_DEVICE, null)`

**Scenario: Set default for every ModelType variant**
- **Given:** `FakeDefaultModelRepository`
- **When:** `setDefault()` called for each of `FAST`, `THINKING`, `MAIN`, `DRAFT_ONE`, `DRAFT_TWO`, `FINAL_SYNTHESIS`, `VISION`
- **Then:** All 7 calls succeed without exception; each `lastSetCall` has correct `ModelType`

---

### `GetDefaultModelsUseCaseTest`

**Scenario: Returns seeded defaults**
- **Given:** Repository seeded with all 7 `ModelType` slots as `ON_DEVICE`
- **When:** `observeDefaults()` collected
- **Then:** Emits list of size 7; all have `source = ON_DEVICE`

---

### `InferenceFactoryImplTest`

**Scenario: Returns correct on-device engine for FAST**
- **Given:** `InferenceFactoryImpl` injected with distinct mock `LlmInferencePort` per qualifier
- **When:** `getInferenceService(ModelType.FAST)`
- **Then:** Returns the `@FastModelEngine` instance (identity check with `assertSame`)

**Scenario: Returns correct on-device engine for THINKING**
- **Given:** Same setup
- **When:** `getInferenceService(ModelType.THINKING)`
- **Then:** Returns the `@ThinkingModelEngine` instance

**Scenario: Returns correct on-device engine for every ModelType**
- **Given:** 7 distinct mock `LlmInferencePort` instances, one per qualifier
- **When:** `getInferenceService()` called for each of: `FAST`, `THINKING`, `MAIN`, `DRAFT_ONE`, `DRAFT_TWO`, `FINAL_SYNTHESIS`, `VISION`
- **Then:** Each returns the correct engine (identity/reference equality); no two calls return the same instance (unless they share the same qualifier, which none do in this setup)

---

### `GenerateChatResponseUseCaseRefactorTest`

**Scenario: FAST mode routes through InferenceFactoryPort**
- **Given:** `FakeInferenceFactory` configured with a mock `LlmInferencePort` that emits `[PartialResponse("Hello"), Finished]`
- **When:** Use case invoked with `mode = Mode.FAST`
- **Then:** `FakeInferenceFactory.resolvedTypes` contains exactly `[ModelType.FAST]`; final accumulated content is `"Hello"`

**Scenario: THINKING mode routes through InferenceFactoryPort**
- **Given:** Same `FakeInferenceFactory` setup
- **When:** Use case invoked with `mode = Mode.THINKING`
- **Then:** `FakeInferenceFactory.resolvedTypes` contains exactly `[ModelType.THINKING]`

**Scenario: CREW mode still delegates to PipelineExecutorPort (not InferenceFactoryPort)**
- **Given:** `FakeInferenceFactory` + `FakePipelineExecutor`
- **When:** Use case invoked with `mode = Mode.CREW`
- **Then:** `FakeInferenceFactory.resolvedTypes` is empty; pipeline executor was invoked

---

### `ApiModelRepositoryImplTest`

**Scenario: Save persists entity to DAO and key to ApiKeyManager**
- **Given:** MockK `ApiModelsDao`, MockK `ApiKeyManager`
- **When:** `save(config, "sk-test")`
- **Then:** `coVerify { dao.upsert(any()) }` called; `verify { apiKeyManager.save(any(), "sk-test") }` called

**Scenario: Delete removes entity from DAO and key from ApiKeyManager**
- **Given:** MockK `ApiModelsDao`, MockK `ApiKeyManager`
- **When:** `delete(1L)`
- **Then:** `coVerify { dao.deleteById(1L) }` called; `verify { apiKeyManager.delete(1L) }` called

**Scenario: Entity-to-domain mapping preserves all tuning fields**
- **Given:** `ApiModelEntity` with all fields populated (including `topK`, `frequencyPenalty`, `presencePenalty`, `stopSequences`)
- **When:** Mapped to `ApiModelConfig`
- **Then:** All fields match exactly: `temperature`, `topP`, `topK`, `maxTokens`, `frequencyPenalty`, `presencePenalty`, `stopSequences`, `isReasoning`, `isVision`, `baseUrl`

**Scenario: ObserveAll emits domain models**
- **Given:** DAO's `observeAll()` returns flow with 2 entities
- **When:** Repository's `observeAll()` collected
- **Then:** Emits list of 2 `ApiModelConfig` with correct field mapping

---

### `DefaultModelRepositoryImplTest`

**Scenario: getDefault returns ON_DEVICE assignment with display name**
- **Given:** `DefaultModelEntity(FAST, ON_DEVICE, null)` in DAO; `ModelsDao` returns model with `displayName = "Gemma 3n E2B"` for `FAST`
- **When:** `getDefault(ModelType.FAST)`
- **Then:** Returns `DefaultModelAssignment(FAST, ON_DEVICE, null, "Gemma 3n E2B")`

**Scenario: getDefault returns API assignment with config**
- **Given:** `DefaultModelEntity(FAST, API, apiModelId = 5)` in DAO; `ApiModelsDao.getById(5)` returns entity
- **When:** `getDefault(ModelType.FAST)`
- **Then:** Returns `DefaultModelAssignment(FAST, API, apiModelConfig = <mapped config>, null)`

**Scenario: setDefault persists entity**
- **Given:** MockK `DefaultModelsDao`
- **When:** `setDefault(ModelType.THINKING, ModelSource.API, 3L)`
- **Then:** `coVerify { dao.upsert(DefaultModelEntity(THINKING, API, 3L, any())) }`

**Scenario: resetAssignmentsForApiModel cascades on delete**
- **Given:** MockK `DefaultModelsDao`
- **When:** Called with `apiModelId = 5L`
- **Then:** `coVerify { dao.resetAssignmentsForApiModel(5L) }` — sets all rows referencing `apiModelId = 5` back to `ON_DEVICE`

---

### `ApiKeyManagerTest`

**Scenario: Save and retrieve a key**
- **Given:** `ApiKeyManager` with real or mocked `EncryptedSharedPreferences`
- **When:** `save(1L, "sk-ant-secret")`; then `get(1L)`
- **Then:** Returns `"sk-ant-secret"`

**Scenario: Delete removes the key**
- **Given:** Key saved for `apiModelId = 1`
- **When:** `delete(1L)`; then `get(1L)`
- **Then:** Returns `null`

**Scenario: Get returns null for non-existent key**
- **Given:** Empty store
- **When:** `get(999L)`
- **Then:** Returns `null`

---

### `ApiModelConfigTest`

**Scenario: Default values are correct for all tuning fields**
- **Given:** `ApiModelConfig(displayName = "Test", provider = ANTHROPIC, modelId = "test-model")`
- **When:** Inspected
- **Then:** `maxTokens == 4096`, `temperature == 0.7`, `topP == 0.95`, `topK == null`, `frequencyPenalty == 0.0`, `presencePenalty == 0.0`, `stopSequences == emptyList()`, `isReasoning == false`, `isVision == false`, `baseUrl == null`

---

## 4. Error Path & Edge Case Scenarios

---

### `SaveApiModelUseCaseTest` — Validation

**Scenario: Reject blank display name**
- **Given:** `SaveApiModelUseCase`
- **When:** Invoked with `displayName = ""` (other fields valid)
- **Then:** Throws `IllegalArgumentException` with message containing "display name"

**Scenario: Reject blank display name (whitespace-only)**
- **Given:** `SaveApiModelUseCase`
- **When:** Invoked with `displayName = "   "` (whitespace only)
- **Then:** Throws `IllegalArgumentException` with message containing "display name"

**Scenario: Reject blank API key**
- **Given:** `SaveApiModelUseCase`
- **When:** Invoked with `apiKey = ""`
- **Then:** Throws `IllegalArgumentException` with message containing "API key"

**Scenario: Reject blank model ID**
- **Given:** `SaveApiModelUseCase`
- **When:** Invoked with `modelId = ""`
- **Then:** Throws `IllegalArgumentException` with message containing "model ID"

**Scenario: Reject negative maxTokens**
- **Given:** `SaveApiModelUseCase`
- **When:** Invoked with `maxTokens = -1`
- **Then:** Throws `IllegalArgumentException` with message containing "max tokens"

**Scenario: Reject maxTokens of zero**
- **Given:** `SaveApiModelUseCase`
- **When:** Invoked with `maxTokens = 0`
- **Then:** Throws `IllegalArgumentException` with message containing "max tokens"

**Scenario: Reject temperature out of range (> 2.0)**
- **Given:** `SaveApiModelUseCase`
- **When:** Invoked with `temperature = 2.5`
- **Then:** Throws `IllegalArgumentException` with message containing "temperature"

**Scenario: Reject temperature out of range (< 0.0)**
- **Given:** `SaveApiModelUseCase`
- **When:** Invoked with `temperature = -0.1`
- **Then:** Throws `IllegalArgumentException` with message containing "temperature"

**Scenario: Reject topP out of range (> 1.0)**
- **Given:** `SaveApiModelUseCase`
- **When:** Invoked with `topP = 1.5`
- **Then:** Throws `IllegalArgumentException` with message containing "top_p"

**Scenario: Reject topP out of range (< 0.0)**
- **Given:** `SaveApiModelUseCase`
- **When:** Invoked with `topP = -0.1`
- **Then:** Throws `IllegalArgumentException` with message containing "top_p"

**Scenario: Reject frequencyPenalty out of range (> 2.0)**
- **Given:** `SaveApiModelUseCase`
- **When:** Invoked with `frequencyPenalty = 2.5`
- **Then:** Throws `IllegalArgumentException` with message containing "frequency penalty"

**Scenario: Reject frequencyPenalty out of range (< -2.0)**
- **Given:** `SaveApiModelUseCase`
- **When:** Invoked with `frequencyPenalty = -2.5`
- **Then:** Throws `IllegalArgumentException` with message containing "frequency penalty"

**Scenario: Reject presencePenalty out of range (> 2.0)**
- **Given:** `SaveApiModelUseCase`
- **When:** Invoked with `presencePenalty = 2.5`
- **Then:** Throws `IllegalArgumentException` with message containing "presence penalty"

**Scenario: Reject topK less than 1 when non-null**
- **Given:** `SaveApiModelUseCase`
- **When:** Invoked with `topK = 0`
- **Then:** Throws `IllegalArgumentException` with message containing "top_k"

**Scenario: Accept topK as null (not all providers use it)**
- **Given:** `SaveApiModelUseCase`
- **When:** Invoked with `topK = null` (and all other fields valid)
- **Then:** Saves successfully; stored config has `topK = null`

**Scenario: Accept boundary temperature = 0.0**
- **Given:** `SaveApiModelUseCase`
- **When:** Invoked with `temperature = 0.0`
- **Then:** Saves successfully

**Scenario: Accept boundary temperature = 2.0**
- **Given:** `SaveApiModelUseCase`
- **When:** Invoked with `temperature = 2.0`
- **Then:** Saves successfully

**Scenario: Accept maximum stopSequences count (5)**
- **Given:** `SaveApiModelUseCase`
- **When:** Invoked with `stopSequences = listOf("a", "b", "c", "d", "e")`
- **Then:** Saves successfully

**Scenario: Reject more than 5 stop sequences**
- **Given:** `SaveApiModelUseCase`
- **When:** Invoked with `stopSequences = listOf("a", "b", "c", "d", "e", "f")`
- **Then:** Throws `IllegalArgumentException` with message containing "stop sequences"

---

### `DeleteApiModelUseCaseTest` — Edge Cases

**Scenario: Delete non-existent model is a no-op**
- **Given:** Empty repository
- **When:** `delete(999L)` invoked
- **Then:** No exception thrown; repository remains empty

**Scenario: Delete model with no default assignments does not affect defaults**
- **Given:** Model `id = 1` exists; no default assignments reference it
- **When:** `delete(1L)` invoked
- **Then:** Default assignments remain unchanged

---

### `SetDefaultModelUseCaseTest` — Edge Cases

**Scenario: Set API default with null apiModelId throws**
- **Given:** `SetDefaultModelUseCase`
- **When:** `setDefault(ModelType.FAST, ModelSource.API, apiModelId = null)`
- **Then:** Throws `IllegalArgumentException` with message containing "API model ID"

**Scenario: Set ON_DEVICE default ignores apiModelId**
- **Given:** `SetDefaultModelUseCase`
- **When:** `setDefault(ModelType.FAST, ModelSource.ON_DEVICE, apiModelId = 99L)`
- **Then:** Saved with `apiModelId = null` (ignored)

---

### `InferenceFactoryImplTest` — Edge Cases

**Scenario: Multiple calls for same ModelType return same instance**
- **Given:** `InferenceFactoryImpl` with Lazy providers
- **When:** `getInferenceService(ModelType.FAST)` called twice
- **Then:** Both calls return the same `LlmInferencePort` instance (reference equality)

---

### `GenerateChatResponseUseCaseRefactorTest` — Behavioral Parity

**Scenario: InferenceFactoryPort failure propagates as error state**
- **Given:** `FakeInferenceFactory` whose `getInferenceService()` throws `RuntimeException("Engine unavailable")`
- **When:** Use case invoked with `mode = Mode.FAST`
- **Then:** Flow emits `AccumulatedMessages` with error content containing "Error"

---

### `ApiModelRepositoryImplTest` — Edge Cases

**Scenario: Save with id = 0 triggers insert (autoGenerate)**
- **Given:** `ApiModelConfig(id = 0, ...)`
- **When:** `save(config, "key")`
- **Then:** DAO's `upsert()` called with entity having `id = 0` (Room autoGenerates)

**Scenario: Mapping handles null baseUrl correctly**
- **Given:** Entity with `baseUrl = null`
- **When:** Mapped to domain
- **Then:** `ApiModelConfig.baseUrl` is `null`

**Scenario: Mapping handles null topK correctly**
- **Given:** Entity with `topK = null`
- **When:** Mapped to domain
- **Then:** `ApiModelConfig.topK` is `null`

---

## 5. Mutation Defense

---

### Lazy Implementation Risk: `InferenceFactoryImpl` ignoring `ModelType` and returning a hardcoded engine

A lazy implementation might always return the `fastOnDevice` engine regardless of the `ModelType` parameter, since the stub currently doesn't check default model assignments. All tests would pass if we only check that _a_ `LlmInferencePort` is returned.

**Defense Scenario: InferenceFactory does not return the same instance for distinct ModelTypes**
- **Given:** 7 distinct `LlmInferencePort` mocks, each with a unique toString (created separately)
- **When:** `getInferenceService()` called for all 7 `ModelType` values
- **Then:** All 7 returned instances are referentially distinct (no two `===` equal); specifically, `getInferenceService(ModelType.THINKING) !== getInferenceService(ModelType.FAST)`

---

### Lazy Implementation Risk: `SaveApiModelUseCase` passes validation by not actually validating

A lazy implementation might call `repository.save()` directly without validation, passing all happy-path tests.

**Defense Scenario: Blank API key with non-blank name and modelId must fail**
- **Given:** `SaveApiModelUseCase` with `FakeApiModelRepository`
- **When:** Invoked with `displayName = "Valid Name"`, `modelId = "valid-model"`, `apiKey = ""`
- **Then:** Throws `IllegalArgumentException`; `repository.getAll()` is still empty (save was never called)

---

### Lazy Implementation Risk: `DeleteApiModelUseCase` deletes from repo but forgets to cascade default assignments

A lazy implementation might only call `repository.delete()` without calling `defaultModelRepository.resetAssignmentsForApiModel()`.

**Defense Scenario: Delete cascades to default assignments**
- **Given:** Model `id = 1`; defaults: `FAST → (API, 1)`, `THINKING → (API, 1)`, `MAIN → (ON_DEVICE, null)`
- **When:** `delete(1L)`
- **Then:** After delete, `defaultRepository.getDefault(FAST)` has `source = ON_DEVICE`; `defaultRepository.getDefault(THINKING)` has `source = ON_DEVICE`; `defaultRepository.getDefault(MAIN)` is unchanged

---

### Lazy Implementation Risk: `GenerateChatResponseUseCase` still hardcodes service selection instead of using InferenceFactoryPort

A lazy refactor might keep the old `fastModelService`/`thinkingModelService` fields alongside the new `InferenceFactoryPort` and only route some modes through the factory.

**Defense Scenario: Removing old qualifier fields doesn't break FAST or THINKING modes**
- **Given:** `GenerateChatResponseUseCase` constructed with ONLY `InferenceFactoryPort` (no direct `fastModelService`/`thinkingModelService`)
- **When:** Invoked with `mode = Mode.FAST`, then `mode = Mode.THINKING`
- **Then:** Both succeed; `FakeInferenceFactory.resolvedTypes` contains `[FAST, THINKING]` in order; no `UninitializedPropertyAccessException` or similar

---

## 6. Exhaustive API Tuning Parameters Reference

The `ApiModelEntity` and `ApiModelConfig` domain model MUST include the following tuning fields to be exhaustive across Anthropic, OpenAI, and Google:

| Field | Type | Default | Range | Used By |
|---|---|---|---|---|
| `temperature` | `Double` | `0.7` | `0.0..2.0` | All three |
| `topP` | `Double` | `0.95` | `0.0..1.0` | All three |
| `topK` | `Int?` | `null` | `≥ 1` or null | Anthropic, Google |
| `maxTokens` | `Int` | `4096` | `≥ 1` | All three |
| `frequencyPenalty` | `Double` | `0.0` | `-2.0..2.0` | OpenAI |
| `presencePenalty` | `Double` | `0.0` | `-2.0..2.0` | OpenAI |
| `stopSequences` | `List<String>` | `emptyList()` | `max 5 items` | All three |

> **Note:** `topK` is nullable because OpenAI does not support it. `frequencyPenalty` and `presencePenalty` are OpenAI-specific but included for completeness — they default to `0.0` and have no effect on Anthropic/Google (enforced at the API request builder level in the future ticket).

### Fields deliberately excluded (future ticket scope):
- `stream` — always true (SSE streaming is the default inference pattern)
- `system` — managed by existing system prompt infrastructure
- `tools` / `tool_choice` — tool use deferred
- `candidateCount` — single response only
- `metadata` — no use case
- `stop` vs `stop_sequences` — normalized to `stopSequences` (request builder maps to provider-specific key name)

---

## 7. InferenceService Refactor Test Note

Per user comment, `InferenceService.kt` also needs its 4 `dagger.Lazy<LlmInferencePort>` qualifier injections (lines 85-98) replaced with a single `dagger.Lazy<InferenceFactoryPort>` injection. This is verified structurally (compilation) rather than via unit test since `InferenceService` is an Android `Service` that cannot be unit-tested without Robolectric. The compilation check in `./gradlew :feature:moa-pipeline-worker:assemble` covers this.

---

## 8. Test File Locations

| Test Class | File Path |
|---|---|
| `SaveApiModelUseCaseTest` | `core/domain/src/test/kotlin/.../domain/usecase/byok/SaveApiModelUseCaseTest.kt` |
| `DeleteApiModelUseCaseTest` | `core/domain/src/test/kotlin/.../domain/usecase/byok/DeleteApiModelUseCaseTest.kt` |
| `GetApiModelsUseCaseTest` | `core/domain/src/test/kotlin/.../domain/usecase/byok/GetApiModelsUseCaseTest.kt` |
| `SetDefaultModelUseCaseTest` | `core/domain/src/test/kotlin/.../domain/usecase/byok/SetDefaultModelUseCaseTest.kt` |
| `GetDefaultModelsUseCaseTest` | `core/domain/src/test/kotlin/.../domain/usecase/byok/GetDefaultModelsUseCaseTest.kt` |
| `InferenceFactoryImplTest` | `feature/inference/src/test/kotlin/.../feature/inference/InferenceFactoryImplTest.kt` |
| `GenerateChatResponseUseCaseRefactorTest` | `core/domain/src/test/kotlin/.../domain/usecase/chat/GenerateChatResponseUseCaseRefactorTest.kt` |
| `ApiModelRepositoryImplTest` | `core/data/src/test/kotlin/.../core/data/repository/ApiModelRepositoryImplTest.kt` |
| `DefaultModelRepositoryImplTest` | `core/data/src/test/kotlin/.../core/data/repository/DefaultModelRepositoryImplTest.kt` |
| `ApiKeyManagerTest` | `core/data/src/test/kotlin/.../core/data/security/ApiKeyManagerTest.kt` |
| `ApiModelConfigTest` | `core/domain/src/test/kotlin/.../domain/model/config/ApiModelConfigTest.kt` |
