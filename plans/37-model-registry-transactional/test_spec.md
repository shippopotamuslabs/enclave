# Test Specification — 37-model-registry-transactional

## Behavioral Scenarios

### Scenario 1: Initial Model Registration (No Existing Default)
**Given** No models are registered for `ModelType.MAIN` in both `models` and `default_models` tables.
**When** `setRegisteredModel` is called with `ModelType.MAIN` and `ModelStatus.CURRENT`.
**Then** A new `ModelEntity` is inserted into the `models` table.
**And** A new `DefaultModelEntity` is inserted into the `default_models` table with `source = ModelSource.ON_DEVICE`.
**And** The internal cache of `ModelRegistryImpl` is updated.

### Scenario 2: Subsequent Model Registration (Existing Default)
**Given** A model is already registered for `ModelType.MAIN` and a `DefaultModelEntity` exists for it.
**When** `setRegisteredModel` is called with a new `ModelConfiguration` for `ModelType.MAIN`.
**Then** The existing `ModelEntity` is updated (or marked as OLD).
**And** The `DefaultModelEntity` for `ModelType.MAIN` is NOT modified or duplicated.
**And** The existing `DefaultModelEntity` remains in the database.

### Scenario 3: Transactional Integrity (Database Error)
**Given** The database throws an exception during the second insert operation in `setRegisteredModel`.
**When** `setRegisteredModel` is called.
**Then** The entire operation is rolled back (no new entries in `models` or `default_models`).
**And** The internal cache of `ModelRegistryImpl` is NOT updated.

## Error Paths

### Error 1: DAO Insertion Failure
**Given** `modelsDao.upsert` fails due to a constraint violation or closed database.
**When** `setRegisteredModel` is called.
**Then** `setRegisteredModel` throws an exception.
**And** The internal cache remains unchanged.
