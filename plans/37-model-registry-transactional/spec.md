# Implementation Plan — 37-model-registry-transactional

## Objective
Ensure that `ModelRegistryImpl.setRegisteredModel` performs all database operations within a single transaction and automatically initializes a default model assignment for the given `ModelType` if one does not already exist. This ensures that the first model registered for a slot (e.g., after the initial application setup) is immediately usable as the default model.

## Acceptance Criteria
- [x] All database operations in `setRegisteredModel` occur within a Room transaction.
- [x] If no entry exists in the `default_models` table for the provided `ModelType`, a new entry is created with `source = ModelSource.ON_DEVICE`.
- [x] Existing logic for marking previous CURRENT models as OLD (if `markExistingAsOld` is true) is preserved and executed within the same transaction.
- [x] Internal cache in `ModelRegistryImpl` is updated only if the database transaction succeeds.
- [x] Unit tests verify the transactional behavior and the automatic default assignment.

## Architecture

### Files to Modify
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ModelRegistryImpl.kt`
- `core/testing/src/main/kotlin/com/browntowndev/pocketcrew/core/testing/FakeModelRegistry.kt`

## Data Contracts

### Modified Types
In `ModelRegistryImpl.kt`:
```kotlin
class ModelRegistryImpl @Inject constructor(
    private val modelsDao: ModelsDao,
    private val defaultModelsDao: DefaultModelsDao, // Added
    private val transactionProvider: TransactionProvider, // Added
    private val logger: LoggingPort
) : ModelRegistryPort {
    // ...
}
```

## Permissions & Config Delta
None

## Constitution Audit
- **Clean Architecture**: `ModelRegistryImpl` (Data Layer) depends on `ModelRegistryPort` (Domain Port) and injects `TransactionProvider` (Domain Port). `DefaultModelsDao` and `ModelsDao` are Data Layer types. This is compliant.
- **Data Layer Rules**:
    - Multi-table operations are wrapped in a transaction using `TransactionProvider`.
    - `DefaultModelEntity` is used for the `default_models` table.
- **Code Style**:
    - Constructor injection is used.
    - `System.currentTimeMillis()` is used for timestamps in the data layer.

## Cross-Spec Dependencies
None
