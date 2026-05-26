# Spec — Committed Prompts Persistence

**Status:** Awaiting Approval  
**Ticket ID:** `committed-prompts-persistence`  
**Feature:** Persist committed inference prompts locally in a Room-backed store for user RAG prefix stability across app restarts/kills.

---

## 1. Feature Summary

Currently, `InMemoryCommittedInferencePromptStore` holds the committed inference prompts in a process-wide, in-memory `ConcurrentHashMap` keyed by `MessageId`. These prompts are generated during tool execution or RAG preflight query expansion, then committed when the inference event loop reaches the `Finished` state (inside `RustLoopInferenceService`).
`ChatHistoryRehydrator` uses this store when rebuilding conversation history, ensuring prior user turns maintain prefix stability and exact prompt context during a live session.

However, because this cache is in-memory only, restarting the app or a background process kill causes the cache to be lost. Subsequent turn rehydrations read the raw user message contents from the `message` table, which lacks the expanded search results/system context, breaking prompt prefix caching/stability.

The solution is to persist committed prompts in a Room-backed store. We will introduce `RoomCommittedInferencePromptStore` which implements `CommittedInferencePromptPort`, using a new Room database child table `message_committed_inference_prompt`. To maintain high performance and avoid blocking during live turn completions, the store will employ a write-through in-memory cache in front of Room, executing writes/deletes asynchronously on an application-scoped coroutine dispatcher and reads synchronously via blocking queries on cache misses.

---

## 2. File Structure Changes

| File Path | Action | Responsibility |
|---|---|---|
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/local/MessageCommittedInferencePromptEntity.kt` | **Create** | Room entity definition representing the table `message_committed_inference_prompt`. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/local/MessageCommittedInferencePromptDao.kt` | **Create** | Room DAO interface defining insert/update, retrieve, and delete operations on the prompt table. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/local/EnclaveDatabase.kt` | **Modify** | Registers the new entity in `@Database` configuration, bumps database version from `3` to `4`, and declares the abstract DAO getter. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/inference/RoomCommittedInferencePromptStore.kt` | **Create** | Implements `CommittedInferencePromptPort` using `MessageCommittedInferencePromptDao` with an in-memory write-through cache. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/DataModule.kt` | **Modify** | Provides `MessageCommittedInferencePromptDao` via Hilt and binds `CommittedInferencePromptPort` to `RoomCommittedInferencePromptStore` instead of the in-memory store. |
| `feature/inference/src/main/kotlin/com/browntowndev/enclave/feature/inference/di/PromptModule.kt` | **Modify** | Removes the binding of `CommittedInferencePromptPort` to `InMemoryCommittedInferencePromptStore`. |
| `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/local/EnclaveDatabaseMigrationTest.kt` | **Modify** | Verifies the existence of the table `message_committed_inference_prompt` and its structural columns. |
| `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/inference/RoomCommittedInferencePromptStoreTest.kt` | **Create** | Unit tests for `RoomCommittedInferencePromptStore` verifying caching, persistence, and cache-miss database reload operations. |
| `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/local/ChatHistoryRehydratorIntegrationTest.kt` | **Create** | Integration tests confirming that `ChatHistoryRehydrator` reads from the database via `RoomCommittedInferencePromptStore` to rehydrate historical turns after app restarts. |

---

## 3. API Contracts and Interfaces

### 3.1 Room Entity: `MessageCommittedInferencePromptEntity`
- **Table Name:** `message_committed_inference_prompt`
- **Fields:**
  - `messageId: MessageId` (Primary Key, Column: `message_id`, String)
  - `inferenceContent: String` (Column: `inference_content`, TEXT)
  - `updatedAt: Long` (Column: `updated_at`, INTEGER)
- **Foreign Keys:**
  - Parent: `MessageEntity`
  - Parent Column: `id`
  - Child Column: `message_id`
  - On Delete: `CASCADE`
- **Indices:**
  - `Index(value = ["message_id"])`

### 3.2 Room DAO: `MessageCommittedInferencePromptDao`
- **Signature:**
```kotlin
interface MessageCommittedInferencePromptDao {
    fun insertOrUpdate(entity: MessageCommittedInferencePromptEntity): Unit
    fun getByMessageId(messageId: MessageId): MessageCommittedInferencePromptEntity?
    fun deleteByMessageId(messageId: MessageId): Unit
}
```
*Note: Methods are defined without `suspend` as the interface `CommittedInferencePromptPort` is synchronous, allowing blocking execution or background launches.*

### 3.3 Port Implementation: `RoomCommittedInferencePromptStore`
- **Signature:**
```kotlin
class RoomCommittedInferencePromptStore(
    private val dao: MessageCommittedInferencePromptDao,
    private val scope: CoroutineScope,
    private val logger: LoggingPort
) : CommittedInferencePromptPort {
    override fun commit(userMessageId: MessageId, content: String): Unit
    override fun get(userMessageId: MessageId): String?
    override fun clear(userMessageId: MessageId): Unit
}
```

---

## 4. Database Schema Migrations

The `EnclaveDatabase` version will be bumped from `3` to `4`.
Since `DataModule.kt` instantiates the database with `fallbackToDestructiveMigration(dropAllTables = true)`, a destructive migration is acceptable and will automatically drop all tables and reconstruct them matching the v4 schema on launch. No explicit `Migration` instance is required.

---

## 5. Ordered Implementation Tasks

1. **Database Schema Setup**  
   - Define `MessageCommittedInferencePromptEntity` in `core/data/.../local/MessageCommittedInferencePromptEntity.kt`.  
   - Define `MessageCommittedInferencePromptDao` in `core/data/.../local/MessageCommittedInferencePromptDao.kt`.  
   - Register `MessageCommittedInferencePromptEntity` in `EnclaveDatabase.kt`, bump `version` to `4`, and declare `messageCommittedInferencePromptDao()`.  
   *Satisfies: SCN-PERSIST-001, SCN-PERSIST-004*

2. **DAO Verification & Hilt Setup**  
   - Add DAO provider function to `DataModule.kt`.  
   *Satisfies: SCN-PERSIST-001*

3. **Store Implementation**  
   - Implement `RoomCommittedInferencePromptStore` under `core/data/.../inference/RoomCommittedInferencePromptStore.kt`.  
   - Use a `ConcurrentHashMap` for cache backing.  
   - `commit(...)` updates cache synchronously, and spawns `scope.launch(Dispatchers.IO)` to run `dao.insertOrUpdate(...)` within a `try-catch` logging exceptions.  
   - `get(...)` performs cache lookup; on miss, invokes `runBlocking` to fetch from the DAO.  
   - `clear(...)` clears cache and spawns `scope.launch(Dispatchers.IO)` to run `dao.deleteByMessageId(...)`.  
   - Bind `CommittedInferencePromptPort` to `RoomCommittedInferencePromptStore` inside `DataRepositoryModule` in `DataModule.kt`.  
   - Remove duplicate binding from `PromptModule.kt` in `feature/inference`.  
   *Satisfies: SCN-PERSIST-001, SCN-PERSIST-002, SCN-PERSIST-003, SCN-PERSIST-006, SCN-PERSIST-007*

4. **Unit & Integration Testing**  
   - Implement database structure assertions in `EnclaveDatabaseMigrationTest.kt`.  
   - Implement caching, persistence, and cache-miss reload unit tests in `RoomCommittedInferencePromptStoreTest.kt`.  
   - Implement full rehydrator integration tests in `ChatHistoryRehydratorIntegrationTest.kt` simulating process death and verifying prompt replacement.  
   *Satisfies: SCN-PERSIST-001, SCN-PERSIST-002, SCN-PERSIST-003, SCN-PERSIST-004, SCN-PERSIST-005, SCN-PERSIST-006, SCN-PERSIST-007*

---

## 6. Drift and Escalation Rules

- **Schema Locking Violation:** If we detect a need to store transient state (like `is_writing` or `is_saving`), immediately halt and consult constraints. Schema updates must solely support prefix prompt text stability.
- **Dagger/Hilt Compilation Failures:** If removing the binding from `PromptModule.kt` causes compilation issues in downstream modules, check that `:core:data` is registered correctly in `:app` dependencies and that the graph has single bindings for `CommittedInferencePromptPort`.
