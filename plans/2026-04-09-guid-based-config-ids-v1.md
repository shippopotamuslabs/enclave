# Plan: GUID-Based IDs for LocalModelConfigurationEntity

## Objective

Replace `id: Long` with `id: String` (GUID) for `LocalModelConfigurationEntity`. Generate a stable GUID on insert, use the `configId` from `model_config.json` as the authoritative ID. Since the app is unreleased, no migrations needed - just update all code.

*GUID Generation Code Sample*

```kotlin
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun generateId(): String {
    val myUuid = Uuid.random()
    return myUuid.toString()
}
```

---

## Implementation Plan

### Phase 1: Update Entity and DAOs

- [ ] **Task 1.1:** Update `LocalModelConfigurationEntity` - change `id: Long` to `id: String`, keep as Primary Key
- [ ] **Task 1.2:** Update `LocalModelConfigurationsDao` - change all query parameter types from `Long` to `String`
- [ ] **Task 1.3:** Update `DefaultModelEntity` - change `localConfigId: Long?` to `localConfigId: String?`
- [ ] **Task 1.4:** Update `DefaultModelsDao` - change query parameter types
- [ ] **Task 1.5:** Update `LocalModelRepositoryImpl` - handle String IDs, generate GUID when inserting new config
- [ ] **Task 1.6:** Update `DefaultModelRepositoryImpl` - handle String IDs
- [ ] **Task 1.7:** Update `SyncLocalModelRegistryUseCase` - use `configId` from model_config.json, remove fragile `findMatchingConfigId` logic

### Phase 2: Update Domain Models

- [ ] **Task 2.1:** Update `LocalModelConfiguration` domain model - change `id: Long` to `id: String`
- [ ] **Task 2.2:** Update `RemoteModelConfig` - add `configId: String` field
- [ ] **Task 2.3:** Update `LocalModelRepositoryPort` interface - String IDs
- [ ] **Task 2.4:** Update `DefaultModelRepositoryPort` interface - String IDs
- [ ] **Task 2.5:** Update `DefaultModelAssignment` domain model - String localConfigId/apiConfigId

### Phase 3: Update JSON Config and Fetcher

- [ ] **Task 3.1:** Add `configId` (GUID) to all entries in `model_config.json`
- [ ] **Task 3.2:** Update `ModelConfigFetcherImpl.parseModelConfig()` - parse configId from JSON
- [ ] **Task 3.3:** Update `ModelConfigFetcherImpl.toLocalModelAssets()` - propagate configId to LocalModelConfiguration

### Phase 4: Update All Consumers

- [ ] **Task 4.1:** Update all code that constructs `LocalModelConfiguration` - provide configId
- [ ] **Task 4.2:** Update all code that queries configs by ID
- [ ] **Task 4.3:** Update tests - FakeLocalModelRepository, FakeDefaultModelRepository, all test fixtures
- [ ] **Task 4.4:** Update `PocketCrewDatabase` schema export (version stays at 1)

---

## Key Design Decisions

### ID Generation Strategy
- When inserting from `model_config.json`: use the `configId` from JSON directly
- When user creates custom config: generate new GUID

### Matching Logic (Simplified)
`SyncLocalModelRegistryUseCase` now:
1. Looks up config by `configId` (from JSON)
2. If found: update it
3. If not found: insert with `configId` as the ID

No more fragile parameter matching.

### Foreign Key Updates
- `default_models.local_config_id` → `String?`
- `default_models.api_config_id` → `String?` (may also need GUID treatment for api configs - TBD)

---

## Files to Modify

### Entity Layer
```
core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/
  - LocalModelConfigurationEntity.kt      # id: String
  - LocalModelConfigurationsDao.kt        # String params
  - DefaultModelEntity.kt                # String config IDs
  - DefaultModelsDao.kt                   # String params
```

### Repository Layer
```
core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/
  - LocalModelRepositoryImpl.kt           # GUID generation, String IDs
  - DefaultModelRepositoryImpl.kt         # String IDs
```

### Domain Layer
```
core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/config/
  - LocalModelConfiguration.kt           # id: String
  - RemoteModelConfig.kt                 # configId: String
  - DefaultModelAssignment.kt            # String config IDs

core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/repository/
  - LocalModelRepositoryPort.kt         # String IDs
  - DefaultModelRepositoryPort.kt       # String IDs
```

### Use Cases
```
core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/modelconfig/
  - SyncLocalModelRegistryUseCase.kt     # Use configId for matching
  - SaveLocalModelConfigurationUseCase.kt
  - DeleteLocalModelConfigurationUseCase.kt

core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/byok/
  - SetDefaultModelUseCase.kt
  - GetDefaultModelsUseCase.kt
  - DeleteApiModelConfigurationUseCase.kt
```

### Config Fetcher
```
core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/download/
  - ModelConfigFetcherImpl.kt            # Parse configId
```

### Config
```
  - model_config.json                    # Add configId GUID to all entries
```

### Tests (Many files)
```
  - All test files that construct or mock configs with Long IDs
```

---

## Verification Criteria

- [ ] Build succeeds with no compile errors
- [ ] App starts without UNIQUE constraint error
- [ ] Remote config changes update existing configs (not create duplicates)
- [ ] All unit tests pass
- [ ] All integration tests pass

---

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Missing a consumer of the ID | Grep for `LocalModelConfiguration` and `configId` across codebase |
| Test failures due to hardcoded IDs | Update all test fixtures to use String IDs |
| Foreign key cascade issues | Verify FK relationships still work after type change |

---

## model_config.json Example

```json
{
  "fast": {
    "configId": "550e8400-e29b-41d4-a716-446655440001",
    "huggingFaceModelName": "litert-community/gemma-4-E4B-it-litert-lm",
    "displayName": "Gemma 4 E4B (Fast)",
    ...
  },
  "thinking": {
    "configId": "550e8400-e29b-41d4-a716-446655440002",
    ...
  }
}
```
