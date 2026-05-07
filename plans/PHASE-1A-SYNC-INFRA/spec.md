# Technical Specification: Database and Sync Infrastructure (Phase 1A)

## 1. Objective
Implement sync tracking for Chat and Message entities, update DAOs to flag mutations for sync, implement a background SyncWorker, and enhance FirestoreDataSource with mandatory encryption.

Acceptance Criteria:
- `pending_sync` column added to `chat` and `message` tables.
- Room database version bumped to 5 with valid `MIGRATION_4_5`.
- All `ChatDao` and `MessageDao` mutation methods set `pending_sync = true`.
- `SyncWorker` identifies and pushes `pending_sync = true` data to cloud.
- `FirestoreDataSource` encrypts content before cloud transmission.
- `FirestoreDataSource` fails explicitly if no encryption key is cached.

## 2. System Architecture

### Target Files
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ChatEntity.kt`: Modify to add `pending_sync` field.
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/MessageEntity.kt`: Modify to add `pending_sync` field.
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/PocketCrewDatabase.kt`: Bump version to 5 and add `MIGRATION_4_5`.
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ChatDao.kt`: Update mutation methods to set `pending_sync = true`.
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/MessageDao.kt`: Update mutation methods to set `pending_sync = true`.
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt`: Update `syncAllLocalDataToCloud` (or add targeted sync method) with encryption.
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/sync/SyncWorker.kt`: Create new `CoroutineWorker` for background sync.

### Component Boundaries
- `SyncWorker` resides in `core/data` as it handles infrastructure-level sync.
- It consumes `ChatDao`, `MessageDao`, and `CloudSyncPort` (implemented by `FirestoreDataSource`).
- Encryption logic is delegated to `CryptoManagerPort`.

## 3. Data Models & Schemas
- `ChatEntity`: Add `@ColumnInfo(name = "pending_sync") val pendingSync: Boolean = false`.
- `MessageEntity`: Add `@ColumnInfo(name = "pending_sync") val pendingSync: Boolean = false`.
- `MIGRATION_4_5`:
    - `ALTER TABLE chat ADD COLUMN pending_sync INTEGER NOT NULL DEFAULT 0`
    - `ALTER TABLE message ADD COLUMN pending_sync INTEGER NOT NULL DEFAULT 0`

## 4. API Contracts & Interfaces
- `CloudSyncPort` (existing):
    - `syncAllLocalDataToCloud()`: Will be updated to handle encryption.
- New internal method in `FirestoreDataSource` (or update existing) to handle individual entity sync if needed, but for Phase 1A, `syncAllLocalDataToCloud` or a variant will be used.

## 5. Permissions & Config Delta
- No permissions or config changes. (Standard Internet and WorkManager permissions already assumed).

## 6. Constitution Audit
This design adheres to the project's core architectural rules by maintaining clean separation between data infrastructure and domain logic, utilizing ports for external dependencies (encryption, cloud sync), and ensuring mandatory test coverage.

## 7. Cross-Spec Dependencies
No cross-spec dependencies.
