# FEAT-cloud-sync-event-feed Test Spec

## Test Framework And Exact Run Commands

TypeScript Functions tests:

- `npm --prefix functions test` (pure handler tests; runs without the Firestore emulator)
- `npm --prefix functions run test:rules` (security-rules tests; wraps `mocha lib/*.rules.test.js` inside `firebase emulators:exec --only firestore`)

Focused Android domain tests:

- `./gradlew :core:domain:testDebugUnitTest --tests "*StartCloudSyncListeningUseCaseTest*"`

Focused Android data tests:

- `./gradlew :core:data:testDebugUnitTest --tests "*FirestoreSyncEventCodecTest*"`
- `./gradlew :core:data:testDebugUnitTest --tests "*FirestoreSyncEventApplierTest*"`
- `./gradlew :core:data:testDebugUnitTest --tests "*FirestoreSyncEventFeedClientTest*"`
- `./gradlew :core:data:testDebugUnitTest --tests "*FirestoreSyncDependencyGraphTest*"`
- `./gradlew :core:data:testDebugUnitTest --tests "*FirestoreDataSourceTest*"`

Focused app and settings tests:

- `./gradlew :app:testDebugUnitTest --tests "*MainViewModelTest*"`
- `./gradlew :app:testDebugUnitTest --tests "*CloudSyncStartOrderingTest*"`
- `./gradlew :feature:settings:testDebugUnitTest --tests "*SettingsGeneralDelegateTest*"`

Required implementation validation after focused tests pass:

- `npm --prefix functions test`
- `npm --prefix functions run test:rules`
- `./gradlew :core:domain:testDebugUnitTest`
- `./gradlew :core:data:testDebugUnitTest`
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :feature:settings:testDebugUnitTest`
- `./gradlew :core:data:assembleDebug`
- `./gradlew :app:kspDebugKotlin`
- `./gradlew :core:data:ktlintCheck`
- `./gradlew detekt`

## Scenario Index

| Scenario ID | Area | Summary |
| --- | --- | --- |
| SCN-FUNC-001 | Functions | Upsert trigger writes a monotonic sync event. |
| SCN-FUNC-002 | Functions | Duplicate trigger event ID writes no duplicate event. |
| SCN-FUNC-003 | Functions | Stale delete after recreate does not emit destructive delete. |
| SCN-FUNC-004 | Functions | Non-allowlisted collection is ignored. |
| SCN-BACKFILL-001 | Functions | Backfill emits dependency-ordered baseline events. |
| SCN-BACKFILL-002 | Functions | Backfill is idempotent after `feedInitialized` is true. |
| SCN-SEC-001 | Security | Owner can read sync events but cannot write them. |
| SCN-SEC-002 | Security | Non-owner cannot read owner sync events. |
| SCN-USECASE-001 | Domain | Upload, replay, and listener start occur in order. |
| SCN-USECASE-002 | Domain | Upload failure prevents replay and listener start. |
| SCN-USECASE-003 | Domain | Replay failure prevents listener start. |
| SCN-ANDROID-001 | Android data | Fresh hydrate applies events after sequence zero. |
| SCN-ANDROID-002 | Android data | Malformed event fails with exact exception message. |
| SCN-ANDROID-003 | Android data | Feed client query uses `sequence > lastAppliedSequence`. |
| SCN-ANDROID-004 | Android data | Child-before-parent event resolves parent without placeholder rows. |
| SCN-ANDROID-005 | Android data | Sequence advances only after Room transaction success. |
| SCN-ANDROID-006 | Android data | Replayed events at or below last applied sequence are ignored. |
| SCN-ANDROID-007 | Android data | Delete event uses adapter delete behavior. |
| SCN-ANDROID-008 | Android data | Every adapter collection has a dependency graph entry. |
| SCN-ANDROID-009 | Android data | Missing required parent fails without advancing sequence. |
| SCN-LIVE-001 | Live sync | Listener attaches to `sync_events` only. |
| SCN-LIVE-002 | Live sync | Live event applies and advances sequence once. |
| SCN-DATASOURCE-001 | Data source | Replay fails when signed out. |
| SCN-DATASOURCE-002 | Data source | Replay fails when vault is locked. |
| SCN-DATASOURCE-003 | Data source | Replay loops until `hasMore` is false. |
| SCN-PENDING-001 | Pending upload | Outbound writes include sync origin metadata. |
| SCN-PENDING-002 | Pending upload | Adapter encrypted payload fields are preserved. |
| SCN-PLACEHOLDER-001 | Placeholder removal | Normal sync inserts no `"Syncing..."` chat or message. |
| SCN-ENTRY-001 | App entry | MainViewModel matching vault path starts ordered sync. |
| SCN-ENTRY-002 | App entry | MainViewModel cloud adoption starts replay without upload. |
| SCN-ENTRY-003 | Settings entry | Settings reauth and adoption start ordered sync. |
| SCN-PURGE-001 | Purge | Purge detaches event listener. |
| SCN-PURGE-002 | Purge | Chat purge emits remote delete events for other devices. |
| SCN-CHECKPOINT-001 | Checkpoint | Large replay is page-resumable without skipping events. |
| SCN-MUT-001 | Mutation defense | Function without idempotency creates duplicate sequences and fails. |
| SCN-MUT-002 | Mutation defense | Android implementation using old per-collection listeners or placeholders fails. |

## Happy Path Scenarios

### SCN-FUNC-001: Upsert Trigger Writes A Monotonic Sync Event

Given `users/alice/chats/chat-1` is written with `updated_at` equal to `1700000001000`, `_sync_device_id` equal to `"device-a"`, and `_sync_client_mutation_id` equal to `"mutation-1"`
And `users/alice/sync_meta/state.nextSequence` is `0`
When the sync feed function handles the Firestore write event with trigger event ID `"event-1"`
Then `users/alice/sync_events/00000000000000000001.sequence` is `1`
And `users/alice/sync_events/00000000000000000001.operation` is `"upsert"`
And `users/alice/sync_events/00000000000000000001.collectionPath` is `"chats"`
And `users/alice/sync_events/00000000000000000001.entityId` is `"chat-1"`
And `users/alice/sync_events/00000000000000000001.deviceId` is `"device-a"`
And `users/alice/sync_events/00000000000000000001.clientMutationId` is `"mutation-1"`
And `users/alice/sync_meta/state.nextSequence` is `1`

### SCN-BACKFILL-001: Backfill Emits Dependency-Ordered Baseline Events

Given `users/alice/chats/chat-1` exists
And `users/alice/messages/message-1` exists with `chat_id` equal to `"chat-1"`
And `users/alice/message_files/file-1` exists with `message_id` equal to `"message-1"` and `chat_id` equal to `"chat-1"`
And `users/alice/sync_meta/state.feedInitialized` is `false`
When authenticated user `"alice"` calls the backfill sync feed callable
Then `users/alice/sync_events/00000000000000000001.collectionPath` is `"chats"`
And `users/alice/sync_events/00000000000000000002.collectionPath` is `"messages"`
And `users/alice/sync_events/00000000000000000003.collectionPath` is `"message_files"`
And `users/alice/sync_meta/state.feedInitialized` is `true`

### SCN-USECASE-001: Upload, Replay, And Listener Start Occur In Order

Given `uploadLocalFirst` is `true`
And `CloudSyncPort.syncAllLocalDataToCloud()` returns `Result.success(Unit)`
And the first `CloudSyncPort.syncRemoteChangesFromCloud()` returns `CloudSyncEventReplayResult(eventsApplied = 200, lastAppliedSequence = 200, hasMore = true)`
And the second `CloudSyncPort.syncRemoteChangesFromCloud()` returns `CloudSyncEventReplayResult(eventsApplied = 1, lastAppliedSequence = 201, hasMore = false)`
When `StartCloudSyncListeningUseCase.invoke(uploadLocalFirst = true)` runs
Then the call order is exactly `"syncAllLocalDataToCloud,syncRemoteChangesFromCloud,syncRemoteChangesFromCloud,startListening"`
And the result is `Result.success(Unit)`

### SCN-ANDROID-001: Fresh Hydrate Applies Events After Sequence Zero

Given `FirestoreSyncStateStore.lastAppliedSequence("alice")` is `0`
And the feed contains event sequence `1` for `chats/chat-1`
And the source document `users/alice/chats/chat-1` has encrypted name for plaintext `"Roadmap"`
When `FirestoreDataSource.syncRemoteChangesFromCloud()` runs for user `"alice"`
Then local Room contains chat ID `"chat-1"`
And the decrypted local chat name is `"Roadmap"`
And `FirestoreSyncStateStore.lastAppliedSequence("alice")` is `1`
And `CloudSyncEventReplayResult.eventsApplied` is `1`
And `CloudSyncEventReplayResult.hasMore` is `false`

### SCN-ANDROID-004: Child-Before-Parent Event Resolves Parent Without Placeholder Rows

Given `FirestoreSyncStateStore.lastAppliedSequence("alice")` is `0`
And the feed sequence `1` is an upsert for `messages/message-1`
And the source message has `chat_id` equal to `"chat-1"` and plaintext content `"hello"`
And the source parent chat `users/alice/chats/chat-1` has plaintext name `"Parent Chat"`
When `FirestoreSyncEventApplier` applies event sequence `1`
Then local Room contains chat ID `"chat-1"` with decrypted name `"Parent Chat"`
And local Room contains message ID `"message-1"` with decrypted content `"hello"`
And `FirestoreSyncPlaceholderDao.insertPlaceholderChatAndMessage` call count is `0`
And no local chat has decrypted name `"Syncing..."`
And `FirestoreSyncStateStore.lastAppliedSequence("alice")` is `1`

### SCN-LIVE-001: Listener Attaches To Sync Events Only

Given the vault is unlocked
And `FirestoreSyncStateStore.lastAppliedSequence("alice")` is `7`
When `FirestoreDataSource.startListening()` runs for user `"alice"`
Then Firestore registers exactly `1` snapshot listener
And the listener collection path is `"users/alice/sync_events"`
And the listener query lower bound is `sequence > 7`
And Firestore registers `0` listeners for `"users/alice/chats"`
And Firestore registers `0` listeners for `"users/alice/messages"`

### SCN-PENDING-001: Outbound Writes Include Sync Origin Metadata

Given the local device ID is `"device-a"`
And the generated client mutation ID is `"mutation-upload-1"`
And the sync clock returns `1700000003000`
And local chat `"chat-1"` is pending sync
When `FirestorePendingSyncEngine.syncPendingChanges("alice")` uploads the chat
Then the Firestore write to `users/alice/chats/chat-1` includes `_sync_device_id` equal to `"device-a"`
And the Firestore write includes `_sync_client_mutation_id` equal to `"mutation-upload-1"`
And the Firestore write includes `_sync_written_at` equal to `1700000003000`

### SCN-ENTRY-001: MainViewModel Matching Vault Path Starts Ordered Sync

Given `DetectVaultReconciliationStateUseCase` returns `AlreadyMatching`
And the vault unlock succeeds
When `MainViewModel` completes cloud vault reconciliation
Then `StartCloudSyncListeningUseCase.invoke(uploadLocalFirst = true)` is called exactly `1` time
And `CloudSyncPort.startListening()` is called exactly `0` times directly by `MainViewModel`

## Error And Edge Case Scenarios

### SCN-FUNC-002: Duplicate Trigger Event ID Writes No Duplicate Event

Given `users/alice/sync_processed_events/hash-event-1.sequence` is `1`
And `users/alice/sync_meta/state.nextSequence` is `1`
When the sync feed function handles a Firestore write event with trigger event ID `"event-1"`
Then the number of documents in `users/alice/sync_events` is `1`
And `users/alice/sync_meta/state.nextSequence` is `1`

### SCN-FUNC-003: Stale Delete After Recreate Does Not Emit Destructive Delete

Given a delete trigger for `users/alice/messages/message-1` has before `updated_at` equal to `1700000001000`
And the current source document `users/alice/messages/message-1` exists with `updated_at` equal to `1700000002000`
When the sync feed function handles the delete trigger
Then the new sync event operation is `"upsert"`
And the new sync event sourceUpdatedAt is `1700000002000`
And the new sync event sourceExists is `true`

### SCN-FUNC-004: Non-Allowlisted Collection Is Ignored

Given `users/alice/sync_meta/state.nextSequence` is `4`
When the sync feed function handles a write to `users/alice/sync_events/00000000000000000004`
Then the number of newly written sync events is `0`
And `users/alice/sync_meta/state.nextSequence` is `4`

### SCN-BACKFILL-002: Backfill Is Idempotent After Feed Initialized

Given `users/alice/sync_meta/state.feedInitialized` is `true`
And `users/alice/sync_meta/state.nextSequence` is `8`
When authenticated user `"alice"` calls the backfill sync feed callable
Then the callable result status is `"already_initialized"`
And `users/alice/sync_meta/state.nextSequence` is `8`

### SCN-SEC-001: Owner Can Read Sync Events But Cannot Write Them

Given authenticated user `"alice"` requests `get` on `users/alice/sync_events/00000000000000000001`
Then the Firestore rule result is `"allow"`
When authenticated user `"alice"` requests `create` on `users/alice/sync_events/00000000000000000002`
Then the Firestore rule result is `"deny"`

### SCN-SEC-002: Non-Owner Cannot Read Owner Sync Events

Given authenticated user `"bob"` requests `get` on `users/alice/sync_events/00000000000000000001`
Then the Firestore rule result is `"deny"`

### SCN-USECASE-002: Upload Failure Prevents Replay And Listener Start

Given `uploadLocalFirst` is `true`
And `CloudSyncPort.syncAllLocalDataToCloud()` returns `Result.failure(IllegalStateException("Vault locked"))`
When `StartCloudSyncListeningUseCase.invoke(uploadLocalFirst = true)` runs
Then `CloudSyncPort.syncRemoteChangesFromCloud()` is called exactly `0` times
And `CloudSyncPort.startListening()` is called exactly `0` times
And the result exception type is `IllegalStateException`
And the result exception message is `"Vault locked"`

### SCN-USECASE-003: Replay Failure Prevents Listener Start

Given `uploadLocalFirst` is `false`
And `CloudSyncPort.syncRemoteChangesFromCloud()` returns `Result.failure(IllegalStateException("missing required parent chats/chat-1 for messages/message-1"))`
When `StartCloudSyncListeningUseCase.invoke(uploadLocalFirst = false)` runs
Then `CloudSyncPort.startListening()` is called exactly `0` times
And the result exception type is `IllegalStateException`
And the result exception message is `"missing required parent chats/chat-1 for messages/message-1"`

### SCN-ANDROID-002: Malformed Event Fails With Exact Exception Message

Given a sync event document has `schemaVersion` equal to `99`
When `FirestoreSyncEventCodec` parses the document
Then the exception type is `IllegalArgumentException`
And the exception message is `"unsupported sync event schema version: 99"`

### SCN-ANDROID-003: Feed Client Query Uses Sequence Greater Than Last Applied Sequence

Given `FirestoreSyncStateStore.lastAppliedSequence("alice")` is `42`
When `FirestoreSyncEventFeedClient.fetchNextPage("alice")` runs
Then the Firestore query collection path is `"users/alice/sync_events"`
And the Firestore query filter is `"sequence > 42"`
And the Firestore query orderBy field is `"sequence"`
And the Firestore query limit is `200`

### SCN-ANDROID-005: Sequence Advances Only After Room Transaction Success

Given `FirestoreSyncStateStore.lastAppliedSequence("alice")` is `5`
And event sequence `6` is an upsert for `chats/chat-6`
And the Room transaction throws `SQLiteConstraintException` with message `"constraint failed"`
When `FirestoreSyncEventApplier` applies event sequence `6`
Then `FirestoreSyncStateStore.lastAppliedSequence("alice")` is `5`
And local Room does not contain chat ID `"chat-6"`
And the exception type is `SQLiteConstraintException`

### SCN-ANDROID-006: Replayed Events At Or Below Last Applied Sequence Are Ignored

Given `FirestoreSyncStateStore.lastAppliedSequence("alice")` is `9`
And the feed contains event sequence `9` for `chats/chat-9`
When `FirestoreSyncEventApplier` receives event sequence `9`
Then local Room does not write chat ID `"chat-9"`
And `FirestoreSyncStateStore.lastAppliedSequence("alice")` is `9`

### SCN-ANDROID-007: Delete Event Uses Adapter Delete Behavior

Given local Room contains message ID `"message-1"` with `deleted` equal to `false`
And the sync event sequence `10` has operation `"delete"` for `messages/message-1`
When `FirestoreSyncEventApplier` applies event sequence `10`
Then local Room contains message ID `"message-1"` with `deleted` equal to `true`
And `FirestoreSyncStateStore.lastAppliedSequence("alice")` is `10`

### SCN-ANDROID-008: Every Adapter Collection Has A Dependency Graph Entry

Given `FirestoreSyncAdapterRegistry.adapters` contains `19` adapters
When `FirestoreSyncDependencyGraph` is validated against the registry
Then the number of registry collection paths missing from the dependency graph is `0`
And the number of dependency graph collection paths missing from the registry is `0`

### SCN-ANDROID-009: Missing Required Parent Fails Without Advancing Sequence

Given `FirestoreSyncStateStore.lastAppliedSequence("alice")` is `3`
And event sequence `4` is an upsert for `messages/message-1`
And source message `users/alice/messages/message-1` has `chat_id` equal to `"chat-missing"`
And source parent chat `users/alice/chats/chat-missing` does not exist
When `FirestoreSyncEventApplier` applies event sequence `4`
Then `FirestoreSyncStateStore.lastAppliedSequence("alice")` is `3`
And local Room does not contain message ID `"message-1"`
And the exception type is `IllegalStateException`
And the exception message is `"missing required parent chats/chat-missing for messages/message-1"`

### SCN-LIVE-002: Live Event Applies And Advances Sequence Once

Given `FirestoreSyncStateStore.lastAppliedSequence("alice")` is `20`
And the live listener receives one added sync event with sequence `21`
When the listener callback processes the event
Then `FirestoreSyncEventApplier.apply` is called exactly `1` time for sequence `21`
And `FirestoreSyncStateStore.lastAppliedSequence("alice")` is `21`

### SCN-DATASOURCE-001: Replay Fails When Signed Out

Given `FirestoreDataSource` has `userIdProvider()` returning `null`
When `syncRemoteChangesFromCloud()` runs
Then the result exception type is `IllegalStateException`
And the result exception message is `"Not signed in"`

### SCN-DATASOURCE-002: Replay Fails When Vault Is Locked

Given `FirestoreDataSource` has user ID `"alice"`
And `cryptoManager.hasCachedKey()` returns `false`
When `syncRemoteChangesFromCloud()` runs
Then the result exception type is `IllegalStateException`
And the result exception message is `"Vault locked"`

### SCN-DATASOURCE-003: Replay Loops Until HasMore Is False

Given the first feed page contains `200` events ending at sequence `200`
And the second feed page contains `2` events ending at sequence `202`
When `FirestoreDataSource.syncRemoteChangesFromCloud()` runs twice through `StartCloudSyncListeningUseCase`
Then the combined applied event count is `202`
And `FirestoreSyncStateStore.lastAppliedSequence("alice")` is `202`
And `CloudSyncEventReplayResult.hasMore` is `false`

### SCN-PENDING-002: Adapter Encrypted Payload Fields Are Preserved

Given local chat `"chat-1"` has encrypted name ciphertext `"cipher-name"`
When `FirestorePendingSyncEngine.syncPendingChanges("alice")` builds the Firestore map
Then the Firestore field `"name"` is `"cipher-name"`
And the Firestore field `"_sync_device_id"` is `"device-a"`
And the Firestore map does not contain plaintext `"Roadmap"`

### SCN-PLACEHOLDER-001: Normal Sync Inserts No Syncing Chat Or Message

Given event sequence `1` is an upsert for `message_files/file-1`
And source `message_files/file-1` references `message_id` equal to `"message-1"` and `chat_id` equal to `"chat-1"`
And source parent message and chat both exist
When `FirestoreSyncEventApplier` applies event sequence `1`
Then `FirestoreSyncPlaceholderDao.insertPlaceholderChatAndMessage` call count is `0`
And no local chat has decrypted name `"Syncing..."`
And no local message has decrypted content `"Syncing..."`

### SCN-ENTRY-002: MainViewModel Cloud Adoption Starts Replay Without Upload

Given `AdoptCloudVaultUseCase` returns `Result.success(Unit)`
When `MainViewModel.confirmAdoptCloud()` completes
Then `StartCloudSyncListeningUseCase.invoke(uploadLocalFirst = false)` is called exactly `1` time
And `CloudSyncPort.syncAllLocalDataToCloud()` is called exactly `0` times directly by `MainViewModel`

### SCN-ENTRY-003: Settings Reauth And Adoption Start Ordered Sync

Given settings vault reauth returns `AlreadyMatching`
When `SettingsGeneralDelegate.onSubmitVaultReauthPassword()` completes
Then `StartCloudSyncListeningUseCase.invoke(uploadLocalFirst = true)` is called exactly `1` time
When `SettingsGeneralDelegate.onConfirmAdoptCloud()` completes after successful adoption
Then `StartCloudSyncListeningUseCase.invoke(uploadLocalFirst = false)` is called exactly `1` time
And `CloudSyncPort.startListening()` is called exactly `0` times directly by `SettingsGeneralDelegate`

### SCN-PURGE-001: Purge Detaches Event Listener

Given `FirestoreDataSource.startListening()` attached one sync event listener
When `FirestoreCloudPurgeClient.deleteUserAccount()` succeeds
Then the listener registration remove call count is `1`
And the active listener count is `0`

### SCN-PURGE-002: Chat Purge Emits Remote Delete Events For Other Devices

Given `users/alice/chats/chat-1` exists
And `users/alice/sync_meta/state.nextSequence` is `11`
When `FirestoreCloudPurgeClient.deleteAllChatsFromCloud()` deletes `users/alice/chats/chat-1`
And the sync feed function handles the delete trigger
Then `users/alice/sync_events/00000000000000000012.operation` is `"delete"`
And `users/alice/sync_events/00000000000000000012.collectionPath` is `"chats"`
And `users/alice/sync_events/00000000000000000012.entityId` is `"chat-1"`

### SCN-CHECKPOINT-001: Large Replay Is Page-Resumable Without Skipping Events

Given `FirestoreSyncStateStore.lastAppliedSequence("alice")` is `0`
And the feed contains `401` events with contiguous sequences from `1` through `401`
When replay page size is `200`
And `StartCloudSyncListeningUseCase.invoke(uploadLocalFirst = false)` runs
Then `CloudSyncPort.syncRemoteChangesFromCloud()` is called exactly `3` times
And `FirestoreSyncStateStore.lastAppliedSequence("alice")` is `401`
And `CloudSyncPort.startListening()` is called exactly `1` time

## Mutation-Defense Risk And Scenarios

### SCN-MUT-001: Function Without Idempotency Creates Duplicate Sequences And Fails

Mutation risk: a lazy implementation increments `nextSequence` for every trigger invocation and does not check `sync_processed_events`.

Given trigger event ID `"event-dup"` is delivered twice for `users/alice/chats/chat-1`
And `users/alice/sync_meta/state.nextSequence` starts at `0`
When the sync feed function handles both deliveries
Then the number of documents in `users/alice/sync_events` is `1`
And `users/alice/sync_events/00000000000000000001.triggerEventId` is `"event-dup"`
And `users/alice/sync_meta/state.nextSequence` is `1`

### SCN-MUT-002: Android Implementation Using Old Per-Collection Listeners Or Placeholders Fails

Mutation risk: a lazy implementation keeps the old adapter listener loop and only adds a `sync_events` listener beside it, or keeps placeholder insertion in child handlers.

Given the vault is unlocked
And `FirestoreSyncStateStore.lastAppliedSequence("alice")` is `0`
When `FirestoreDataSource.startListening()` runs
Then Firestore registers exactly `1` snapshot listener
And the listener collection path is `"users/alice/sync_events"`
And Firestore registers `0` listeners for `"users/alice/chats"`
And Firestore registers `0` listeners for `"users/alice/messages"`
When a `message_files/file-1` event is applied with existing parent source docs
Then `FirestoreSyncPlaceholderDao.insertPlaceholderChatAndMessage` call count is `0`
And no local row contains decrypted text `"Syncing..."`
