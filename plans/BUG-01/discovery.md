# Discovery: Log Native Teardown Failures (BUG-01)

## 1. Goal Summary
The goal is to eliminate "silent failures" during the teardown of native LiteRT components in `ConversationManagerImpl` and `LiteRtInferenceServiceImpl`. By replacing empty catch blocks with structured logging, we will gain visibility into why engine initialization or cancellation might be failing, which is currently masked by suppressed exceptions.

## 2. Target Module Index
Unified view of the inference feature components affected by this change.

### Existing Data Models
- `ConversationSignature`: Private data class in `ConversationManagerImpl.kt` for tracking state configuration.
- `ChatRequestKey`: Key for active chat streams in `InferenceEventBus.kt`.
- `ImagePayload`, `VisionRequestPayload`: Data classes for vision-related processing (`ImagePayloads.kt`).
- `Entry`: Private data class in `ActiveChatTurnStore.kt` for managing chat turn snapshots.

### Dependencies & API Contracts
- **LiteRT (com.google.ai.edge.litertlm)**: Core native library providing `Engine` and `Conversation` classes which require proper `close()` handling.
- **LoggingPort**: Domain-level logging interface.
- **ToolExecutorPort**: Executing native tool calls.
- **ActiveModelProviderPort**, **LocalModelRepositoryPort**: Model resolution and repository access.
- **ConversationManager**: Internal interface for managing LLM lifecycles.
- **LiteRtConversation**: Wrapper for the native `Conversation` interface.

### Utility/Shared Classes
- `AndroidLoggingAdapter`: Bridge used to route domain `LoggingPort` calls to `android.util.Log`. (Identified by Inference Probe)
- `ActiveChatTurnStore`: Singleton managing active chat snapshots. (Identified by Inference Probe)
- `ImageDownscaler`: Utility for image pre-processing. (Identified by Inference Probe)

### Impact Radius
- `ConversationManagerImpl.kt`: **High Impact.** Primary location of empty catch blocks during `candidate?.close()` in the `initializeEngine` method (Lines 942, 946, 952-953).
- `LiteRtInferenceServiceImpl.kt`: **Medium Impact.** Contains an empty catch block during `conversationManager.closeEngine()` in `attemptInference` (Line 156).
- `Logging Strategy`: Modifications must align with existing use of both `android.util.Log` and `LoggingPort`.

## 3. Cross-Probe Analysis
### Overlaps Identified
- N/A: Only a single probe (Inference Layer) was executed for this focused bug fix.

### Gaps & Uncertainties
- **Logging Consistency**: The codebase uses both `android.util.Log` and `loggingPort`. We need to determine if teardown failures—which are infrastructure/native level—should be logged via the platform `Log.e` for immediate visibility in logcat or via the domain `LoggingPort` for potential remote reporting.
- **Exception Context**: It's unclear if the teardown failures provide actionable diagnostic info (like LiteRT error codes) or if they are generic `IllegalStateException`s.

### Conflicts (if any)
- None identified.

## 4. High-Impact Clarifying Questions
*None identified. Proceeding to Spec phase.*

## 5. Probe Coverage Summary
| Layer/Directory | Probe Agent | Key Findings |
|----------------|------------|-------------|
| feature/inference | Inference Probe | Identified 4+ specific locations of empty catch blocks in `ConversationManagerImpl` and `LiteRtInferenceServiceImpl`. |
