# Technical Specification: Search Tool Enable Sync

Ticket: `search-tool-enable-sync`

## 1. Objective

Sync the Web search and X search enable/disable toggles (and the Web search provider) between devices via Firestore, mirroring the `api_keys` collection contract that already syncs Tavily/Exa/X BYOK keys. Today the toggles live only in local DataStore prefs and default to `false` on a fresh install, so a new device that signs in gets the API keys but reads the toggles as "Disabled" until the user re-enables them.

Acceptance criteria:
- A new `search_tool_settings` Firestore collection, with a single document `users/{uid}/search_tool_settings/main`, holds `searchEnabled`, `xSearchEnabled`, and `searchProvider` and propagates to the local DataStore so the existing `SettingsData.settingsFlow` and `SettingsUiStateFactory` toggles flip after sign-in.
- Local changes to `updateSearchEnabled`, `updateSearchProvider`, or `updateXSearchEnabled` are enqueued for upload on the next pending sync, and the claim is cleared after the round-trip succeeds.
- A remote snapshot that arrives before any local save still flips the local DataStore flags and propagates to UI; an in-flight remote apply does not clobber a newer local edit.
- A clear-key event that leaves `searchEnabled` true does not disable search; remote apply must read the three fields, not toggle just the boolean.

## 2. System Architecture

### Target Files

- Modify `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/repository/SettingsRepositoryImpl.kt` (route the three toggle updates through a new queue + remote apply seams; add a `clearFromRemote` path)
- Create `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/sync/SearchToolSettingsSyncQueue.kt` (DataStore-backed pending/claimed state machine, parallel to `ApiKeyManager`'s pending keys)
- Create `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/firestore/handlers/SearchToolSettingsSyncHandler.kt` (`EntitySyncAdapter<SearchToolSettingsRecord>` mirroring `ApiKeySyncHandler`)
- Modify `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/firestore/FirestoreSyncContext.kt` (expose the new queue and a `SearchToolSettingsRepository` port for the handler)
- Modify `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/firestore/FirestoreSyncDependencies.kt` (same — pass through to context)
- Modify `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/FirestoreDataSource.kt` (`FirestoreSyncDependenciesFacade` constructor injects the new queue and the `SettingsRepository` port)
- Modify `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/firestore/FirestoreSyncAdapterRegistry.kt` (register the new handler)
- Modify `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/firestore/FirestoreSyncDependencyGraph.kt` (add `search_tool_settings` entry with `NO_PARENTS`)
- Modify `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/firestore/FirestoreListenerCoordinator.kt` (gate `startListening` on `cryptoManager.hasCachedKey()`; this gate already exists for the sync-events listener, document that the new handler rides that same gate)
- Modify `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/DataModule.kt` (provide `SearchToolSettingsSyncQueue` as `@Singleton`; bind nothing else — `SettingsRepository` is already bound)
- Modify `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/firestore/FirestorePendingSyncEngine.kt` (no signature change; the new handler rides the existing `adapters.forEach { claimPending() }` loop, so this is a no-op call-out but is listed for traceability)
- Create `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/repository/SearchToolSettingsRecord.kt` (the sync payload shape — domain-side carrier)
- Modify `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/remote/FirestoreDataSourceTestFactory.kt` (thread the new queue + repo port so existing tests can wire it)
- Create `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/remote/firestore/handlers/SearchToolSettingsSyncHandlerTest.kt` (handler unit tests — round-trip, vault gate, mutation defense)
- Create `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/sync/SearchToolSettingsSyncQueueTest.kt` (queue state machine)
- Modify `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/repository/SettingsRepositoryImplSyncTest.kt` (create new test file or extend existing — local writes enqueue, remote apply does not re-enqueue)
- Modify `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/remote/ApiKeyManagerSyncAdapterTest.kt` (no code change; add one scenario that proves a missing search-tool-settings handler does not break API key sync — to keep regression coverage if the registry ordering shifts)

### Component Boundaries

- `core/data` owns the queue, the handler, the record, and the repository wiring. The new public surface is `SettingsRepository` (existing) plus an internal `SearchToolSettingsSyncQueue` consumed by the handler and the repository.
- The handler is registered alongside `ApiKeySyncHandler` so the existing `FirestorePendingSyncEngine.syncPendingChanges → adapters.forEach { claimPending() } → getClaimedForSync → toFirestore → writeBatch → markSynced` pipeline carries the new collection with zero engine changes.
- Remote apply flows through `FirestoreSyncEventApplier → RoomFirestoreSyncLocalStore.upsert("search_tool_settings", "main", …) → SearchToolSettingsSyncHandler.upsertLocal → SearchToolSettingsRecord → SettingsRepositoryImpl.applyRemoteSearchToolSettings` (internal seam), which writes the same DataStore keys the existing `updateSearchEnabled` / `updateXSearchEnabled` / `updateSearchProvider` already touch. The existing `settingsFlow` then propagates to `SettingsUiStateFactory` and the UI without further code.
- The vault-unlock gate already lives on `FirestoreListenerCoordinator.startListening()` (`!ctx.cryptoManager.hasCachedKey()` returns early). The new handler does not need a new gate; it is covered by the existing one. The plan calls this out explicitly because the briefing asked.

## 3. Data Models & Schemas

### 3.1 Firestore document shape

Path: `users/{uid}/search_tool_settings/main`

Fields:
- `search_enabled: Boolean` (mirror of local `settings_search_enabled`)
- `x_search_enabled: Boolean` (mirror of local `settings_x_search_enabled`)
- `search_provider: String` (one of `TAVILY`, `EXA` — the `name` of `SearchProvider`)
- `updated_at: Long` (epoch millis, server-side, written by the producer)
- `_sync_device_id: String` (existing sync metadata, set by `FirestorePendingSyncEngine.syncMetadata()`)
- `_sync_client_mutation_id: String`
- `_sync_written_at: Long`

The three user-facing fields are the only ones the handler reads on `fromFirestore`; the three `_sync_*` fields are stripped by `FirestoreSensitiveFieldRepairer.repairInbound` like every other collection.

### 3.2 Why a single doc per user

Justification: the three flags always change together when the user edits Search or X search; per-toggle documents would multiply the round-trips (3× `markPendingSync` calls, 3× writes per `syncPendingChanges`) for the same 3-field payload. The doc is tiny (< 1 KB) so the cost of writing it whole on any change is negligible. The `api_keys` collection is per-alias because each alias is a separate user secret that may be added or deleted independently; the search settings have no such independent life cycle.

### 3.3 Local DataStore keys (unchanged)

`SettingsRepositoryImpl` continues to read/write:
- `settings_search_enabled` (Boolean)
- `settings_x_search_enabled` (Boolean)
- `settings_search_provider` (String — `SearchProvider.name`)

The only new local state is a single `search_tool_settings_pending_sync` DataStore `booleanPreferencesKey` that records "there is a pending upload". This is intentionally a boolean, not a queue of partial edits, because the document is whole-or-nothing.

## 4. API Contracts & Interfaces

### 4.1 Domain port (no change)

`SettingsRepository` keeps its current public surface. The new behaviour is added inside `SettingsRepositoryImpl` and is internal.

### 4.2 Internal queue shape

```kotlin
internal class SearchToolSettingsSyncQueue @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val syncScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    suspend fun markPendingSync()
    suspend fun claimPending(): Boolean   // returns true iff there was a pending write
    suspend fun clearClaimedSync()
    suspend fun isPending(): Boolean
}
```

DataStore key: `search_tool_settings_pending_sync` (boolean, default `false`). The "claimed" state is not stored — once `claimPending()` returns, the boolean is cleared and the next `syncPendingChanges` cycle will not pick it up again until the user changes a toggle and `markPendingSync()` is called again. This matches the spirit of `ApiKeyManager.claimPending` while not needing a separate claimed-set, because the upload is atomic per document.

### 4.3 Internal record

```kotlin
internal data class SearchToolSettingsRecord(
    val searchEnabled: Boolean,
    val xSearchEnabled: Boolean,
    val searchProvider: SearchProvider,
    val updatedAt: Long,
)
```

### 4.4 Handler contract (mirrors `ApiKeySyncHandler`)

```kotlin
internal class SearchToolSettingsSyncHandler(
    private val ctx: FirestoreSyncContext,
) : EntitySyncAdapter<SearchToolSettingsRecord> {
    override val collectionPath: String = "search_tool_settings"

    override suspend fun claimPending() = ctx.searchToolSettingsSyncQueue.claimPending()
    override suspend fun getClaimedForSync(): List<SearchToolSettingsRecord>
    override suspend fun markSynced(entities: List<SearchToolSettingsRecord>) =
        ctx.searchToolSettingsSyncQueue.clearClaimedSync()

    override fun getEntityId(entity: SearchToolSettingsRecord): String = "main"
    override fun shouldDelete(entity: SearchToolSettingsRecord): Boolean = false

    override suspend fun toFirestore(local: SearchToolSettingsRecord): Map<String, Any?>
    override fun fromFirestore(doc: DocumentSnapshot): SearchToolSettingsRecord?
    override suspend fun upsertLocal(
        entity: SearchToolSettingsRecord,
        doc: DocumentSnapshot,
        overwrite: Boolean,
    )
    override suspend fun deleteLocal(id: String) = Unit  // never delete the singleton
}
```

`upsertLocal` reads the current DataStore values (via `ctx.settingsRepository`) and calls internal `applyRemoteSearchToolSettings(record, overwrite)` on the repository. If the local `pendingSync` flag is set and the remote `eventDeviceId` matches the local device, the apply is treated as the echo of our own upload and skipped (mirrors `FirestoreSyncRemoteApplyPolicy.overwriteForSameDeviceEcho`).

### 4.5 Repository seams

Inside `SettingsRepositoryImpl`:

```kotlin
internal suspend fun applyRemoteSearchToolSettings(
    record: SearchToolSettingsRecord,
    overwrite: Boolean,
)
```

`applyRemoteSearchToolSettings` writes the three DataStore keys directly (no `markPendingSync` recursion — the write is silent). It is the only caller that bypasses the pending-sync enqueue, and is restricted to the `internal` visibility.

The three public mutators gain one line each:
- `updateSearchEnabled(enabled)` → `dataStore.edit { SEARCH_ENABLED_KEY = enabled }` then `syncQueue.markPendingSync()`
- `updateSearchProvider(provider)` → `dataStore.edit { SEARCH_PROVIDER_KEY = provider.name }` then `syncQueue.markPendingSync()`
- `updateXSearchEnabled(enabled)` → `dataStore.edit { X_SEARCH_ENABLED_KEY = enabled }` then `syncQueue.markPendingSync()`

If `updateSearchEnabled` and `updateSearchProvider` are called in the same save (the `onSaveSearchSkillSettings` path writes both), the second `markPendingSync()` is idempotent — the queue is a boolean.

## 5. Permissions & Config Delta

No Android runtime permission or manifest changes. No Gradle dependency changes. No new DAO, no new Room tables — this is consistent with the schema-lock rule for transient/state-machine data.

## 6. Constitution Audit

- Clean Architecture / dependency direction: queue and handler live in `:core:data`. The domain port (`SettingsRepository`) is unchanged. `:feature:settings` and `:app` do not gain imports from `:core:data`'s sync internals. The new `SearchToolSettingsRecord` is `internal` to `:core:data` and never appears in domain APIs.
- Repository pattern, mapping & threading: `SearchToolSettingsSyncQueue` is the only new "data source" for the handler. The handler maps Firestore → `SearchToolSettingsRecord` → `SettingsData`-shaped DataStore writes inside the repository, in line with `DATA_LAYER_RULES.md` §4.
- Schema lock: no new tables, no new columns. The DataStore boolean is not a Room table.
- Hilt: queue is `@Singleton` provided by `DataModule`. The `FirestoreSyncDependenciesFacade` constructor gains two new parameters (`searchToolSettingsSyncQueue: SearchToolSettingsSyncQueue`, `settingsRepository: SettingsRepository`); existing 13-arg constructor becomes 15-arg — within Detekt's 16-parameter limit. If Detekt complains, the fix is the existing pattern of moving the new args into the secondary private constructor and threading through `forTests`.
- AndroJack (per `AGENTS.md` §3.2): the builder will run `mcp_androjack_android_code_validator` against every generated Kotlin file. The plan flags this as a runtime gate, not a planning one.
- Tests: JUnit 5 + Turbine + MockK (or `FakeSettingsRepository` for the queue→repository round-trip), per `UNIT_TESTING.md` and `CODE_STYLE_RULES.md` §8.1.

## 7. Vault Unlock / Listener Ordering

`FirestoreListenerCoordinator.startListening()` already returns early when `!ctx.cryptoManager.hasCachedKey()`. The new handler rides this same listener — it does not open its own. The plan confirms this in writing (see §2 Component Boundaries) and one of the test scenarios (S-LISTENER-001) asserts that the listener does not call `SearchToolSettingsSyncHandler.upsertLocal` while the vault is locked, by feeding a `CryptoManagerPort` that returns `false` from `hasCachedKey()` and observing that `applyLiveEvents` is a no-op.

## 8. Cross-Spec Dependencies

- Depends on the existing `api_keys` collection contract via the same `ApiKeyManager` shape (used as the model for `SearchToolSettingsSyncQueue`).
- No changes to `:feature:settings` UI, `:feature:inference`, or the chat persistence model.
- Does not touch `/contracts/`. If implementation discovers a contract gap (e.g., the dependency-direction rule needs an explicit clause for internal repositories receiving a sync record), the builder escalates with a "Contract Waiver needed" note rather than editing the contract.

## 9. Ordered Implementation Tasks

Each task references the scenario IDs from `test_spec.md` that it satisfies.

1. **Create `SearchToolSettingsRecord` data class** (internal) with the four fields. Satisfies: S-ROUNDTRIP-001 (type existence).
2. **Create `SearchToolSettingsSyncQueue`** with `markPendingSync`, `claimPending`, `clearClaimedSync`, `isPending`. DataStore key `search_tool_settings_pending_sync`. Satisfies: S-QUEUE-001, S-QUEUE-002, S-QUEUE-003, S-QUEUE-004.
3. **Modify `SettingsRepositoryImpl`**:
   - Inject `SearchToolSettingsSyncQueue` via `@Inject` constructor.
   - Add `internal suspend fun applyRemoteSearchToolSettings(record, overwrite)`.
   - Add one-line `syncQueue.markPendingSync()` call to `updateSearchEnabled`, `updateXSearchEnabled`, `updateSearchProvider`. Satisfies: S-LOCAL-001, S-LOCAL-002, S-LOCAL-003, S-LOCAL-004, S-REMOTEAPPLY-001, S-REMOTEAPPLY-002, S-REMOTEAPPLY-003, S-REMOTEAPPLY-004.
4. **Modify `FirestoreSyncContext` and `FirestoreSyncDependencies`** to expose `searchToolSettingsSyncQueue` and `settingsRepository`. Satisfies (no scenario by itself, but the wiring step is required by S-HANDLER-001 through S-HANDLER-006).
5. **Create `SearchToolSettingsSyncHandler`** mirroring `ApiKeySyncHandler`. `getClaimedForSync` returns a one-element list of `SearchToolSettingsRecord` when `claimPending()` returned `true`; otherwise empty. `toFirestore` writes `search_enabled`, `x_search_enabled`, `search_provider`, `updated_at`. `fromFirestore` returns null on missing fields. `upsertLocal` calls `ctx.settingsRepository.applyRemoteSearchToolSettings(record, overwrite = overwrite)` and respects the same-device-echo skip. Satisfies: S-ROUNDTRIP-001, S-ROUNDTRIP-002, S-ROUNDTRIP-003, S-REMOTEAPPLY-001, S-REMOTEAPPLY-002, S-REMOTEAPPLY-003, S-REMOTEAPPLY-004, S-ECHO-001.
6. **Modify `FirestoreSyncAdapterRegistry`** to register `SearchToolSettingsSyncHandler(ctx)`. Satisfies: S-HANDLER-001.
7. **Modify `FirestoreSyncDependencyGraph`** to add `"search_tool_settings" to NO_PARENTS`. Satisfies: S-HANDLER-002.
8. **Modify `FirestoreDataSource.FirestoreSyncDependenciesFacade`** primary constructor to inject `searchToolSettingsSyncQueue` and `settingsRepository`; pass them through to `FirestoreSyncDependencies.toContext()`. Satisfies: S-HANDLER-001, S-HANDLER-003.
9. **Modify `DataModule`** to provide `SearchToolSettingsSyncQueue` as `@Singleton` taking the existing settings `DataStore<Preferences>`. Satisfies: S-HANDLER-001.
10. **Modify `FirestoreDataSourceTestFactory`** (`createTestFirestoreDataSource`) and `FirestoreSyncDependenciesFacade.forTests` to accept and thread the new queue + repository port, defaulting to fakes so existing tests pass without modification. Satisfies: S-ROUNDTRIP-001 through S-ECHO-001 (the test scaffolding).
11. **Create `SearchToolSettingsSyncHandlerTest`** covering round-trip (handler → Firestore → handler), vault gate, same-device echo, three-fields-not-one (mutation defense), provider-only change, and a no-op local default. Satisfies: S-ROUNDTRIP-001, S-ROUNDTRIP-002, S-ROUNDTRIP-003, S-ECHO-001, S-LISTENER-001, S-MUTATION-001, S-MUTATION-002, S-MUTATION-003.
12. **Create `SearchToolSettingsSyncQueueTest`** covering pending → claim → clear cycle, idempotent `markPendingSync`, and `claimPending` returning `false` when nothing is pending. Satisfies: S-QUEUE-001 through S-QUEUE-004.
13. **Create or extend `SettingsRepositoryImplSyncTest`** with: local `updateSearchEnabled(true)` makes the queue pending, `updateSearchProvider(EXA)` makes the queue pending, `updateXSearchEnabled(false)` makes the queue pending, `applyRemoteSearchToolSettings(...)` writes the keys but does not mark the queue pending. Satisfies: S-LOCAL-001 through S-LOCAL-004 and S-REMOTEAPPLY-001 through S-REMOTEAPPLY-004.
14. **Run scoped validation**: `./gradlew :core:data:testDebugUnitTest --tests "*SearchToolSettings*"` and `./gradlew :core:data:testDebugUnitTest --tests "*SettingsRepositoryImplSync*"`, then broaden to `./gradlew testDebugUnitTest` if the change touched any other module's tests. Run `./gradlew ktlintCheck` and `./gradlew detekt` for the affected modules.

## 10. Out of Scope

- The TTS provider toggle, the memories toggle, the theme toggle, and the background-inference toggle are NOT in scope. Each of those gets its own ticket if/when sync is requested.
- No telemetry or analytics around how often the settings flip or which device authored the latest version. `updated_at` is written for the future, not for a present feature.
- No bulk "sync everything" / "purge this account" affordance in settings UI.
