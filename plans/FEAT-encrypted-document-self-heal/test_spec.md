# Test Spec: Encrypted Document Self-Heal

## Test Framework & Run Command
Framework: Kotlin/JVM unit tests using JUnit 5, MockK, kotlinx-coroutines-test, Turbine for Flow tests when needed, and Robolectric for Worker tests that require Android framework APIs. Run: `GRADLE_USER_HOME=/tmp/pocket-crew-gradle ./gradlew :core:data:testDebugUnitTest --tests "*DocumentPersistenceSelfHealerTest*" --tests "*ArtifactToolExecutorImplTest*" --tests "*SearchArtifactToolExecutorTest*" --tests "*EmbeddingRegenerationWorkerTest*" --tests "*FirestoreDataSourceTest*" --no-daemon --stacktrace`.

## Scenario Index
S1 — Room plaintext document fields are encrypted and marked for sync.
S2 — Already encrypted Room document fields are a no-op.
S3 — New artifact documents persist encrypted fields.
S4 — Appending to a plaintext legacy artifact repairs it before update.
S5 — Firestore plaintext ingress is encrypted locally and repaired remotely.
S6 — Room plaintext egress is encrypted before Firestore write and repaired locally.
S7 — Artifact search repairs plaintext before returning block results.
S8 — Embedding regeneration repairs plaintext before embedding block text.
E1 — Locked vault blocks self-heal with a specific exception.
E2 — Warning logs never include plaintext or ciphertext payloads.
E3 — Firestore missing required document fields is skipped without local insert.
M1 — Mixed encrypted/plaintext fields repair only plaintext fields and preserve encrypted values.

## Happy Path Scenarios

### S1: Room plaintext document fields are encrypted and marked for sync
- **Given:** Room contains `DocumentEntity(id="doc-1", messageId=MessageId("msg-1"), json="{\"version\":\"1.0\",\"metadata\":{\"title\":\"Quarterly Plan\",\"author\":\"Ada\"},\"blocks\":[]}", title="Quarterly Plan", author="Ada", pendingSync=0)` and `FakeLocalDataEncryptionService` returns values prefixed with `"ENC:v1:"`.
- **When:** `DocumentPersistenceSelfHealer.encryptForRoom(entity, source=DocumentPersistenceSide.ROOM, markPendingSyncOnRepair=true)` is invoked.
- **Then:** The result entity has `json="ENC:v1:{\"version\":\"1.0\",\"metadata\":{\"title\":\"Quarterly Plan\",\"author\":\"Ada\"},\"blocks\":[]}"`, `title="ENC:v1:Quarterly Plan"`, `author="ENC:v1:Ada"`, `pendingSync=1`, and repairs equal `{DocumentFieldRepair(DocumentFieldName.JSON, DocumentPersistenceSide.ROOM), DocumentFieldRepair(DocumentFieldName.TITLE, DocumentPersistenceSide.ROOM), DocumentFieldRepair(DocumentFieldName.AUTHOR, DocumentPersistenceSide.ROOM)}`.

### S2: Already encrypted Room document fields are a no-op
- **Given:** Room contains `DocumentEntity(id="doc-2", messageId=MessageId("msg-2"), json="ENC:v1:{\"version\":\"1.0\",\"metadata\":{\"title\":\"Encrypted\"},\"blocks\":[]}", title="ENC:v1:Encrypted", author=null, pendingSync=0)`.
- **When:** `DocumentPersistenceSelfHealer.encryptForRoom(entity, source=DocumentPersistenceSide.ROOM, markPendingSyncOnRepair=true)` is invoked.
- **Then:** The result entity has `json="ENC:v1:{\"version\":\"1.0\",\"metadata\":{\"title\":\"Encrypted\"},\"blocks\":[]}"`, `title="ENC:v1:Encrypted"`, `author=null`, `pendingSync=0`, and repairs equal `{}`.

### S3: New artifact documents persist encrypted fields
- **Given:** `ArtifactToolExecutor.execute` receives `ToolCallRequest(toolName="start_artifact", argumentsJson="{\"title\":\"Roadmap\",\"author\":\"Ada\"}", assistantMessageId=MessageId("assistant-1"), provider="test", modelType=ModelType.FAST)` and the document validator returns `Result.success(Unit)`.
- **When:** `ArtifactToolExecutor.execute(request)` is invoked.
- **Then:** The inserted `DocumentEntity` has `title="ENC:v1:Roadmap"`, `author="ENC:v1:Ada"`, `json` starting with `"ENC:v1:"`, `pendingSync=0`, and the returned `resultJson` contains `"\"status\":\"success\""`.

### S4: Appending to a plaintext legacy artifact repairs it before update
- **Given:** `DocumentDao.getDocumentById("doc-legacy")` returns `DocumentEntity(id="doc-legacy", messageId=MessageId("assistant-1"), json="{\"version\":\"1.0\",\"metadata\":{\"title\":\"Legacy\"},\"blocks\":[]}", title="Legacy", author=null, pendingSync=0)` and `append_blocks` arguments are `{"artifactId":"doc-legacy","blocks":[{"type":"paragraph","text":"hello"}],"isComplete":true}`.
- **When:** `ArtifactToolExecutor.execute(request)` is invoked for `ToolDefinition.APPEND_BLOCKS.name`.
- **Then:** Room repair writes `json="ENC:v1:{\"version\":\"1.0\",\"metadata\":{\"title\":\"Legacy\"},\"blocks\":[]}"`, `title="ENC:v1:Legacy"`, `author=null`, `pendingSync=1`; the final updated document has `json` starting with `"ENC:v1:"`, `pendingSync=1`, and the returned `resultJson` contains `"\"isComplete\":true"`.

### S5: Firestore plaintext ingress is encrypted locally and repaired remotely
- **Given:** Firestore document id `"doc-firestore-1"` in collection `"documents"` has fields `message_id="msg-1"`, `chat_id="chat-1"`, `json="{\"version\":\"1.0\",\"metadata\":{\"title\":\"Remote\"},\"blocks\":[]}"`, `title="Remote"`, `author="Grace"`, `updated_at=5000`, `created_at=1000`, `deleted=false`.
- **When:** the documents snapshot listener processes an `ADDED` change for `"doc-firestore-1"`.
- **Then:** `DocumentDao.insertDocument` receives `json="ENC:v1:{\"version\":\"1.0\",\"metadata\":{\"title\":\"Remote\"},\"blocks\":[]}"`, `title="ENC:v1:Remote"`, `author="ENC:v1:Grace"`, `pendingSync=0`; Firestore repair writes fields `json="ENC:v1:{\"version\":\"1.0\",\"metadata\":{\"title\":\"Remote\"},\"blocks\":[]}"`, `title="ENC:v1:Remote"`, and `author="ENC:v1:Grace"` to `"users/test-user-id/documents/doc-firestore-1"`.

### S6: Room plaintext egress is encrypted before Firestore write and repaired locally
- **Given:** `DocumentDao.getClaimedForSync()` returns `DocumentEntity(id="doc-egress-1", messageId=MessageId("msg-1"), json="{\"version\":\"1.0\",\"metadata\":{\"title\":\"Local\"},\"blocks\":[]}", title="Local", author="Lin", pendingSync=2, updatedAt=7000)` and `messageDao.getMessageById(MessageId("msg-1"))` returns a message with `chatId=ChatId("chat-1")`.
- **When:** `FirestoreDataSource.syncPendingChanges()` is invoked.
- **Then:** the Firestore batch set for `"users/test-user-id/documents/doc-egress-1"` contains `json="ENC:v1:{\"version\":\"1.0\",\"metadata\":{\"title\":\"Local\"},\"blocks\":[]}"`, `title="ENC:v1:Local"`, `author="ENC:v1:Lin"`, `updated_at=7000`; Room repair writes the same encrypted values with `pendingSync=2`; after the batch succeeds, `DocumentDao.markSynced(listOf("doc-egress-1"))` is invoked.

### S7: Artifact search repairs plaintext before returning block results
- **Given:** `DocumentDao.searchSimilarBlocks` returns `["doc-search-1_0"]`, `DocumentDao.getDocumentById("doc-search-1")` returns `DocumentEntity(id="doc-search-1", messageId=MessageId("assistant-1"), json="{\"version\":\"1.0\",\"metadata\":{\"title\":\"Searchable\"},\"blocks\":[{\"type\":\"paragraph\",\"text\":\"needle text\"}]}", title="Searchable", author=null, pendingSync=0)`, and `EmbeddingEnginePort.getEmbedding("needle")` returns `floatArrayOf(0.1f, 0.2f)`.
- **When:** `SearchArtifactToolExecutor.execute` receives `SearchArtifactParams(artifactId="doc-search-1", query="needle")`.
- **Then:** Room repair writes `json="ENC:v1:{\"version\":\"1.0\",\"metadata\":{\"title\":\"Searchable\"},\"blocks\":[{\"type\":\"paragraph\",\"text\":\"needle text\"}]}"`, `title="ENC:v1:Searchable"`, `author=null`, `pendingSync=1`; the returned `resultJson` contains `"\"status\":\"success\""` and `"\"text\":\"needle text\""`.

### S8: Embedding regeneration repairs plaintext before embedding block text
- **Given:** `EmbeddingRegenerationWorker` consumes `PendingWork(messageIds=[], memoryIds=[], documentIds=["doc-embed-1"])`; `DocumentDao.getDocumentById("doc-embed-1")` returns `DocumentEntity(id="doc-embed-1", messageId=MessageId("assistant-1"), json="{\"version\":\"1.0\",\"metadata\":{\"title\":\"Embed\"},\"blocks\":[{\"type\":\"paragraph\",\"text\":\"alpha beta\"}]}", title="Embed", author=null, pendingSync=0)`; `EmbeddingEnginePort.getEmbedding("alpha beta")` returns `floatArrayOf(1.0f, 2.0f)`.
- **When:** `EmbeddingRegenerationWorker.doWork()` is invoked.
- **Then:** the worker result equals `ListenableWorker.Result.success()`, Room repair writes `json="ENC:v1:{\"version\":\"1.0\",\"metadata\":{\"title\":\"Embed\"},\"blocks\":[{\"type\":\"paragraph\",\"text\":\"alpha beta\"}]}"`, `title="ENC:v1:Embed"`, `author=null`, `pendingSync=1`, and `EmbeddingEnginePort.getEmbedding("alpha beta")` is invoked.

## Error & Edge Case Scenarios

### E1: Locked vault blocks self-heal with a specific exception
- **Given:** `LocalDataEncryptionService.encryptString("Secret")` throws `IllegalStateException("Encryption session locked")` and the input entity is `DocumentEntity(id="doc-locked", messageId=MessageId("msg-locked"), json="Secret", title="Secret", author=null, pendingSync=0)`.
- **When:** `DocumentPersistenceSelfHealer.encryptForRoom(entity, source=DocumentPersistenceSide.ROOM, markPendingSyncOnRepair=true)` is invoked.
- **Then:** `DocumentSelfHealLockedException` is thrown with message `"Document self-heal requires unlocked vault"` and `DocumentDao.repairDocumentEncryption` is invoked `0` times.

### E2: Warning logs never include plaintext or ciphertext payloads
- **Given:** the input entity is `DocumentEntity(id="doc-log", messageId=MessageId("msg-log"), json="{\"secret\":\"do not log\"}", title="do not log", author="do not log author", pendingSync=0)`.
- **When:** `DocumentPersistenceSelfHealer.encryptForRoom(entity, source=DocumentPersistenceSide.ROOM, markPendingSyncOnRepair=true)` is invoked.
- **Then:** every warning log message contains `"doc-log"` and one of `"JSON"`, `"TITLE"`, `"AUTHOR"`; every warning log message contains none of `"do not log"`, `"do not log author"`, `"ENC:v1:"`, and `"{\"secret\":\"do not log\"}"`.

### E3: Firestore missing required document fields is skipped without local insert
- **Given:** Firestore document id `"doc-missing"` in collection `"documents"` has fields `message_id="msg-1"`, `title="Remote"`, `updated_at=5000`, `created_at=1000`, `deleted=false`, and no `json` field.
- **When:** the documents snapshot listener processes an `ADDED` change for `"doc-missing"`.
- **Then:** `DocumentDao.insertDocument` is invoked `0` times, Firestore repair for `"users/test-user-id/documents/doc-missing"` is invoked `0` times, and a warning log contains `"doc-missing"` and `"JSON"`.

## Mutation Defense

### Lazy Implementation Risk
A lazy implementation could encrypt only `json` and ignore mixed plaintext `title` or `author` fields, which would still pass scenarios that use all-plaintext rows but would leave sensitive metadata plaintext.

### M1: Defense Scenario
- **Given:** Firestore document id `"doc-mixed"` has `message_id="msg-1"`, `chat_id="chat-1"`, `json="ENC:v1:{\"version\":\"1.0\",\"metadata\":{\"title\":\"Mixed\"},\"blocks\":[]}"`, `title="Mixed"`, `author="ENC:v1:Ada"`, `updated_at=9000`, `created_at=1000`, `deleted=false`.
- **When:** the documents snapshot listener processes an `ADDED` change for `"doc-mixed"`.
- **Then:** `DocumentDao.insertDocument` receives `json="ENC:v1:{\"version\":\"1.0\",\"metadata\":{\"title\":\"Mixed\"},\"blocks\":[]}"`, `title="ENC:v1:Mixed"`, `author="ENC:v1:Ada"`, `pendingSync=0`; Firestore repair writes exactly `title="ENC:v1:Mixed"` and does not rewrite `json` or `author`.
