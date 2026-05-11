# Test Specification: Log Native Teardown Failures (BUG-01)

## 1. Happy Path Scenarios
These scenarios verify that when a teardown failure occurs, it is captured in the logs.

### Scenario: Engine Initialization Cancellation Teardown Logged
- **Given:** `ConversationManagerImpl` is initializing a LiteRT `Engine`.
- **When:** A `CancellationException` occurs and `candidate?.close()` throws a `RuntimeException("Teardown failed")`.
- **Then:** `Log.w` is called with message "Failed to close engine during cancellation cleanup" and the exception.

### Scenario: Engine Initialization Failure Teardown Logged
- **Given:** `ConversationManagerImpl` is initializing a LiteRT `Engine`.
- **When:** A `Throwable` occurs during `initialize()` and `candidate?.close()` throws a `RuntimeException("Teardown failed")`.
- **Then:** `Log.w` is called with message "Failed to close engine during error cleanup" and the exception.

### Scenario: Inference Reset Teardown Logged
- **Given:** `LiteRtInferenceServiceImpl` is performing inference.
- **When:** An inference error occurs requiring engine reset, and `conversationManager.closeEngine()` throws a `RuntimeException("Teardown failed")`.
- **Then:** `Log.w` is called with message "Failed to close engine during inference reset" and the exception.

## 2. Error Path & Edge Case Scenarios
These scenarios verify that logging does not interfere with existing exception propagation.

### Scenario: Initialization Exception Re-thrown
- **Given:** `ConversationManagerImpl` initialization fails with `IllegalStateException("Init failed")`.
- **When:** Teardown also fails and is logged.
- **Then:** The original `IllegalStateException` (or its wrapped version) is still thrown to the caller.

## 3. Mutation Defense
### Lazy Implementation Risk
The most likely lazy implementation is replacing the empty catch with a log call that misses the exception object or provides no context, or accidentally changing the exception propagation logic (e.g., catching the original exception and not re-throwing).

### Defense Scenario
- **Given:** An initialization error occurred.
- **When:** Teardown fails.
- **Then:** Verify that the original error is still propagated AND the log contains the teardown exception stack trace.
