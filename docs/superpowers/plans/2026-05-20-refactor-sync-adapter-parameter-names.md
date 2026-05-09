# Refactor EntitySyncAdapter Parameter Names Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure all `EntitySyncAdapter` implementations in `FirestoreDataSource.kt` use the exact parameter names from the interface definition.

**Architecture:** Surgical refactoring of parameter names in anonymous objects and classes implementing `EntitySyncAdapter`.

**Tech Stack:** Kotlin, Android (Firestore)

---

### Task 1: Refactor Chat, Message, and Memories Adapters

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt`

- [ ] **Step 1: Refactor `chatSyncAdapter`**
    - `markSynced(entities: List<ChatEntity>)`
    - `toFirestore(local: ChatEntity)`
    - `upsertLocal(entity: ChatEntity, doc: DocumentSnapshot, overwrite: Boolean)`
    - `getEntityId(entity: ChatEntity)`

- [ ] **Step 2: Refactor `messageSyncAdapter`**
    - `markSynced(entities: List<MessageEntity>)`
    - `toFirestore(local: MessageEntity)`
    - `upsertLocal(entity: MessageEntity, doc: DocumentSnapshot, overwrite: Boolean)`
    - `getEntityId(entity: MessageEntity)`

- [ ] **Step 3: Refactor `memoriesSyncAdapter`**
    - `markSynced(entities: List<MemoriesEntity>)`
    - `toFirestore(local: MemoriesEntity)`
    - `fromFirestore(doc: DocumentSnapshot)` (Rename `snapshot` to `doc`)
    - `upsertLocal(entity: MemoriesEntity, doc: DocumentSnapshot, overwrite: Boolean)`
    - `getEntityId(entity: MemoriesEntity)`

### Task 2: Refactor Vision Analysis, Message File, and Web Search Adapters

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt`

- [ ] **Step 1: Refactor `visionAnalysisSyncAdapter`**
    - `markSynced(entities: List<MessageVisionAnalysisEntity>)`
    - `toFirestore(local: MessageVisionAnalysisEntity)`
    - `fromFirestore(doc: DocumentSnapshot)` (Rename `snapshot` to `doc`)
    - `upsertLocal(entity: MessageVisionAnalysisEntity, doc: DocumentSnapshot, overwrite: Boolean)`
    - `getEntityId(entity: MessageVisionAnalysisEntity)`

- [ ] **Step 2: Refactor `messageFileSyncAdapter`**
    - `markSynced(entities: List<MessageFileEntity>)`
    - `toFirestore(local: MessageFileEntity)`
    - `fromFirestore(doc: DocumentSnapshot)` (Rename `snapshot` to `doc`)
    - `upsertLocal(entity: MessageFileEntity, doc: DocumentSnapshot, overwrite: Boolean)`
    - `getEntityId(entity: MessageFileEntity)`

- [ ] **Step 3: Refactor `webSearchSourceSyncAdapter`**
    - `markSynced(entities: List<WebSearchSourceEntity>)`
    - `toFirestore(local: WebSearchSourceEntity)`
    - `fromFirestore(doc: DocumentSnapshot)` (Rename `snapshot` to `doc`)
    - `upsertLocal(entity: WebSearchSourceEntity, doc: DocumentSnapshot, overwrite: Boolean)`
    - `getEntityId(entity: WebSearchSourceEntity)`

### Task 3: Refactor Model Config and Default Model Adapters

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt`

- [ ] **Step 1: Refactor `apiModelConfigSyncAdapter`**
    - `markSynced(entities: List<ApiModelConfigurationEntity>)`
    - `toFirestore(local: ApiModelConfigurationEntity)`
    - `fromFirestore(doc: DocumentSnapshot)` (Rename `snapshot` to `doc`)
    - `upsertLocal(entity: ApiModelConfigurationEntity, doc: DocumentSnapshot, overwrite: Boolean)`
    - `getEntityId(entity: ApiModelConfigurationEntity)`

- [ ] **Step 2: Refactor `localModelConfigSyncAdapter`**
    - `markSynced(entities: List<LocalModelConfigurationEntity>)`
    - `toFirestore(local: LocalModelConfigurationEntity)`
    - `fromFirestore(doc: DocumentSnapshot)` (Rename `snapshot` to `doc`)
    - `upsertLocal(entity: LocalModelConfigurationEntity, doc: DocumentSnapshot, overwrite: Boolean)`
    - `getEntityId(entity: LocalModelConfigurationEntity)`

- [ ] **Step 3: Refactor `defaultModelSyncAdapter`**
    - `markSynced(entities: List<DefaultModelEntity>)`
    - `toFirestore(local: DefaultModelEntity)`
    - `fromFirestore(doc: DocumentSnapshot)` (Rename `snapshot` to `doc`)
    - `upsertLocal(entity: DefaultModelEntity, doc: DocumentSnapshot, overwrite: Boolean)`
    - `getEntityId(entity: DefaultModelEntity)`

### Task 4: Refactor Local Model, TTS, Media Provider, and API Credentials Adapters

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt`

- [ ] **Step 1: Refactor `localModelSyncAdapter`**
    - `markSynced(entities: List<LocalModelEntity>)`
    - `toFirestore(local: LocalModelEntity)`
    - `fromFirestore(doc: DocumentSnapshot)` (Rename `snapshot` to `doc`)
    - `upsertLocal(entity: LocalModelEntity, doc: DocumentSnapshot, overwrite: Boolean)`
    - `getEntityId(entity: LocalModelEntity)`

- [ ] **Step 2: Refactor `ttsProviderSyncAdapter`**
    - `markSynced(entities: List<TtsProviderEntity>)`
    - `toFirestore(local: TtsProviderEntity)`
    - `fromFirestore(doc: DocumentSnapshot)` (Rename `snapshot` to `doc`)
    - `upsertLocal(entity: TtsProviderEntity, doc: DocumentSnapshot, overwrite: Boolean)`
    - `getEntityId(entity: TtsProviderEntity)`

- [ ] **Step 3: Refactor `mediaProviderSyncAdapter`**
    - `markSynced(entities: List<MediaProviderEntity>)`
    - `toFirestore(local: MediaProviderEntity)`
    - `fromFirestore(doc: DocumentSnapshot)` (Rename `snapshot` to `doc`)
    - `upsertLocal(entity: MediaProviderEntity, doc: DocumentSnapshot, overwrite: Boolean)`
    - `getEntityId(entity: MediaProviderEntity)`

- [ ] **Step 4: Refactor `apiCredentialsSyncAdapter`**
    - `markSynced(entities: List<ApiCredentialsEntity>)`
    - `toFirestore(local: ApiCredentialsEntity)`
    - `fromFirestore(doc: DocumentSnapshot)` (Rename `snapshot` to `doc`)
    - `upsertLocal(entity: ApiCredentialsEntity, doc: DocumentSnapshot, overwrite: Boolean)`
    - `getEntityId(entity: ApiCredentialsEntity)`

### Task 5: Refactor ApiKey, Studio Album, and Studio Media Adapters

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt`

- [ ] **Step 1: Refactor `ApiKeySyncAdapter`**
    - `markSynced(entities: List<Pair<String, String>>)`
    - `getEntityId(entity: Pair<String, String>)`
    - `shouldDelete(entity: Pair<String, String>)`
    - `toFirestore(local: Pair<String, String>)`
    - `fromFirestore(doc: DocumentSnapshot)`
    - `upsertLocal(entity: Pair<String, String>, doc: DocumentSnapshot, overwrite: Boolean)`

- [ ] **Step 2: Refactor `studioAlbumSyncAdapter`**
    - `markSynced(entities: List<StudioAlbumEntity>)`
    - `getEntityId(entity: StudioAlbumEntity)`
    - `toFirestore(local: StudioAlbumEntity)`
    - `fromFirestore(doc: DocumentSnapshot)` (Rename `snapshot` to `doc`)
    - `upsertLocal(entity: StudioAlbumEntity, doc: DocumentSnapshot, overwrite: Boolean)`
    - `shouldDelete(entity: StudioAlbumEntity)`

- [ ] **Step 3: Refactor `studioMediaSyncAdapter`**
    - `markSynced(entities: List<StudioMediaEntity>)`
    - `getEntityId(entity: StudioMediaEntity)`
    - `toFirestore(local: StudioMediaEntity)`
    - `fromFirestore(doc: DocumentSnapshot)` (Rename `snapshot` to `doc`)
    - `upsertLocal(entity: StudioMediaEntity, doc: DocumentSnapshot, overwrite: Boolean)`
    - `shouldDelete(entity: StudioMediaEntity)`

- [ ] **Step 4: Verify Compilation**
    - Run: `./gradlew :core:data:compileKotlin`
