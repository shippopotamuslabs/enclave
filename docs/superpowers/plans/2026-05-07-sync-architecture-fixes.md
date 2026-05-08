# Sync Architecture Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 14 sync-related issues including thread safety, TOCTOU races, and logic errors in Firestore sync.

**Architecture:** Implement a three-state `pending_sync` integer (0: clean, 1: dirty, 2: in-flight) across all syncable entities to prevent data loss. Use Mutex for thread-safe listener management and atomic SQL updates for message content.

**Tech Stack:** Kotlin, Room, Firestore, WorkManager, Hilt

---

### Task 1: Thread-Safe Firestore Listeners (C1, I1, M2)

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt`

- [ ] **Step 1: Add Mutex and update listener management**
    Introduce a `Mutex` to guard `listenerRegistrations`. Add a check for `cryptoManager.hasCachedKey()` in `startListening()` to ensure vault is unlocked.
    ```kotlin
    private val listenerMutex = kotlinx.coroutines.sync.Mutex()

    override fun startListening() {
        externalScope.launch {
            listenerMutex.withLock {
                if (listenerRegistrations.isNotEmpty()) return@withLock
                if (!cryptoManager.hasCachedKey()) return@withLock // I1 fix
                
                val userId = userIdProvider() ?: return@withLock
                // ... start listeners ...
            }
        }
    }
    ```

- [ ] **Step 2: Update `stopListening()`**
    ```kotlin
    override fun stopListening() {
        externalScope.launch {
            listenerMutex.withLock {
                listenerRegistrations.forEach { it.remove() }
                listenerRegistrations.clear()
            }
        }
    }
    ```

- [ ] **Step 3: Commit**
    ```bash
    git add core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt
    git commit -m "fix(sync): make Firestore listeners thread-safe and vault-aware"
    ```

### Task 2: DAO Migration - Three-State `pending_sync` (I2)

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/*.kt` (All syncable DAOs)

- [ ] **Step 1: Define the Pattern for DAOs**
    For each DAO (Chat, Message, Memories, etc.), add `claimPending()`, update `getPendingSyncX()` to `getClaimedForSync()`, and update `markSynced()`.
    Example for `ChatDao`:
    ```kotlin
    @Query("UPDATE chat SET pending_sync = 2 WHERE pending_sync = 1")
    abstract suspend fun claimPending()

    @Query("SELECT * FROM chat WHERE pending_sync = 2")
    abstract suspend fun getClaimedForSync(): List<ChatEntity>

    @Query("UPDATE chat SET pending_sync = 0 WHERE id IN (:ids) AND pending_sync = 2")
    abstract suspend fun markSynced(ids: List<ChatId>)
    ```

- [ ] **Step 2: Update all 15+ DAOs**
    Apply this pattern to all entities tracked in `FirestoreDataSource.adapters`. Ensure `pendingSync` field in `@Entity` remains named the same or is updated to `Int` if type safety is desired (Room treats `Boolean` as `Int` 0/1 anyway).

- [ ] **Step 3: Update `EntitySyncAdapter` interface in `FirestoreDataSource`**
    ```kotlin
    private interface EntitySyncAdapter<RoomT> {
        suspend fun claimPending()
        suspend fun getClaimedForSync(): List<RoomT>
        suspend fun markSynced(entities: List<RoomT>)
        // ... rest unchanged ...
    }
    ```

- [ ] **Step 4: Commit**
    ```bash
    git add core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/
    git commit -m "refactor(sync): implement three-state pending_sync across all DAOs"
    ```

### Task 3: Atomic Message Content Updates (I3)

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/MessageDao.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ChatRepositoryImpl.kt`

- [ ] **Step 1: Add atomic update methods to `MessageDao`**
    ```kotlin
    @Query("UPDATE message SET content = content || :suffix, pending_sync = 1 WHERE id = :id")
    abstract suspend fun appendMessageContentAtomic(id: MessageId, suffix: String)

    @Query("UPDATE message SET thinking_raw = COALESCE(thinking_raw, '') || :suffix, pending_sync = 1 WHERE id = :id")
    abstract suspend fun appendThinkingRawAtomic(id: MessageId, suffix: String)
    ```

- [ ] **Step 2: Update `ChatRepositoryImpl` to use atomic methods**
    Remove read-modify-write blocks in `appendMessageContent` and `appendThinkingRaw`.

- [ ] **Step 3: Commit**
    ```bash
    git add core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/MessageDao.kt \
            core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/ChatRepositoryImpl.kt
    git commit -m "fix(sync): implement atomic message and thinking content appends"
    ```

### Task 4: Sync Adapter Logic Fixes (C3, C4, I4, M4)

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt`

- [ ] **Step 1: Fix `chatSyncAdapter.upsertLocal` (C3)**
    Update branch to include `|| overwrite`.
    ```kotlin
    if (existing == null || existing.created.time == 0L || existing.name.isEmpty() || overwrite) {
        chatDao.insert(entity.copy(pendingSync = 0))
    }
    ```

- [ ] **Step 2: Fix Studio Adapters (C4)**
    Add the `existing == null || overwrite` guard to `studioAlbumSyncAdapter` and `studioMediaSyncAdapter`.

- [ ] **Step 3: Fix Memories Embedding (I4)**
    Move `embeddingCoordinator.enqueueMemory` inside the `if` block.

- [ ] **Step 4: Fix Missing Parent IDs (M4)**
    In `toFirestore` for vision, files, and web sources, check if message exists. Skip if null.
    ```kotlin
    val msg = messageDao.getMessageById(entity.messageId) ?: return emptyMap()
    val chatId = msg.message.chatId.value
    ```

- [ ] **Step 5: Commit**
    ```bash
    git add core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt
    git commit -m "fix(sync): correct adapter logic for renames, studio entities, and parent references"
    ```

### Task 5: WorkManager & Lifecycle Fixes (C2, I5, I6, M1)

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/sync/CloudSyncTrigger.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/sync/EmbeddingRegenerationCoordinator.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt`

- [ ] **Step 1: Change policy to KEEP (C2)**
    In `CloudSyncTrigger.kt`, change `ExistingWorkPolicy.REPLACE` to `ExistingWorkPolicy.KEEP`.

- [ ] **Step 2: Thread-safe triggerWorker (I5)**
    In `EmbeddingRegenerationCoordinator.kt`, move `triggerWorker()` inside the `mutex.withLock` block in all `enqueue` methods.

- [ ] **Step 3: Implement `syncAllLocalDataToCloud` (I6, M1)**
    Implement the loop to claim and sync all entities.
    ```kotlin
    adapters.forEach { it.claimAllLocal() } // Need to add this to adapter
    syncPendingChanges()
    ```

- [ ] **Step 4: Commit**
    ```bash
    git add core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/sync/ \
            core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt
    git commit -m "fix(sync): optimize work policies and implement full migration sync"
    ```
