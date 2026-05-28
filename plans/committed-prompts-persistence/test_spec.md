# Test Spec — Committed Prompts Persistence

**Status:** Awaiting Approval  
**Ticket ID:** `committed-prompts-persistence`  

---

## 1. Test Framework & Run Commands

We utilize **JUnit 5** and **Robolectric** (for database interaction validation) in the `:core:data` module.

### Command to run all local data unit and integration tests:
```bash
./gradlew :core:data:testDebugUnitTest
```

### Command to run only the prompt store unit tests:
```bash
./gradlew :core:data:testDebugUnitTest --tests "com.browntowndev.enclave.core.data.inference.RoomCommittedInferencePromptStoreTest"
```

### Command to run the rehydrator integration tests:
```bash
./gradlew :core:data:testDebugUnitTest --tests "com.browntowndev.enclave.core.data.local.ChatHistoryRehydratorIntegrationTest"
```

### Command to run the schema migration tests:
```bash
./gradlew :core:data:testDebugUnitTest --tests "com.browntowndev.enclave.core.data.local.EnclaveDatabaseMigrationTest"
```

---

## 2. Scenario Index

| Scenario ID | Type | Description |
|---|---|---|
| **SCN-PERSIST-001** | Happy Path | Verify `commit()` updates the in-memory cache and asynchronously persists the prompt to the Room database. |
| **SCN-PERSIST-002** | Happy Path | Verify `get()` resolves cache misses by reading from the Room database, caching the value, and returning it. |
| **SCN-PERSIST-003** | Happy Path | Verify `clear()` removes the prompt from the cache and asynchronously deletes it from the Room database. |
| **SCN-PERSIST-004** | Edge Case | Verify database Cascade delete: deleting a message entity automatically deletes its associated committed prompt. |
| **SCN-PERSIST-005** | Integration | Verify `ChatHistoryRehydrator` successfully resolves and integrates a Room-persisted prompt when rehydrating a chat history after app restart simulation. |
| **SCN-PERSIST-006** | Mutation Defense | Verify querying a non-existent or blank message ID returns exactly `null` rather than a default or stubbed string. |
| **SCN-PERSIST-007** | Mutation Defense | Verify distinct keys support: committing distinct prompts to distinct message IDs retrieves the correct distinct content for each ID. |

---

## 3. Test Scenarios

### SCN-PERSIST-001: Write-through cache write and immediate DB check
* **Given** an empty `RoomCommittedInferencePromptStore` backed by an in-memory `EnclaveDatabase`
  * **And** a user message with ID `"msg-123"` exists in the `message` table
* **When** `store.commit(MessageId("msg-123"), "Expanded user query with RAG contexts")` is called
* **Then** the in-memory cache must return `"Expanded user query with RAG contexts"` immediately when calling `store.get(MessageId("msg-123"))`
  * **And** the database table `message_committed_inference_prompt` must contain a record with `message_id` = `"msg-123"` and `inference_content` = `"Expanded user query with RAG contexts"` within 500 milliseconds

### SCN-PERSIST-002: Reload from database on cache-miss (restarting simulation)
* **Given** an in-memory `EnclaveDatabase` with a record in `message_committed_inference_prompt`:
  * `message_id` = `"msg-abc"`
  * `inference_content` = `"Prior RAG query turn"`
* **And** a new `RoomCommittedInferencePromptStore` instance is instantiated (re-hydrating with empty cache to simulate app restart)
* **When** `store.get(MessageId("msg-abc"))` is called
* **Then** the store must fetch from the database and return `"Prior RAG query turn"`
  * **And** a subsequent call to `store.get(MessageId("msg-abc"))` must resolve from the in-memory cache directly without querying the database again

### SCN-PERSIST-003: Clear prompt clears cache and deletes DB record
* **Given** a `RoomCommittedInferencePromptStore` with a cached and persisted prompt for ID `"msg-456"`:
  * `inference_content` = `"Stale prompt context"`
* **When** `store.clear(MessageId("msg-456"))` is called
* **Then** calling `store.get(MessageId("msg-456"))` must return exactly `null`
  * **And** the database table `message_committed_inference_prompt` must not contain any record with `message_id` = `"msg-456"` within 500 milliseconds

### SCN-PERSIST-004: Cascade deletion verification
* **Given** an in-memory `EnclaveDatabase` containing:
  * A message entity with ID `"msg-to-delete"`
  * An associated committed prompt in `message_committed_inference_prompt` with `message_id` = `"msg-to-delete"`
* **When** the message entity with ID `"msg-to-delete"` is deleted from the `message` table
* **Then** the record with `message_id` = `"msg-to-delete"` in `message_committed_inference_prompt` must be automatically deleted by SQLite cascade trigger
  * **And** calling `store.get(MessageId("msg-to-delete"))` (with an empty cache) must return exactly `null`

### SCN-PERSIST-005: Chat history rehydration integration with database reload
* **Given** an in-memory `EnclaveDatabase` containing:
  * A chat session with ID `"chat-1"`
  * A historical user message `"msg-user"` with content `"What is the weather?"`
  * An assistant response message `"msg-assistant"` with content `"It is sunny."`
  * A committed prompt in `message_committed_inference_prompt` for `"msg-user"` with content `"System: Weather info is sunny. User: What is the weather?"`
* **And** a new `RoomCommittedInferencePromptStore` (empty cache) and `ChatHistoryRehydrator` are configured
* **When** `rehydrator` is invoked to rehydrate the history of `"chat-1"` for a new turn
* **Then** the history payload set on the mock `LlmInferencePort` must contain a `ChatMessage` with `role` = `Role.USER` and `content` containing `"System: Weather info is sunny. User: What is the weather?"`
  * **And** it must not contain a message with content containing `"What is the weather?"` without the system prepended context (verifying that the committed prompt was used instead of the raw DB message content)

### SCN-PERSIST-006: Mutation Defense - Non-existent ID queries
* **Given** a `RoomCommittedInferencePromptStore` backing an empty `message_committed_inference_prompt` table
* **When** `store.get(MessageId("non-existent-msg-id"))` is called
* **Then** the returned value must be exactly `null`

### SCN-PERSIST-007: Mutation Defense - Distinct keys separation
* **Given** a `RoomCommittedInferencePromptStore`
  * **And** user messages with IDs `"msg-x"` and `"msg-y"` exist in the database
* **When** `store.commit(MessageId("msg-x"), "Prompt Content X")` is called
  * **And** `store.commit(MessageId("msg-y"), "Prompt Content Y")` is called
* **Then** calling `store.get(MessageId("msg-x"))` must return exactly `"Prompt Content X"`
  * **And** calling `store.get(MessageId("msg-y"))` must return exactly `"Prompt Content Y"`
