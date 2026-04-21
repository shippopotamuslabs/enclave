# Technical Specification: Refactor InferenceService To SharedFlow

## 1. Objective
Optimizing the MOA inference pipeline by replacing the inefficient `BroadcastReceiver` mechanism in `InferenceService` with a more performant `SharedFlow` implementation for streaming text chunks and progress updates.

Acceptance Criteria:
- `InferenceService` emits all progress and text chunks to `InferenceEventBus`.
- `InferenceServicePipelineExecutor` collects from `InferenceEventBus` instead of listening for broadcasts.
- `LocalBroadcastManager` and `BroadcastReceiver` are completely removed from these components.
- Zero functional regression in UI updates (thinking chunks, text chunks, completion, errors).

## 2. System Architecture

### Target Files
- [MODIFY] [InferenceEventBus.kt](file:///home/sean/Code/pocket-crew/feature/moa-pipeline-worker/src/main/kotlin/com/browntowndev/pocketcrew/feature/moa/InferenceEventBus.kt)
- [MODIFY] [InferenceService.kt](file:///home/sean/Code/pocket-crew/feature/moa-pipeline-worker/src/main/kotlin/com/browntowndev/pocketcrew/feature/moa/service/InferenceService.kt)
- [MODIFY] [InferenceServicePipelineExecutor.kt](file:///home/sean/Code/pocket-crew/feature/moa-pipeline-worker/src/main/kotlin/com/browntowndev/pocketcrew/feature/moa/InferenceServicePipelineExecutor.kt)

### Component Boundaries
- `InferenceEventBus`: Acts as the central reactive pipe within the MOA module. It is a Singleton injected into both the `InferenceService` (producer) and `InferenceServicePipelineExecutor` (consumer).
- `InferenceService`: Foreground service that performs the pipeline logic. It will no longer use `Intent` broadcasts for internal communication.
- `InferenceServicePipelineExecutor`: Implements `PipelineExecutorPort`. It will subscribe to the `InferenceEventBus` and map events to the domain-expected `Flow<MessageGenerationState>`.

## 3. Data Models & Schemas
- `MessageGenerationState`: Reused from `:core:domain` as the payload for `InferenceEventBus`.
- `InferenceEventBus` Payload: `Pair<String, MessageGenerationState>` where `String` is the `chatId`. This aligns with the pattern used in `ChatInferenceService`.

## 4. API Contracts & Interfaces
- `InferenceEventBus`:
    - `val events: SharedFlow<Pair<String, MessageGenerationState>>`
    - `fun emit(chatId: String, state: MessageGenerationState)` (suspend)
    - `fun tryEmit(chatId: String, state: MessageGenerationState)` (non-blocking)

## 5. Permissions & Config Delta
No permissions or config changes.

## 6. Constitution Audit
This design adheres to the project's core architectural rules by:
- Preserving clean separation between domain ports (`PipelineExecutorPort`) and feature implementation.
- Using `SharedFlow` for high-frequency streaming as per modern Kotlin/Android best practices.
- Leveraging Hilt for singleton lifecycle management.

## 7. Cross-Spec Dependencies
No cross-spec dependencies.
