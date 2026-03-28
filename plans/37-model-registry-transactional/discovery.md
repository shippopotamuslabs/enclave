# Discovery Report — 37-model-registry-transactional

## Executive Summary
This report covers the research for ensuring `ModelRegistryImpl.setRegisteredModel` is transactional and correctly initializes default model configurations. Currently, when a model is registered (e.g., after the first download), it is saved to the `models` table, but the `default_models` table may not have an entry for that `ModelType`. This results in the model not being selected as the default for its slot until manually configured. The proposed change will automate this first-time setup and ensure atomic database operations.

## Module Structure
- **Module**: `core:data`
- **Key Classes**:
    - `ModelRegistryImpl`: The repository implementation for model registration.
    - `ModelsDao`: DAO for the `models` table (which stores downloaded configs).
    - `DefaultModelsDao`: DAO for the `default_models` table (which maps slots to models).
    - `PocketCrewDatabase`: Room database definition.
    - `RoomTransactionProvider`: Implementation of `TransactionProvider` for Room-based transactions.

## Data Models
- **`ModelEntity`**: Stores on-device model configurations (displayName, file name, tunings, etc.).
- **`DefaultModelEntity`**: Maps a `ModelType` (slot) to a `ModelSource` (ON_DEVICE or API) and optional `apiModelId`.
- **`ModelType`**: Enum defining logical slots: `VISION`, `DRAFT_ONE`, `DRAFT_TWO`, `MAIN`, `FAST`.
- **`ModelSource`**: Enum: `ON_DEVICE`, `API`.

## API Surface
- **`ModelRegistryPort.setRegisteredModel(config, status, markExistingAsOld)`**: The target method for modification.

## Dependencies
- **Room Persistence Library**: Used for data storage and transactions.
- **Dagger Hilt**: Used for dependency injection.
- **Kotlin Coroutines**: Used for asynchronous database operations.

## Utility Patterns
- **`TransactionProvider` Port**: Abstracted transaction interface used in the domain layer, implemented by `RoomTransactionProvider` in the data layer.
- **Mutex-based caching**: `ModelRegistryImpl` uses a `Mutex` to synchronize updates to its internal `Map<ModelType, ModelConfiguration>` cache.

## Gap Analysis
- **Transactional Integrity**: `setRegisteredModel` currently performs multiple DAO calls (possibly marking old as OLD, then upserting new) without an explicit transaction. If the second call fails, the database could be in an inconsistent state.
- **Auto-Initialization**: There is no logic in the data layer to ensure a `DefaultModelEntity` is created for a slot when the first model for that slot is registered.

## Risk Assessment
- **Deadlock Potential**: Since `ModelRegistryImpl` uses a `Mutex` for its cache and Room uses database locks, we must ensure we don't hold the mutex while waiting for a long-running transaction, or vice-versa, in a way that causes issues. However, the current cache update happens *after* the DB update, which is safe.
- **Breaking Changes**: No breaking changes to the `ModelRegistryPort` interface are required.

## Recommendation
Proceed to Phase 2 (Spec). The scope is well-defined and fits within a Tier 2 change.
