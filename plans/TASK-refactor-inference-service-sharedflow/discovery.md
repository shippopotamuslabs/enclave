# Discovery: Refactor InferenceService To SharedFlow

## 1. Goal Summary
Refactor the MOA (Crew) inference pipeline communication from `BroadcastReceiver` to `SharedFlow`. This involves updating `InferenceService` to emit progress and chunk updates via a singleton `InferenceEventBus` and refactoring `InferenceServicePipelineExecutor` to collect these events directly, improving performance for high-frequency token streaming.

## 2. Target Module Index
Unified view of existing code analyzed in the MOA pipeline module.

### Existing Data Models
- `feature/moa-pipeline-worker/src/main/kotlin/com/browntowndev/pocketcrew/feature/moa/InferenceEventBus.kt`: `InferenceEventBus` — Singleton intended for `SharedFlow` events. Currently holds `Intent` and is unused.
- `feature/moa-pipeline-worker/src/main/kotlin/com/browntowndev/pocketcrew/feature/moa/InferenceServicePipelineExecutor.kt`: `InferenceServicePipelineExecutor` — Implements `PipelineExecutorPort`. Current listener of `BroadcastReceiver`.
- `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/model/chat/MessageGenerationState.kt`: `MessageGenerationState` — Domain model for state emissions.

### Dependencies & API Contracts
- `InferenceService`: Foreground service emitting broadcasts.
- `PipelineExecutorPort`: Domain interface implemented by `InferenceServicePipelineExecutor`.
- `LocalBroadcastManager`: Current IPC mechanism for progress updates.

### Utility/Shared Classes
- `feature/moa-pipeline-worker/src/main/kotlin/com/browntowndev/pocketcrew/feature/moa/service/InferenceServiceStarter.kt`: `InferenceServiceStarter` — Starts/stops `InferenceService`.
- `feature/moa-pipeline-worker/src/main/kotlin/com/browntowndev/pocketcrew/feature/moa/di/InferenceExecutorModule.kt`: Hilt module for `PipelineExecutorPort`.

### Impact Radius
- `InferenceService.kt`: Modify to inject `InferenceEventBus` and replace `broadcast` calls with `eventBus.emit()`.
- `InferenceServicePipelineExecutor.kt`: Remove `BroadcastReceiver` logic and collect from `eventBus.events`.
- `InferenceEventBus.kt`: Refactor to use `MessageGenerationState` instead of `Intent`.

## 3. Cross-Probe Analysis
### Overlaps Identified
- `InferenceService` and `InferenceServicePipelineExecutor` are the core components identified in all probes as needing modification.

### Gaps & Uncertainties
- **Event Scope:** Should `InferenceEventBus` use a `Pair<ChatId, MessageGenerationState>` like `ChatInferenceService` to ensure multiple concurrent (or sequential) chats don't leak events?
- **Buffer Capacity:** What is the ideal `extraBufferCapacity` for high-frequency token streaming? `ChatInferenceService` uses 64, while `InferenceEventBus` (unused) currently uses 1024.

### Conflicts (if any)
- None identified.

## 4. High-Impact Clarifying Questions
*None identified. Proceeding to Spec phase.*

## 5. Probe Coverage Summary
| Layer/Directory | Probe Agent | Key Findings |
|----------------|------------|-------------|
| feature/moa-pipeline-worker/.../moa | Discovery Synthesizer | Identified unused `InferenceEventBus` and `BroadcastReceiver` bottleneck in `InferenceService`. |
