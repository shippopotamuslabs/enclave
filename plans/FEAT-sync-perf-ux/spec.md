# Specification: Firestore Sync Performance and Unlock Progress Improvements

This specification details the optimization of Firestore sync replay, debounced embedding worker scheduling, and cold-start unlock progress feedback without compromising sync order, database transaction safety, or background worker contracts.

## User Review Required

> [!NOTE]
> - **Firestore Count Queries**: We will use a Firestore aggregation count for an approximate startup progress denominator only. This count must not be relied upon for correctness. If the aggregation query fails, the sync process must continue unimpeded and fallback to an indeterminate progress state.
> - **Embedding Parallelism**: We do not assume the concrete `EmbeddingEnginePort` implementation is thread-safe. If a concurrency capability is added, it must default to `1`, and parallel embedding generation is only performed when a concrete engine explicitly reports a value greater than `1`.
> - **Worker Progress**: Background worker progress must be written through `CoroutineWorker.setProgress()` using WorkManager progress data keys. We decouple the worker from direct singleton progress manager callbacks.
> - **Sync Ordering**: Firestore event application remains strictly sequence-ordered. Prefetching can fetch documents ahead, but `FirestoreSyncEventApplier.apply()` will advance `lastAppliedSequence` only after the local Room transaction commits.

---

## Component Breakdown

### 1. Data Sync Fetch Caching (`core/data`)
- **Document Cache**: Create a per-session cache that runs bounded concurrent fetches for the dependency closure (root documents and required parents iteratively) before applying the sync events.
- **Cache-First Local Store**: Update `RoomFirestoreSyncLocalStore` to decode from cached snapshots first. If a sensitive field repair requires a write-back to Firestore, the cache entry must be invalidated and refreshed first.
- **Feed Live Prefetch**: Integrate prefetching in the live listener coordinator to cache only contiguous event paths up to the first sequence gap.

### 2. Background Embedding Debouncing (`core/data`)
- **Debounced Scheduling**: Debounce the unique WorkManager scheduling call in `EmbeddingRegenerationCoordinator` by 500ms. Keep all pending message, memory, and document IDs stored in memory during the debounce window.
- **Worker Progress & Cancellation**: Transition `EmbeddingRegenerationWorker` to report progress via `setProgress()`. Add explicit `CancellationException` propagation, and keep embedding calls serialized unless the concrete engine explicitly supports concurrency.

### 3. Sync Progress Feedback & UI (`core/domain`, `app`, `core/ui`, `feature/settings`)
- **Domain Modeling**: Introduce `CloudSyncReplayProgress` in `:core:domain` to report applied counts, estimates, and fractions.
- **Unlock Integration**: Add progress callbacks to `StartCloudSyncListeningUseCase` and expose the progress state through `MainViewModel` to the unlock screen. Thread the progress parameters into `VaultUnlockContent` and render a visually compliant `LinearProgressIndicator`.

---

## File Structure

| Path | Action | Responsibility |
|---|---|---|
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/model/sync/CloudSyncReplayProgress.kt` | **Create** | Pure domain model representing the current sync replay progress. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/firestore/FirestoreSyncDocumentCache.kt` | **Create** | Stores prefetched `DocumentSnapshot` instances, computes dependency closures recursively, and handles fetches. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/port/repository/CloudSyncPort.kt` | **Modify** | Add `getRemainingEventsCount` method signature to fetch remaining events. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/port/inference/EmbeddingEnginePort.kt` | **Optional Modify** | Add a default `maxConcurrentEmbeddings` capability only if parallel worker execution is implemented; default must be serialized (`1`). |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/firestore/FirestoreSyncEventApplier.kt` | **Avoid modifying unless required** | Keep sequence ordering and transaction behavior unchanged; prefetch belongs in the cache/source-store wiring, not in the applier contract. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/firestore/FirestoreSyncProductionWiring.kt` | **Modify** | Wire cache lookup into `FirestoreBackedSyncSourceDocumentStore` and `RoomFirestoreSyncLocalStore`. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/FirestoreDataSource.kt` | **Modify** | Instantiate and manage the cache, handle page prefetch/clear, and query the remaining events count. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/remote/firestore/FirestoreListenerCoordinator.kt` | **Modify** | Prefetch only contiguous events in the live-drain window and clear the cache. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/sync/EmbeddingRegenerationCoordinator.kt` | **Modify** | Implement 500ms debouncing logic for worker execution requests. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/sync/EmbeddingRegenerationWorker.kt` | **Modify** | Propagate cancellation, implement parallel engine calls based on capability, and update progress via WorkManager. |
| `core/data/src/main/kotlin/com/browntowndev/enclave/core/data/sync/EmbeddingProgressManager.kt` | **Optional Modify** | If still used by UI, observe WorkManager unique work progress stream to update re-indexing status and progress. |
| `core/domain/src/main/kotlin/com/browntowndev/enclave/domain/usecase/security/StartCloudSyncListeningUseCase.kt` | **Modify** | Accept progress callbacks, query remaining count, and emit calculated progress states. |
| `app/src/main/kotlin/com/browntowndev/enclave/presentation/MainViewModel.kt` | **Modify** | Track and clear unlock sync progress in `AppStartupState.Ready` and pass callback to use case. |
| `core/ui/src/main/kotlin/com/browntowndev/enclave/core/ui/component/vault/VaultUnlockContent.kt` | **Modify** | Thread progress/status parameters, render M3 `LinearProgressIndicator` and status text with test tags. |
| `feature/settings/src/main/kotlin/com/browntowndev/enclave/feature/settings/components/UnlockVaultScreen.kt` | **Modify** | Thread progress and status parameters from screen wrapper to content body. |
| `core/data/src/test/kotlin/com/browntowndev/enclave/core/data/remote/firestore/FirestoreSyncDocumentCacheTest.kt` | **Create** | Unit tests for document cache, prefetching, and dependency closure calculations. |

---

## Type Signatures & API Contracts

### 1. `CloudSyncReplayProgress` (Domain Model)
```kotlin
package com.browntowndev.enclave.domain.model.sync

data class CloudSyncReplayProgress(
    val appliedCount: Long,
    val totalEstimate: Long?,
    val progressFraction: Float?, // null represents indeterminate state
    val status: String,
)
```

### 2. `CloudSyncPort`
```kotlin
package com.browntowndev.enclave.domain.port.repository

interface CloudSyncPort {
    // Existing methods omitted...
    suspend fun getRemainingEventsCount(): Result<Long>
}
```

### 3. `EmbeddingEnginePort`
```kotlin
package com.browntowndev.enclave.domain.port.inference

interface EmbeddingEnginePort {
    // Existing methods omitted...
    val maxConcurrentEmbeddings: Int
        get() = 1
}
```

### 4. `FirestoreSyncDocumentCache`
```kotlin
package com.browntowndev.enclave.core.data.remote.firestore

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

internal class FirestoreSyncDocumentCache(
    private val firestore: FirebaseFirestore,
    private val maxConcurrency: Int = 4,
) : FirestoreSyncSourceDocumentStore {
    // Public member signatures omitted in this plan: implementation must expose
    // prefetch, snapshot lookup, invalidation, cache clearing, and getDocument.
}
```

### 5. `StartCloudSyncListeningUseCase`
```kotlin
package com.browntowndev.enclave.domain.usecase.security

import com.browntowndev.enclave.domain.model.sync.CloudSyncReplayProgress

class StartCloudSyncListeningUseCase {
    suspend operator fun invoke(
        uploadLocalFirst: Boolean = true,
        onProgress: ((CloudSyncReplayProgress) -> Unit)? = null,
    ): Result<Unit>
}
```

---

## Ordered Implementation Tasks

### Phase 1: Data Sync Fetch Caching
1. **Create `FirestoreSyncDocumentCache.kt`**: Implement `FirestoreSyncSourceDocumentStore`, support bounded concurrent fetches using a `Semaphore`, compute parent closures recursively via `FirestoreSyncDependencyGraph`, and support snapshot lookups, invalidation, and clearing.
   - *Satisfies*: `[SCENARIO-01]`, `[SCENARIO-02]`
2. **Modify `FirestoreSyncProductionWiring.kt`**:
   - Update `FirestoreBackedSyncSourceDocumentStore` to accept the cache and perform cache-first lookup, falling back to Firestore if missing.
   - Update `RoomFirestoreSyncLocalStore` to accept the cache, check the cache in `upsert` before fetching from Firestore, and invalidate the cached path before decoding if sensitive field repair writes merged data back to Firestore.
   - *Satisfies*: `[SCENARIO-01]`, `[SCENARIO-03]`, `[SCENARIO-04]`, `[SCENARIO-19]`
3. **Modify `FirestoreDataSource.kt`**:
   - Add `getRemainingEventsCount()` implementation to query aggregate count of user sync events where `sequence > lastAppliedSequence`.
   - Build a single shared `FirestoreSyncDocumentCache` instance in `buildProductionApplier()`.
   - In `syncRemoteChangesFromCloud()`, prefetch events in the page, and clear the cache in a `finally` block for each page.
   - *Satisfies*: `[SCENARIO-01]`, `[SCENARIO-05]`, `[SCENARIO-06]`, `[SCENARIO-07]`
4. **Modify `FirestoreListenerCoordinator.kt`**:
   - In `applyLiveEvents`, determine the contiguous event sequence list starting at `lastApplied + 1`, request prefetch for those events, and clear the cache once the drain operation completes.
   - *Satisfies*: `[SCENARIO-08]`
5. **Create `FirestoreSyncDocumentCacheTest.kt`**: Write unit tests validating dependency closure fetching, bounded parallelism, and invalidations.
   - *Satisfies*: `[SCENARIO-01]`, `[SCENARIO-02]`, `[SCENARIO-03]`, `[SCENARIO-04]`

### Phase 2: Background Embedding & Re-indexing
6. **Optionally modify `EmbeddingEnginePort.kt`**: Define a default `maxConcurrentEmbeddings: Int` read-only capability property with `get() = 1` only if worker parallelism is implemented.
   - *Satisfies*: `[SCENARIO-14]`, `[SCENARIO-15]`
7. **Modify `EmbeddingRegenerationCoordinator.kt`**:
   - Replace immediate unique work scheduling with a debounced scheduling coroutine job.
   - Wait 500ms after an enqueue request. Additional enqueue events cancel the pending job and schedule a new 500ms wait.
   - *Satisfies*: `[SCENARIO-09]`, `[SCENARIO-10]`
8. **Modify `EmbeddingRegenerationWorker.kt`**:
   - Replace direct `progressManager` calls with `setProgress()`.
   - Add explicit try-catch blocks to catch and rethrow `CancellationException`.
   - Keep embedding calls sequential by default. Implement structured parallel embedding generation via a coroutine `Semaphore` only if the engine capability exists and reports a value greater than 1.
   - Skip malformed/undecryptable document blocks without failing the worker execution.
   - *Satisfies*: `[SCENARIO-11]`, `[SCENARIO-12]`, `[SCENARIO-13]`, `[SCENARIO-14]`, `[SCENARIO-15]`
9. **Modify `EmbeddingProgressManager.kt` only if needed by existing UI consumers**:
   - If `EmbeddingProgressPort` remains the UI-facing abstraction, observe WorkManager progress for `"enclave_embedding_regen"` outside the worker. Do not have the worker call the singleton manager directly.
   - *Satisfies*: `[SCENARIO-12]`
10. **Extend Coordinator & Worker Tests**:
    - Update `EmbeddingRegenerationCoordinatorTest` to verify 500ms debounce using virtual time dispatchers.
    - Update `EmbeddingRegenerationWorkerTest` to verify thread-safe execution bounds, cancellation recovery, and progress updates.
    - *Satisfies*: `[SCENARIO-09]`, `[SCENARIO-10]`, `[SCENARIO-11]`, `[SCENARIO-12]`, `[SCENARIO-13]`, `[SCENARIO-14]`, `[SCENARIO-15]`

### Phase 3: Sync Progress Feedback & UI
11. **Create `CloudSyncReplayProgress.kt`**: Define the progress data class in the domain layer.
    - *Satisfies*: `[SCENARIO-16]`, `[SCENARIO-17]`
12. **Modify `StartCloudSyncListeningUseCase.kt`**:
    - Accept `onProgress` callback parameter.
    - Fetch the remaining count before replay. Report initial indeterminate state if it is unavailable or zero.
    - After each page is applied, compute cumulative applied count, update estimated total, and invoke `onProgress` with calculated fractions.
    - *Satisfies*: `[SCENARIO-05]`, `[SCENARIO-16]`, `[SCENARIO-17]`
13. **Modify `MainViewModel.kt`**:
    - Add `unlockSyncProgress: Float?` and `unlockSyncStatus: String?` fields to `AppStartupState.Ready`.
    - Pass the progress listener callback in `startCloudSyncAndListen`.
    - Explicitly clear progress and status fields on sync success, sync failure, and in the unlock `finally` block.
    - *Satisfies*: `[SCENARIO-16]`, `[SCENARIO-17]`
14. **Modify UI Screens**:
    - Thread progress parameters from `UnlockVaultScreen` into `VaultUnlockContent`.
    - Render a `LinearProgressIndicator` when `isUnlocking` is true. Override `gapSize = 0.dp`, `drawStopIndicator = {}`, and apply parent clip to prevent canvas bleeding. Show status text with semantic tags.
    - *Satisfies*: `[SCENARIO-18]`
15. **Extend UI and UseCase Tests**:
    - Update `StartCloudSyncListeningUseCaseTest` and `MainViewModelTest` to assert progress callbacks, estimate adjustments, and state clearing.
    - Update Compose UI tests to verify indicator/status visibility and indeterminate state fallbacks.
    - *Satisfies*: `[SCENARIO-05]`, `[SCENARIO-16]`, `[SCENARIO-17]`, `[SCENARIO-18]`
