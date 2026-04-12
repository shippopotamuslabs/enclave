# Technical Specification: Google and Local Envelope Search Skill

## 1. Objective
Implement the shared local tool-call contract and the remaining Kotlin-only Step 1 runtime support. This ticket must add prompt composition for local models, wire Google into a tool-capable path, and teach LiteRT and MediaPipe to detect a full `<tool_call>...</tool_call>` envelope, execute the shared search executor, inject the result back into the same session, and continue generation without exposing raw tool JSON.

Acceptance criteria:
- `SearchToolPromptComposer` produces the canonical local envelope instructions and `GenerateChatResponseUseCase` uses it only for local runtimes.
- `InferenceFactoryImpl` extends the shared executor wiring from `13A` into Google, LiteRT, and MediaPipe.
- `GoogleInferenceServiceImpl` executes a tool-capable request path with function declarations and one tool replay.
- LiteRT and MediaPipe complete one envelope-driven tool loop in the same conversation.
- Stored prompt templates and persisted messages remain unchanged.

## 2. System Architecture

### Target Files
- Create `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/SearchToolPromptComposer.kt`
- Modify `core/domain/src/main/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/GenerateChatResponseUseCase.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/InferenceFactoryImpl.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/GoogleRequestMapper.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/GoogleInferenceServiceImpl.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/LiteRtInferenceServiceImpl.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/ConversationManagerImpl.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/ConversationImpl.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/MediaPipeInferenceServiceImpl.kt`
- Create `core/domain/src/test/kotlin/com/browntowndev/pocketcrew/domain/usecase/chat/SearchToolPromptComposerTest.kt`
- Modify `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/GoogleRequestMapperTest.kt`
- Create `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/GoogleInferenceServiceImplTest.kt`
- Modify `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/LiteRtInferenceServiceImplTest.kt`
- Modify `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/LiteRtInferenceServiceImplGenerationOptionsTest.kt`
- Modify `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/ConversationImplTest.kt`
- Create `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/MediaPipeInferenceServiceImplTest.kt`

### Component Boundaries
`SearchToolPromptComposer` is the only place that knows the literal local envelope format. `GenerateChatResponseUseCase` remains responsible for applying that contract to the runtime prompt while keeping stored prompt configuration unchanged. `feature/inference` owns runtime control flow. `GoogleRequestMapper` and `GoogleInferenceServiceImpl` translate the shared tool definition into Google function declarations and handle a one-call replay path. `LiteRtInferenceServiceImpl`, `MediaPipeInferenceServiceImpl`, `ConversationManagerImpl`, and `ConversationImpl` own streaming detection, tool execution, result injection, and continuation.

## 3. Data Models & Schemas
Reuse the contracts from `13A`: `ToolDefinition`, `ToolCallRequest`, `ToolExecutionResult`, `ToolExecutorPort`, and the `GenerationOptions` search fields. Add no new persistence models. `SearchToolPromptComposer` must generate a runtime instruction string containing:

```text
<tool_call>{"name":"tavily_web_search","arguments":{"query":"..."}}</tool_call>
```

The composer must preserve any existing configured system prompt text and append the local tool contract without mutating the stored template source.

## 4. API Contracts & Interfaces
`GenerateChatResponseUseCase` keeps its public signature unchanged. For local search-capable models it must set the effective runtime prompt to the configured system prompt plus the `SearchToolPromptComposer` contract. For non-local models handled here, prompt composition remains unchanged.

`GoogleRequestMapper` must expose function declarations for `tavily_web_search`. `GoogleInferenceServiceImpl` must:
- use the tool-capable path when search is enabled
- execute exactly one `ToolExecutorPort` request per turn
- replay the tool result before returning final text
- throw `IllegalStateException` instead of silently retrying through the streaming-only path if the tool path fails before final text

`LiteRtInferenceServiceImpl` and `MediaPipeInferenceServiceImpl` must:
- detect a complete local envelope
- suppress the raw envelope from the visible output stream
- execute the shared executor
- inject the result back into the current conversation
- resume generation until assistant text is complete

Typed failures:
- malformed envelope -> `IllegalStateException`
- unknown tool name -> `IllegalArgumentException`
- missing or blank `query` -> `IllegalArgumentException`
- Google tool-path failure before final response -> `IllegalStateException`

## 5. Permissions & Config Delta
No permissions or config changes. No manifest edits, Gradle changes, ProGuard changes, settings changes, or network-module changes are allowed in this ticket.

## 6. Constitution Audit
This design adheres to the project's core architectural rules by keeping prompt composition in `:core:domain`, runtime loops in `:feature:inference`, and persistence and settings concerns out of scope while preserving the existing chat-storage and inference-port boundaries.

## 7. Cross-Spec Dependencies
Depends on `13A-shared-and-remote-search-skill`. `13C-llama-search-tool-bridge` depends on this ticket's local envelope contract but is otherwise isolated to the llama stack. `13D-real-tavily-and-settings` depends on the stable search contract established by `13A`.
