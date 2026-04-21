# Test Specification: Refactor InferenceService To SharedFlow

## 1. Happy Path Scenarios

### Scenario: Streaming Text Chunks via SharedFlow
- **Given:** An active `InferenceService` and an `InferenceServicePipelineExecutor` collecting from the `InferenceEventBus`.
- **When:** `InferenceService` emits a `GeneratingText("Hello", modelType)` event to the bus for `chatId "123"`.
- **Then:** `InferenceServicePipelineExecutor.execute()` emits `MessageGenerationState.GeneratingText("Hello", modelType)` to its collector.

### Scenario: Pipeline Step Completion via SharedFlow
- **Given:** An active `InferenceService`.
- **When:** `InferenceService` emits a `StepCompleted` state for a specific step.
- **Then:** The `InferenceServicePipelineExecutor` receives and forwards this state correctly.

## 2. Error Path & Edge Case Scenarios

### Scenario: Inference Error Handling
- **Given:** A pipeline execution in progress.
- **When:** `InferenceService` encounters an exception and emits `MessageGenerationState.Failed(exception, modelType)`.
- **Then:** The `InferenceServicePipelineExecutor` emits the `Failed` state and completes the flow.

### Scenario: Chat ID Mismatch Filtering
- **Given:** An `InferenceServicePipelineExecutor` observing `chatId "123"`.
- **When:** `InferenceEventBus` receives an event for `chatId "456"`.
- **Then:** The executor for `chatId "123"` does NOT emit any update.

## 3. Mutation Defense

### Lazy Implementation Risk
A lazy implementation might forget to remove the `BroadcastReceiver` registration code while adding the `SharedFlow` logic, leading to duplicate emissions or resource leaks if both paths are active.

### Defense Scenario
- **Given:** The `InferenceServicePipelineExecutor` has been started.
- **When:** A local broadcast is sent with a `BROADCAST_CHUNK` action.
- **Then:** The `InferenceServicePipelineExecutor` should NOT emit any state update (verifying that the `BroadcastReceiver` is truly removed/inactive).
