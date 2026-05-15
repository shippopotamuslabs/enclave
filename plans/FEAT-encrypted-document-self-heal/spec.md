# Spec: Encrypted Document Self-Heal

## Objective
Build a data-layer self-healing flow that prevents Pocket Crew artifact document fields from remaining plaintext in Room or Firestore. When plaintext is encountered at document persistence boundaries, the app classifies the affected field, logs only document id/field/source metadata, encrypts the value while the vault is unlocked, writes the intended destination, and repairs the source side.

## File Structure

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/DocumentPersistenceSelfHealer.kt` | Classify document field encryption state, encrypt plaintext document fields, decrypt encrypted document fields for consumers, and produce non-plaintext repair reports. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/DocumentDao.kt:15-100` | Add focused repair queries for encrypted document field replacement without schema changes. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/artifact/ArtifactToolExecutor.kt:53-235` | Route artifact start, append, and returned complete document reads through `DocumentPersistenceSelfHealer`. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/artifact/SearchArtifactToolExecutor.kt:19-89` | Route search consumer document reads through `DocumentPersistenceSelfHealer` before parsing blocks. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/sync/EmbeddingRegenerationWorker.kt:155-198` | Route embedding consumer document reads through `DocumentPersistenceSelfHealer` before parsing blocks. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt:145-170,1569-1615,1749-1845` | Route document Firestore ingress and egress through `DocumentPersistenceSelfHealer`, repair plaintext source sides, and preserve existing sync semantics. |
| Modify | `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/DataModule.kt:226-230` | Provide the new self-healer through Hilt constructor injection if constructor injection alone is insufficient in this module. |
| Create | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/DocumentPersistenceSelfHealerTest.kt` | Unit tests for classification, encryption, locked-vault failure, sanitized logging, and local repair result contracts. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/artifact/ArtifactToolExecutorImplTest.kt:24-240` | Extend artifact write/read tests to verify plaintext legacy rows are repaired before append and returned documents remain decrypted only in tool output. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/artifact/SearchArtifactToolExecutorTest.kt:1-66` | Extend search consumer tests to verify plaintext legacy rows are encrypted before block search output is produced. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/EmbeddingRegenerationWorkerTest.kt:1-117` | Extend embedding worker tests to verify plaintext legacy rows are repaired before embedding content is consumed. |
| Modify | `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSourceTest.kt:57-119` | Extend Firestore document ingress and egress tests to verify plaintext repair, encrypted writes, and warning logs without plaintext. |

## Data Models & Schemas
Existing schema only. `DocumentEntity` already maps Room table `documents` with sensitive fields `json: String`, `title: String`, and nullable `author: String?`. No new Room table, column, Firestore collection, or Firestore field is authorized.

New data-layer-only types in `DocumentPersistenceSelfHealer.kt`:
- `enum class DocumentFieldName { JSON, TITLE, AUTHOR }`
- `enum class DocumentPersistenceSide { ROOM, FIRESTORE }`
- `enum class DocumentConsumer { ARTIFACT_APPEND, ARTIFACT_SEARCH, EMBEDDING_REGENERATION, FIRESTORE_SYNC }`
- `data class DocumentFieldRepair(val field: DocumentFieldName, val side: DocumentPersistenceSide)`
- `data class DocumentSelfHealResult(val entity: DocumentEntity, val repairs: Set<DocumentFieldRepair>)`
- `class DocumentSelfHealLockedException(message: String) : IllegalStateException(message)`

## API Contracts & Interfaces
Add to `DocumentDao`:

```kotlin
@Query(
    """
    UPDATE documents
    SET json = :json,
        title = :title,
        author = :author,
        updated_at = :updatedAt,
        pending_sync = :pendingSync
    WHERE id = :id
    """,
)
suspend fun repairDocumentEncryption(
    id: String,
    json: String,
    title: String,
    author: String?,
    updatedAt: Long,
    pendingSync: Int,
)
```

Create `DocumentPersistenceSelfHealer`:

```kotlin
@Singleton
class DocumentPersistenceSelfHealer @Inject constructor(
    private val documentDao: DocumentDao,
    private val localEncryption: LocalDataEncryptionService,
    private val loggingPort: LoggingPort,
) {
    suspend fun encryptForRoom(
        entity: DocumentEntity,
        source: DocumentPersistenceSide,
        markPendingSyncOnRepair: Boolean,
    ): DocumentSelfHealResult

    suspend fun encryptForFirestore(entity: DocumentEntity): DocumentSelfHealResult

    suspend fun readForConsumer(
        id: String,
        consumer: DocumentConsumer,
        markPendingSyncOnRepair: Boolean = true,
    ): DocumentEntity?

    fun decryptForConsumer(entity: DocumentEntity): DocumentEntity
}
```

Behavioral contract:
- `encryptForRoom` encrypts plaintext `json`, `title`, and non-null `author` fields, writes repaired values to Room through `repairDocumentEncryption` when any field changed, and sets `pendingSync = 1` only when `markPendingSyncOnRepair` is `true`.
- `encryptForFirestore` returns an encrypted entity for Firestore serialization and repairs the Room row with `pendingSync = 2` when a claimed sync row is self-healed during egress.
- `readForConsumer` loads a document by id, self-heals Room plaintext using `encryptForRoom`, then returns a decrypted `DocumentEntity` to the caller for JSON parsing, search, embedding, or tool output.
- `decryptForConsumer` decrypts encrypted fields and leaves plaintext fields unchanged only in memory.
- Any locked vault failure from `LocalDataEncryptionService.encryptString` is wrapped as `DocumentSelfHealLockedException` with exact message `"Document self-heal requires unlocked vault"`.
- Warning logs must include document id, field name, and side only. They must not include `json`, `title`, `author`, decrypted content, plaintext substrings, or ciphertext payloads.

## Config / Permissions Delta
None.

## Constitution Audit
This design adheres to `AGENTS.md`, `ARCHITECTURE_RULES.md`, `DATA_LAYER_RULES.md`, `CODE_STYLE_RULES.md`, and `UNIT_TESTING.md`: all new behavior stays in `:data`, no domain or UI framework types are introduced, no schema changes are made, and JUnit 5/MockK/Robolectric-style unit coverage is planned before production changes.

## Subagent-Driven Execution

Downstream executors (TDD Red Sub-Agent, Implementation Sub-Agent, or a human following this plan) act as **coordinators first, implementers second**. For each **Implementation Task** below:

1. **Spawn** a child subagent scoped to that task alone (for example: author the failing tests for the task's named scenarios, or implement the production change the task describes).
2. **Review** the child subagent's output against this document as the contract — accept nothing that contradicts the File Structure, API Contracts & Interfaces, the task **Files** lists, or the task-to-scenario bindings — before advancing to the next task.
3. **Repeat** task by task in order. Do not fold multiple unrelated tasks into one child run unless a task explicitly instructs a bundled scope.

Scenario IDs are the **unit of work** each child subagent must satisfy for its task; cross-check every ID the task names under **Verifies scenarios** against `test_spec.md` before closing the task.

**Per-child review checklist (parent executor):**
- Paths touched match the task **Files** entries and the File Structure table; no edits outside listed paths.
- Every **Verifies scenarios** ID for that task shows the intended test state for the current phase (red where failure is expected; green where pass is expected), per `test_spec.md`.
- No scope drift: no unlisted files, no extra behaviors, no shortcuts that bypass this spec.
- Deliverables live in real source and test files aligned with this spec — no substitute that embeds runnable tests or implementation bodies here or otherwise diverges from the contract.

## Implementation Tasks

### Task 1: Add self-healer red tests

**Files:**
- Create: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/DocumentPersistenceSelfHealerTest.kt`

**Change:** Add failing unit tests for the `DocumentPersistenceSelfHealer` API contracts: local plaintext classification and repair, encrypted no-op behavior, locked-vault failure, and sanitized warning logs.

**Verifies scenarios:** S1, S2, E1, E2

**Commit message:**

```text
feat(FEAT-encrypted-document-self-heal): add self-healer contract tests
```

### Task 2: Implement core self-healer and DAO repair query

**Files:**
- Create: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/security/DocumentPersistenceSelfHealer.kt`
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/DocumentDao.kt:15-100`

**Change:** Implement `DocumentPersistenceSelfHealer` and `DocumentDao.repairDocumentEncryption` exactly as declared in API Contracts & Interfaces.

**Verifies scenarios:** S1, S2, E1, E2

**Commit message:**

```text
feat(FEAT-encrypted-document-self-heal): implement document encryption self-healer
```

### Task 3: Add artifact persistence red tests

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/artifact/ArtifactToolExecutorImplTest.kt:24-240`

**Change:** Add failing tests showing `start_artifact` persists encrypted values through the self-healer, `append_blocks` repairs a plaintext legacy Room row before updating it, and complete tool output exposes decrypted document JSON only in the returned `resultJson`.

**Verifies scenarios:** S3, S4

**Commit message:**

```text
feat(FEAT-encrypted-document-self-heal): add artifact persistence self-heal tests
```

### Task 4: Route artifact persistence through self-healer

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/artifact/ArtifactToolExecutor.kt:53-235`

**Change:** Inject `DocumentPersistenceSelfHealer`; call `encryptForRoom` before inserting or updating `DocumentEntity`, and call `readForConsumer` for append reads so plaintext legacy rows are repaired before JSON parsing.

**Verifies scenarios:** S3, S4

**Commit message:**

```text
feat(FEAT-encrypted-document-self-heal): self-heal artifact writes
```

### Task 5: Add Firestore sync red tests

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSourceTest.kt:57-119`

**Change:** Add failing tests for document sync ingress and egress: plaintext Firestore fields are accepted only after encryption and remote repair, plaintext Room fields are encrypted before Firestore write, and locked vault sync fails with `DocumentSelfHealLockedException`.

**Verifies scenarios:** S5, S6, E3, M1

**Commit message:**

```text
feat(FEAT-encrypted-document-self-heal): add Firestore document self-heal tests
```

### Task 6: Implement Firestore ingress and egress repair

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt:145-170,1569-1615,1749-1845`

**Change:** Inject and use `DocumentPersistenceSelfHealer` in `documentSyncAdapter`; replace rejecting plaintext remote document fields with encrypted local upsert plus source Firestore repair, and replace direct `encryptedForFirestore` calls with `encryptForFirestore` before batch writes.

**Verifies scenarios:** S5, S6, E3, M1

**Commit message:**

```text
feat(FEAT-encrypted-document-self-heal): repair document sync plaintext
```

### Task 7: Add search consumer red tests

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/artifact/SearchArtifactToolExecutorTest.kt:1-66`

**Change:** Add failing tests proving `SEARCH_CURRENT_ARTIFACT` repairs plaintext Room document fields before parsing blocks and does not include plaintext title or full document JSON in warning logs.

**Verifies scenarios:** S7, E2

**Commit message:**

```text
feat(FEAT-encrypted-document-self-heal): add artifact search self-heal tests
```

### Task 8: Route artifact search through self-healer

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/artifact/SearchArtifactToolExecutor.kt:19-89`

**Change:** Inject `DocumentPersistenceSelfHealer` and replace direct `DocumentDao.getDocumentById` plus manual decryption with `readForConsumer` using `DocumentConsumer.ARTIFACT_SEARCH`.

**Verifies scenarios:** S7, E2

**Commit message:**

```text
feat(FEAT-encrypted-document-self-heal): self-heal artifact search reads
```

### Task 9: Add embedding consumer red tests

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/EmbeddingRegenerationWorkerTest.kt:1-117`

**Change:** Add failing Robolectric/JVM tests proving document embedding regeneration repairs plaintext Room rows before parsing blocks and still embeds literal block text `"alpha beta"` from the decrypted in-memory document.

**Verifies scenarios:** S8

**Commit message:**

```text
feat(FEAT-encrypted-document-self-heal): add embedding self-heal tests
```

### Task 10: Route embedding regeneration through self-healer

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/sync/EmbeddingRegenerationWorker.kt:155-198`

**Change:** Inject `DocumentPersistenceSelfHealer` into `EmbeddingRegenerationWorker` and its factory path, then replace direct `DocumentDao.getDocumentById` plus manual decryption with `readForConsumer` using `DocumentConsumer.EMBEDDING_REGENERATION`.

**Verifies scenarios:** S8

**Commit message:**

```text
feat(FEAT-encrypted-document-self-heal): self-heal embedding document reads
```

### Task 11: Wire dependency injection tests and provider

**Files:**
- Modify: `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/DataModule.kt:226-230`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/artifact/ArtifactToolExecutorImplTest.kt:24-240`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/artifact/SearchArtifactToolExecutorTest.kt:1-66`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/EmbeddingRegenerationWorkerTest.kt:1-117`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSourceTest.kt:57-119`

**Change:** Update constructors and test fixture creation for every class that now depends on `DocumentPersistenceSelfHealer`; add a Hilt provider only if constructor injection is not discovered automatically in the existing component graph.

**Verifies scenarios:** S3, S5, S7, S8

**Commit message:**

```text
feat(FEAT-encrypted-document-self-heal): wire document self-healer dependencies
```

### Task 12: Run focused and required validation

**Files:**
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/security/DocumentPersistenceSelfHealerTest.kt`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/artifact/ArtifactToolExecutorImplTest.kt:24-240`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/artifact/SearchArtifactToolExecutorTest.kt:1-66`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/sync/EmbeddingRegenerationWorkerTest.kt:1-117`
- Modify: `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSourceTest.kt:57-119`

**Change:** Execute focused tests, then required data-layer checks for this ticket: `./gradlew :core:data:testDebugUnitTest --tests "*DocumentPersistenceSelfHealerTest*" --tests "*ArtifactToolExecutorImplTest*" --tests "*SearchArtifactToolExecutorTest*" --tests "*EmbeddingRegenerationWorkerTest*" --tests "*FirestoreDataSourceTest*"`, `./gradlew :core:data:assemble`, and `./gradlew :core:data:ktlintCheck`.

**Verifies scenarios:** S1, S2, S3, S4, S5, S6, S7, S8, E1, E2, E3, M1

**Commit message:**

```text
feat(FEAT-encrypted-document-self-heal): validate document self-heal flow
```
