# Test Specification: Search Tool Enable Sync

Ticket: `search-tool-enable-sync`

Stable scenario IDs are referenced from `spec.md` §9. All scenarios are Gherkin; literal expected values are stated for every assertion. No TBD/TODO/placeholders.

## 1. Happy Path Scenarios

### Scenario: S-QUEUE-001 — queue mark-claim-clear cycle

- **Given:** a fresh `SearchToolSettingsSyncQueue` backed by an in-memory DataStore
- **When:** the test calls `markPendingSync()`, then `claimPending()`, then `isPending()`
- **Then:**
  - After `markPendingSync()`, `isPending()` returns `true`
  - `claimPending()` returns `true`
  - After `claimPending()`, `isPending()` returns `false`
  - `clearClaimedSync()` completes without throwing and `isPending()` remains `false`

### Scenario: S-QUEUE-002 — claimPending on empty queue

- **Given:** a fresh `SearchToolSettingsSyncQueue` with no prior `markPendingSync` call
- **When:** the test calls `claimPending()`
- **Then:** `claimPending()` returns `false` and `isPending()` remains `false`

### Scenario: S-QUEUE-003 — repeated markPendingSync is idempotent

- **Given:** a `SearchToolSettingsSyncQueue`
- **When:** the test calls `markPendingSync()` three times in a row
- **Then:**
  - `isPending()` returns `true`
  - a single subsequent `claimPending()` returns `true` and clears the state to `false`
  - calling `claimPending()` a second time returns `false`

### Scenario: S-QUEUE-004 — claimPending after clearClaimedSync returns false

- **Given:** a `SearchToolSettingsSyncQueue` that has gone through `markPendingSync()` → `claimPending()` → `clearClaimedSync()`
- **When:** the test calls `claimPending()` again
- **Then:** `claimPending()` returns `false`

### Scenario: S-LOCAL-001 — updateSearchEnabled enqueues pending sync

- **Given:** a `SettingsRepositoryImpl` constructed with a real `SearchToolSettingsSyncQueue` and a real `DataStore<Preferences>`
- **When:** the test calls `updateSearchEnabled(true)`
- **Then:**
  - `settingsFlow.first().searchEnabled` is `true`
  - the queue's `isPending()` is `true`

### Scenario: S-LOCAL-002 — updateXSearchEnabled enqueues pending sync

- **Given:** a `SettingsRepositoryImpl` constructed with a real `SearchToolSettingsSyncQueue` and a real `DataStore<Preferences>`
- **When:** the test calls `updateXSearchEnabled(true)`
- **Then:**
  - `settingsFlow.first().xSearchEnabled` is `true`
  - the queue's `isPending()` is `true`

### Scenario: S-LOCAL-003 — updateSearchProvider enqueues pending sync

- **Given:** a `SettingsRepositoryImpl` constructed with a real `SearchToolSettingsSyncQueue` and a real `DataStore<Preferences>`
- **When:** the test calls `updateSearchProvider(SearchProvider.EXA)`
- **Then:**
  - `settingsFlow.first().searchProvider` is `SearchProvider.EXA`
  - the queue's `isPending()` is `true`

### Scenario: S-LOCAL-004 — combined onSaveSearchSkillSettings path enqueues once

- **Given:** a `SettingsRepositoryImpl` constructed with a real `SearchToolSettingsSyncQueue`
- **When:** the test calls `updateSearchEnabled(true)` then immediately `updateSearchProvider(SearchProvider.EXA)` (the same call order as `SettingsSearchDelegate.onSaveSearchSkillSettings`)
- **Then:**
  - `settingsFlow.first().searchEnabled` is `true`
  - `settingsFlow.first().searchProvider` is `SearchProvider.EXA`
  - the queue's `isPending()` is `true` (idempotent — a single `claimPending()` returns `true`)

### Scenario: S-ROUNDTRIP-001 — getClaimedForSync returns a single SearchToolSettingsRecord when pending

- **Given:** a `SearchToolSettingsSyncHandler` constructed against a real `SearchToolSettingsSyncQueue` and a fake `SettingsRepository` whose `settingsFlow` emits `SettingsData(searchEnabled = true, xSearchEnabled = false, searchProvider = SearchProvider.TAVILY, …)`
- **When:** the test calls `markPendingSync()` on the queue, then `handler.claimPending()`, then `handler.getClaimedForSync()`
- **Then:**
  - the returned list has exactly one element
  - that element equals `SearchToolSettingsRecord(searchEnabled = true, xSearchEnabled = false, searchProvider = SearchProvider.TAVILY, updatedAt = <queue-mark-time>)`
  - `updatedAt` is a Long `>= 0` and is produced by the same clock the queue used at `markPendingSync()` time

### Scenario: S-ROUNDTRIP-002 — getClaimedForSync returns empty when nothing pending

- **Given:** a `SearchToolSettingsSyncHandler` against a queue with no `markPendingSync` call
- **When:** the test calls `handler.claimPending()` then `handler.getClaimedForSync()`
- **Then:** `claimPending()` returns `false` and `getClaimedForSync()` returns an empty list

### Scenario: S-ROUNDTRIP-003 — toFirestore writes the four user-facing fields

- **Given:** a `SearchToolSettingsRecord(searchEnabled = true, xSearchEnabled = true, searchProvider = SearchProvider.EXA, updatedAt = 1700000000000L)`
- **When:** the test calls `handler.toFirestore(record)`
- **Then:** the returned map contains exactly the keys `search_enabled → true`, `x_search_enabled → true`, `search_provider → "EXA"`, `updated_at → 1700000000000L` (no other keys, no metadata — the engine adds `_sync_*` later)

### Scenario: S-REMOTEAPPLY-001 — fromFirestore decodes a complete document

- **Given:** a `DocumentSnapshot` whose `id` is `"main"`, with `search_enabled = true`, `x_search_enabled = false`, `search_provider = "TAVILY"`, `updated_at = 1700000000000L`
- **When:** the test calls `handler.fromFirestore(doc)`
- **Then:** the returned `SearchToolSettingsRecord` equals `SearchToolSettingsRecord(searchEnabled = true, xSearchEnabled = false, searchProvider = SearchProvider.TAVILY, updatedAt = 1700000000000L)`

### Scenario: S-REMOTEAPPLY-002 — upsertLocal writes all three flags and propagates via settingsFlow

- **Given:** a `SettingsRepositoryImpl` with a real `DataStore<Preferences>` and the new internal `applyRemoteSearchToolSettings` seam
- **And:** a `SearchToolSettingsSyncHandler` constructed with that repository
- **When:** the test calls `handler.upsertLocal(SearchToolSettingsRecord(true, true, SearchProvider.EXA, 1700000000000L), doc, overwrite = true)`
- **Then:**
  - `settingsFlow.first().searchEnabled` is `true`
  - `settingsFlow.first().xSearchEnabled` is `true`
  - `settingsFlow.first().searchProvider` is `SearchProvider.EXA`
  - the queue's `isPending()` is `false` (a remote apply must NOT enqueue a follow-up upload)

### Scenario: S-REMOTEAPPLY-003 — upsertLocal with overwrite=false respects local pending edit

- **Given:** a `SettingsRepositoryImpl` where the user has just called `updateSearchEnabled(false)` and `markPendingSync()` is set
- **And:** a `SearchToolSettingsSyncHandler` constructed with that repository
- **When:** the test calls `handler.upsertLocal(SearchToolSettingsRecord(true, false, SearchProvider.TAVILY, 1700000000000L), doc, overwrite = false)`
- **Then:** `settingsFlow.first().searchEnabled` remains `false` (the local pending edit wins) and the queue is still pending

### Scenario: S-REMOTEAPPLY-004 — fromFirestore returns null when search_provider is missing

- **Given:** a `DocumentSnapshot` whose `id` is `"main"`, with `search_enabled = true`, `x_search_enabled = false`, NO `search_provider` field, `updated_at = 1700000000000L`
- **When:** the test calls `handler.fromFirestore(doc)`
- **Then:** the result is `null` (the applier will then fail or skip cleanly — see S-MUTATION-001 for the contract that the handler does not silently default the provider)

### Scenario: S-ECHO-001 — same-device echo does not flip the local value

- **Given:** a `SearchToolSettingsSyncHandler` constructed with a `SettingsRepository` whose `searchEnabled` is currently `true` and whose queue `isPending()` is `true` (this device just uploaded)
- **And:** a `DocumentSnapshot` whose `id` is `"main"`, with `search_enabled = false`, `x_search_enabled = false`, `search_provider = "TAVILY"`, `updated_at = 1700000000000L`, and `eventDeviceId == localDeviceId` (the echo of our own write arriving back)
- **When:** the test calls `handler.upsertLocal(record, doc, overwrite = false)` with `eventDeviceId == localDeviceId`
- **Then:** `settingsFlow.first().searchEnabled` remains `true` and the queue is still pending for the next write

### Scenario: S-HANDLER-001 — handler is registered under collectionPath "search_tool_settings"

- **Given:** a `FirestoreSyncAdapterRegistry` constructed with a real `FirestoreSyncContext` that has a real queue and a fake repository
- **When:** the test calls `registry.adapters`
- **Then:** the list contains exactly one adapter with `collectionPath == "search_tool_settings"`, and `getEntityId(record) == "main"` for any `SearchToolSettingsRecord`

### Scenario: S-HANDLER-002 — search_tool_settings has no required parents in the dependency graph

- **Given:** the `FirestoreSyncDependencyGraph`
- **When:** the test calls `requiredParents("search_tool_settings", emptyMap())`
- **Then:** the result is an empty list (this is what allows the applier to upsert the singleton without resolving parents)

### Scenario: S-HANDLER-003 — handler rides the same Firestore sync pipeline as api_keys

- **Given:** a `FirestorePendingSyncEngine` constructed with the standard 22-adapter registry plus the new `SearchToolSettingsSyncHandler`
- **When:** the test calls `engine.syncPendingChanges(userId)` after `markPendingSync()` on the queue
- **Then:** exactly one write to `users/test-user-id/search_tool_settings/main` is dispatched in a `runBatch`, with the body matching S-ROUNDTRIP-003, and the queue's `isPending()` is `false` after the call

## 2. Error Path & Edge Case Scenarios

### Scenario: S-LISTENER-001 — listener does not apply while vault is locked

- **Given:** a `FirestoreListenerCoordinator` whose `ctx.cryptoManager.hasCachedKey()` returns `false`
- **When:** the test calls `coordinator.startListening()` and then drives a synthetic live event for `search_tool_settings/main` with `search_enabled = true`
- **Then:**
  - `coordinator` does not call `SearchToolSettingsSyncHandler.upsertLocal`
  - `searchEnabled` on the local settings flow remains `false`
  - no log line at level `error` is emitted (this is the expected no-op, not a failure)

### Scenario: S-LISTENER-002 — listener applies the document once the vault is unlocked

- **Given:** a `FirestoreListenerCoordinator` whose `ctx.cryptoManager.hasCachedKey()` returns `false`, then returns `true` after a `refreshCachedKey()` call
- **When:** the test calls `coordinator.startListening()` (locked) → `coordinator.startListening()` (unlocked) → drives a live event for `search_tool_settings/main` with `search_enabled = true`
- **Then:** `searchEnabled` on the local settings flow is `true` and the queue is NOT marked pending

### Scenario: S-MUTATION-001 — handler refuses to default missing provider (three-fields-not-one defense)

- **Given:** a `SearchToolSettingsSyncHandler` against a `SettingsRepository` whose current `searchProvider` is `SearchProvider.EXA`
- **And:** a `DocumentSnapshot` whose `id` is `"main"`, with `search_enabled = true`, `x_search_enabled = true`, NO `search_provider` field, `updated_at = 1700000000000L`
- **When:** the test calls `handler.fromFirestore(doc)` and then — if non-null — `handler.upsertLocal(record, doc, overwrite = true)`
- **Then:** `fromFirestore(doc)` returns `null` and `settingsFlow.first().searchProvider` is still `SearchProvider.EXA` (the lazy implementation that returns `SearchProvider.TAVILY` as a default would fail this scenario)

### Scenario: S-MUTATION-002 — clear-key-with-search-enabled-true does not disable search

- **Given:** a `SettingsRepositoryImpl` whose current `searchEnabled` is `true` and `tavilyKeyPresent` is `true`
- **And:** a `SearchToolSettingsSyncHandler` constructed with that repository
- **When:** the test calls `settingsRepository.clearTavilyApiKey()` then `handler.upsertLocal(SearchToolSettingsRecord(true, false, SearchProvider.TAVILY, 1700000000000L), doc, overwrite = true)`
- **Then:** `settingsFlow.first().searchEnabled` is `true` (a remote apply that re-asserts `search_enabled = true` must not be silently flipped to `false` because the local key is gone — the lazy implementation that always writes `false` when the key is absent would fail this)

### Scenario: S-MUTATION-003 — provider-only change is propagated (not silently dropped)

- **Given:** a `SettingsRepositoryImpl` whose current `searchProvider` is `SearchProvider.TAVILY`
- **And:** a `SearchToolSettingsSyncHandler` constructed with that repository
- **When:** the test calls `handler.upsertLocal(SearchToolSettingsRecord(false, false, SearchProvider.EXA, 1700000000000L), doc, overwrite = true)`
- **Then:** `settingsFlow.first().searchProvider` is `SearchProvider.EXA` AND `settingsFlow.first().searchEnabled` is `false` AND `settingsFlow.first().xSearchEnabled` is `false` (the lazy implementation that only writes the provider and skips the booleans would fail the boolean assertions)

### Scenario: S-MUTATION-004 — local save in flight is not clobbered by a stale remote echo

- **Given:** a `SettingsRepositoryImpl` whose current `searchEnabled` is `false` and `markPendingSync()` is set (the user just disabled search)
- **And:** a `SearchToolSettingsSyncHandler` constructed with that repository
- **When:** the test calls `handler.upsertLocal(SearchToolSettingsRecord(true, false, SearchProvider.TAVILY, 1500000000000L), doc, overwrite = false)` where `eventDeviceId` is a different device and `record.updatedAt < local updatedAt`
- **Then:** `settingsFlow.first().searchEnabled` remains `false` (the local newer edit wins) and the queue is still pending for the next write

## 3. Mutation Defense

### Lazy Implementation Risk

The most likely lazy implementation is to read only the boolean toggles from the remote document and either (a) skip the third field (`searchProvider`) entirely or (b) default missing values to `false` / `TAVILY`. A subtler lazy version is to apply the remote payload without re-enqueuing local writes (good) but to clear the local settings flag whenever the corresponding key is missing from the user's BYOK vault (bad — the toggle and the key are independent).

### Defense Scenarios

- **S-MUTATION-001** catches the "default missing provider to `TAVILY`" implementation.
- **S-MUTATION-002** catches the "if the key is absent, force the toggle to `false`" implementation.
- **S-MUTATION-003** catches the "only write the provider, skip the booleans" implementation.

All three are required; passing any two while failing the third still indicates a lazy implementation that must be rejected.
