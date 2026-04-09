# Technical Specification: Llama Search Tool Bridge

## 1. Objective
Implement the search-skill bridge for the llama.cpp stack. This ticket must detect the shared local tool envelope during native token decoding, surface one parsed tool request to Kotlin, execute the shared search executor, inject the tool result back into the active llama session, and resume assistant generation without clearing conversation state.

Acceptance criteria:
- `LlamaInferenceServiceImpl` and `InferenceFactoryImpl` accept the shared executor and local tool contract from earlier tickets.
- `token_processor.hpp` can detect one complete `<tool_call>...</tool_call>` block incrementally.
- `llama-jni.cpp` bridges the parsed request to Kotlin and accepts the result payload for replay.
- `JniLlamaEngine` and `LlamaChatSessionManager` resume generation in the same session after replay.
- Raw tool JSON never reaches the user-visible output stream.

## 2. System Architecture

### Target Files
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/InferenceFactoryImpl.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/LlamaInferenceServiceImpl.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/llama/LlamaChatSessionManager.kt`
- Modify `feature/inference/src/main/kotlin/com/browntowndev/pocketcrew/feature/inference/llama/JniLlamaEngine.kt`
- Modify `llama-android/src/main/cpp/llama-jni.cpp`
- Modify `llama-android/src/main/cpp/token_processor.hpp`
- Modify `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/llama/JniLlamaEngineGenerateWithOptionsTest.kt`
- Modify `feature/inference/src/test/kotlin/com/browntowndev/pocketcrew/feature/inference/llama/LlamaChatSessionManagerTest.kt`
- Modify `llama-android/src/main/cpp/test_token_processor.cpp`

### Component Boundaries
`LlamaInferenceServiceImpl` remains the Android-facing inference entry point. `LlamaChatSessionManager` owns conversation continuity and any resume-oriented session operations needed after a tool replay. `JniLlamaEngine` owns the Kotlin side of the callback bridge and result handoff. `token_processor.hpp` owns safe incremental detection of a complete envelope, while `llama-jni.cpp` owns the native callback and token injection path. No persistence or settings logic belongs here.

## 3. Data Models & Schemas
Reuse the local envelope contract established in `13B`:

```text
<tool_call>{"name":"tavily_web_search","arguments":{"query":"..."}}</tool_call>
```

Reuse `ToolCallRequest`, `ToolExecutionResult`, `ToolExecutorPort`, and the fixed JSON result contract from `13A`. No new persistence models, Room entities, or settings models are added in this ticket.

## 4. API Contracts & Interfaces
`InferenceFactoryImpl` must pass the shared `ToolExecutorPort` into the llama service path. `LlamaInferenceServiceImpl` must enable tool-aware generation only when the shared feature flag and local prompt contract are present.

`JniLlamaEngine` must expose a Kotlin callback boundary that:
- receives one parsed tool request from native code
- validates that the tool name is `tavily_web_search`
- executes the shared executor exactly once per turn
- returns the result payload to native code for replay

`llama-jni.cpp` and `token_processor.hpp` must:
- buffer tokens until a complete envelope is detected
- stop visible emission of the raw envelope
- hand the parsed tool request to Kotlin
- inject the returned result into the active context
- continue decoding without resetting session state

Typed failures:
- malformed envelope -> `IllegalStateException`
- unknown tool name -> `IllegalArgumentException`
- missing or blank `query` -> `IllegalArgumentException`
- replay failure or session resume failure -> `IllegalStateException`

## 5. Permissions & Config Delta
No permissions or config changes. No manifest edits, Gradle changes, settings changes, ProGuard changes, or network-module changes are allowed in this ticket.

## 6. Constitution Audit
This design adheres to the project's core architectural rules by isolating llama-specific runtime and JNI work to the llama stack, reusing the shared domain tool contracts from earlier tickets, and keeping persistence, settings, and network concerns out of the native bridge.

## 7. Cross-Spec Dependencies
Depends on `13A-shared-and-remote-search-skill` for shared tool contracts and executor wiring. Depends on `13B-google-and-local-envelope-search-skill` for the canonical local envelope contract.
