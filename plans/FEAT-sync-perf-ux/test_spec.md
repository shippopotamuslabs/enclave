# Test Specification: Firestore Sync Performance and Unlock Progress Improvements

This test specification defines the acceptance criteria and test scenarios using Gherkin format for the sync performance and unlock progress improvements.

---

### Phase 1: Data Sync Fetch Caching

#### [SCENARIO-01] Prefetching resolves full dependency closure for upsert events
- **Given** the user ID is "user_123"
- **And** a list of remote sync events is:
  | sequence | operation | collectionPath | entityId |
  | 100 | "upsert" | "messages" | "msg1" |
  | 101 | "delete" | "memories" | "mem1" |
- **And** the message at "messages/msg1" contains the following data map:
  | key | value |
  | "chat_id" | "chat1" |
  | "user_message_id" | "msg0" |
- **And** the chat at "chats/chat1" contains no parent fields
- **And** the message at "messages/msg0" contains no parent fields
- **When** `FirestoreSyncDocumentCache.prefetch` is executed for these events
- **Then** the cache must execute bounded parallel requests to Firestore for:
  - "users/user_123/messages/msg1"
- **And** the cache must analyze the fetched message data to resolve required parents:
  - "chats/chat1"
  - "messages/msg0"
- **And** the cache must fetch those parent documents recursively from Firestore
- **And** the cache must ignore the delete event for "memories/mem1"
- **And** duplicate document paths discovered across root events and parent traversal must be fetched at most once during the prefetch pass

#### [SCENARIO-02] Missing required parent surfaces as IllegalStateException during apply
- **Given** the user ID is "user_123"
- **And** a sync event is:
  | sequence | operation | collectionPath | entityId |
  | 100 | "upsert" | "messages" | "msg1" |
- **And** the message at "messages/msg1" contains:
  | key | value |
  | "chat_id" | "chat1" |
- **And** the document "users/user_123/chats/chat1" does not exist in the remote database
- **When** the applier runs `apply` for "messages/msg1"
- **Then** the applier must throw `IllegalStateException` with the exact message:
  ```
  missing required parent chats/chat1 for messages/msg1
  ```
- **And** the applier must not open or commit any Room database transaction for this event
- **And** the state store's `lastAppliedSequence` must not advance past the current sequence

#### [SCENARIO-03] RoomFirestoreSyncLocalStore decodes from cached snapshots first
- **Given** the document cache contains a cached `DocumentSnapshot` for path "users/user_123/chats/chat1"
- **When** `RoomFirestoreSyncLocalStore.upsert` is called for "chats/chat1"
- **Then** the store must retrieve and decode from the cached `DocumentSnapshot`
- **And** the store must execute exactly `0` network requests to Firestore for this path

#### [SCENARIO-04] Sensitive field repair invalidates cached snapshot before decode
- **Given** the document cache contains a cached `DocumentSnapshot` for path "users/user_123/api_keys/key1"
- **And** the `FirestoreSensitiveFieldRepairer` determines that the inbound document data is unencrypted/repaired
- **When** the local store writes the repaired data back to Firestore
- **Then** the local store must invalidate the cache entry for "users/user_123/api_keys/key1"
- **And** the local store must fetch the fresh repaired snapshot from Firestore before decoding it via the sync adapter

#### [SCENARIO-05] Aggregation count query falls back to indeterminate progress on failure
- **Given** the cloud sync port starts replaying events
- **And** the Firestore aggregation count query for `users/user_123/sync_events` fails with a network exception
- **When** the startup sync use case begins
- **Then** the sync execution must proceed successfully
- **And** the progress callback must be invoked with:
  | progressFraction | status |
  | null | "Checking for updates..." |

#### [SCENARIO-06] Replay clears document cache even when apply fails
- **Given** a replay page contains prefetched documents for user "user_123"
- **And** applying sequence `100` fails with a non-cancellation exception
- **When** `FirestoreDataSource.syncRemoteChangesFromCloud()` returns failure
- **Then** the document cache must be cleared in a `finally` block
- **And** the state store's `lastAppliedSequence` must not advance for the failed event
- **And** the next replay attempt must not decode from the stale cached snapshots from the failed attempt

#### [SCENARIO-07] Remaining event count is computed after stale cursor recovery
- **Given** local Room state is empty
- **And** the persisted sync cursor is stale and greater than zero
- **When** `getRemainingEventsCount()` is invoked for the signed-in user
- **Then** stale cursor recovery must run before the aggregation count query uses `lastAppliedSequence`
- **And** the count query must target `users/user_123/sync_events` with `sequence > recoveredLastAppliedSequence`

#### [SCENARIO-08] Live-drain prefetches only contiguous events starting from last applied sequence
- **Given** the last applied sequence in the state store is `50`
- **And** the live listener buffers future events in `pendingLiveEvents` map:
  | sequence | operation | collectionPath | entityId |
  | 51 | "upsert" | "messages" | "msg51" |
  | 52 | "upsert" | "messages" | "msg52" |
  | 54 | "upsert" | "messages" | "msg54" |
- **When** live-drain is triggered on event arrival
- **Then** the listener coordinator must prefetch events `51` and `52`
- **And** the coordinator must not prefetch event `54` (as sequence `53` is missing)
- **And** the coordinator must clear the document cache once the contiguous drain attempt finishes

---

### Phase 2: Background Embedding & Re-indexing

#### [SCENARIO-09] Embedding regeneration coordinator debounces worker scheduling
- **Given** no embedding worker is currently running or scheduled
- **When** three enqueues occur in rapid succession:
  - `enqueueMessage("m1")` at 0ms
  - `enqueueMessage("m2")` at 100ms
  - `enqueueDocument("d1")` at 200ms
- **Then** before virtual time reaches 500ms after the last enqueue, WorkManager must receive `0` unique work requests
- **And** after virtual time advances past the debounce window, WorkManager must receive exactly `1` unique work request named "enclave_embedding_regen"
- **And** the pending work consumed by the worker must include "m1", "m2", and "d1"

#### [SCENARIO-10] Embedding coordinator retains failed pending work
- **Given** a pending batch contains message "m1" and document "d1"
- **And** the worker consumes the pending batch
- **When** processing the batch fails and `markFailed` is called
- **Then** a later `consumePending` call must return "m1" and "d1"
- **And** `onWorkerFinished` must schedule another debounced worker run when pending work remains

#### [SCENARIO-11] Embedding worker propagates cancellation and retries recoverable failures
- **Given** the embedding worker is running a batch of jobs
- **When** the parent coroutine scope is cancelled
- **Then** the worker must immediately throw `CancellationException`
- **And** the worker must not catch or swallow this exception
- **And** if a database transaction fails with a recoverable timeout, the worker must return `Result.retry()`

#### [SCENARIO-12] Embedding worker writes progress through WorkManager progress data
- **Given** the embedding worker processes a batch with `4` total items
- **When** two items have completed successfully
- **Then** the worker must call `setProgress()` with persisted WorkManager data representing current `2`, total `4`, and fraction `0.5f`
- **And** the worker must not call `EmbeddingProgressManager.updateProgress()` directly as the worker-to-UI bridge

#### [SCENARIO-13] Embedding worker skips malformed documents without failing the worker
- **Given** the embedding worker processes a batch of documents: "doc1", "doc2", and "doc3"
- **And** decoding the JSON payload for "doc2" throws a decryption or parsing error
- **When** the worker executes `processDocument("doc2")`
- **Then** the worker must log the error
- **And** the worker must skip the remainder of "doc2" block generation
- **And** the worker must continue to process "doc3" successfully
- **And** the worker must return `Result.success()` if "doc1" and "doc3" finish without errors

#### [SCENARIO-14] Embedding worker serializes engine calls by default
- **Given** the injected `EmbeddingEnginePort` does not expose a concurrency capability or reports `maxConcurrentEmbeddings = 1`
- **And** the batch contains `5` items to re-index
- **When** the worker processes the batch
- **Then** the worker must invoke `embeddingEngine.getEmbedding` sequentially, ensuring no two embeddings are generated in parallel

#### [SCENARIO-15] Embedding worker processes in parallel only when engine supports concurrent embeddings
- **Given** the injected `EmbeddingEnginePort` explicitly reports `maxConcurrentEmbeddings = 4`
- **And** the batch contains `10` items to re-index
- **When** the worker processes the batch
- **Then** the worker must execute the embedding generation using structured concurrency
- **And** the number of concurrent active jobs invoking `embeddingEngine.getEmbedding` must never exceed `4`

---

### Phase 3: Sync Progress Feedback & UI

#### [SCENARIO-16] Unlock sync progress is reported and cleared correctly
- **Given** the remaining event count query returns `300` events
- **And** the sync replay pages events in size of `200`
- **When** the first page of `200` events is applied
- **Then** the progress callback must be invoked with:
  | progressFraction | status |
  | 0.67f | "Syncing updates (200/300)..." |
- **And** when the second page of `100` events is applied, the progress callback must be invoked with:
  | progressFraction | status |
  | 1.00f | "Syncing updates (300/300)..." |
- **And** when sync finishes successfully, the `unlockSyncProgress` and `unlockSyncStatus` in `MainViewModel` must be reset to `null`
- **And** if sync fails or is cancelled, the progress states must be reset to `null`

#### [SCENARIO-17] Unlock progress handles changing or unavailable totals
- **Given** the remaining event count query returns `0` or fails
- **When** sync replay still applies one or more pages
- **Then** the progress callback must keep `progressFraction` as `null`
- **And** the status must remain useful without claiming a false total, such as "Syncing updates..."
- **And** the sync use case must still start the live listener after replay succeeds

#### [SCENARIO-18] LinearProgressIndicator renders with visual purity and correct test tags
- **Given** the vault is in the unlocking state
- **And** the `unlockSyncProgress` is `0.75f`
- **And** the `unlockSyncStatus` is "Syncing updates..."
- **When** `VaultUnlockContent` is composed
- **Then** the progress indicator must have test tag "unlock_sync_progress_indicator"
- **And** it must draw with `strokeCap = StrokeCap.Butt`, `gapSize = 0.dp`, and `drawStopIndicator = {}`
- **And** the status text must display "Syncing updates..." with test tag "unlock_sync_status_text"
- **And** when `unlockSyncProgress` is `null`, the indicator with test tag "unlock_sync_progress_indicator_indeterminate" must be rendered

---

### Mutation Defense

#### [SCENARIO-19] Cache bypass detection (Mutation Defense)
- **Given** an instrumented Firestore test double records document `get()` calls
- **And** the document cache is pre-populated with `5` document snapshots
- **When** a replay phase executes `upsert` for these `5` documents
- **Then** the test double must verify that `firestore.document().get()` is called exactly `0` times for those cached paths
- **And** if any call bypasses the cache to fetch directly from the remote Firestore database during normal operation, the test execution must fail
