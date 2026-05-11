# Spec: Sync Architecture Fixes (2026-05-07)

## Overview
This specification addresses 14 issues identified in the Sync Architecture Review, ranging from critical thread-safety bugs to minor performance optimizations and race conditions.

## Architecture Changes

### 1. Three-State `pending_sync` (I2)
All Room entities will move from `pendingSync: Boolean` to a three-state `INTEGER` in SQLite:
*   **0 (Clean):** Synced.
*   **1 (Dirty):** Needs upload (locally modified).
*   **2 (In-Flight):** Claimed by the current `SyncWorker` run.

**Impacted DAOs:** All 16 syncable DAOs (Chat, Message, Memories, VisionAnalysis, MessageFile, WebSearchSource, etc.)

**New Standard DAO Pattern:**
```kotlin
@Query("UPDATE table SET pending_sync = 2 WHERE pending_sync = 1")
abstract suspend fun claimPending()

@Query("SELECT * FROM table WHERE pending_sync = 2")
abstract suspend fun getClaimedForSync(): List<EntityT>

@Query("UPDATE table SET pending_sync = 0 WHERE id IN (:ids) AND pending_sync = 2")
abstract suspend fun markSynced(ids: List<IdT>)
```

### 2. Thread-Safe `FirestoreDataSource` (C1, I1, M2)
*   Wrap `listenerRegistrations` operations in a `Mutex`.
*   Update `startListening()` to check `cryptoManager.hasCachedKey()` (vault status) before adding listeners. This prevents redundant re-delivery of snapshots when the vault is locked.
*   The `isNotEmpty()` guard in `startListening()` will be moved inside the `Mutex` lock.

### 3. Atomic Repository Updates (I3)
*   Update `MessageDao` with an atomic append query:
    ```kotlin
    @Query("UPDATE message SET content = content || :suffix, pending_sync = 1 WHERE id = :id")
    abstract suspend fun appendMessageContentAtomic(id: MessageId, suffix: String)
    ```
*   Update `ChatRepositoryImpl.appendMessageContent` and `appendThinkingRaw` to use this atomic update.

### 4. Logic Fixes in `FirestoreDataSource` Adapters

#### C3: Chat Renames
Update `chatSyncAdapter.upsertLocal` to check `overwrite`:
```kotlin
if (existing == null || existing.created.time == 0L || existing.name.isEmpty() || overwrite) {
    chatDao.insert(entity.copy(pendingSync = 0)) // 0 = clean
}
```

#### C4: Studio Adapters
Update `studioAlbumSyncAdapter` and `studioMediaSyncAdapter` to use the same "existing guard" pattern used by other adapters, preventing local `pending_sync` flags from being overwritten by inbound `ADDED` events.

#### I4: Memories Embedding
Move the `embeddingCoordinator.enqueueMemory(entity.id)` call inside the `if (existing == null || ...)` block in `memoriesSyncAdapter.upsertLocal`.

#### M4: Missing Message IDs in `toFirestore`
Update `visionAnalysisSyncAdapter`, `messageFileSyncAdapter`, and `webSearchSourceSyncAdapter` to handle cases where the parent message might have been deleted:
*   If `messageDao.getMessageById(id)` returns null, the adapter should return `emptyMap()` or a signal to skip the upload, rather than writing a document with `chat_id = null`.

### 5. WorkManager & Lifecycle (C2, I5, I6, M1)
*   **C2:** Change `ExistingWorkPolicy.REPLACE` to `KEEP` in `CloudSyncTrigger`.
*   **I5:** Move `triggerWorker()` inside the `mutex.withLock` in `EmbeddingRegenerationCoordinator`.
*   **I6 / M1:** Implement the migration logic in `syncAllLocalDataToCloud()`. It will now trigger a full `claimPending()` on all adapters to ensure any unsynced data is uploaded.

## Verification Plan

### Automated Tests
1.  **Concurrency Test (C1):** Spawn 100 coroutines calling `startListening()`/`stopListening()` simultaneously and verify only one set of listeners exists.
2.  **TOCTOU Race Test (I2):**
    *   Claim entity (state 1 -> 2).
    *   Write update in separate thread (state 2 -> 1).
    *   Call `markSynced()` for the claimed version.
    *   Verify state is still `1` (dirty) and not `0` (clean).
3.  **Atomic Append Test (I3):** Concurrent appends to a message content should result in all suffixes being present in the final string.

### Manual Verification
*   Verify cross-device chat renames propagate correctly.
*   Verify studio media created locally survives a remote sync event.
*   Verify sync progress under "rapid typing" (ensure worker doesn't perpetually restart without progress).
