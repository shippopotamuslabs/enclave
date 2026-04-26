# Test Specification: Fix Inference Cancellation Stuckness

## 1. Happy Path Scenarios

### Scenario: Immediate UI Cleanup on Cancellation
- **Given:** A chat inference is in progress (state is PROCESSING in both DB and Snapshot Port).
- **When:** `ACTION_STOP` is received by `ChatInferenceService`.
- **Then:** `activeChatTurnSnapshotPort.clear(key)` is called immediately.
- **Then:** `persistenceSession.flush(markIncompleteAsCancelled = true)` is called immediately.
- **Then:** The message state in the database transitions to `COMPLETE`.

## 2. Error Path & Edge Case Scenarios

### Scenario: Stop during Engine Loading
- **Given:** Inference is in `ENGINE_LOADING` state.
- **When:** User hits 'Stop'.
- **Then:** The same immediate cleanup logic applies, ensuring the message is marked `COMPLETE` even if no text was generated.

## 3. Mutation Defense
### Lazy Implementation Risk
The most likely lazy implementation is only calling `currentJob.cancel()` and relying on the `catch` block to handle cleanup. This fails when the job is blocked by native code (e.g. LiteRT) and doesn't hit a suspension point.

### Defense Scenario
- **Given:** An inference job that is currently blocked in a non-suspending native call.
- **When:** `stopInference()` is called.
- **Then:** The snapshot port must be cleared and the DB must be updated via `flush(true)` BEFORE the job is confirmed cancelled, ensuring the UI reflects the stop action instantly.
