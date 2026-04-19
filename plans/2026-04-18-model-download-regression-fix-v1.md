# Model Download Regression Analysis & Fix Plan

## Objective
Diagnose and plan fixes for four regressions in the model download pipeline:
1. Status bars jumping/flickering in ModelDownloadScreen
2. Downloads reporting failures
3. Downloads don't auto-start on WiFi after notification permission
4. Downloads are 20-30x slower than before (speed improves after first file completes)

## Root Cause Analysis

### Issue 1: Status bars jumping/flickering

**Root Cause: `animateFloatAsState` on per-file progress**

In `ModelDownloadScreen.kt:521-524`, each `FileProgressItem` uses:
```kotlin
val animatedProgress by animateFloatAsState(
    targetValue = progress(),
    label = "fileProgress"
)
```

The `progress()` lambda resolves `file.progress` which is `bytesDownloaded.toFloat() / totalBytes`. When multiple files are being actively downloaded **concurrently** (all started via `async` in the worker), their progress bars are ALL animating simultaneously. When the `currentDownloads` list is replaced on each progress update (in `DownloadStateManager.applyProgressUpdate`), Compose may re-order or re-key items, causing `animateFloatAsState` to jump between values as it animates toward the target.

Additionally, the overall `DownloadHeader` progress uses `animateFloatAsState` at line 330-333. When progress updates come in for individual files, the overall percentage recalculates, causing visible jumps.

**Compounding factor**: The `FileProgressList` uses `key = { it.displayName }` (line 428). Since display names can be shared across model types that share the same physical file (e.g., "Qwen 3 8B" for multiple model configs), keys could collide, causing Compose to confuse items.

### Issue 2: Downloads reporting failures

**Root Cause: `channel.force(true)` on every 8KB chunk causes I/O errors and timeouts**

In `HttpFileDownloader.kt:136-137`:
```kotlin
channel.write(byteBuffer)
channel.force(true)  // fsync after EVERY 8KB chunk
```

This `force(true)` calls `fsync()` on every single 8KB read from the network. For a 5GB file, this means ~625,000 fsync calls. Each `fsync` blocks until physical disk write completes (typically 5-15ms on flash storage). This:
- **Massive per-chunk latency**: 5-15ms × 625,000 = 3,125–9,375 seconds of JUST fsync wait time
- During fsync, the network read buffer fills up, potentially causing socket timeouts
- On devices with slow eMMC storage, this causes `SocketTimeoutException` or `SocketException` (connection reset) because the network buffer overflows while the thread is stuck in fsync
- This explains both the failures AND the extreme slowness

The same pattern exists in `downloadWithRetry` at line 248.

### Issue 3: Downloads don't auto-start on WiFi after notification permission

**Root Cause: `autoStartDownloads = false` + race with `isInForeground` flag**

In `ModelDownloadScreen.kt:97-99`:
```kotlin
factory.create(modelsResult, errorMessage, autoStartDownloads = false)
```

The ViewModel's `init` block only calls `startDownloads()` when `autoStartDownloads && modelsToDownload.isNotEmpty()`. Since it's `false`, downloads never start automatically from `init`.

The ViewModel then relies on `checkModels()` being called from the UI. Looking at the LaunchedEffect in `ModelDownloadScreen.kt:158-178`:
- If `!hasNotificationPermission`: launches permission request dialog
- On grant: calls `viewModel.checkModels()` which calls `startDownloads()`
- If already has permission: calls `viewModel.checkModels()` IF status is IDLE or ERROR

But `startDownloads()` at line 192-201 has this guard:
```kotlin
if (!isInForeground) {
    hasPendingDownloadCheck = true
    return@launch  // BLOCKS THE DOWNLOAD
}
```

The `isInForeground` flag is set by `onAppForegrounded()` which runs in a `LaunchedEffect`. There's a **race condition**: the permission dialog callback fires, calls `checkModels()` → `startDownloads()`, but the `ON_RESUME` lifecycle event may not have fired yet if the dialog callback happens before the lifecycle observer. The result: `isInForeground` is `false`, the download is blocked, and it's set as pending.

When `onAppForegrounded()` is called and `hasPendingDownloadCheck` is true, it calls `checkModels()` again (not `startDownloads()` directly). But `checkModels()` only starts downloads if `modelsToDownload.isNotEmpty()` — which may or may not work depending on state.

Also, even if the permission IS already granted, the `else` branch at line 173-176:
```kotlin
val currentStatus = downloadState.status
if (currentStatus == DownloadStatus.IDLE || currentStatus == DownloadStatus.ERROR) {
    viewModel.checkModels()
}
```
This only starts if the current status is IDLE or ERROR. If the app freshly initialized to CHECKING status (which `initializeWithStartupResult` sets), this condition is false and downloads never start.

### Issue 4: 20-30x slower downloads, speeds up after first file completes

**Root Cause: Same as Issue 2 — `channel.force(true)` on every 8KB chunk**

The `force(true)` (fsync) after every 8KB write is the primary cause. Each fsync blocks for disk I/O completion:
- Without fsync: 8KB network read → memory write → next read (microseconds)
- With fsync: 8KB network read → memory write → **5-15ms fsync wait** → next read

For a 5GB file: ~625,000 fsync calls × ~10ms = ~6,250 seconds (~104 minutes) of blocking
Expected speed without fsync: ~50-100 MB/s on WiFi 6
Observed speed with per-chunk fsync: ~2-5 MB/s

**Why does speed improve after the first file completes?** The first file downloads sequentially. Once it finishes, the `completedBytes` accumulator no longer blocks on that file's progress callback. The remaining files benefit from the already-warmed filesystem cache and reduced lock contention on the `progressTracker`. But they're still hampered by the fsync-per-chunk pattern.

**Additional factor**: The `progressChannel.trySend(Unit)` and `shouldUpdateProgress()` throttle at 500ms intervals, but the progress callback itself (`onProgress`) is called on every chunk AND updates the `ConcurrentHashMap` state. Each state update triggers a full `serializeToWorkData()` call (serialization of all file states to strings) when the channel is consumed.

## Implementation Plan

### Fix 1: Eliminate per-chunk fsync in HttpFileDownloader (CRITICAL — fixes issues 2 & 4)

- [ ] In `HttpFileDownloader.kt` download method (line ~131-142), remove `channel.force(true)` from the per-chunk loop. Replace with periodic flushing: only call `channel.force(true)` every N megabytes (e.g., every 64MB or every 8192 chunks) to balance data durability with performance. Keep the final `channel.force(true)` before hash verification.
- [ ] In `HttpFileDownloader.kt` `downloadWithRetry` method (line ~238-253), apply the same periodic flushing pattern — remove per-chunk `channel.force(true)` and add periodic flushing.
- [ ] Add a constant `SYNC_INTERVAL_BYTES = 64L * 1024 * 1024` (64MB) to control how often to fsync during download. After each chunk write, check if `totalBytesRead % SYNC_INTERVAL_BYTES < bytesRead` to determine if it's time to sync.
- [ ] Keep `channel.force(true)` call after the download loop completes (before closing the channel) to ensure data is durable before SHA-256 verification.

### Fix 2: Fix auto-start gating logic (fixes issue 3)

- [ ] In `ModelDownloadScreen.kt`, change `autoStartDownloads = false` to `autoStartDownloads = true` for the case when the user already has notification permission. The ViewModel initialization should start downloads immediately if the permission is already granted and the app is in foreground.
- [ ] Keep `autoStartDownloads = false` for the case when notification permission needs to be requested (so downloads don't start until after the dialog interaction).
- [ ] Update the `LaunchedEffect(Unit)` logic in `ModelDownloadScreen.kt:
  - If permission already granted and status is IDLE/CHECKING/ERROR: call `viewModel.checkModels()` (not just IDLE/ERROR)
  - This also covers the race condition where orchestrator status hasn't settled yet

- [ ] In `DownloadViewModel.startDownloads()`, change the foreground guard to retry automatically rather than just setting `hasPendingDownloadCheck`. When `isInForeground` is false, use a short delay and retry instead of requiring a lifecycle callback. OR, simplify by removing the foreground guard since WorkManager already handles `ForegroundServiceStartNotAllowedException` with `Result.retry()`.

### Fix 3: Fix progress bar flickering/jumping (fixes issue 1)

- [ ] In `DownloadHeader` composable (line 330-343), replace `animateFloatAsState` with `animateFloatAsState` that has a shorter animation duration or use `Spring` spec with low stiffness to reduce visible jumps.
- [ ] In `FileProgressItem` composable (line 521-524), replace `animateFloatAsState` for file progress with a simpler approach: use `animateFloatAsState` with `AnimationSpec` that has a very short duration (200ms) or use `snapIn` for small changes and smooth for large changes.
- [ ] In the `FileProgressList` composable (line 428), change key from `it.displayName` to `it.sha256` to prevent key collisions when files share display names.
- [ ] In `DownloadStateManager.applyProgressUpdate()` (line 43-46), the "preserve currentDownloads when update returns empty list" logic could cause stale data. Add a check: if the update's `currentDownloads` is non-null but empty AND the file states from the worker have zero progress, treat it as a legitimate clear rather than preserving old data.

### Fix 4: Fix "first file never shows as finished" (additional symptom from user)

- [ ] In `ModelDownloadWorker.downloadSpec()`, after marking a file as `FileStatus.COMPLETE` (line 323-328), the progress update via `progressChannel.trySend(Unit)` should trigger a `setProgress()` call that includes the COMPLETED status. Verify that `serializeToWorkData()` correctly serializes `FileStatus.COMPLETE` so the UI receives it.
- [ ] In `WorkProgressParser.parseRunning()`, verify that when a file has `status=COMPLETE` in the `FILES_PROGRESS` array, it gets parsed and forwarded to the UI. Currently, `parseRunning` only handles the `DOWNLOADING` case as the overall status. Files that completed mid-batch should still show as COMPLETE.
- [ ] In `ModelDownloadScreen.kt`, the `FileProgressItem` composable only shows a progress bar when `file.status == FileStatus.DOWNLOADING || file.status == FileStatus.PAUSED` (line 518). Files with `COMPLETE` status should show the checkmark and no progress bar. Verify the icon mapping at line 471-477 correctly shows `CheckCircle` for COMPLETE files.

## Verification Criteria

- [ ] Downloads complete at expected device speed (50-100 MB/s on WiFi 6, not 2-5 MB/s)
- [ ] No fsync calls during download chunks; only periodic (every 64MB) and final sync
- [ ] Downloads auto-start on WiFi after granting notification permission without manual intervention
- [ ] Downloads auto-start when notification permission was already granted
- [ ] Progress bars don't flicker/jump when multiple files download concurrently
- [ ] Completed files show checkmark icon and progress bar disappears
- [ ] Failed downloads show error state with retry option
- [ ] Download speed is consistently fast throughout the entire download, not just after first file

## Potential Risks and Mitigations

1. **Risk: Less frequent fsync means potential data loss on crash**
   - Mitigation: Temp files with `.tmp` extension already protect against partial reads. The SHA-256 hash verification at the end of download ensures integrity. If the app crashes mid-download, the temp file is resumed from the last successfully written position (already handled by `existingBytes` parameter). 64MB sync interval is a reasonable trade-off between durability and performance.

2. **Risk: Removing foreground guard could cause WorkManager foreground service failures**
   - Mitigation: `ModelDownloadWorker.doWork()` already catches `ForegroundServiceStartNotAllowedException` and returns `Result.retry()`. WorkManager will retry the work when the app returns to foreground. The foreground guard was overly conservative and caused the auto-start bug.

3. **Risk: Changing progress bar animation could make progress updates feel less smooth**
   - Mitigation: Use spring-based animation with appropriate damping rather than removing animation entirely. Short-duration tween (200ms) is visually similar to "instant" but avoids the jarring snap of raw float values.

4. **Risk: Auto-start on permission grant could fail if orchestrator not yet initialized**
   - Mitigation: The orchestrator's `initializeWithStartupResult()` is called during app startup before the download screen appears. The ViewModel receives `modelsResult` which contains the scan results. If the orchestrator hasn't initialized, `startDownloads()` will throw, which is caught by the viewModelScope error handler.

## Alternative Approaches

1. **Alternative for fsync: Write to buffer, flush in batches**
   - Instead of modifying fsync frequency, could use a `BufferedOutputStream` wrapping the `FileChannel` and only flush periodically. This is more complex and doesn't actually solve the problem since `force(true)` on the channel is the bottleneck, not the write itself.

2. **Alternative for auto-start: Use a one-shot coroutine that polls for readiness**
   - Instead of relying on lifecycle callbacks and permission dialogs, could have the ViewModel start a coroutine that periodically checks if conditions are met (foreground + permission) and auto-starts. This is more robust but adds complexity.

3. **Alternative for flickering: Use `derivedStateOf` to reduce recompositions**
   - Could wrap the progress calculations in `derivedStateOf` to reduce the frequency of state changes that trigger recompositions. This has less control than animation tuning and doesn't address the root cause of stale state preservation in `applyProgressUpdate`.