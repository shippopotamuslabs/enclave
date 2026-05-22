# FEAT-cloud-sync-event-feed Spec

## Feature Summary

Replace Pocket Crew's per-collection Firestore listener hydration path with a server-sequenced per-user sync event feed. Firebase Cloud Functions will observe allowlisted user entity collections, write idempotent `sync_events` entries with monotonic per-user sequences, and Android will consume one ordered feed for both initial hydration and live deltas. Room remains the local source of truth. Existing Firestore entity documents remain the canonical cloud payloads; events contain ordering metadata and pointers, not large encrypted payloads.

The new Android flow is:

1. Vault reconciliation completes.
2. Optional local pending upload runs.
3. Android replays `users/{uid}/sync_events` from its local `lastAppliedSequence`.
4. Android starts one live listener on `sync_events`.
5. Each event is applied only after required parent source documents are present locally.

Normal sync must not create placeholder `ChatEntity` or `MessageEntity` rows containing `"Syncing..."`. Placeholder cleanup may stay temporarily as migration cleanup for developer data, but placeholder creation is removed from the event-feed path.

External grounding:

- Android module boundary design follows the official modularization architecture guidance surfaced through AndroJack.
- Firebase Firestore triggers can be at-least-once and out-of-order, so Functions must be idempotent and Android must not assume trigger delivery order is commit order.
- Firestore transactions are the sequencing primitive for `sync_meta/state.nextSequence`.
- Firestore Security Rules do not constrain Admin SDK writes, so rules must deny client writes to server-owned sync collections while allowing Functions to write them.

## File Structure Table

| File | Action | Responsibility |
| --- | --- | --- |
| `plans/FEAT-cloud-sync-event-feed/spec.md` | Create | Approved implementation contract for this feature. |
| `plans/FEAT-cloud-sync-event-feed/test_spec.md` | Create | Scenario and validation contract for this feature. |
| `functions/src/syncEventFeed.ts` | Create | Cloud Functions sync feed module: allowlist, trigger handler, sequence transaction, idempotency guard, backfill callable, event type helpers. |
| `functions/src/syncEventFeed.test.ts` | Create | TypeScript unit tests for sequencing, duplicate trigger suppression, stale delete handling, allowlist filtering, and backfill. |
| `functions/src/index.ts` | Modify | Export sync feed triggers and callable without changing existing share handlers. |
| `functions/package.json` | Modify | Add test/build script adjustments only if the new tests require a narrower command or helper dependency already accepted by the project. |
| `firestore.rules` | Create | Track Firestore rules in repo; owner reads allowed, entity writes constrained, client writes denied for `sync_events`, `sync_meta`, and `sync_processed_events`. |
| `firebase.json` | Modify | Add Firestore rules deployment configuration while preserving existing storage, functions, and hosting entries. |
| `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/port/repository/CloudSyncPort.kt` | Modify | Add a domain-safe remote replay contract with no Firebase, Room, Android, or data-layer types. |
| `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/sync/CloudSyncEventReplayResult.kt` | Create | Domain result for remote feed replay: applied event count, last applied sequence, and whether more pages remain. |
| `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/security/StartCloudSyncListeningUseCase.kt` | Modify | Enforce order: optional upload, remote event replay, then live listener; fail without starting live listener if upload or replay fails. |
| `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/security/StartCloudSyncListeningUseCaseTest.kt` | Modify | Verify the new use-case ordering and failure behavior. |
| `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/DataModule.kt` | Modify | Provide any new data-layer sync state store dependencies using existing DataStore patterns and inject `PocketCrewDatabase` where Room transactions are required. |
| `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/EntitySyncAdapter.kt` | Modify | Keep the existing adapter contract for entity encode/decode/apply; add only non-Firestore-domain helper contracts if required by the event applier. |
| `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSource.kt` | Modify | Implement the new `CloudSyncPort` replay method, wire feed client/applier/listener, and keep pending upload and vault APIs unchanged. |
| `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/firestore/FirestoreSyncDependencies.kt` | Modify | Add database and sync state dependencies needed by the event applier without widening domain APIs. |
| `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/firestore/FirestoreSyncContext.kt` | Modify | Remove placeholder creation from normal sync context; keep logging, crypto, repair, purge, and optional cleanup helpers. |
| `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/firestore/FirestoreSyncAdapterRegistry.kt` | Modify | Retain the adapter list as the source of collection allowlist and adapter lookup; expose collection-to-adapter lookup for the feed applier. |
| `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/firestore/FirestoreListenerCoordinator.kt` | Modify | Replace per-collection listeners with a single `sync_events` listener ordered by `sequence`; keep serialization and detach behavior. |
| `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/firestore/FirestorePendingSyncEngine.kt` | Modify | Add common sync metadata to outbound entity writes: `_sync_device_id`, `_sync_client_mutation_id`, and `_sync_written_at`. Preserve existing adapter payloads and batching. |
| `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/firestore/FirestoreCloudPurgeClient.kt` | Modify | Keep purge listener detachment; ensure purge collection lists come from the registry where possible and do not depend on placeholder cleanup. |
| `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/firestore/FirestoreSyncEvent.kt` | Create | Internal data model for server events: sequence, operation, collection path, entity ID, entity path, source metadata, origin metadata, and schema version. |
| `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/firestore/FirestoreSyncEventCodec.kt` | Create | Parse Firestore `sync_events` documents into `FirestoreSyncEvent` with exact validation errors. |
| `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/firestore/FirestoreSyncEventFeedClient.kt` | Create | Page remote events, fetch source documents, attach/detach feed listener, and expose no Firebase types outside `:core:data`. |
| `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/firestore/FirestoreSyncEventApplier.kt` | Create | Apply ordered events in Room transactions, resolve parent dependencies before child rows, repair encrypted fields, and advance sequence only after successful application. |
| `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/firestore/FirestoreSyncDependencyGraph.kt` | Create | Declarative collection dependency map derived from Room foreign keys and adapter payload fields. |
| `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/firestore/FirestoreSyncStateStore.kt` | Create | DataStore-backed per-user state for `lastAppliedSequence`, stable local `deviceId`, and feed schema version. |
| `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/remote/firestore/handlers/*.kt` | Modify | Remove calls to `ensurePlaceholderMessage`; preserve existing timestamp conflict checks and pending-sync clearing behavior. |
| `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/FirestoreSyncPlaceholderDao.kt` | Modify | Retain only if needed for developer-data cleanup during migration; otherwise remove in a later explicit cleanup phase after tests prove no normal path calls it. |
| `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSourceTest.kt` | Modify | Cover data source replay, listener startup, vault-locked behavior, and no per-collection listener registration. |
| `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/FirestoreDataSourceTestFactory.kt` | Modify | Provide fake feed dependencies, database transaction dependency, and sync state store fakes. |
| `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/firestore/FirestoreSyncEventCodecTest.kt` | Create | Validate event parsing and exact malformed-event failures. |
| `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/firestore/FirestoreSyncEventApplierTest.kt` | Create | Verify parent resolution, sequence advancement, idempotency, delete handling, and no placeholder insertion. |
| `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/firestore/FirestoreSyncEventFeedClientTest.kt` | Create | Verify paged replay queries and listener query shape. |
| `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/firestore/FirestoreSyncDependencyGraphTest.kt` | Create | Verify dependency mapping for every sync adapter collection. |
| `core/data/src/test/kotlin/com/browntowndev/pocketcrew/core/data/remote/firestore/FirestoreSyncAdapterRegistryTest.kt` | Modify | Verify collection paths are unique and all paths are represented in the dependency graph and Functions allowlist documentation. |
| `app/src/main/kotlin/com/browntowndev/pocketcrew/presentation/MainViewModel.kt` | Modify | Keep vault reconciliation entry points, but rely on the updated use case for replay-before-listen semantics. |
| `app/src/test/kotlin/com/browntowndev/pocketcrew/presentation/MainViewModelTest.kt` | Modify | Update assertions from direct listener behavior to use-case replay/listen behavior. |
| `app/src/test/kotlin/com/browntowndev/pocketcrew/sync/CloudSyncStartOrderingTest.kt` | Modify | Verify auth/vault state starts ordered replay through the use case and never calls `CloudSyncPort.startListening()` directly. |
| `feature/settings/src/main/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsGeneralDelegate.kt` | Modify | Keep reauth/adopt flow, but rely on the updated use case for replay-before-listen semantics after unlock or cloud adoption. |
| `feature/settings/src/test/kotlin/com/browntowndev/pocketcrew/feature/settings/SettingsGeneralDelegateTest.kt` | Modify | Update settings sync-order assertions for replay-before-listen. |

## API Contracts And Interfaces

### CloudSyncPort Contract

Modify `CloudSyncPort` with a domain-safe method:

- `suspend fun syncRemoteChangesFromCloud(): Result<CloudSyncEventReplayResult>`

Existing methods remain:

- `fun startListening()`
- `fun stopListening()`
- `suspend fun syncAllLocalDataToCloud(): Result<Unit>`
- `suspend fun syncEntitiesToCloud(chats: List<Chat>, messages: List<Message>): Result<Unit>`
- `suspend fun syncPendingChanges(): Result<Unit>`
- vault metadata methods
- purge methods

`startListening()` must only attach the live `sync_events` listener. It must not perform a bulk replay and must not attach listeners to entity collections.

### CloudSyncEventReplayResult

Create a domain model with these fields:

- `eventsApplied: Int`
- `lastAppliedSequence: Long`
- `hasMore: Boolean`

`hasMore` is `true` when the replay stops because it hit the configured page limit and another replay pass should continue immediately. The use case must loop until `hasMore` is `false` before starting live listening.

### StartCloudSyncListeningUseCase Contract

`StartCloudSyncListeningUseCase.invoke(uploadLocalFirst: Boolean)` must:

1. If `uploadLocalFirst` is `true`, call `cloudSyncPort.syncAllLocalDataToCloud()` and fail if it returns failure.
2. Call `cloudSyncPort.syncRemoteChangesFromCloud()` repeatedly until `hasMore` is `false`.
3. Call `cloudSyncPort.startListening()` only after upload and replay success.
4. Return `Result.failure` without starting the listener on upload failure, replay failure, vault-locked failure, or not-signed-in failure.

### Functions Sync Event Contract

New server-owned collections:

- `users/{uid}/sync_meta/state`
- `users/{uid}/sync_events/{paddedSequence}`
- `users/{uid}/sync_processed_events/{eventIdHash}`

`sync_meta/state` fields:

- `nextSequence: number`
- `schemaVersion: 1`
- `feedInitialized: boolean`
- `updatedAtMillis: number`
- `backfilledAtMillis: number | null`

`sync_events/{paddedSequence}` fields:

- `sequence: number`
- `schemaVersion: 1`
- `operation: "upsert" | "delete"`
- `collectionPath: string`
- `entityType: string`
- `entityId: string`
- `entityPath: string`
- `sourceExists: boolean`
- `sourceUpdatedAt: number`
- `sourceDeleted: boolean`
- `deviceId: string | null`
- `clientMutationId: string | null`
- `triggerEventId: string`
- `emittedAtMillis: number`

`sync_processed_events/{eventIdHash}` fields:

- `triggerEventId: string`
- `sequence: number`
- `processedAtMillis: number`

The Functions handler must process only allowlisted direct user subcollections. The initial allowlist is:

- `chats`
- `messages`
- `chat_summaries`
- `memories`
- `vision_analysis`
- `message_files`
- `web_search_sources`
- `message_transcript_segments`
- `documents`
- `api_credentials`
- `api_keys`
- `api_model_configs`
- `local_models`
- `local_model_configs`
- `default_models`
- `tts_providers`
- `media_providers`
- `studio_albums`
- `studio_gallery`

Excluded collections:

- `sync_events`
- `sync_meta`
- `sync_processed_events`
- vault root metadata fields on `users/{uid}`
- shared chat/public collections

### Function Idempotency And Stale Trigger Rules

Firestore triggers are at-least-once and not ordered. The function must not encode stale trigger order as domain truth.

For every trigger:

1. Derive `uid`, `collectionPath`, and `entityId` from params.
2. Ignore non-allowlisted collections.
3. Use the Cloud Functions event ID as the idempotency key.
4. In one Firestore transaction, read `sync_processed_events/{eventIdHash}`. If it exists, return without writing another sync event.
5. Read the current source entity document.
6. If the trigger is a delete and the source document currently exists with a newer or different mutation identity than the deleted snapshot, emit an `upsert` event for the current source document instead of a stale `delete`.
7. Otherwise emit `upsert` for existing source docs and `delete` for absent source docs.
8. Increment `sync_meta/state.nextSequence`, write `sync_events/{paddedSequence}`, write `sync_processed_events/{eventIdHash}`, and update `sync_meta/state` atomically.

### Android Event Replay Contract

`FirestoreSyncEventFeedClient`:

- Query path: `users/{uid}/sync_events`
- Query constraints: `whereGreaterThan("sequence", lastAppliedSequence)`, `orderBy("sequence")`, `limit(pageSize)`
- Default `pageSize`: `200`
- Live listener query: same `whereGreaterThan` and `orderBy` shape based on the current state store sequence at attachment time

`FirestoreSyncEventCodec`:

- Rejects missing `sequence` with `IllegalArgumentException("sync event missing sequence")`
- Rejects unsupported `schemaVersion` with `IllegalArgumentException("unsupported sync event schema version: X")`
- Rejects blank `collectionPath` with `IllegalArgumentException("sync event missing collectionPath")`
- Rejects blank `entityId` with `IllegalArgumentException("sync event missing entityId")`
- Rejects unsupported `operation` with `IllegalArgumentException("unsupported sync event operation: VALUE")`

`FirestoreSyncEventApplier`:

- Ignores events with `sequence <= lastAppliedSequence`.
- Looks up the adapter by `collectionPath`.
- For `upsert`, fetches the current source document at `entityPath`.
- Repairs sensitive fields through the existing `FirestoreSensitiveFieldRepairer` before decoding.
- Resolves required parent source documents recursively before applying the child.
- Applies the event and all fetched parents in a Room transaction.
- Advances `lastAppliedSequence` only after the transaction commits.
- For `delete`, calls the adapter delete path and then advances sequence after commit.
- Enqueues embedding regeneration exactly as existing handlers do when message/document content changes.
- Does not call `ensurePlaceholderMessage` or `FirestoreSyncPlaceholderDao`.

### Dependency Graph Contract

The dependency graph must map every sync collection to required parent refs:

| Collection | Required parent refs |
| --- | --- |
| `chats` | none |
| `messages` | `chats/{chat_id}` and optional `messages/{user_message_id}` when present |
| `chat_summaries` | `chats/{chat_id}` and optional `messages/{last_summarized_message_id}` when present |
| `message_files` | `messages/{message_id}` and `chats/{chat_id}` when `chat_id` exists |
| `web_search_sources` | `messages/{message_id}` and `chats/{chat_id}` when `chat_id` exists |
| `message_transcript_segments` | `messages/{message_id}` and `chats/{chat_id}` when `chat_id` exists |
| `vision_analysis` | `messages/{user_message_id}` and `chats/{chat_id}` when `chat_id` exists |
| `documents` | `messages/{message_id}` and `chats/{chat_id}` when `chat_id` exists |
| `local_model_configs` | `local_models/{local_model_id}` |
| `api_model_configs` | `api_credentials/{api_credentials_id}` when present |
| `default_models` | any referenced `local_model_configs`, `api_model_configs`, `tts_providers`, and `media_providers` |
| `studio_gallery` | `studio_albums/{album_id}` when present |
| `memories` | none |
| `local_models` | none |
| `api_credentials` | none |
| `api_keys` | none |
| `tts_providers` | none |
| `media_providers` | none |
| `studio_albums` | none |

If a required parent source document is missing and the child is not a delete/tombstone, replay fails with `IllegalStateException("missing required parent COLLECTION/ID for COLLECTION/ID")` and `lastAppliedSequence` remains unchanged.

### Conflict Policy

The cloud source document is canonical for clean local rows. Existing local `updatedAt` guards remain for dirty or newer local rows:

- If local row has `pendingSync != 0` and the event is not from the same `_sync_device_id`, preserve the local row and leave pending sync intact.
- If local row is clean, apply the remote event according to sequence order.
- If the event originated from the same `_sync_device_id`, treat it as an echo and allow the existing adapter merge to clear `pendingSync`.
- Deletes use the same adapter soft-delete behavior currently used by listeners.

This plan does not add Room sync-version columns. If later implementation proves timestamp and pending-sync metadata cannot protect a specific entity type, that is a schema escalation and requires explicit developer authorization before adding columns.

## Data Model And Schema Changes

### Firestore Schema

Add server-owned sync feed collections under each user:

- `users/{uid}/sync_meta/state`
- `users/{uid}/sync_events/{paddedSequence}`
- `users/{uid}/sync_processed_events/{eventIdHash}`

Add optional metadata fields to synced entity documents during Android upload:

- `_sync_device_id`
- `_sync_client_mutation_id`
- `_sync_written_at`

These fields are not encrypted and must contain no user content.

### Room Schema

No Room table or column changes are planned for the initial implementation. This avoids a local migration and follows the schema lock. The per-user `lastAppliedSequence` and stable local device ID live in DataStore.

### DataStore Schema

Add keys managed only by `FirestoreSyncStateStore`:

- `cloud_sync_device_id`
- `cloud_sync_last_applied_sequence_{uid}`
- `cloud_sync_feed_schema_version_{uid}`

### Security Rules

Add tracked Firestore rules:

- Owner may read their own `users/{uid}/sync_events`.
- Client writes to `users/{uid}/sync_events/{eventId}` are denied.
- Client writes to `users/{uid}/sync_meta/{doc}` are denied.
- Client reads and writes to `users/{uid}/sync_processed_events/{doc}` are denied.
- Owner may read/write allowlisted entity collections under their own user document, subject to existing app constraints.
- Client writes must not set server-owned event documents. Entity metadata fields beginning with `_sync_` are allowed only on allowlisted entity documents and must be scalar strings/numbers.

If production Firestore rules already exist outside the repo, implementation must import them into `firestore.rules` before replacing them. Do not deploy a deny-by-default ruleset that omits existing required app access.

## Ordered Implementation Tasks

1. Create Functions sync feed module and tests.
   - Implement allowlist, event type mapping, idempotency key storage, transaction sequencing, stale delete protection, and exports.
   - Verified by: SCN-FUNC-001, SCN-FUNC-002, SCN-FUNC-003, SCN-FUNC-004, SCN-MUT-001.

2. Add Functions backfill callable.
   - Authenticated callable scans existing allowlisted user collections, emits baseline events in dependency order using the same sequence transaction, sets `feedInitialized` to `true`, and is idempotent when called again.
   - Verified by: SCN-BACKFILL-001, SCN-BACKFILL-002.

3. Add tracked Firestore rules and Firebase config.
   - Create `firestore.rules`, wire it in `firebase.json`, and preserve existing storage/functions/hosting behavior.
   - Verified by: SCN-SEC-001, SCN-SEC-002.

4. Add domain replay result and extend `CloudSyncPort`.
   - Add `CloudSyncEventReplayResult` and `syncRemoteChangesFromCloud()` with no Firebase, Room, Android, or data-layer types in domain.
   - Verified by: SCN-USECASE-001, SCN-USECASE-002, SCN-USECASE-003.

5. Update `StartCloudSyncListeningUseCase`.
   - Enforce upload, replay-until-drained, then listener startup. Propagate upload/replay failures and do not start the listener on failure.
   - Verified by: SCN-USECASE-001, SCN-USECASE-002, SCN-USECASE-003.

6. Add Android sync state store.
   - Persist stable device ID and per-user `lastAppliedSequence` in DataStore. Add fakes for tests.
   - Verified by: SCN-ANDROID-001, SCN-ANDROID-005, SCN-ANDROID-006.

7. Add event codec, feed client, and dependency graph.
   - Parse event docs with exact validation failures, page replay queries, live listener query shape, and parent dependency mapping for every adapter.
   - Verified by: SCN-ANDROID-001, SCN-ANDROID-002, SCN-ANDROID-003, SCN-ANDROID-008.

8. Add event applier.
   - Apply upsert/delete events transactionally, fetch and apply required parents recursively, repair sensitive fields, preserve embedding regeneration behavior, and advance sequence only after commit.
   - Verified by: SCN-ANDROID-001, SCN-ANDROID-004, SCN-ANDROID-005, SCN-ANDROID-006, SCN-ANDROID-007, SCN-ANDROID-009.

9. Replace live listener implementation.
   - Modify `FirestoreListenerCoordinator` so it attaches exactly one `sync_events` listener and no entity collection listeners.
   - Verified by: SCN-LIVE-001, SCN-LIVE-002, SCN-MUT-002.

10. Wire `FirestoreDataSource`.
    - Build feed client/applier/listener from existing dependencies, implement `syncRemoteChangesFromCloud()`, keep vault checks and sync mutex behavior, and ensure `startListening()` skips when signed out or vault locked.
    - Verified by: SCN-DATASOURCE-001, SCN-DATASOURCE-002, SCN-DATASOURCE-003.

11. Enrich outbound pending writes.
    - Add `_sync_device_id`, `_sync_client_mutation_id`, and `_sync_written_at` to all pending sync uploads without changing adapter-specific encrypted payloads.
    - Verified by: SCN-PENDING-001, SCN-PENDING-002.

12. Remove placeholder creation from normal sync.
    - Remove `ensurePlaceholderMessage` calls from message child handlers and message handler. Keep defensive FK error reporting in tests, but no production path should insert `"Syncing..."` placeholder rows.
    - Verified by: SCN-ANDROID-004, SCN-PLACEHOLDER-001, SCN-MUT-002.

13. Update app and settings entry-point tests.
    - Keep MainViewModel and SettingsGeneralDelegate vault reconciliation behavior intact while updating assertions to the replay-before-listen use case.
    - Verified by: SCN-ENTRY-001, SCN-ENTRY-002, SCN-ENTRY-003.

14. Adjust purge behavior.
    - Ensure purge detaches the event listener, uses registry-backed collection names, and allows Functions to emit delete events for other devices. Account deletion may delete sync feed collections because no future client should hydrate that user.
    - Verified by: SCN-PURGE-001, SCN-PURGE-002.

15. Add checkpoint design hook without implementing compaction.
    - Keep event replay page-based and resumable. Add explicit schema fields so a later `sync_snapshots` implementation can be added without changing event semantics.
    - Verified by: SCN-CHECKPOINT-001.

16. Run focused then broadened validation.
    - Functions tests and Android module tests must pass before handoff.
    - Verified by every scenario in `test_spec.md`.

## Phased Rollout And Placeholder Removal

Phase 1: Server feed foundation.

- Add Functions sync feed and backfill.
- Add security rules.
- Existing Android listeners may still exist until Android consumer lands.

Phase 2: Android replay consumer.

- Add DataStore state, event codec, feed client, dependency graph, and applier.
- Add `CloudSyncPort.syncRemoteChangesFromCloud()`.
- Update use case to replay before listener startup.

Phase 3: Live listener switch.

- Replace per-collection listener registration with single `sync_events` listener.
- Keep retry logging for unexpected FK failures, but dependency resolver should prevent normal FK races.

Phase 4: Placeholder retirement.

- Remove all normal calls to placeholder insertion.
- Keep or remove `FirestoreSyncPlaceholderDao` only after tests prove no production path references it. If deleting the DAO requires no Room schema change, it can be done in the same feature. If any Room schema change is proposed, request explicit developer authorization first.

Phase 5: Checkpoint and compaction.

- After the event feed is stable, add `sync_snapshots` and safe pruning only when the app has a device registry or a retention policy. Until then, keep events unpruned for correctness.

## Drift And Escalation Rules

- Do not modify `AGENTS.md` or any file under `contracts/`.
- Do not expose Firebase, Firestore, Room, DataStore, Android, or Hilt types through `:core:domain`.
- Do not add Room tables or columns without explicit developer authorization.
- Do not replace Room as the source of truth.
- Do not store decrypted content or large encrypted payloads inside `sync_events`.
- Do not rely on Cloud Functions trigger delivery order as commit order.
- Do not allow clients to write `sync_events`, `sync_meta`, or `sync_processed_events`.
- If an implementation discovers existing Firestore rules outside the repo, import and preserve them before adding sync feed restrictions.
- If a new syncable collection is found, add it to the Functions allowlist, Android adapter registry, dependency graph, security rules, and tests in the same change.
- If parent resolution cannot make a child insert safe without a placeholder, stop and report the exact collection and missing FK path before adding placeholders or schema changes.
- If conflict handling needs per-entity server sequence columns in Room, stop and request explicit developer authorization for the schema migration.
