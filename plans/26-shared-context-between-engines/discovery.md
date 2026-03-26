# Discovery: Shared Context Between Engines

## Objective
Analyze the current codebase to understand how conversation history is handled across different LLM inference engines (Llama.cpp, LiteRT, MediaPipe) and determine what changes are necessary to support conversation history for LiteRT and MediaPipe models.

## Current State Analysis
- **Entry Point**: `GenerateChatResponseUseCase.kt` is the domain use case responsible for orchestrating chat generation. It currently calls `rehydrateHistory()` which fetches existing messages and passes them to `LlmInferencePort.setHistory(messages)`.
- **Llama.cpp Backend (`LlamaInferenceServiceImpl.kt`)**: Fully implements `setHistory(messages)`. The history is loaded into the `LlamaChatSessionManager`.
- **LiteRT Backend (`LiteRtInferenceServiceImpl.kt`)**: The `setHistory(messages)` implementation is currently a placeholder (no-op). It delegates its conversation state management to `ConversationManagerPort`.
- **MediaPipe Backend (`MediaPipeInferenceServiceImpl.kt`)**: The `setHistory(messages)` implementation is also a placeholder. It uses MediaPipe's `LlmInferenceSession` which implicitly manages its own state for multi-turn chats, but doesn't currently seed it with historical messages.

## Framework Capabilities & Requirements

### 1. Google AI Edge (LiteRT)
- The underlying engine is accessed via `com.google.ai.edge.litertlm.Engine` and `Conversation`.
- To instantiate a `Conversation` with history, the framework uses a `ConversationConfig` object.
- **Key Insight**: `ConversationConfig` accepts a `preface` parameter (a list of `Message` objects) to seed the conversation history. 
- **Required Changes**:
  - `ConversationManagerPort` must be extended to accept the history list.
  - `ConversationManagerImpl` must cache these messages and translate domain `ChatMessage` objects into the SDK's `Message` objects.
  - When creating a new `Conversation` instance in `getConversation()`, it must populate the `ConversationConfig.preface` with the cached history.

### 2. MediaPipe LlmInference
- The underlying API used is `com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession`.
- `LlmInferenceSession` naturally maintains context for successive prompts.
- **Key Insight**: There is no direct initialization parameter to "set history" upon session creation. Instead, history must be iteratively built using `session.addQueryChunk()`. 
- **Required Changes**:
  - `MediaPipeInferenceServiceImpl` needs to cache the domain history when `setHistory` is called.
  - When the session is created or before generating a response, the cached messages must be formatted and fed into the session using `addQueryChunk(text)`, possibly combined with the new prompt to form a full contextual state before triggering `generateResponseAsync()`.

## Scope Classification
Based on the defined workflows:
- **Files Modified**: 
  - `core/domain/.../port/inference/ConversationManagerPort.kt`
  - `feature/inference/.../ConversationManagerImpl.kt`
  - `feature/inference/.../LiteRtInferenceServiceImpl.kt`
  - `feature/inference/.../MediaPipeInferenceServiceImpl.kt`
- **Complexity**: The changes involve crossing the domain and feature boundary to manage stateful sessions within two different third-party inference engines.
- **Workflow Recommendation**: **Medium Change** (CFAW approach), requiring a formal spec, test-driven implementation (Gherkin/Unit tests), and rigorous code review.