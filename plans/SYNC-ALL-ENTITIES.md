# Implementation Plan: Sync All Remaining Entities to Firestore

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend Cloud Sync to every remaining entity in the local database, adding encryption for personal content, Firebase Storage for media, and embedding regeneration on-device.

**Architecture:** Use a generic `EntitySyncAdapter` pattern in `FirestoreDataSource` to orchestrate push/pull of 14+ entities. Encrypt sensitive fields with the vault key. Use WorkManager for background sync and embedding regeneration.

**Tech Stack:** Kotlin, Room, Firestore, Firebase Storage, WorkManager, AES-GCM (CryptoManager).

---

### Task 1: Stabilize DAOs (Phase 1 & 2)

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/MemoriesDao.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/WebSearchSourceDao.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/TtsProviderDao.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/MediaProviderDao.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiModelConfigurationsDao.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/LocalModelsDao.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ApiCredentialsDao.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/LocalModelConfigurationsDao.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/DefaultModelsDao.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/StudioMediaDao.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/StudioAlbumDao.kt`
- Create: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/MessageFileDao.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/MessageVisionAnalysisDao.kt`

- [ ] **Step 1: Implement sync methods in MemoriesDao**
```kotlin
@Query("SELECT * FROM memories WHERE pending_sync = 1")
suspend fun getPendingSyncMemories(): List<MemoriesEntity>

@Query("UPDATE memories SET pending_sync = 0 WHERE id IN (:ids)")
suspend fun markSynced(ids: List<String>)
```
- [ ] **Step 2: Implement sync methods in WebSearchSourceDao**
```kotlin
@Query("SELECT * FROM web_search_source WHERE pending_sync = 1")
suspend fun getPendingSyncSources(): List<WebSearchSourceEntity>

@Query("UPDATE web_search_source SET pending_sync = 0 WHERE id IN (:ids)")
suspend fun markSynced(ids: List<String>)
```
- [ ] **Step 3: Repeat for all other DAOs**
Add `getPendingSyncXxx()` and `markSynced(ids)` to:
- `TtsProviderDao`
- `MediaProviderDao`
- `ApiModelConfigurationsDao`
- `LocalModelsDao`
- `ApiCredentialsDao`
- `LocalModelConfigurationsDao`
- `DefaultModelsDao`
- `StudioMediaDao`
- `StudioAlbumDao`
- `MessageVisionAnalysisDao`

- [ ] **Step 4: Create MessageFileDao**
```kotlin
@Dao
interface MessageFileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MessageFileEntity)

    @Query("SELECT * FROM message_file WHERE id = :id")
    suspend fun getById(id: String): MessageFileEntity?

    @Query("SELECT * FROM message_file WHERE message_id = :messageId")
    suspend fun getByMessageId(messageId: MessageId): List<MessageFileEntity>

    @Query("DELETE FROM message_file WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM message_file WHERE pending_sync = 1")
    suspend fun getPendingSyncFiles(): List<MessageFileEntity>

    @Query("UPDATE message_file SET pending_sync = 0 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
}
```
- [ ] **Step 5: Register MessageFileDao in PocketCrewDatabase**
- [ ] **Step 6: Run SyncDaoTest to verify they pass**
Run: `./gradlew :data:testDebugUnitTest --tests "com.browntowndev.pocketcrew.core.data.local.SyncDaoTest"`
Expected: PASS

### Task 2: Phase 0 — Infrastructure Refactor (EntitySyncAdapter)

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt`

- [ ] **Step 1: Define EntitySyncAdapter interface in FirestoreDataSource**
```kotlin
private interface EntitySyncAdapter<RoomT> {
    val collectionPath: String
    suspend fun getPendingLocal(): List<RoomT>
    suspend fun markSynced(entities: List<RoomT>)
    fun toFirestore(local: RoomT): Map<String, Any?>
    fun fromFirestore(doc: DocumentSnapshot): RoomT?
    suspend fun upsertLocal(entity: RoomT)
    suspend fun deleteLocal(id: String)
}
```
- [ ] **Step 2: Implement ChatSyncAdapter**
- [ ] **Step 3: Implement MessageSyncAdapter**
- [ ] **Step 4: Refactor startListening/stopListening to use adapters**
- [ ] **Step 5: Refactor syncPendingChanges to iterate over adapters**
- [ ] **Step 6: Run FirestoreDataSourceTest to verify no regressions**
Run: `./gradlew :data:testDebugUnitTest --tests "com.browntowndev.pocketcrew.core.data.remote.FirestoreDataSourceTest"`
Expected: PASS

### Task 3: Phase 1 — Configuration & Provider Adapters

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/LocalModelRepositoryImpl.kt` (and others)

- [ ] **Step 1: Add adapters for ApiModelConfiguration, LocalModelConfiguration, DefaultModel, LocalModel, TtsProvider, MediaProvider**
- [ ] **Step 2: Handle FK ordering in FirestoreDataSource**
Ensure parents are inserted before children in listeners.
- [ ] **Step 3: Add SyncTrigger calls to remaining Phase 1 repositories**
- [ ] **Step 4: Verify with tests**

### Task 4: Phase 2 — Personal Content (Encrypted)

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/MemoriesRepositoryImpl.kt` (already done?)
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/MessageRepositoryImpl.kt`

- [ ] **Step 1: Add encrypted adapters for Memories, VisionAnalysis, MessageFile, WebSearchSource**
Use `cryptoManager.encrypt/decrypt` for sensitive fields.
- [ ] **Step 2: Implement MessageFileDao usage in MessageRepositoryImpl**
- [ ] **Step 3: Add SyncTrigger calls to vision analysis/file/web search writes**

### Task 5: Phase 3 — API Credentials & ApiKeyManager

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/ApiKeyManager.kt`

- [ ] **Step 1: Add ApiCredentials adapter**
- [ ] **Step 2: Implement pending-sync tracking for ApiKeyManager (DataStore)**
- [ ] **Step 3: Add ApiKeyManager adapter to FirestoreDataSource**

### Task 6: Phase 4 — Firebase Storage Integration (Media)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `core/data/build.gradle.kts`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/di/DataModule.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt`

- [ ] **Step 1: Add Firebase Storage dependencies**
- [ ] **Step 2: Implement Storage upload/download logic in adapters**
Encrypt media bytes with vault key before upload.
- [ ] **Step 3: Update Chat image sync to use Storage**
- [ ] **Step 4: Add StudioMedia and StudioAlbum adapters**

### Task 7: Phase 5 — Embedding Regeneration

**Files:**
- Create: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/sync/EmbeddingRegenerationWorker.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt` (triggers)
- Modify: `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/auth/LoginWithGoogleUseCase.kt`

- [ ] **Step 1: Implement EmbeddingRegenerationWorker**
- [ ] **Step 2: Add post-insert triggers to FirestoreDataSource listeners**
- [ ] **Step 3: Kick off regeneration after initial sync in LoginUseCase**

### Task 8: Phase 6 — Media-Aware Share

**Files:**
- Modify: `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/share/ShareModels.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/repository/FirestoreShareRepository.kt`
- Modify: `functions/src/index.ts`

- [ ] **Step 1: Update Share models to include media fields**
- [ ] **Step 2: Implement media upload in FirestoreShareRepository**
- [ ] **Step 3: Update Cloud Function to handle mediaPaths and render media in HTML**
- [ ] **Step 4: Add scheduled cleanup for expired share media**
