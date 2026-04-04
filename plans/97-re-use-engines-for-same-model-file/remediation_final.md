# Remediation Plan: SHA-256 Engine Reuse & Constitutional Compliance

This plan addresses the findings from the initial PR feedback and the Principal Auditor's discovery for ticket #97.

## 1. Objective
Correct the engine reuse implementation so a single SHA-based service correctly serves multiple roles (FAST/THINKING) with role-specific outputs and proper lifecycle accounting, while strictly adhering to the project's architectural constraints (no `runBlocking`, specific exceptions).

## 2. Implementation Steps

### Phase 1: Break Role-Binding in Service Implementations
- **`LlmInferencePort`**: Ensure `sendPrompt` implementations return events tagged with the `modelType` provided at request time.
- **`LlamaInferenceServiceImpl`**: 
    - Remove `modelType` from the constructor.
    - Update `sendPrompt` and `ensureModelLoaded` to use the `modelType` passed at request time.
    - **Constitutional Fix**: Replace `runBlocking` in `closeSession` and `clearConversation` with proper coroutine management (e.g., using the engine's dedicated executor).
- **`LiteRtInferenceServiceImpl` & `MediaPipeInferenceServiceImpl`**:
    - Remove `modelType` from constructor.
    - Implement the `sendPrompt(..., options: GenerationOptions, ...)` overload to respect reasoning budgets and token limits.

### Phase 2: Correct Lifecycle & Reference Counting
- **`InferenceFactoryImpl`**:
    - Fix the double-counting bug: `getInferenceService` should not increment if `registerUsage` is also called.
    - Ensure `releaseUsage` is the single source of truth for closing service sessions.
    - Audit `LlamaChatSessionManager` for potential state clobbering when roles are swapped for the same model.

### Phase 3: Domain & Use Case Refinement
- **`GenerateChatResponseUseCase`**:
    - Remove redundant `registerUsage` calls.
    - **Constitutional Fix**: Replace generic `Exception` catching with specific domain/engine exceptions.
- **`InferenceEvent`**: Ensure `modelType` is correctly propagated to the UI accumulator.

### Phase 4: Configuration & UI
- **`model_config.json`**: Correct the "thinking" preset label (line 102).

## 3. Verification Plan
- **Tests**: Update `InferenceFactoryImplShaReuseTest.kt` to verify role-specific event emission and refcount stability.
- **Manual**: Verify Settings UI labels and engine load logs in Logcat.
